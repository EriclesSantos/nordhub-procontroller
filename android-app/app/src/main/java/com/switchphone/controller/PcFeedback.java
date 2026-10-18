package com.switchphone.controller;

/** Shared eight-byte response for both PC transports: motors, NH signature, slot, flags, version, reserved. */
public final class PcFeedback {
    private PcFeedback(){}
    public static boolean valid(byte[] b){return b!=null&&b.length==8&&b[2]==78&&b[3]==72&&(b[4]&255)<4&&(b[5]&254)==0&&b[6]==1&&b[7]==0;}
}
