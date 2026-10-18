package com.switchphone.layouts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared drawing and hit-test geometry, expressed in the actual available viewport. */
public final class ControllerGeometry {
    public static final class Key {
        public final float x,y,w,h;
        public final String label;
        public final int id;
        public final boolean round;
        Key(float x,float y,float w,float h,String label,int id,boolean round) {
            this.x=x;this.y=y;this.w=w;this.h=h;this.label=label;this.id=id;this.round=round;
        }
        public boolean contains(float px,float py) {
            return round?Math.hypot(px-x,py-y)<=w/2:Math.abs(px-x)<=w/2&&Math.abs(py-y)<=h/2;
        }
    }
    public final float unit,radius,leftX,leftY,rightX,rightY,dpadX,dpadY,dpadStep;
    public final List<Key> keys;
    public ControllerGeometry(float width,float height,int layout) {
        if(width<=0||height<=0||layout<0||layout>2)throw new IllegalArgumentException("Invalid controller viewport");
        unit=Math.min(width/1000f,height/400f);radius=70*unit;
        leftX=width*(layout==2?.34f:.16f);leftY=height*(layout==2?.76f:.38f);
        rightX=width*.68f;rightY=height*.76f;
        dpadX=width*.16f;dpadY=height*(layout==2?.38f:.77f);dpadStep=48*unit;
        ArrayList<Key> result=new ArrayList<>();
        add(result,dpadX,dpadY-dpadStep,44,44,"▲",20,false);
        add(result,dpadX+dpadStep,dpadY,44,44,"▶",21,false);
        add(result,dpadX,dpadY+dpadStep,44,44,"▼",22,false);
        add(result,dpadX-dpadStep,dpadY,44,44,"◀",23,false);
        float fx=width*.84f,fy=height*.38f,spacing=60*unit;
        String[] labels=layout==0?new String[]{"B","A","Y","X"}:layout==1?new String[]{"A","B","X","Y"}:new String[]{"×","○","□","△"};
        add(result,fx,fy+spacing,68,68,labels[0],0,true);
        add(result,fx+spacing,fy,68,68,labels[1],1,true);
        add(result,fx-spacing,fy,68,68,labels[2],2,true);
        add(result,fx,fy-spacing,68,68,labels[3],3,true);
        String[] shoulders=layout==0?new String[]{"L","R","ZL","ZR"}:layout==1?new String[]{"LB","RB","LT","RT"}:new String[]{"L1","R1","L2","R2"};
        add(result,width*.14f,28*unit,110,44,shoulders[0],4,false);
        add(result,width*.86f,28*unit,110,44,shoulders[1],5,false);
        add(result,width*.29f,28*unit,110,44,shoulders[2],6,false);
        add(result,width*.71f,28*unit,110,44,shoulders[3],7,false);
        add(result,width*.445f,height*.38f,82,44,layout==0?"−":layout==1?"View":"Share",8,false);
        add(result,width*.555f,height*.38f,82,44,layout==0?"+":layout==1?"Menu":"Options",9,false);
        add(result,width*.445f,height*.59f,82,44,"Home",10,false);
        add(result,width*.555f,height*.59f,82,44,"Capture",11,false);
        add(result,width*.45f,height*.83f,64,44,"L3",12,false);
        add(result,width*.55f,height*.83f,64,44,"R3",13,false);
        keys=Collections.unmodifiableList(result);
    }
    private void add(ArrayList<Key> target,float x,float y,float w,float h,String label,int id,boolean round) {
        target.add(new Key(x,y,w*unit,h*unit,label,id,round));
    }
}
