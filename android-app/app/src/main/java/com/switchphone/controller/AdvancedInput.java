package com.switchphone.controller;

/** Pure input transforms shared by all visual layouts. No smoothing delay is added. */
public final class AdvancedInput {
    private AdvancedInput(){}
    public static float clamp(float value,float min,float max){return Float.isFinite(value)?Math.max(min,Math.min(max,value)):0;}
    public static float[] stick(float x,float y,float deadzone,float gain,int curve,boolean invertX,boolean invertY){
        x=clamp(x,-1,1);y=clamp(y,-1,1);deadzone=clamp(deadzone,0,.4f);gain=clamp(gain,.2f,2);
        float length=(float)Math.hypot(x,y);if(length<=deadzone)return new float[]{0,0};
        float radius=clamp((Math.min(1,length)-deadzone)/(1-deadzone),0,1);
        radius=(float)Math.pow(radius,curve==1?1.8:curve==2?.65:1);radius=Math.min(1,radius*gain);
        return new float[]{x/length*radius*(invertX?-1:1),y/length*radius*(invertY?-1:1)};
    }
    public static float trigger(float drag,float travel,boolean digital){return digital?1:clamp(.15f+drag/Math.max(1,travel),.05f,1);}
    public static int remap(int held,int[] mapping){
        if(mapping==null||mapping.length!=14)throw new IllegalArgumentException("Mapping length");
        int result=0;for(int i=0;i<14;i++){int target=mapping[i];if(target< -1||target>13)throw new IllegalArgumentException("Mapping target");if(target>=0&&(held&(1<<i))!=0)result|=1<<target;}return result;
    }
    public static float gyroAxis(float radians,float gain,boolean inverted){
        float value=Math.abs(radians)<.015f?0:radians/1.4f*gain;return clamp(inverted?-value:value,-1,1);
    }
}
