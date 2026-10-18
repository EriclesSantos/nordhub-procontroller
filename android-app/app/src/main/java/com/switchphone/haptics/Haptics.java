package com.switchphone.haptics;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Touch feedback and bounded approximation of game rumble using the phone's actuator. */
public final class Haptics {
    private Haptics() {}
    public static void stop(Context context){Vibrator v=context.getSystemService(Vibrator.class);if(v!=null)v.cancel();}
    public static void game(Context context,int low,int high,int gain){
        Vibrator vibrator=context.getSystemService(Vibrator.class);if(vibrator==null||!vibrator.hasVibrator())return;
        int amplitude=Math.round(Math.max(low,high)*Math.max(0,Math.min(100,gain))/100f);
        if(amplitude<=0){vibrator.cancel();return;}
        int strong=vibrator.hasAmplitudeControl()?Math.min(255,amplitude):VibrationEffect.DEFAULT_AMPLITUDE;
        vibrator.vibrate(VibrationEffect.createOneShot(110,strong));
    }
    public static void press(Context context, int intensity) {
        if (intensity<=0) return;
        Vibrator vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator!=null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(12,Math.max(1,Math.min(255,intensity*255/100))));
        }
    }
}
