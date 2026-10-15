package com.dhoon.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MediaBridgeService extends Service {
    private static final String CH = "playback";
    private static final int ID = 42;
    private static MediaSession session;
    private static String title = "dhoon", artist = "", artUrl = "";
    private static long durMs = 0;
    private static boolean playing = false;
    private static long posMs = 0;
    private static MediaBridgeService self;
    private static Bitmap art;
    private static boolean artLoading = false;

    public static void updateTrack(Context c, String t, String a, String u, int durSec) {
        title = t; artist = a; durMs = durSec * 1000L; playing = true; posMs = 0;
        if (u == null || !u.equals(artUrl)) { art = null; }
        artUrl = u == null ? "" : u;
        start(c);
    }

    public static void updateState(Context c, boolean p) {
        playing = p;
        start(c);
    }

    public static void updateProgress(Context c, long ms) {
        posMs = ms;
        if (self != null && session != null) self.applyState();
    }

    private static void start(Context c) {
        Intent i = new Intent(c, MediaBridgeService.class);
        i.setAction("render");
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? "render" : intent.getAction();
        if ("toggle".equals(a)) { MainActivity.sendCmd("toggle"); return START_STICKY; }
        if ("next".equals(a))   { MainActivity.sendCmd("next");   return START_STICKY; }
        if ("prev".equals(a))   { MainActivity.sendCmd("prev");   return START_STICKY; }
        ensureSession();
        render();
        if (art == null && !artLoading && artUrl.startsWith("http")) loadArt();
        return START_STICKY;
    }

    private void ensureSession() {
        if (session != null) return;
        session = new MediaSession(this, "dhoon");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay()  { MainActivity.sendCmd("toggle"); }
            @Override public void onPause() { MainActivity.sendCmd("toggle"); }
            @Override public void onSkipToNext()     { MainActivity.sendCmd("next"); }
            @Override public void onSkipToPrevious() { MainActivity.sendCmd("prev"); }
            @Override public void onSeekTo(long pos) {
                posMs = pos;
                MainActivity.sendCmd("seek:" + pos);
                applyState();
            }
        });
        session.setActive(true);
    }

    private PendingIntent act(String action, int req) {
        Intent i = new Intent(this, MediaBridgeService.class);
        i.setAction(action);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, req, i, f);
    }

    private void render() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(this, 9, open, pf);

        applyState();
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
        if (art != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(mb.build());

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
         .setContentTitle(title)
         .setContentText(artist)
         .setContentIntent(openPi)
         .setOngoing(playing)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .addAction(new Notification.Action(android.R.drawable.ic_media_previous, "Previous", act("prev", 1)))
         .addAction(new Notification.Action(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                 playing ? "Pause" : "Play", act("toggle", 2)))
         .addAction(new Notification.Action(android.R.drawable.ic_media_next, "Next", act("next", 3)));
        if (art != null) b.setLargeIcon(art);
        Notification.MediaStyle style = new Notification.MediaStyle().setMediaSession(session.getSessionToken());
        style.setShowActionsInCompactView(0, 1, 2);
        b.setStyle(style);
        startForeground(ID, b.build());
    }

    private void applyState() {
        if (session == null) return;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY |
                        PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, posMs, 1f)
                .build());
    }

    private void loadArt() {
        artLoading = true;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(artUrl).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                InputStream in = c.getInputStream();
                art = BitmapFactory.decodeStream(in);
                in.close();
            } catch (Exception ignored) {}
            artLoading = false;
            if (art != null) render();
        }).start();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        self = null;
        if (session != null) { session.setActive(false); session.release(); session = null; }
        super.onDestroy();
    }
}
