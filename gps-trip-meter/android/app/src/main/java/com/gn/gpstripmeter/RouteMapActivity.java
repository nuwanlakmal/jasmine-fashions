package com.gn.gpstripmeter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

public class RouteMapActivity extends Activity {
    private WebView map;
    private TextView info;
    private boolean pageLoaded;
    private String previousJson = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            List<double[]> points = TrackStore.read(RouteMapActivity.this, 2200);
            String data = TrackStore.asJson(RouteMapActivity.this).toString();
            info.setText(points.size() == 0 ?
                    "No route yet. START the trip and wait for a valid GPS fix." :
                    "Saved route: " + points.size() + " displayed points • Refreshes every 4 sec");
            if (pageLoaded && !data.equals(previousJson)) {
                previousJson = data;
                map.evaluateJavascript("window.renderTrip(" + data + ");", null);
            }
            handler.postDelayed(this, 4000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xFF101B30);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(15);
        info.setPadding(15, 16, 15, 13);
        info.setText("Opening saved GPS route...");
        column.addView(info);

        map = new WebView(this);
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.getSettings().setAllowFileAccess(false);
        map.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                pageLoaded = true;
                refresh.run();
            }
        });
        column.addView(map, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button google = new Button(this);
        google.setAllCaps(false);
        google.setText("OPEN GOOGLE MAPS (SUGGESTED DIRECTIONS)");
        google.setTextSize(14);
        google.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGoogleMaps(); }
        });
        column.addView(google, new LinearLayout.LayoutParams(-1, -2));

        TextView explanation = new TextView(this);
        explanation.setTextColor(0xFFB9CDE3);
        explanation.setTextSize(12);
        explanation.setPadding(15, 6, 15, 15);
        explanation.setText("Blue line = exact saved GPS points (OpenStreetMap). " +
                "Google Maps recalculates road directions and may differ from your actual trip. " +
                "Internet needed for map tiles. Route points are saved offline.");
        column.addView(explanation);
        setContentView(column);

        map.loadDataWithBaseURL("https://unpkg.com/", html(), "text/html", "UTF-8", null);
    }

    private String html() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<style>html,body,#map{height:100%;margin:0;background:#e1e8ee}" +
                ".info{position:absolute;z-index:900;top:10px;left:10px;right:10px;" +
                "background:white;border-radius:8px;padding:10px;font:14px sans-serif;" +
                "box-shadow:0 1px 6px #777}</style></head><body>" +
                "<div id='map'></div><div class='info' id='hint'>Loading OpenStreetMap tiles...</div>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<script>var m=null,route=null,start=null,finish=null;" +
                "function renderTrip(points){" +
                "if(typeof L==='undefined'){document.getElementById('hint').textContent='Map requires internet connection. GPS points are still saved.';return;}" +
                "if(!m){m=L.map('map').setView([7.8731,80.7718],7);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'," +
                "{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(m);}" +
                "if(route)m.removeLayer(route);if(start)m.removeLayer(start);if(finish)m.removeLayer(finish);" +
                "if(points.length===0){document.getElementById('hint').textContent='Waiting for route points...';return;}" +
                "route=L.polyline(points,{color:'#0660e0',weight:5,opacity:0.9}).addTo(m);" +
                "start=L.circleMarker(points[0],{color:'green',fillColor:'green',radius:7,fillOpacity:1}).addTo(m).bindPopup('Start');" +
                "finish=L.circleMarker(points[points.length-1],{color:'red',fillColor:'red',radius:7,fillOpacity:1}).addTo(m).bindPopup('Latest location');" +
                "if(points.length>1){m.fitBounds(route.getBounds(),{padding:[24,24],maxZoom:17});}" +
                "else m.setView(points[0],16);" +
                "document.getElementById('hint').textContent='Recorded route • '+points.length+' points • Green=start, Red=latest';" +
                "}" +
                "setTimeout(function(){renderTrip([])},6000);" +
                "</script></body></html>";
    }

    private void openGoogleMaps() {
        List<double[]> points = TrackStore.read(this, 800);
        if (points.size() == 0) {
            info.setText("Start tracking and move outdoors to save route points first.");
            return;
        }
        // Google Maps URLs show directions, not arbitrary recorded polylines.
        String origin = coords(points.get(0));
        String destination = coords(points.get(points.size() - 1));
        Uri.Builder builder = Uri.parse("https://www.google.com/maps/dir/").buildUpon();
        builder.appendQueryParameter("api", "1");
        builder.appendQueryParameter("origin", origin);
        builder.appendQueryParameter("destination", destination);
        builder.appendQueryParameter("travelmode", "driving");
        if (points.size() > 5) {
            StringBuilder stops = new StringBuilder();
            for (int i=1; i<=3; i++) {
                if (i>1) stops.append("|");
                stops.append(coords(points.get((int)((long)i * (points.size()-1) / 4))));
            }
            builder.appendQueryParameter("waypoints", stops.toString());
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
        } catch (Exception e) {
            info.setText("Google Maps app/browser unavailable.");
        }
    }

    private String coords(double[] point) {
        return String.format(Locale.US, "%.6f,%.6f", point[0], point[1]);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        if (info != null) refresh.run();
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        if (map != null) map.destroy();
        super.onDestroy();
    }
}
