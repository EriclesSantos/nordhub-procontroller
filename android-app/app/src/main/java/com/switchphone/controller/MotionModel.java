package com.switchphone.controller;

/** Bias calibration requires 100 consecutive stationary samples; stale data is never reused. */
public final class MotionModel {
    private final float[] bias=new float[3],sum=new float[3],sample=new float[6];
    private long timestamp;private int count;private boolean calibrating,calibrated;
    public synchronized void calibrate(){calibrating=true;count=0;java.util.Arrays.fill(sum,0);}
    public synchronized boolean calibrating(){return calibrating;}
    public synchronized boolean calibrated(){return calibrated;}
    public synchronized int progress(){return count;}
    public synchronized void feed(long millis,float gx,float gy,float gz,float ax,float ay,float az){
        float[] raw={gx,gy,gz,ax,ay,az};for(float v:raw)if(!Float.isFinite(v))return;
        if(millis<timestamp)return;timestamp=millis;
        if(calibrating){
            double acceleration=Math.sqrt(ax*ax+ay*ay+az*az);
            if(Math.max(Math.max(Math.abs(gx),Math.abs(gy)),Math.abs(gz))>.2||Math.abs(acceleration-9.80665)>1.5){count=0;java.util.Arrays.fill(sum,0);}
            else {for(int i=0;i<3;i++)sum[i]+=raw[i];if(++count==100){for(int i=0;i<3;i++)bias[i]=sum[i]/100;calibrating=false;calibrated=true;}}
        }
        for(int i=0;i<3;i++){sample[i]=raw[i]-bias[i];sample[i+3]=raw[i+3];}
    }
    public synchronized float[] snapshot(long millis){return millis>=timestamp&&millis-timestamp<=150&&!calibrating?sample.clone():new float[6];}
    public synchronized void clear(){timestamp=0;java.util.Arrays.fill(sample,0);}
    /** Sensor coordinates transformed to the current display plane. Z remains out of screen. */
    public static float[] rotate(float x,float y,float z,int rotation){return switch(rotation){case 1->new float[]{-y,x,z};case 2->new float[]{-x,-y,z};case 3->new float[]{y,-x,z};default->new float[]{x,y,z};};}
}
