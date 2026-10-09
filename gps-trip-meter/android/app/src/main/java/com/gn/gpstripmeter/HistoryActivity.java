package com.gn.gpstripmeter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private LinearLayout list;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(9), dp(13), dp(9), dp(13));
        return text;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101B30);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(25), dp(16), dp(20));
        scroll.addView(list);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        renderHistory();
    }

    private void renderHistory() {
        list.removeAllViews();
        TextView title = label("DAILY DISTANCE HISTORY", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(title);
        list.addView(label("Each date's total stays saved when a new day begins",
                13, 0xFFB7CDDF));
        SharedPreferences prefs = getSharedPreferences(TripService.PREFS, MODE_PRIVATE);
        String today = DailyHistory.today();
        addRow(today, DailyHistory.getDayMeters(prefs, today), true);
        List<String> days = DailyHistory.recordedDays(prefs);
        int previousCount = 0;
        for (String date : days) {
            if (today.equals(date)) continue;
            addRow(date, DailyHistory.getDayMeters(prefs, date), false);
            previousCount++;
        }
        if (previousCount == 0)
            list.addView(label("Earlier days will appear here after you record trips.",
                    14, 0xFFB7CDDF));
        TextView footer = label("© 2026 Nuwan Lakmal | GN design", 13, 0xFF9AACBF);
        list.addView(footer);
    }

    private void addRow(String date, float meters, boolean isToday) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(isToday ? 0xFF155D61 : 0xFF263950);
        TextView day = label(date + (isToday ? "  •  TODAY" : ""), 16, Color.WHITE);
        row.addView(day, new LinearLayout.LayoutParams(0, dp(60), 1));
        TextView distance = label(String.format(Locale.US, "%.2f km", meters / 1000f),
                19, 0xFF6AE9C4);
        distance.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(distance);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        list.addView(row, params);
    }
}
