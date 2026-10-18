package com.switchphone.controller;

/** Local touch diagnostics never mutate the state read by Bluetooth, including periodic reports. */
public final class InputRouter {
    private final ControllerState transport=new ControllerState(),local=new ControllerState();
    private boolean testing;
    public ControllerState transport(){return transport;}
    public ControllerState touch(){return testing?local:transport;}
    public boolean testing(){return testing;}
    public void testing(boolean enabled){transport.clear();local.clear();testing=enabled;}
    public void clear(){transport.clear();local.clear();}
}
