package com.switchphone.controller;

/** Shared logical controls. Positional face buttons are mapped by the visible layout. */
public final class ControllerState {
    public static final int SOUTH=0, EAST=1, WEST=2, NORTH=3, L=4, R=5, ZL=6, ZR=7,
        MINUS=8, PLUS=9, HOME=10, CAPTURE=11, L3=12, R3=13;
    private int buttons, hat=8;
    private float leftTrigger,rightTrigger,gyroX,gyroY;
    private final byte[] axes = new byte[4];
    private final float[] precise=new float[4];
    public synchronized void set(int index, boolean down) {
        if (index < 0 || index > 13) throw new IllegalArgumentException("Button index");
        if (down) buttons |= 1 << index; else buttons &= ~(1 << index);
        if(index==ZL)leftTrigger=down?1:0;if(index==ZR)rightTrigger=down?1:0;
    }
    public synchronized void replace(int buttons, int hat, float lx, float ly, float rx, float ry) {
        if ((buttons & ~0x3fff) != 0 || hat < 0 || hat > 8) throw new IllegalArgumentException("State");
        this.buttons=buttons; this.hat=hat;
        leftTrigger=(buttons&(1<<ZL))!=0?1:0;rightTrigger=(buttons&(1<<ZR))!=0?1:0;
        axes[0]=axis(lx); axes[1]=axis(ly); axes[2]=axis(rx); axes[3]=axis(ry);
        precise[0]=AdvancedInput.clamp(lx,-1,1);precise[1]=AdvancedInput.clamp(ly,-1,1);precise[2]=AdvancedInput.clamp(rx,-1,1);precise[3]=AdvancedInput.clamp(ry,-1,1);
    }
    private static byte axis(float value) {
        if (!Float.isFinite(value)) return 0;
        return (byte)Math.round(Math.max(-1, Math.min(1,value))*127);
    }
    public synchronized void triggers(float left,float right){leftTrigger=AdvancedInput.clamp(left,0,1);rightTrigger=AdvancedInput.clamp(right,0,1);}
    public synchronized int leftTrigger(){return (buttons&(1<<ZL))==0?0:Math.round(leftTrigger*255);}
    public synchronized int rightTrigger(){return (buttons&(1<<ZR))==0?0:Math.round(rightTrigger*255);}
    public synchronized void gyroAim(float x,float y){gyroX=AdvancedInput.clamp(x,-1,1);gyroY=AdvancedInput.clamp(y,-1,1);}
    public synchronized void clear() { buttons=0;hat=8;leftTrigger=rightTrigger=gyroX=gyroY=0;java.util.Arrays.fill(axes,(byte)0);java.util.Arrays.fill(precise,0); }
    /** Generic diagnostic HID only: buttons LE16, four signed axes, hat 0..7 or neutral 8. */
    public synchronized byte[] report() {
        return new byte[] {(byte)buttons,(byte)(buttons>>8),axes[0],axes[1],axis(axes[2]/127f+gyroX),axis(axes[3]/127f+gyroY),(byte)hat};
    }
    public synchronized short[] preciseAxes(){short[] values=new short[4];for(int i=0;i<4;i++)values[i]=(short)Math.round(32767*AdvancedInput.clamp(precise[i]+(i==2?gyroX:i==3?gyroY:0),-1,1));return values;}
    public static float filterAxis(float value, float deadZone, float sensitivity) {
        float magnitude=Math.min(1,Math.abs(value));
        if (magnitude<=deadZone) return 0;
        return Math.copySign(Math.min(1,(magnitude-deadZone)/(1-deadZone)*sensitivity),value);
    }
}
