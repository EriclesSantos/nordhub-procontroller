package com.switchphone.controller;

import android.content.Context;
import android.hardware.*;
import android.os.SystemClock;
import android.view.WindowManager;
import com.switchphone.settings.Preferences;

/** Sensors run only while the control surface is visible; pausing releases gyro aim. */
public final class MotionInput implements SensorEventListener {
    private final SensorManager manager;private final Sensor gyro,accel;private final WindowManager windows;
    private final ControllerState state;private final Preferences prefs;private final Runnable changed;
    private final float[] acceleration=new float[3];private final MotionModel model=new MotionModel();
    private volatile boolean running;private long lastDispatch;
    public MotionInput(Context context,ControllerState state,Preferences prefs,Runnable changed){
        manager=context.getSystemService(SensorManager.class);windows=context.getSystemService(WindowManager.class);
        gyro=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);accel=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.state=state;this.prefs=prefs;this.changed=changed;
    }
    public boolean available(){return gyro!=null&&accel!=null;}
    public boolean running(){return running;}
    public MotionModel model(){return model;}
    public void start(){if(running||!available()||prefs.gyroMode()==0)return;java.util.Arrays.fill(acceleration,0);model.clear();running=manager.registerListener(this,accel,10000);running=manager.registerListener(this,gyro,10000)&&running;if(!running)manager.unregisterListener(this);}
    public void stop(){running=false;if(manager!=null)manager.unregisterListener(this);model.clear();state.gyroAim(0,0);changed.run();}
    public float[] snapshot(){return running?model.snapshot(SystemClock.elapsedRealtime()):new float[6];}
    @Override public void onSensorChanged(SensorEvent event){
        if(!running)return;int rotation=windows.getDefaultDisplay().getRotation();float[] values=MotionModel.rotate(event.values[0],event.values[1],event.values[2],rotation);
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){System.arraycopy(values,0,acceleration,0,3);return;}
        long now=SystemClock.elapsedRealtime();model.feed(now,values[0],values[1],values[2],acceleration[0],acceleration[1],acceleration[2]);
        float[] m=model.snapshot(now);int mode=prefs.gyroMode();boolean aim=mode==1||mode==2&&state.leftTrigger()>25;
        state.gyroAim(aim?AdvancedInput.gyroAxis(-m[1],prefs.gyroGain()/100f,prefs.gyroInvertX()):0,aim?AdvancedInput.gyroAxis(-m[0],prefs.gyroGain()/100f,prefs.gyroInvertY()):0);
        if(now-lastDispatch>=8){lastDispatch=now;changed.run();}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
