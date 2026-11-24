package com.gssuraha.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.onesignal.OneSignal;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_TARGET_URL = "target_url";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private View loadingOverlay;
    private ImageView loadingGifView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingGifView = findViewById(R.id.loadingGifView);

        if (loadingGifView != null) {
            try {
                Glide.with(this)
                        .asGif()
                        .load(R.drawable.loading_anim)
                        .into(loadingGifView);
            } catch (Throwable t) {
                Log.w(TAG, "GIF load fallback", t);
            }
        }

        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (webView != null) {
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            }
        } catch (Throwable ignored) {
        }

        if (webView != null) {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

            webView.addJavascriptInterface(new PortalBridge(), "GSSurahaApp");

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handleUri(view, request.getUrl());
                }

                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleUri(view, Uri.parse(url));
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (progressBar != null) {
                        progressBar.setProgress(newProgress);
                        if (newProgress >= 100) {
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(0xFF08783E, 0xFF005B35);
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (webView != null) webView.reload();
            });
        }

        GSSurahaApplication.setActiveCallback(targetUrl -> {
            if (targetUrl != null && !targetUrl.trim().isEmpty() && webView != null) {
                webView.loadUrl(targetUrl.trim());
            }
        });

        String startUrl = getInitialUrl(getIntent());
        if (webView != null) {
            webView.loadUrl(startUrl);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String nextUrl = getInitialUrl(intent);
        if (nextUrl != null && !nextUrl.isEmpty() && webView != null) {
            webView.loadUrl(nextUrl);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String pending = GSSurahaApplication.consumePendingUrl();
        if (pending != null && !pending.isEmpty() && webView != null) {
            webView.loadUrl(pending);
        }
    }

    @Override
    protected void onDestroy() {
        GSSurahaApplication.setActiveCallback(null);
        super.onDestroy();
    }

    private String getInitialUrl(Intent intent) {
        if (intent != null) {
            String extraUrl = intent.getStringExtra(EXTRA_TARGET_URL);
            if (extraUrl != null && !extraUrl.trim().isEmpty()) {
                return extraUrl.trim();
            }
            Uri data = intent.getData();
            if (data != null && ("https".equalsIgnoreCase(data.getScheme()) || "http".equalsIgnoreCase(data.getScheme()))) {
                return data.toString();
            }
        }
        return getString(R.string.portal_home_url);
    }

    private boolean handleUri(WebView view, Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        String portalDomain = getString(R.string.portal_domain).toLowerCase();

        if (("https".equals(scheme) || "http".equals(scheme)) && host.equals(portalDomain)) {
            view.loadUrl(uri.toString());
            return true;
        }

        if ("intent".equals(scheme)) {
            try {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (parsed.resolveActivity(getPackageManager()) != null) {
                    startActivity(parsed);
                    return true;
                }
                String fallbackUrl = parsed.getStringExtra("browser_fallback_url");
                if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                    view.loadUrl(fallbackUrl);
                    return true;
                }
            } catch (Exception ignored) {
            }
            return true;
        }

        if ("upi".equals(scheme) || "tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)) {
            try {
                Intent externalIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(externalIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Supported app not found on device", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        if ("https".equals(scheme) || "http".equals(scheme)) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(browserIntent);
            } catch (Exception ignored) {
            }
            return true;
        }

        return false;
    }

    public class PortalBridge {
        @android.webkit.JavascriptInterface
        public void setMemberExternalId(String memberCode) {
            try {
                if (memberCode != null && !memberCode.trim().isEmpty()) {
                    OneSignal.login(memberCode.trim());
                }
            } catch (Throwable ignored) {
            }
        }

        @android.webkit.JavascriptInterface
        public void logoutMember() {
            try {
                OneSignal.logout();
            } catch (Throwable ignored) {
            }
        }
    }
}
