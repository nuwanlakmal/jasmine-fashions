package com.gn.gpstripmeter;
import android.content.Context;
import android.content.SharedPreferences;

public final class ThemePrefs {
    private static final String PREFS = "appearance_settings";
    private static final String KEY_DARK = "dark_premium";
    private ThemePrefs(){}
    public static boolean isDark(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK,true);
    }
    public static void setDark(Context context,boolean dark){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK,dark).commit();
    }
    public static int background(Context c) {return isDark(c)?0xFF0B1425:0xFFF0F4F9;}
    public static int panel(Context c) {return isDark(c)?0xFF17243A:0xFFFFFFFF;}
    public static int primary(Context c) {return isDark(c)?0xFFF4F8FF:0xFF17233A;}
    public static int secondary(Context c) {return isDark(c)?0xFF9FB0C9:0xFF5E7087;}
    public static int accent(Context c) {return isDark(c)?0xFF36E7B3:0xFF067A65;}
}
