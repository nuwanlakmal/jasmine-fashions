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
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private TextView km, meters, status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView line(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(10));
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF101B30);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(20), dp(34), dp(20), dp(25));
        scroll.addView(body);

        TextView header = line("GN GPS TRIP METER", 22, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(header);
        body.addView(line("Android 9 • PickMe trip distance", 13, 0xFFBCD1EA));

        km = line("0.00 km", 49, 0xFF61E2B7);
        km.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        km.setPadding(0, dp(35), 0, 0);
        body.addView(km);
        meters = line("0 metres", 17, Color.WHITE);
        body.addView(meters);
        status = line("PAUSED", 16, 0xFFFFCD67);
        status.setPadding(0, dp(16), 0, dp(24));
        body.addView(status);

        Button start = button("START TRACKING", 0xFF148A70);
        Button pause = button("PAUSE TRACKING", 0xFFD49B36);
        Button reset = button("RESET DISTANCE", 0xFFBD4F61);
        body.addView(start, new LinearLayout.LayoutParams(-1, dp(58)));
        spacer(body);
        body.addView(pause, new LinearLayout.LayoutParams(-1, dp(58)));
        spacer(body);
        body.addView(reset, new LinearLayout.LayoutParams(-1, dp(58)));
        body.addView(line("After pressing START, you can open PickMe or lock your screen. Keep the GPS notification on. For Huawei: Settings > Battery > App launch > manage GN GPS Trip Meter manually and allow background running.", 14, 0xFFBCD1EA));
        setContentView(scroll);

        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startClicked(); }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(TripService.ACTION_PAUSE); }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { send(TripService.ACTION_RESET); }
        });
        refresh();
    }

    private void spacer(LinearLayout container) {
        container.addView(new View(this), new LinearLayout.LayoutParams(1, dp(11)));
    }

    private void startClicked() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please turn on Phone Location / GPS", Toast.LENGTH_LONG).show();
            return;
        }
        send(TripService.ACTION_START);
    }

    private void send(String action) {
        try {
            Intent service = new Intent(this, TripService.class);
            service.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            handler.postDelayed(new Runnable() {
                @Override public void run() { refresh(); }
            }, 500L);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not start GPS service: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startClicked();
        } else {
            Toast.makeText(this, "Allow precise Location permission to track distance", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        if (km == null) return;
        SharedPreferences p = getSharedPreferences(TripService.PREFS, MODE_PRIVATE);
        float m = p.getFloat(TripService.KEY_METERS, 0f);
        boolean active = p.getBoolean(TripService.KEY_ACTIVE, false);
        km.setText(String.format(Locale.US, "%.2f km", m / 1000f));
        meters.setText(String.format(Locale.US, "%.0f metres", m));
        status.setText(active ? "TRACKING • GPS active" : "PAUSED • Press START");
        status.setTextColor(active ? 0xFF61E2B7 : 0xFFFFCD67);
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
