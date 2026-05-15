package com.jisanhsajin.jisantv;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalOrientation;

    // For brightness and volume control
    private AudioManager audioManager;
    private int maxVolume;
    private float brightness = -1;
    private int lastVolume = -1;
    private float startY;
    private boolean isVolumeControl = false;
    private boolean isBrightnessControl = false;
    private boolean isFullScreenMode = false;

    private final String URL = "https://jisanhsajin.gt.tc/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        originalOrientation = getRequestedOrientation();

        // Initialize WebView
        webView = findViewById(R.id.webView);

        // Container for full-screen video
        customViewContainer = new FrameLayout(this);
        addContentView(customViewContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // WebView settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(false);

        // Inject JavaScript to disable video controls
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject JavaScript to disable double-tap and video controls
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(
                            "javascript:(function() {" +
                                    "   var videos = document.getElementsByTagName('video');" +
                                    "   for(var i = 0; i < videos.length; i++) {" +
                                    "       videos[i].controls = false;" +
                                    "       videos[i].disableRemotePlayback = true;" +
                                    "       videos[i].defaultPlaybackRate = 1.0;" +
                                    "       videos[i].playbackRate = 1.0;" +
                                    "   }" +
                                    "   document.addEventListener('dblclick', function(e) {" +
                                    "       e.preventDefault();" +
                                    "       e.stopPropagation();" +
                                    "   }, true);" +
                                    "})()", null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isConnected()) {
                    view.loadUrl(url);
                } else {
                    view.loadUrl("file:///android_asset/offline.html");
                    Toast.makeText(MainActivity.this, "You are offline.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // Handle full-screen video
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                isFullScreenMode = true;

                // Force landscape orientation
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                // Hide WebView and show video
                webView.setVisibility(View.GONE);

                // Add video to container
                customViewContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                customViewContainer.setVisibility(View.VISIBLE);

                customView = view;
                customViewCallback = callback;

                // Create a transparent overlay for touch events
                final View touchOverlay = new View(MainActivity.this);
                touchOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                touchOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                touchOverlay.setOnTouchListener(new View.OnTouchListener() {
                    private Handler handler = new Handler();
                    private Runnable hideToastRunnable;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                startY = event.getY();
                                isVolumeControl = event.getX() > (v.getWidth() / 2);
                                isBrightnessControl = event.getX() < (v.getWidth() / 2);

                                if (hideToastRunnable != null) {
                                    handler.removeCallbacks(hideToastRunnable);
                                }
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                float deltaY = startY - event.getY();
                                float percent = deltaY / v.getHeight() * 2; // Multiply for sensitivity

                                if (isBrightnessControl) {
                                    adjustBrightness(percent);
                                } else if (isVolumeControl) {
                                    adjustVolume(percent);
                                }
                                return true;

                            case MotionEvent.ACTION_UP:
                                showCurrentValues();
                                hideToastRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        // Toast will be hidden automatically
                                    }
                                };
                                handler.postDelayed(hideToastRunnable, 1500);
                                return true;
                        }
                        return true;
                    }
                });

                customViewContainer.addView(touchOverlay);

                // Hide system UI
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;

                isFullScreenMode = false;

                // Restore original orientation
                setRequestedOrientation(originalOrientation);

                // Remove all views from container
                customViewContainer.removeAllViews();
                customViewContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);

                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }

                customView = null;

                // Restore system UI
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        });

        // Load URL
        if (isConnected()) {
            webView.loadUrl(URL);
        } else {
            webView.loadUrl("file:///android_asset/offline.html");
            Toast.makeText(this, "No Internet - Showing cached page", Toast.LENGTH_LONG).show();
        }

        // Network callback
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest request = new NetworkRequest.Builder().build();
            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        if (!URL.equals(webView.getUrl())) {
                            webView.loadUrl(URL);
                            Toast.makeText(MainActivity.this, "Back online. Reloading...", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adjustBrightness(float percent) {
        try {
            float currentBrightness = brightness;
            if (currentBrightness < 0) {
                currentBrightness = getWindow().getAttributes().screenBrightness;
                if (currentBrightness < 0) {
                    currentBrightness = 0.5f;
                }
            }

            float newBrightness = currentBrightness + percent;
            if (newBrightness < 0.01f) newBrightness = 0.01f;
            if (newBrightness > 1.0f) newBrightness = 1.0f;

            brightness = newBrightness;

            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = newBrightness;
            getWindow().setAttributes(layoutParams);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adjustVolume(float percent) {
        try {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int adjustment = Math.round(percent * maxVolume);
            int newVolume = currentVolume + adjustment;

            if (newVolume < 0) newVolume = 0;
            if (newVolume > maxVolume) newVolume = maxVolume;

            lastVolume = newVolume;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showCurrentValues() {
        try {
            int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int volumePercent = (volume * 100) / maxVolume;

            float brightness = getWindow().getAttributes().screenBrightness;
            if (brightness < 0) brightness = 0.5f;
            int brightnessPercent = Math.round(brightness * 100);

            String message = "🔊 Volume: " + volumePercent + "% | ☀️ Brightness: " + brightnessPercent + "%";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}