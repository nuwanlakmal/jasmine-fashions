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
    public static final String ACTION_RESET_TRIP = "com.gn.gpstripmeter.RESET_TRIP";
    public static final String ACTION_NEW_TRIP = "com.gn.gpstripmeter.NEW_TRIP";
    public static final String ACTION_IMPORT_TOTAL = "com.gn.gpstripmeter.IMPORT_TOTAL";
    public static final String EXTRA_IMPORT_METERS = "import_meters";
    public static final String PREFS = "trip";
    public static final String KEY_METERS = "distance"; // Cumulative, non-resettable from UI
    public static final String KEY_TRIP_METERS = "current_trip_distance";
    public static final String KEY_IMPORTED = "previous_total_imported";
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
    private float tripDistance;
    private boolean active;
    private long noticeTime;
    private long lastGpsElapsed;
    private boolean listening;
    private int poorFixes;
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
        tripDistance = prefs.getFloat(KEY_TRIP_METERS, 0f);
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
        if (ACTION_IMPORT_TOTAL.equals(action)) {
            float incoming = intent.getFloatExtra(EXTRA_IMPORT_METERS, -1f);
            if (!prefs.getBoolean(KEY_IMPORTED, false) && Float.isFinite(incoming)
                    && incoming >= 0f && incoming <= 10000000000f) {
                distance += incoming;
                prefs.edit().putBoolean(KEY_IMPORTED, true).commit();
                save();
            }
            if (!active) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            updateNotice(true);
            return START_STICKY;
        }
        if (ACTION_RESET_TRIP.equals(action) || ACTION_NEW_TRIP.equals(action)) {
            tripDistance = 0f; // NEVER reset cumulative "distance".
            previous = null;   // Don't count gap from previous trip.
            TrackStore.clear(this);
            prefs.edit().putInt(KEY_COUNTED, 0).commit();
            save();
            if (ACTION_NEW_TRIP.equals(action)) {
                startTracking();
                return active ? START_STICKY : START_NOT_STICKY;
            }
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
                .setContentText(String.format(Locale.US, "Trip %.2f km | Total %.2f km",
                        tripDistance / 1000f, distance / 1000f))
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
        poorFixes = 0;
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
        // Synchronous commit ensures both counters persist together after every segment.
        prefs.edit().putFloat(KEY_METERS, distance)
                .putFloat(KEY_TRIP_METERS, tripDistance)
                .putBoolean(KEY_ACTIVE, active).commit();
    }

    @Override public void onLocationChanged(Location fix) {
        if (!active || fix == null) return;
        String provider = fix.getProvider() == null ? "unknown" : fix.getProvider();
        boolean isGps = LocationManager.GPS_PROVIDER.equals(provider);
        long nowElapsed = SystemClock.elapsedRealtime();
        float accuracy = fix.hasAccuracy() ? fix.getAccuracy() : 999f;
        if (isGps) lastGpsElapsed = nowElapsed;

        // If satellite accuracy is poor, permit a usable network fix as a fallback.
        if (!isGps && lastGpsElapsed > 0 && nowElapsed - lastGpsElapsed < 14000L
                && accuracy > 35f) return;

        prefs.edit().putLong(KEY_LAST_FIX, System.currentTimeMillis())
                .putString(KEY_SOURCE, isGps ? "GPS" : "NETWORK")
                .putFloat(KEY_ACCURACY, accuracy)
                .putInt(KEY_FIXES, prefs.getInt(KEY_FIXES, 0) + 1).apply();

        if (!fix.hasAccuracy() || accuracy > 135f) {
            poorFixes++;
            setNote("GPS weak: accuracy ±" + Math.round(accuracy)
                    + " m. Move outside and wait for a stronger fix.");
            return;
        }
        poorFixes = 0;

        if (previous == null) {
            previous = new Location(fix);
            TrackStore.append(this, fix);
            setNote("First position saved. Move away from this point.");
            return;
        }
        // Keep the distance anchor across GPS/network provider switches.
        // Resetting it on every switch caused zero-distance trips on weak GPS phones.
        // Accuracy and speed filters below still guard against bad mixed fixes.
        if (accuracy > 85f && previous.getAccuracy() < 35f
                && nowElapsed - (previous.getElapsedRealtimeNanos() / 1000000L) < 15000L) {
            setNote("Weak GPS point ignored; keeping more accurate last location");
            return;
        }

        // Keep the last anchor for small movement instead of replacing it on each
        // update. This fixes trips with GPS changes under 3-9m per location fix.
        float moved = previous.distanceTo(fix);
        long elapsedMs = (fix.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos()) / 1000000L;
        float accuracyMax = Math.max(previous.getAccuracy(), accuracy);
        float threshold = Math.max(4f, Math.min(42f, accuracyMax * 0.38f));

        if (elapsedMs <= 0L) {
            setNote("Waiting for a newer GPS position");
            return;
        }
        if (elapsedMs >= 120000L) {
            previous = new Location(fix);
            TrackStore.append(this, fix);
            setNote("GPS gap detected, starting next route segment.");
            return;
        }
        if (moved < threshold) {
            setNote(String.format(Locale.US,
                    "Waiting for movement: %.0f m detected; needs %.0f m at ±%.0f m accuracy",
                    moved, threshold, accuracyMax));
            return; // DON'T move anchor: accumulate displacement until measurable.
        }
        float speed = moved * 1000f / elapsedMs;
        if (moved > 1800f || speed > 55f) {
            setNote("Suspicious GPS jump ignored");
            // Reset after a jump so new fixes can reconnect without permanent lock.
            previous = new Location(fix);
            TrackStore.append(this, fix);
            return;
        }

        distance += moved;
        tripDistance += moved;
        previous = new Location(fix);
        TrackStore.append(this, fix);
        prefs.edit().putInt(KEY_COUNTED, prefs.getInt(KEY_COUNTED, 0) + 1).apply();
        setNote(String.format(Locale.US, "+%.0f m counted • route point saved", moved));
        save();
        updateNotice(true);
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
