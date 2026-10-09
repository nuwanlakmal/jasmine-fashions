package com.gn.gpstripmeter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.Locale;

public class TripService extends Service implements LocationListener {
    public static final String ACTION_START = "com.gn.gpstripmeter.START";
    public static final String ACTION_PAUSE = "com.gn.gpstripmeter.PAUSE";
    public static final String ACTION_RESET = "com.gn.gpstripmeter.RESET";
    public static final String PREFS = "trip";
    public static final String KEY_METERS = "distance";
    public static final String KEY_ACTIVE = "tracking";
    private static final int NOTICE_ID = 17;
    private static final String CHANNEL = "gn_trip_gps";

    private SharedPreferences prefs;
    private LocationManager locationManager;
    private Location previous;
    private float distance;
    private boolean active;
    private long noticeTime;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        distance = prefs.getFloat(KEY_METERS, 0f);
        active = prefs.getBoolean(KEY_ACTIVE, false);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "GPS trip tracking",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Displays distance while driving");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null && !active) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Required within five seconds after Context.startForegroundService on Android 8+.
        startForeground(NOTICE_ID, makeNotice());
        noticeTime = SystemClock.elapsedRealtime();

        if (ACTION_PAUSE.equals(action)) {
            endTracking();
            return START_NOT_STICKY;
        }
        if (ACTION_RESET.equals(action)) {
            distance = 0f;
            previous = null;
            save();
            if (!active) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            updateNotice(true);
            return START_STICKY;
        }
        startTracking();
        return active ? START_STICKY : START_NOT_STICKY;
    }

    private Notification makeNotice() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("GN GPS Trip Meter")
                .setContentText(String.format(Locale.US, "%.2f km • Tracking on", distance / 1000f))
                .setOngoing(true)
                .setContentIntent(tap)
                .setShowWhen(false)
                .build();
    }

    private void updateNotice(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - noticeTime < 4000L) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTICE_ID, makeNotice());
        noticeTime = now;
    }

    private void startTracking() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            endTracking();
            return;
        }
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            endTracking();
            return;
        }
        active = true;
        previous = null; // Avoid joining the last point before pause/restart.
        save();
        try {
            locationManager.removeUpdates(this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 1f, this);
            updateNotice(true);
        } catch (SecurityException | IllegalArgumentException error) {
            endTracking();
        }
    }

    private void endTracking() {
        active = false;
        previous = null;
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        save();
        stopForeground(true);
        stopSelf();
    }

    private void save() {
        prefs.edit().putFloat(KEY_METERS, distance).putBoolean(KEY_ACTIVE, active).apply();
    }

    @Override public void onLocationChanged(Location fix) {
        if (!active || fix == null || !fix.hasAccuracy() || fix.getAccuracy() > 45f) return;
        if (previous != null) {
            float segment = previous.distanceTo(fix);
            long deltaMs = (fix.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos()) / 1000000L;
            float minimum = Math.max(5f, fix.getAccuracy() * 0.5f);
            // Reject jitter, huge GPS jumps, and impossible vehicle speeds.
            if (deltaMs > 0 && deltaMs < 60000L && segment >= minimum
                    && segment < 800f && (segment * 1000f / deltaMs) <= 55f) {
                distance += segment;
                save();
                updateNotice(false);
            }
        }
        previous = new Location(fix);
    }

    @Override public void onProviderDisabled(String provider) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        save();
        super.onDestroy();
    }
}
