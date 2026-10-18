package com.switchphone.controller;

/** Brief L+R setup pulse overlays a snapshot, preserving fingers held in the underlying state. */
public final class PairingPulse {
    private long until=-1;
    public void start(long now){until=now+300;}
    public void clear(){until=-1;}
    public byte[] apply(byte[] source,long now){
        byte[] out=source.clone();if(now<until)out[0]|=(1<<ControllerState.L)|(1<<ControllerState.R);return out;
    }
}
