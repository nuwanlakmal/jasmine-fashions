package com.gn.gpstripmeter;

import android.content.Context;
import android.location.Location;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Stores the actual recorded GPS positions locally at collection time. */
public final class TrackStore {
    private static final String FILE = "current_trip_points.csv";
    private TrackStore() {}

    public static synchronized void append(Context context, Location location) {
        File file = new File(context.getFilesDir(), FILE);
        String line = String.format(Locale.US, "%.7f,%.7f,%d,%.1f\n",
                location.getLatitude(), location.getLongitude(),
                System.currentTimeMillis(), location.hasAccuracy() ? location.getAccuracy() : -1f);
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(line.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync(); // commit each received travel point to device storage
        } catch (Exception ignored) {
            // Keep UI and service running even if storage temporarily fails.
        }
    }

    public static synchronized void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (file.exists() && !file.delete()) {
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.getFD().sync();
            } catch (Exception ignored) { }
        }
    }

    public static synchronized List<double[]> read(Context context, int limit) {
        List<double[]> values = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE);
        if (!file.exists()) return values;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                try {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180)
                        values.add(new double[]{lat, lon});
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        if (values.size() <= limit) return values;
        List<double[]> sample = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int index = (int) ((long) i * (values.size() - 1) / (limit - 1));
            sample.add(values.get(index));
        }
        return sample;
    }

    public static JSONArray asJson(Context context) {
        JSONArray coordinates = new JSONArray();
        for (double[] point : read(context, 2200)) {
            JSONArray row = new JSONArray();
            try {
                row.put(point[0]);
                row.put(point[1]);
                coordinates.put(row);
            } catch (org.json.JSONException ignored) { }
        }
        return coordinates;
    }
}
