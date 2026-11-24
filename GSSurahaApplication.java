package com.gssuraha.app;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;
import com.onesignal.notifications.INotificationClickEvent;
import com.onesignal.notifications.INotificationClickListener;

import org.json.JSONObject;

public class GSSurahaApplication extends Application {

    private static final String TAG = "GSSurahaApp";
    private static volatile String pendingNotificationUrl = null;
    private static NotificationOpenCallback activeCallback = null;

    public interface NotificationOpenCallback {
        void onOpenUrl(String url);
    }

    public static void setActiveCallback(NotificationOpenCallback callback) {
        activeCallback = callback;
        if (callback != null && pendingNotificationUrl != null && !pendingNotificationUrl.isEmpty()) {
            String urlToOpen = pendingNotificationUrl;
            pendingNotificationUrl = null;
            callback.onOpenUrl(urlToOpen);
        }
    }

    public static synchronized String consumePendingUrl() {
        String u = pendingNotificationUrl;
        pendingNotificationUrl = null;
        return u;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            String appId = getString(R.string.onesignal_app_id);
            if (appId != null && !appId.trim().isEmpty()) {
                OneSignal.getDebug().setLogLevel(LogLevel.NONE);
                OneSignal.initWithContext(this, appId.trim());
                OneSignal.getNotifications().requestPermission(false, Continue.none());
                OneSignal.getNotifications().addClickListener(new NotificationClickHandler());
            }
        } catch (Throwable t) {
            Log.e(TAG, "OneSignal init skipped safely", t);
        }
    }

    private class NotificationClickHandler implements INotificationClickListener {
        @Override
        public void onClick(INotificationClickEvent event) {
            try {
                String targetUrl = extractTargetUrl(event);
                if (targetUrl == null || targetUrl.trim().isEmpty()) {
                    targetUrl = getString(R.string.portal_notifications_url);
                }
                final String finalUrl = targetUrl;
                pendingNotificationUrl = finalUrl;

                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        if (activeCallback != null) {
                            pendingNotificationUrl = null;
                            activeCallback.onOpenUrl(finalUrl);
                        } else {
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            intent.putExtra(MainActivity.EXTRA_TARGET_URL, finalUrl);
                            intent.setData(Uri.parse(finalUrl));
                            startActivity(intent);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private String extractTargetUrl(INotificationClickEvent event) {
        try {
            if (event != null && event.getNotification() != null) {
                JSONObject additionalData = event.getNotification().getAdditionalData();
                if (additionalData != null) {
                    if (additionalData.has("url")) {
                        String u = additionalData.optString("url", "");
                        if (!u.isEmpty()) return u;
                    }
                    if (additionalData.has("target_url")) {
                        String u = additionalData.optString("target_url", "");
                        if (!u.isEmpty()) return u;
                    }
                    if (additionalData.has("route")) {
                        String r = additionalData.optString("route", "");
                        if (!r.isEmpty()) {
                            String base = getString(R.string.portal_home_url);
                            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                            return r.startsWith("http") ? r : (base + "/?route=" + r);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
