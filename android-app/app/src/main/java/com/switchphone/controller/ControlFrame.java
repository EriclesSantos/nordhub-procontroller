package com.switchphone.controller;

import java.nio.*;

/** Wi-Fi v1: fixed 64-byte payload, little endian; classic Bluetooth remains seven bytes. */
public final class ControlFrame {
    private ControlFrame(){}
    public static byte[] encode(ControllerState state,float[] motion,int dx,int dy,int wheel,int mouseButtons,int battery){
        byte[] legacy=state.report();ByteBuffer b=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        b.put(legacy,0,2).put(legacy[6]).put((byte)0);
        for(short value:state.preciseAxes())b.putShort(value);
        b.put((byte)state.leftTrigger()).put((byte)state.rightTrigger()).put((byte)(mouseButtons&3)).put((byte)0);
        b.putShort((short)AdvancedInput.clamp(dx,-300,300)).putShort((short)AdvancedInput.clamp(dy,-300,300)).putShort((short)AdvancedInput.clamp(wheel,-1200,1200)).putShort((short)0);
        for(int i=0;i<6;i++)b.putFloat(motion!=null&&motion.length==6?AdvancedInput.clamp(motion[i],-200,200):0);
        boolean live=false;if(motion!=null&&motion.length==6)for(float value:motion)live|=Float.isFinite(value)&&Math.abs(value)>.00001f;
        b.put((byte)Math.max(0,Math.min(100,battery))).put((byte)(live?1:0));return b.array();
    }
}
