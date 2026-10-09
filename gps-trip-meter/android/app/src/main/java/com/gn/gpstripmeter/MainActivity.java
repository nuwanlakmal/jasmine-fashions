package com.gn.gpstripmeter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 42;
    private static final int GREEN = 0xFF10B981;
    private static final int RED = 0xFFE55A65;
    private static final int AMBER = 0xFFF4BA50;
    private static final int MUTED = 0xFF435066;
    private static final long ACTION_TIMEOUT_MS = 6500L;

    private TextView km, meters, status, detail, gpsSignal;
    private Button start, pause;
    private String pendingAction = null;
    private long pendingSince;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 700L);
        }
    };

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView line(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(17f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }

    private GradientDrawable roundBackground(int fill, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF101B30);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(28), dp(18), dp(24));
        scroll.addView(body);

        TextView header = line("GN GPS TRIP METER", 22, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(header);
        body.addView(line("V9  |  Huawei Android 9  |  PickMe", 13, 0xFFB4C9E1));

        // Visible color-changing ON/OFF switch indicator, not just small text.
        status = line("TRACKING OFF", 22, Color.WHITE);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setBackground(roundBackground(RED, 16));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(68));
        statusParams.topMargin = dp(26);
        body.addView(status, statusParams);

        detail = line("PAUSED  •  Press START to begin", 14, 0xFFB4C9E1);
        body.addView(detail);
        gpsSignal = line("GPS: Waiting to start", 14, 0xFFF4BA50);
        gpsSignal.setBackground(roundBackground(0xFF263950, 12));
        LinearLayout.LayoutParams signalParams = new LinearLayout.LayoutParams(-1, -2);
        signalParams.bottomMargin = dp(6);
        body.addView(gpsSignal, signalParams);

        km = line("0.00 km", 48, 0xFF6AE9C4);
        km.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        km.setPadding(0, dp(25), 0, dp(4));
        body.addView(km);
        meters = line("0 metres", 18, Color.WHITE);
        body.addView(meters);

        start = button("START TRACKING", GREEN);
        pause = button("PAUSE TRACKING", AMBER);
        Button reset = button("RESET DISTANCE", 0xFFAA5275);
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(-1, dp(60));
        firstParams.topMargin = dp(24);
        body.addView(start, firstParams);
        addSpace(body);
        body.addView(pause, new LinearLayout.LayoutParams(-1, dp(60)));
        addSpace(body);
        body.addView(reset, new LinearLayout.LayoutParams(-1, dp(60)));
        addSpace(body);
        Button map = button("VIEW SAVED ROUTE ON MAP", 0xFF356FC1);
        body.addView(map, new LinearLayout.LayoutParams(-1, dp(60)));
        map.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RouteMapActivity.class));
            }
        });

        TextView instructions = line(
                "GREEN = TRACKING ON   |   RED = TRACKING OFF\n\n" +
                "When ON, START is locked until you press PAUSE. " +
                "You can use PickMe and lock your screen while the GPS notification is visible.\n\n" +
                "Huawei: Settings > Battery > App launch > GN GPS Trip Meter V9 > Manage manually > Allow background running.",
                14, 0xFFB4C9E1);
        LinearLayout.LayoutParams notes = new LinearLayout.LayoutParams(-1, -2);
        notes.topMargin = dp(20);
        body.addView(instructions, notes);
        setContentView(scroll);

        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startClicked(); }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isTracking() && pendingAction == null) issue(TripService.ACTION_PAUSE);
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendService(TripService.ACTION_RESET); }
        });
        refresh();
    }

    private void addSpace(LinearLayout body) {
        body.addView(new View(this), new LinearLayout.LayoutParams(dp(1), dp(10)));
    }

    private boolean isTracking() {
        SharedPreferences p = getSharedPreferences(TripService.PREFS, MODE_PRIVATE);
        long heartbeat = p.getLong(TripService.KEY_HEARTBEAT, 0L);
        return p.getBoolean(TripService.KEY_ACTIVE, false) && heartbeat > 0
                && Math.abs(System.currentTimeMillis() - heartbeat) < 24000L;
    }

    private void startClicked() {
        // Avoid duplicate START requests even before the service confirms tracking.
        if (isTracking() || pendingAction != null) return;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Turn on GPS / Location first", Toast.LENGTH_LONG).show();
            return;
        }
        issue(TripService.ACTION_START);
    }

    private void issue(String action) {
        if (pendingAction != null) return;
        pendingAction = action;
        pendingSince = SystemClock.elapsedRealtime();
        refresh();
        if (!sendService(action)) {
            pendingAction = null;
            refresh();
        }
    }

    private boolean sendService(String action) {
        try {
            Intent service = new Intent(this, TripService.class);
            service.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            return true;
        } catch (RuntimeException error) {
            Toast.makeText(this, "GPS service error: " + error.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startClicked();
        } else {
            Toast.makeText(this, "Please allow Location permission", Toast.LENGTH_LONG).show();
        }
    }

    private void styleButton(Button button, boolean enabled, int color, String text) {
        button.setEnabled(enabled);
        button.setAlpha(1f);
        button.setText(text);
        button.setBackgroundTintList(ColorStateList.valueOf(enabled ? color : MUTED));
        button.setTextColor(enabled ? Color.WHITE : 0xFFCBD4E0);
    }

    private void refresh() {
        if (km == null) return;
        SharedPreferences prefs = getSharedPreferences(TripService.PREFS, MODE_PRIVATE);
        float distance = prefs.getFloat(TripService.KEY_METERS, 0f);
        boolean storedOn = prefs.getBoolean(TripService.KEY_ACTIVE, false);
        long heartbeat = prefs.getLong(TripService.KEY_HEARTBEAT, 0L);
        boolean serviceAlive = heartbeat > 0L
                && Math.abs(System.currentTimeMillis() - heartbeat) < 24000L;
        boolean tracking = storedOn && serviceAlive;

        km.setText(String.format(Locale.US, "%.2f km", distance / 1000f));
        meters.setText(String.format(Locale.US, "%.0f metres", distance));

        if (pendingAction != null) {
            boolean complete = TripService.ACTION_START.equals(pendingAction) ? tracking : !tracking;
            if (complete) {
                pendingAction = null;
            } else if (SystemClock.elapsedRealtime() - pendingSince > ACTION_TIMEOUT_MS) {
                pendingAction = null;
                Toast.makeText(this, "No GPS confirmation. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }

        if (pendingAction != null) {
            boolean starting = TripService.ACTION_START.equals(pendingAction);
            status.setText(starting ? "●  STARTING GPS..." : "●  PAUSING...");
            status.setBackground(roundBackground(0xFF946821, 16));
            detail.setText(starting ? "Waiting for tracking confirmation" : "Stopping GPS tracking");
            styleButton(start, false, GREEN, "PLEASE WAIT...");
            styleButton(pause, false, AMBER, "PLEASE WAIT...");
        } else if (tracking) {
            status.setText("●  TRACKING ON");
            status.setBackground(roundBackground(0xFF128C67, 16));
            detail.setText("ACTIVE  •  Measuring GPS travel distance");
            styleButton(start, false, GREEN, "✓  ALREADY TRACKING");
            styleButton(pause, true, AMBER, "PAUSE TRACKING");
        } else {
            status.setText("●  TRACKING OFF");
            status.setBackground(roundBackground(0xFFB73E4E, 16));
            detail.setText(storedOn && !serviceAlive ? "SERVICE STOPPED  •  Press START again" : "PAUSED  •  Distance saved  •  Ready to START");
            styleButton(start, true, GREEN, "START TRACKING");
            styleButton(pause, false, AMBER, "PAUSED");
        }
        long lastFix = prefs.getLong(TripService.KEY_LAST_FIX, 0L);
        float accuracy = prefs.getFloat(TripService.KEY_ACCURACY, -1f);
        int fixes = prefs.getInt(TripService.KEY_FIXES, 0);
        int counted = prefs.getInt(TripService.KEY_COUNTED, 0);
        String source = prefs.getString(TripService.KEY_SOURCE, "NONE");
        String note = prefs.getString(TripService.KEY_LAST_NOTE, "");
        if (storedOn && !serviceAlive) {
            gpsSignal.setText("GPS SERVICE STOPPED!\nAllow background running in Huawei Battery settings.");
            gpsSignal.setTextColor(0xFFFF727F);
        } else if (!tracking) {
            gpsSignal.setText("GPS: OFF • Distance saved");
            gpsSignal.setTextColor(0xFFCBD4E0);
        } else if (lastFix == 0L) {
            gpsSignal.setText("SEARCHING FOR GPS LOCATION...\nGo outside and wait for GPS signal.");
            gpsSignal.setTextColor(0xFFF4BA50);
        } else {
            long age = Math.max(0L, (System.currentTimeMillis() - lastFix) / 1000L);
            gpsSignal.setText(String.format(Locale.US,
                    "GPS: %s • Accuracy: ±%.0f m • Last: %ds ago\n" +
                    "Location updates: %d • Distance segments: %d\n%s",
                    source, accuracy, age, fixes, counted, note));
            gpsSignal.setTextColor(age > 25L || accuracy > 65f ? 0xFFF4BA50 : 0xFF6AE9C4);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
