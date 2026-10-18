package com.switchphone.wifi;

import java.nio.ByteBuffer;

/** Camera Y planes can include row padding and non-unit pixel strides. */
public final class QrLuminance {
    private QrLuminance(){}
    public static byte[] read(ByteBuffer buffer,int width,int height,int rowStride,int pixelStride){
        if(buffer==null||width<1||height<1||width*(long)height>8000000||rowStride<1||pixelStride<1||rowStride<(width-1L)*pixelStride+1)throw new IllegalArgumentException("Camera plane");
        long last=buffer.position()+(height-1L)*rowStride+(width-1L)*pixelStride;if(last>=buffer.limit())throw new IllegalArgumentException("Camera plane length");
        int origin=buffer.position();byte[] result=new byte[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)result[y*width+x]=buffer.get(origin+y*rowStride+x*pixelStride);return result;
    }
}
