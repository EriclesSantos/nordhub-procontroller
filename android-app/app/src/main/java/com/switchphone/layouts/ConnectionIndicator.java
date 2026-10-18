package com.switchphone.layouts;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Three tiny lights. Animate only while pairing is actually pending. */
public final class ConnectionIndicator extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean busy,connected,error;
    public ConnectionIndicator(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    public void status(boolean busy,boolean connected,boolean error){
        this.error=error;this.connected=connected&&!error;this.busy=busy&&!this.connected&&!error;
        setContentDescription(this.connected?"Canal HID conectado":this.busy?"Pareamento em andamento":error?"Falha na conexão":"Desconectado");invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        float radius=Math.min(getHeight()*.24f,getWidth()/10f);
        int step=(int)(SystemClock.uptimeMillis()/220%3);
        for(int i=0;i<3;i++){
            paint.setColor(connected?Color.rgb(74,222,128):busy||error?Color.rgb(239,83,80):Color.rgb(100,111,125));
            paint.setAlpha(busy?(i==step?255:65):255);
            canvas.drawCircle(getWidth()*(i+1)/4f,getHeight()/2f,radius,paint);
        }
        if(busy&&isShown())postInvalidateDelayed(100);
    }
}
