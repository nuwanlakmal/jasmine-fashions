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
    public static final String KEY_HEARTBEAT = "service_heartbeat";
    public static final String KEY_LAST_FIX = "last_fix_time";
    public static final String KEY_ACCURACY = "gps_accuracy";
    public static final String KEY_SOURCE = "gps_provider";
    public static final String KEY_FIXES = "gps_fix_count";
    public static final String KEY_COUNTED = "distance_segments";
    public static final String KEY_LAST_NOTE = "gps_note";
    private static final int NOTICE_ID = 17;
    private static final String CHANNEL = "gn_trip_gps";

    private SharedPreferences prefs;
    private LocationManager locationManager;
    private Location previous;
    private float distance;
    private boolean active;
    private long noticeTime;
    private long lastGpsElapsed;
    private boolean listening;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!active) return;
            prefs.edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply();
            handler.postDelayed(this, 5000L);
        }
    };
    private void setNote(String note) {
        prefs.edit().putString(KEY_LAST_NOTE, note).apply();
    }

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
        if (locationManager == null) {
            endTracking();
            return;
        }
        if (active && listening) return;
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            endTracking();
            return;
        }
        active = true;
        previous = null;
        lastGpsElapsed = 0L;
        prefs.edit().putLong(KEY_LAST_FIX, 0L)
                .putInt(KEY_FIXES, 0).putInt(KEY_COUNTED, 0)
                .putString(KEY_LAST_NOTE, "Waiting for first GPS location").apply();
        save();
        boolean startedGps = false;
        boolean startedNetwork = false;
        try {
            if (gpsEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
                startedGps = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            setNote("Could not start GPS provider");
        }
        try {
            if (networkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1f, this);
                startedNetwork = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            setNote("Network location unavailable");
        }
        listening = startedGps || startedNetwork;
        if (!listening) {
            endTracking();
            return;
        }
        handler.removeCallbacks(heartbeat);
        heartbeat.run();
        updateNotice(true);
    }

    private void endTracking() {
        active = false;
        listening = false;
        handler.removeCallbacks(heartbeat);
        prefs.edit().putLong(KEY_HEARTBEAT, 0L).apply();
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
        if (!active || fix == null) return;
        String provider = fix.getProvider() == null ? "unknown" : fix.getProvider();
        boolean isGps = LocationManager.GPS_PROVIDER.equals(provider);
        long nowElapsed = SystemClock.elapsedRealtime();
        if (isGps) lastGpsElapsed = nowElapsed;

        // Prefer satellite fixes while available; network fixes are fallback only.
        if (!isGps && lastGpsElapsed > 0L && nowElapsed - lastGpsElapsed < 18000L) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_FIX, System.currentTimeMillis())
                .putString(KEY_SOURCE, isGps ? "GPS" : "NETWORK")
                .putFloat(KEY_ACCURACY, fix.hasAccuracy() ? fix.getAccuracy() : 999f)
                .putInt(KEY_FIXES, prefs.getInt(KEY_FIXES, 0) + 1).apply();

        if (!fix.hasAccuracy() || fix.getAccuracy() > 95f) {
            setNote("Weak location accuracy - move outside");
            return;
        }
        if (previous == null) {
            previous = new Location(fix);
            setNote("First GPS point received. Drive to count distance.");
            return;
        }
        if (!provider.equals(previous.getProvider())) {
            previous = new Location(fix);
            setNote("Location source changed; resuming count");
            return;
        }
        float segment = previous.distanceTo(fix);
        long deltaMs = (fix.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos()) / 1000000L;
        float threshold = Math.max(3f, Math.min(9f, Math.max(previous.getAccuracy(), fix.getAccuracy()) * 0.2f));
        if (deltaMs > 0 && deltaMs < 120000L && segment >= threshold
                && segment < 1500f && segment * 1000f / deltaMs <= 60f) {
            distance += segment;
            prefs.edit().putInt(KEY_COUNTED, prefs.getInt(KEY_COUNTED, 0) + 1).apply();
            setNote(String.format(Locale.US, "+%.0f meters added", segment));
            save();
            updateNotice(false);
        } else if (segment < threshold) {
            setNote("Small GPS movement ignored (noise)");
        } else {
            setNote("GPS jump or outdated fix filtered");
        }
        if (deltaMs > 0) previous = new Location(fix);
    }

    @Override public void onProviderDisabled(String provider) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        handler.removeCallbacks(heartbeat);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        save();
        super.onDestroy();
    }
}
