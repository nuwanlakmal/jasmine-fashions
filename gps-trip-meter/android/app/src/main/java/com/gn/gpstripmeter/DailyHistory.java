package com.gn.gpstripmeter;

import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Each ISO local calendar date has its own cumulative value. No daily reset destroys history. */
public final class DailyHistory {
    private static final String PREFIX = "day_km_";
    private DailyHistory() { }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(System.currentTimeMillis()));
    }

    public static String key(String date) {
        return PREFIX + date;
    }

    public static float getDayMeters(SharedPreferences preferences, String date) {
        return preferences.getFloat(key(date), 0f);
    }

    public static List<String> recordedDays(SharedPreferences preferences) {
        Map<String, ?> values = preferences.getAll();
        List<String> dates = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key.startsWith(PREFIX)) {
                String date = key.substring(PREFIX.length());
                if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                    dates.add(date);
            }
        }
        Collections.sort(dates, Collections.reverseOrder());
        return dates;
    }
}
