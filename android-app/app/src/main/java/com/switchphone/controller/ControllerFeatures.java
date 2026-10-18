package com.switchphone.controller;

import android.content.Context;
import android.os.*;
import com.switchphone.haptics.Haptics;
import com.switchphone.settings.Preferences;

/** Permanent feature state and control frame; the destination's transport only carries bytes. */
public final class ControllerFeatures implements PointerSession {
    private final Context context;private final ControllerState state;private final Preferences prefs;private final MotionInput motion;
    private final Handler main=new Handler(Looper.getMainLooper());private boolean visible,rumbling;private volatile long receivedAt;private volatile boolean mouse;
    private int dx,dy,wheel,held,pulse,battery;private long pulseUntil,batteryAt;
    private final Runnable watchdog=new Runnable(){public void run(){if(!visible)return;if(!connected()){if(rumbling)Haptics.stop(context);rumbling=false;clearPointerState();}main.postDelayed(this,250);}};
    public ControllerFeatures(Context context,ControllerState state,Preferences prefs,Runnable changed){this.context=context;this.state=state;this.prefs=prefs;motion=new MotionInput(context,state,prefs,changed);}
    public MotionInput motion(){return motion;}
    @Override public boolean connected(){return receivedAt>0&&SystemClock.elapsedRealtime()-receivedAt<750;}
    @Override public boolean mouseAllowed(){return mouse&&connected();}
    @Override public synchronized void pointer(int x,int y,int w){if(!mouseAllowed())return;dx=(int)AdvancedInput.clamp(dx+x,-300,300);dy=(int)AdvancedInput.clamp(dy+y,-300,300);wheel=(int)AdvancedInput.clamp(wheel+w,-1200,1200);}
    @Override public synchronized void mouseHeld(boolean value){held=value&&mouseAllowed()?1:0;}
    @Override public synchronized void click(boolean right){if(!mouseAllowed())return;pulse=right?2:1;pulseUntil=SystemClock.elapsedRealtime()+70;}
    @Override public synchronized void clearPointerState(){dx=dy=wheel=held=pulse=0;pulseUntil=0;}
    public synchronized byte[] frame(){long now=SystemClock.elapsedRealtime();if(now-batteryAt>1000){battery=context.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);batteryAt=now;}byte[] bytes;synchronized(state){bytes=ControlFrame.encode(state,motion.snapshot(),dx,dy,wheel,mouseAllowed()?held|(now<pulseUntil?pulse:0):0,battery);}dx=dy=wheel=0;return bytes;}
    public boolean feedback(byte[] payload){if(!PcFeedback.valid(payload))return false;byte[] copy=payload.clone();long at=SystemClock.elapsedRealtime();main.post(()->{receivedAt=at;mouse=(copy[5]&1)!=0;if(visible&&connected()){int low=copy[0]&255,high=copy[1]&255;boolean active=(low>0||high>0)&&prefs.gameVibration()>0;if(active||rumbling)Haptics.game(context,low,high,prefs.gameVibration());rumbling=active;}});return true;}
    public void visible(boolean value){visible=value;state.clear();clearPointerState();motion.stop();Haptics.stop(context);rumbling=false;main.removeCallbacks(watchdog);if(value){motion.start();main.postDelayed(watchdog,250);}}
    public void close(){visible(false);main.removeCallbacksAndMessages(null);receivedAt=0;mouse=false;}
}
