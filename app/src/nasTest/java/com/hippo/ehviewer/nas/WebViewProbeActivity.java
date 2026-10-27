package com.hippo.ehviewer.nas;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Minimal, test-only WebView probe for ENC-07.
 *
 * It loads one URL from the intent extra "url", allows navigation only on the
 * same origin, disables file/content access and JS bridges, and cancels every
 * TLS error. External links are blocked and logged; the real restricted
 * WebView belongs to ENC-29.
 */
public class WebViewProbeActivity extends Activity {
    private static final String TAG = "ENC_WEBVIEW_PROBE";

    private WebView webView;
    private String allowedOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String url = intent == null ? null : intent.getStringExtra("url");
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "missing url extra");
            finish();
            return;
        }
        allowedOrigin = originOf(Uri.parse(url));
        if (allowedOrigin == null) {
            Log.e(TAG, "invalid url " + url);
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "page_finished " + url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e(TAG, "page_error " + error.getErrorCode() + " " + request.getUrl());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.e(TAG, "ssl_error primary=" + error.getPrimaryError() + " url=" + error.getUrl());
                handler.cancel();
            }
        });
        setContentView(webView);
        Log.i(TAG, "load " + url + " origin=" + allowedOrigin);
        webView.loadUrl(url);
    }

    private boolean handleUrl(Uri uri) {
        String origin = originOf(uri);
        if (origin != null && origin.equals(allowedOrigin)) {
            Log.i(TAG, "allow " + uri);
            return false;
        }
        Log.w(TAG, "block_external " + uri);
        return true;
    }

    private static String originOf(Uri uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        int port = uri.getPort();
        return scheme + "://" + host + (port == -1 ? "" : ":" + port);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
