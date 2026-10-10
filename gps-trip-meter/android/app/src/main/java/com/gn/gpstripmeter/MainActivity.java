package com.gn.gpstripmeter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
    private static final int REQUEST_LOCATION = 42;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, todayValue, todayDate, totalValue, tripValue, gpsValue;
    private Button start, pause;
    private boolean currentThemeDark;
    private String pendingAction;
    private long pendingTime;

    private final Runnable update = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private int ink() { return currentThemeDark ? 0xFFF4F8FF : 0xFF17233A; }
    private int muted() { return currentThemeDark ? 0xFF9FB0C9 : 0xFF5E7087; }
    private int panel() { return currentThemeDark ? 0xFF17243A : 0xFFFFFFFF; }
    private int background() { return currentThemeDark ? 0xFF0B1425 : 0xFFF0F4F9; }
    private int accent() { return currentThemeDark ? 0xFF36E7B3 : 0xFF067A65; }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radius));
        d.setColor(color);
        return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(3), 0, dp(3), 0);
        b.setMinimumHeight(0);
        b.setMinHeight(0);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }
    private void space(LinearLayout target, int height) {
        target.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void addButton(LinearLayout row, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(47), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(b,p);
    }
    private LinearLayout buttonRow(LinearLayout outer, Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addButton(row, a);
        addButton(row, b);
        outer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8),dp(12),dp(8),dp(12));
        c.setBackground(rounded(panel(), 16));
        return c;
    }
    private LinearLayout metric(String title, String value, int titleColor, int valueColor, int size) {
        LinearLayout c=card();
        c.addView(text(title, 11, titleColor, true));
        TextView v=text(value,size,valueColor,true);
        c.addView(v);
        return c;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        currentThemeDark=ThemePrefs.isDark(this);
        setTheme(currentThemeDark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        buildDashboard();
    }

    private void buildDashboard() {
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(background());
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12),dp(12),dp(12),dp(9));
        scroll.addView(outer);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView name=text("GN  KM COUNTER",18,ink(),true);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        header.addView(name,new LinearLayout.LayoutParams(0,dp(42),1f));
        Button settings=button("⚙ Settings",currentThemeDark ? 0xFF283B57:0xFF55728F);
        settings.setTextSize(13);
        header.addView(settings,new LinearLayout.LayoutParams(dp(115),dp(42)));
        settings.setOnClickListener(v -> startActivity(new Intent(this,SettingsActivity.class)));
        outer.addView(header);
        space(outer,6);

        status=text("●  TRACKING OFF",15,Color.WHITE,true);
        status.setBackground(rounded(0xFFB44659,13));
        outer.addView(status,new LinearLayout.LayoutParams(-1,dp(43)));
        space(outer,9);

        LinearLayout todayCard=card();
        todayCard.setBackground(rounded(currentThemeDark?0xFF103F41:0xFFD9F5EB,16));
        todayDate=text("TODAY • "+DailyHistory.today(),12,currentThemeDark?0xFFB4D4D0:0xFF33675D,true);
        todayCard.addView(todayDate);
        todayValue=text("0.00 km",36,accent(),true);
        LinearLayout.LayoutParams todayParams=new LinearLayout.LayoutParams(-1,dp(78));
        todayCard.addView(todayValue,todayParams);
        outer.addView(todayCard,new LinearLayout.LayoutParams(-1,dp(116)));
        space(outer,8);

        LinearLayout metrics=new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout lifetime=metric("LIFETIME TOTAL","0.00 km",muted(),ink(),23);
        LinearLayout trip=metric("CURRENT TRIP","0.00 km",muted(),accent(),23);
        totalValue=(TextView)lifetime.getChildAt(1);
        tripValue=(TextView)trip.getChildAt(1);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(88),1f);
        left.rightMargin=dp(4);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(88),1f);
        right.leftMargin=dp(4);
        metrics.addView(lifetime,left);
        metrics.addView(trip,right);
        outer.addView(metrics);
        space(outer,8);

        gpsValue=text("GPS • Waiting to start",11,muted(),false);
        gpsValue.setMaxLines(2);
        gpsValue.setBackground(rounded(panel(),10));
        outer.addView(gpsValue,new LinearLayout.LayoutParams(-1,dp(45)));
        space(outer,11);

        Button newTrip=button("＋ NEW TRIP",0xFF257BA3);
        start=button("▶ START",0xFF128969);
        pause=button("Ⅱ PAUSE",0xFFB58932);
        Button reset=button("↺ RESET TRIP",0xFFB14966);
        Button history=button("▤ HISTORY",0xFF346D7C);
        Button map=button("⌖ ROUTE MAP",0xFF365AAB);
        buttonRow(outer,newTrip,start);
        space(outer,7);
        buttonRow(outer,pause,reset);
        space(outer,7);
        buttonRow(outer,history,map);
        space(outer,7);

        TextView footer=text("© 2026 Nuwan Lakmal | GN design",11,muted(),false);
        outer.addView(footer,new LinearLayout.LayoutParams(-1,dp(30)));
        setContentView(scroll);

        start.setOnClickListener(v -> startClicked());
        pause.setOnClickListener(v -> {
            if(isTracking() && pendingAction==null) issue(TripService.ACTION_PAUSE);
        });
        newTrip.setOnClickListener(v -> confirmTrip(TripService.ACTION_NEW_TRIP));
        reset.setOnClickListener(v -> confirmTrip(TripService.ACTION_RESET_TRIP));
        history.setOnClickListener(v -> startActivity(new Intent(this,HistoryActivity.class)));
        map.setOnClickListener(v -> startActivity(new Intent(this,RouteMapActivity.class)));
        refresh();
    }

    private SharedPreferences gps() {
        return getSharedPreferences(TripService.PREFS,MODE_PRIVATE);
    }
    private boolean isTracking() {
        SharedPreferences p=gps();
        long beat=p.getLong(TripService.KEY_HEARTBEAT,0L);
        return p.getBoolean(TripService.KEY_ACTIVE,false) && beat>0L
                && Math.abs(System.currentTimeMillis()-beat)<24000L;
    }
    private void startClicked() {
        if(isTracking() || pendingAction!=null)return;
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
            return;
        }
        LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if(lm==null || (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))){
            Toast.makeText(this,"Please enable Location/GPS",Toast.LENGTH_LONG).show();
            return;
        }
        issue(TripService.ACTION_START);
    }
    private boolean send(String action) {
        try {
            Intent service=new Intent(this,TripService.class);
            service.setAction(action);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
            else startService(service);
            return true;
        }catch(Exception e){
            Toast.makeText(this,"GPS service error: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            return false;
        }
    }
    private void issue(String action) {
        if(pendingAction!=null)return;
        pendingAction=action;
        pendingTime=SystemClock.elapsedRealtime();
        refresh();
        if(!send(action)){pendingAction=null;refresh();}
    }
    private void confirmTrip(String action) {
        String title=TripService.ACTION_NEW_TRIP.equals(action)?"NEW TRIP":"RESET CURRENT TRIP";
        String message=TripService.ACTION_NEW_TRIP.equals(action)
                ?"Start counting this new trip from 0 km? Lifetime and today's total stay saved."
                :"Reset only CURRENT TRIP KM and its route? Lifetime and today's total stay saved.";
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("CANCEL",null)
                .setPositiveButton("YES",(dialog,which)->{
                    if(TripService.ACTION_NEW_TRIP.equals(action)){
                        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                !=PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this,"Allow GPS permission using START first",Toast.LENGTH_LONG).show();
                            return;
                        }
                        LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
                        if(lm==null||(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                                && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))){
                            Toast.makeText(this,"Turn on GPS first",Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if(send(action))handler.postDelayed(this::refresh,600);
                }).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==REQUEST_LOCATION && results.length>0
                && results[0]==PackageManager.PERMISSION_GRANTED) startClicked();
    }
    private void styleButton(Button b,boolean enabled,int tint,String label){
        b.setEnabled(enabled);
        b.setText(label);
        b.setBackgroundTintList(ColorStateList.valueOf(enabled?tint:
                (currentThemeDark?0xFF3D495B:0xFFAAB6C4)));
        b.setTextColor(enabled?Color.WHITE:(currentThemeDark?0xFFAAB6C8:0xFFF6F8FA));
    }
    private void refresh(){
        if(status==null)return;
        SharedPreferences p=gps();
        String day=DailyHistory.today();
        float total=p.getFloat(TripService.KEY_METERS,0f);
        float trip=p.getFloat(TripService.KEY_TRIP_METERS,0f);
        float today=DailyHistory.getDayMeters(p,day);
        totalValue.setText(String.format(Locale.US,"%.2f km",total/1000f));
        tripValue.setText(String.format(Locale.US,"%.2f km",trip/1000f));
        todayValue.setText(String.format(Locale.US,"%.2f km",today/1000f));
        todayDate.setText("TODAY • "+day);
        boolean tracking=isTracking();
        if(pendingAction!=null){
            boolean completed=TripService.ACTION_START.equals(pendingAction)?tracking:!tracking;
            if(completed)pendingAction=null;
            else if(SystemClock.elapsedRealtime()-pendingTime>7000L){
                pendingAction=null;
                Toast.makeText(this,"Tracking not confirmed; check GPS",Toast.LENGTH_SHORT).show();
            }
        }
        if(pendingAction!=null) {
            status.setText("●  "+(TripService.ACTION_START.equals(pendingAction)?"STARTING...":"PAUSING..."));
            status.setBackground(rounded(0xFF936323,13));
            styleButton(start,false,0xFF128969,"WAIT...");
            styleButton(pause,false,0xFFB58932,"WAIT...");
        }else if(tracking) {
            status.setText("●  TRACKING ON");
            status.setBackground(rounded(0xFF148763,13));
            styleButton(start,false,0xFF128969,"✓ ALREADY ON");
            styleButton(pause,true,0xFFB58932,"Ⅱ PAUSE");
        }else{
            status.setText("●  TRACKING OFF");
            status.setBackground(rounded(0xFFB44659,13));
            styleButton(start,true,0xFF128969,"▶ START");
            styleButton(pause,false,0xFFB58932,"PAUSED");
        }
        if(!tracking){
            gpsValue.setText("GPS • Tracking paused");
            return;
        }
        long fix=p.getLong(TripService.KEY_LAST_FIX,0L);
        if(fix<=0){
            gpsValue.setText("GPS • Searching for location. Move outdoors.");
            return;
        }
        long age=Math.max(0,(System.currentTimeMillis()-fix)/1000L);
        float acc=p.getFloat(TripService.KEY_ACCURACY,-1f);
        gpsValue.setText(String.format(Locale.US,
                "%s GPS ±%.0f m • %ds ago\nUpdates %d • Counted %d",
                p.getString(TripService.KEY_SOURCE,"GPS"),acc,age,
                p.getInt(TripService.KEY_FIXES,0),p.getInt(TripService.KEY_COUNTED,0)));
    }
    @Override protected void onResume(){
        super.onResume();
        if(currentThemeDark!=ThemePrefs.isDark(this)){
            recreate();
            return;
        }
        handler.removeCallbacks(update);
        update.run();
    }
    @Override protected void onPause(){
        handler.removeCallbacks(update);
        super.onPause();
    }
}
