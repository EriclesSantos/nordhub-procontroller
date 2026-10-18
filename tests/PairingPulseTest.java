import com.switchphone.controller.*;
import com.switchphone.protocol.NintendoProtocol;

public final class PairingPulseTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ControllerState fingers=new ControllerState();fingers.set(0,true);fingers.set(ControllerState.L,true);
        PairingPulse pulse=new PairingPulse();pulse.start(1000);
        byte[] held=pulse.apply(fingers.report(),1001);
        check((held[0]&0x31)==0x31,"L+R adds to existing buttons");
        check(fingers.report()[0]==0x11,"Pulse does not mutate touch state");
        NintendoProtocol protocol=new NintendoProtocol(new byte[]{2,3,4,5,6,7});
        byte[] simple=protocol.input(held,1001).data;
        check((simple[0]&0x30)==0x30,"Published Pro Controller simple-mode L/R bits 0x10/0x20");
        byte[] command=new byte[11];command[9]=3;command[10]=0x30;protocol.output(1,command,held,1002);
        byte[] full=protocol.input(held,1003).data;
        check((full[2]&0x40)!=0&&(full[4]&0x40)!=0,"Nintendo full-mode L+R positions");
        byte[] released=pulse.apply(fingers.report(),1300);
        check((released[0]&0x30)==0x10,"Pulse expiry preserves L held by a finger and releases only synthetic R");
        pulse.start(2000);pulse.clear();check((pulse.apply(new ControllerState().report(),2001)[0]&0x30)==0,"Cancellation sends neutral L+R");
        pulse.start(3000);pulse.start(3100);check((pulse.apply(new ControllerState().report(),3300)[0]&0x30)==0x30,"Repeated press extends pulse predictably");
        check((pulse.apply(new ControllerState().report(),3400)[0]&0x30)==0,"Extended pulse eventually releases");
        System.out.println("PASS: "+checks+" L+R setup pulse assertions (3F/30, timed release, held-finger isolation)");
    }
}
