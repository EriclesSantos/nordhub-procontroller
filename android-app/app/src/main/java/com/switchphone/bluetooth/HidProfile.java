package com.switchphone.bluetooth;

import com.switchphone.protocol.NintendoDescriptor;

/** Radio naming and report protocol are separate choices; neither changes Android security. */
public final class HidProfile {
    private final boolean switchIdentity,nintendo;
    private HidProfile(boolean switchIdentity,boolean nintendo){this.switchIdentity=switchIdentity;this.nintendo=nintendo;}
    public static HidProfile forAttempt(boolean switchTarget,int experiment){return new HidProfile(switchTarget,switchTarget&&experiment!=4);}
    public boolean switchIdentity(){return switchIdentity;}
    public boolean nintendoProtocol(){return nintendo;}
    public boolean genericSwitch(){return switchIdentity&&!nintendo;}
    public String name(){return switchIdentity?"Pro Controller":"Nördhub ProController";}
    public String description(){return switchIdentity?"SwitchPhone experimental controller":"Nordhub generic gamepad";}
    public String provider(){return switchIdentity?"SwitchPhone":"Nordhub";}
    public byte subclass(){return 0x08;}
    public byte[] descriptor(){return nintendo?NintendoDescriptor.bytes():DiagnosticDescriptor.bytes();}
    public String diagnostic(){return "Perfil HID: "+(nintendo?"Nintendo parcial":"genérico • relatório 0x01")+" • identificação: "+(switchIdentity?"Pro Controller":"nome Bluetooth original do celular")+" • subclasse solicitada: gamepad (não comprova classe de rádio)";}
}
