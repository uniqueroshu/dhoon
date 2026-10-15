package com.dhoon.music;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
    private WebView web;
    private WebView ytWeb;
    private LinearLayout ytPanel;
    private TextView ytTitle;
    private LinearLayout ytBar;
    private TextView ytMiniBtn, ytCloseBtn;
    private boolean ytMini = false;
    private int ytDock = 0;
    private TextView ytMoveBtn;
    private ValueCallback<Uri[]> fileCb;
    private static WeakReference<WebView> webRef;
    private static boolean lastPlaying = false;

    public static void sendCmd(final String c) {
        final WebView w = webRef == null ? null : webRef.get();
        if (w != null) w.post(() -> w.evaluateJavascript("window.dhoonCmd && dhoonCmd('" + c + "')", null));
    }

    public class Bridge {
        @JavascriptInterface
        public String appVersion() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return ""; }
        }
        @JavascriptInterface
        public void onTrack(String title, String artist, String art, int duration) {
            MediaBridgeService.updateTrack(MainActivity.this, title, artist, art, duration);
        }
        @JavascriptInterface
        public void onState(boolean playing) {
            lastPlaying = playing;
            MediaBridgeService.updateState(MainActivity.this, playing);
        }
        @JavascriptInterface
        public void onProgress(int posSec) {
            MediaBridgeService.updateProgress(MainActivity.this, posSec * 1000L);
        }
        @JavascriptInterface
        public void ytPlay(final String id, final String title) {
            runOnUiThread(() -> showYt(id, title));
        }
        @JavascriptInterface
        public void ytLoad(final String id, final String title) {
            runOnUiThread(() -> { showYt(id, title); setYtMini(true); });
        }
        @JavascriptInterface
        public void ytCtl(final String cmd) {
            if (cmd == null || !cmd.matches("play|pause|seek:[0-9.]{1,9}")) return;
            runOnUiThread(() -> { if (ytWeb != null) ytWeb.evaluateJavascript("window.ytc&&ytc('" + cmd + "')", null); });
        }
        @JavascriptInterface
        public void ytClose() {
            runOnUiThread(() -> hideYt());
        }
        @JavascriptInterface
        public void openUrl(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) { /* no browser */ }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#121212"));
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().getDecorView().setSystemUiVisibility(
                0);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        web = new WebView(this);
        webRef = new WeakReference<>(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Playlist poster"), 11);
                } catch (Exception e) {
                    fileCb.onReceiveValue(null);
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "DhoonBridge");
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        buildYt(root);
        setContentView(root);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 11) {
            if (fileCb != null) {
                Uri[] out = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
                fileCb.onReceiveValue(out);
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // Official YouTube embedded player (visible, with its own controls). Plays only while the panel is open
    // and the app is in the foreground. No stream extraction, no hidden/background playback.
    private void buildYt(FrameLayout root) {
        ytPanel = new LinearLayout(this);
        ytPanel.setOrientation(LinearLayout.VERTICAL);
        ytPanel.setBackgroundColor(Color.BLACK);
        ytPanel.setVisibility(View.GONE);
        ytPanel.setClickable(true);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(8), dp(10));
        ytTitle = new TextView(this);
        ytTitle.setTextColor(Color.WHITE);
        ytTitle.setTextSize(14);
        ytTitle.setSingleLine(true);
        ytTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(ytTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ytBar = bar;
        TextView mini = new TextView(this);
        mini.setText("Minimise");
        mini.setTextColor(Color.WHITE);
        mini.setTextSize(15);
        mini.setPadding(dp(14), dp(8), dp(14), dp(8));
        mini.setOnClickListener(v -> setYtMini(!ytMini));
        ytMiniBtn = mini;
        bar.addView(mini, new LinearLayout.LayoutParams(-2, -2));
        TextView mv = new TextView(this);
        mv.setText("Move");
        mv.setTextColor(Color.WHITE);
        mv.setTextSize(13);
        mv.setPadding(dp(8), dp(8), dp(8), dp(8));
        mv.setVisibility(View.GONE);
        mv.setOnClickListener(v -> { ytDock = 1 - ytDock; setYtMini(true); });
        ytMoveBtn = mv;
        bar.addView(mv, new LinearLayout.LayoutParams(-2, -2));
        TextView close = new TextView(this);
        ytCloseBtn = close;
        close.setText("Close");
        close.setTextColor(Color.WHITE);
        close.setTextSize(15);
        close.setPadding(dp(16), dp(8), dp(16), dp(8));
        close.setOnClickListener(v -> hideYt());
        bar.addView(close, new LinearLayout.LayoutParams(-2, -2));
        ytPanel.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        ytWeb = new WebView(this);
        WebSettings ys = ytWeb.getSettings();
        ys.setJavaScriptEnabled(true);
        ys.setDomStorageEnabled(true);
        ys.setMediaPlaybackRequiresUserGesture(false);
        ytWeb.setBackgroundColor(Color.BLACK);
        ytWeb.addJavascriptInterface(new Object() {
            @JavascriptInterface public void onState(int st) { fwd("s", st, 0); }
            @JavascriptInterface public void onTime(double cur, double dur) { fwd("t", cur, dur); }
            @JavascriptInterface public void onError(int code) { fwd("e", code, 0); }
        }, "YTB");
        ytWeb.setWebViewClient(new WebViewClient());
        ytWeb.setWebChromeClient(new WebChromeClient());
        int w = getResources().getDisplayMetrics().widthPixels;
        ytPanel.addView(ytWeb, new LinearLayout.LayoutParams(-1, Math.max(dp(200), w * 9 / 16)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.TOP;
        root.addView(ytPanel, lp);
    }

    // Mini mode: small docked corner video (still visible, official player). Tap "Expand" to restore, "X" to close.
    private void fwd(String k, double a, double b) {
        final String js = "window.dhoonYt&&dhoonYt('" + k + "'," + a + "," + b + ")";
        if (web != null) web.post(() -> web.evaluateJavascript(js, null));
    }

    private void setYtMini(boolean mini) {
        if (ytPanel == null) return;
        ytMini = mini;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ytPanel.getLayoutParams();
        LinearLayout.LayoutParams wl = (LinearLayout.LayoutParams) ytWeb.getLayoutParams();
        if (mini) {
            lp.width = dp(200);
            lp.height = -2;
            if (ytDock == 0) {
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.setMargins(0, dp(64), dp(8), 0);
            } else {
                lp.gravity = Gravity.BOTTOM | Gravity.END;
                lp.setMargins(0, 0, dp(8), dp(150));
            }
            wl.height = dp(200);
            ytMoveBtn.setVisibility(View.VISIBLE);
            ytTitle.setVisibility(View.GONE);
            ytBar.setPadding(dp(2), dp(0), dp(0), dp(0));
            ytBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ytMiniBtn.setText("Expand");
            ytMiniBtn.setTextSize(13);
            ytCloseBtn.setText("X");
            ytCloseBtn.setTextSize(13);
        } else {
            int w = getResources().getDisplayMetrics().widthPixels;
            lp.width = -1;
            lp.height = -2;
            lp.gravity = Gravity.TOP;
            lp.setMargins(0, 0, 0, 0);
            wl.height = Math.max(dp(200), w * 9 / 16);
            ytMoveBtn.setVisibility(View.GONE);
            ytTitle.setVisibility(View.VISIBLE);
            ytBar.setPadding(dp(16), dp(10), dp(8), dp(10));
            ytMiniBtn.setText("Minimise");
            ytMiniBtn.setTextSize(15);
            ytCloseBtn.setText("Close");
            ytCloseBtn.setTextSize(15);
        }
        ytPanel.setLayoutParams(lp);
        ytWeb.setLayoutParams(wl);
    }

    private void showYt(String id, String title) {
        if (!id.matches("[A-Za-z0-9_-]{6,20}")) return;
        if (ytMini) setYtMini(false);
        ytTitle.setText(title == null ? "" : title);
        ytPanel.setVisibility(View.VISIBLE);
        String html = "<html><body style='margin:0;background:#000'><div id='p'></div>"
                + "<style>#p,iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var pl;function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{videoId:'" + id + "',"
                + "playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,controls:1,enablejsapi:1,origin:'https://com.dhoon.music'},"
                + "events:{onReady:function(e){e.target.playVideo();},"
                + "onStateChange:function(e){YTB.onState(e.data);},onError:function(e){YTB.onError(e.data);}}});"
                + "setInterval(function(){try{if(pl&&pl.getCurrentTime)YTB.onTime(pl.getCurrentTime(),pl.getDuration());}catch(x){}},500);}"
                + "function ytc(c){if(!pl)return;if(c=='play')pl.playVideo();else if(c=='pause')pl.pauseVideo();else if(c.indexOf('seek:')==0)pl.seekTo(parseFloat(c.slice(5)),true);}"
                + "</script></body></html>";
        ytWeb.loadDataWithBaseURL("https://com.dhoon.music/", html, "text/html", "utf-8", null);
        ytWeb.onResume();
    }

    private void hideYt() {
        if (ytPanel == null) return;
        if (ytMini) setYtMini(false);
        ytPanel.setVisibility(View.GONE);
        ytWeb.loadUrl("about:blank");
        fwd("x", 0, 0);
    }

    @Override
    protected void onPause() {
        // The video player must not keep playing once the app leaves the screen.
        if (ytPanel != null && ytPanel.getVisibility() == View.VISIBLE) ytWeb.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ytPanel != null && ytPanel.getVisibility() == View.VISIBLE) ytWeb.onResume();
    }

    private long lastBackMs;

    @Override
    public void onBackPressed() {
        if (ytPanel != null && ytPanel.getVisibility() == View.VISIBLE && !ytMini) { setYtMini(true); return; }
        web.evaluateJavascript(
                "window.dhoonBack?window.dhoonBack():false",
                value -> {
                    if ("true".equals(value)) return;
                    long now = System.currentTimeMillis();
                    if (now - lastBackMs < 2500) {
                        MainActivity.super.onBackPressed();
                    } else {
                        lastBackMs = now;
                        android.widget.Toast.makeText(MainActivity.this,
                                "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        // Keep the WebView alive while music is playing so background audio keeps going;
        // the foreground service holds the process and controls it via sendCmd.
        if (web != null && !lastPlaying) web.destroy();
        super.onDestroy();
    }
}
