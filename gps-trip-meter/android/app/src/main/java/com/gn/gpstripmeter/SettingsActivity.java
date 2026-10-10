package com.gn.gpstripmeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;

public class SettingsActivity extends Activity {
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView line(String s,int size,int color){
        TextView v=new TextView(this);
        v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(dp(4),dp(10),dp(4),dp(10));return v;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    private void render(){
        boolean dark=ThemePrefs.isDark(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(ThemePrefs.background(this));
        LinearLayout content=new LinearLayout(this);
        content.setPadding(dp(21),dp(20),dp(21),dp(20));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        TextView title=line("SETTINGS",24,ThemePrefs.primary(this));
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(title);
        content.addView(line("Appearance",17,ThemePrefs.primary(this)));

        Switch toggle=new Switch(this);
        toggle.setText("Dark Premium theme");
        toggle.setChecked(dark);
        toggle.setTextColor(ThemePrefs.primary(this));
        toggle.setTextSize(16);
        toggle.setPadding(dp(7),dp(18),dp(7),dp(18));
        content.addView(toggle);
        TextView choice=line(dark?"Current: Dark Premium":"Current: Light Clean",
                14,ThemePrefs.accent(this));
        content.addView(choice);
        toggle.setOnCheckedChangeListener((button,checked)->{
            ThemePrefs.setDark(this,checked);
            render(); // Return to main screen to apply the chosen appearance.
        });

        content.addView(line("Data",17,ThemePrefs.primary(this)));
        SharedPreferences gps=getSharedPreferences(TripService.PREFS,MODE_PRIVATE);
        if(!gps.getBoolean(TripService.KEY_IMPORTED,false)){
            Button importOld=new Button(this);
            importOld.setAllCaps(false);
            importOld.setText("Carry over V12 Lifetime Total (once)");
            content.addView(importOld,new LinearLayout.LayoutParams(-1,dp(62)));
            importOld.setOnClickListener(v->showImportDialog());
        }
        content.addView(line("Daily KM history and current trip are stored on this phone. " +
                "Old V12 history does not automatically transfer. " +
                "Clearing data or uninstalling this app deletes locally stored KM.",
                13,ThemePrefs.secondary(this)));
        TextView footer=line("© 2026 Nuwan Lakmal | GN design",12,ThemePrefs.secondary(this));
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);
        setContentView(scroll);
    }
    private void showImportDialog(){
        SharedPreferences prefs=getSharedPreferences(TripService.PREFS,MODE_PRIVATE);
        if(prefs.getBoolean(TripService.KEY_IMPORTED,false))return;
        EditText entry=new EditText(this);
        entry.setSingleLine(true);
        entry.setHint("e.g. 123.45");
        entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
            .setTitle("Carry over V12 Lifetime KM")
            .setMessage("Read Lifetime Total from V12. Add it ONCE to V13 Total KM. Past daily history cannot be transferred.")
            .setView(entry)
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("SAVE ONCE",(dialog,which)->{
                try{
                    float km=Float.parseFloat(entry.getText().toString().trim());
                    if(Float.isNaN(km)||Float.isInfinite(km)||km<0f||km>10000000f)
                        throw new NumberFormatException();
                    Intent i=new Intent(this,TripService.class);
                    i.setAction(TripService.ACTION_IMPORT_TOTAL);
                    i.putExtra(TripService.EXTRA_IMPORT_METERS,km*1000f);
                    if(Build.VERSION.SDK_INT>=26) startForegroundService(i);
                    else startService(i);
                    new android.os.Handler().postDelayed(this::render,900L);
                }catch(NumberFormatException e){
                    new AlertDialog.Builder(this).setMessage("Enter a valid positive KM number.").setPositiveButton("OK",null).show();
                }
            }).show();
    }
}
