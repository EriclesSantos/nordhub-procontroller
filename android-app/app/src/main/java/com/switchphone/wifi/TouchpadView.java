package com.switchphone.wifi;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import com.switchphone.settings.Preferences;
import com.switchphone.controller.PointerSession;

/** One finger pointer/tap/hold-drag, two finger scroll/tap-right. No new game buttons. */
@android.annotation.SuppressLint("ViewConstructor") // Created programmatically with the live session; never inflated from XML.
public final class TouchpadView extends View {
    private final PointerSession service;private final Preferences prefs;private final Paint paint=new Paint(3);private final Handler handler=new Handler(Looper.getMainLooper());
    private float x,y,startX,startY,travel,scroll;private int peak;private long down;private boolean dragging,tracking;
    private final Runnable hold=()->{if(tracking&&peak==1&&travel<12*getResources().getDisplayMetrics().density){dragging=true;beginDrag();}};
    public TouchpadView(Context context,PointerSession service,Preferences prefs){super(context);this.service=service;this.prefs=prefs;setContentDescription("Touchpad: um dedo move, toque clica; dois dedos rolam e tocam com botão direito; segure para arrastar.");setKeepScreenOn(true);}
    private void beginDrag(){service.mouseHeld(true);}
    @Override protected void onDraw(Canvas c){c.drawColor(0xff0d141d);paint.setColor(0xffa88cff);paint.setTextSize(24*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);c.drawText("TOUCHPAD",getWidth()/2f,getHeight()*.35f,paint);paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);paint.setColor(0xffcbd5e1);c.drawText("1 dedo: mover / tocar / segurar e arrastar",getWidth()/2f,getHeight()*.53f,paint);c.drawText("2 dedos: rolar / tocar com botão direito",getWidth()/2f,getHeight()*.65f,paint);}
    @Override public boolean onTouchEvent(MotionEvent e){if(!service.connected()||!service.mouseAllowed())return true;
        int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){tracking=true;dragging=false;travel=scroll=0;peak=1;down=SystemClock.elapsedRealtime();startX=x=e.getX();startY=y=e.getY();handler.postDelayed(hold,350);requestUnbufferedDispatch(e);}
        if(action==MotionEvent.ACTION_POINTER_DOWN){peak=Math.max(peak,e.getPointerCount());handler.removeCallbacks(hold);service.mouseHeld(false);dragging=false;}
        if(action==MotionEvent.ACTION_MOVE&&tracking){float nx=0,ny=0;for(int i=0;i<e.getPointerCount();i++){nx+=e.getX(i);ny+=e.getY(i);}nx/=e.getPointerCount();ny/=e.getPointerCount();float mx=nx-x,my=ny-y;travel+=(float)Math.hypot(mx,my);
            if(e.getPointerCount()==1&&peak==1)service.pointer(Math.round(mx*prefs.touchSensitivity()/100f),Math.round(my*prefs.touchSensitivity()/100f),0);
            else if(e.getPointerCount()>=2){scroll+=my;int steps=(int)(scroll/(20*getResources().getDisplayMetrics().density));if(steps!=0){service.pointer(0,0,-steps*120);scroll-=steps*20*getResources().getDisplayMetrics().density;}}
            x=nx;y=ny;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){float nx=0,ny=0;int n=0;for(int i=0;i<e.getPointerCount();i++)if(action!=MotionEvent.ACTION_POINTER_UP||i!=e.getActionIndex()){nx+=e.getX(i);ny+=e.getY(i);n++;}if(n>0){x=nx/n;y=ny/n;}}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(hold);service.mouseHeld(false);if(action==MotionEvent.ACTION_UP&&!dragging&&travel<12*getResources().getDisplayMetrics().density&&SystemClock.elapsedRealtime()-down<300){service.click(peak>1);performClick();}tracking=false;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);service.mouseHeld(false);service.clearPointerState();super.onDetachedFromWindow();}
}
