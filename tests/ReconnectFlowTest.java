import com.switchphone.bluetooth.PairingFlow;
import com.switchphone.bluetooth.BondMonitor;
import static com.switchphone.bluetooth.PairingFlow.Action.*;
import static com.switchphone.bluetooth.PairingFlow.Step.*;

/** Existing associations must be usable without initiating a second discovery/pairing. */
public final class ReconnectFlowTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(boolean permission:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){
            PairingFlow flow=new PairingFlow();BondMonitor bond=new BondMonitor();
            for(int cycle=0;cycle<3;cycle++){
                flow.begin(permission,enabled);bond.reset();
                if(!permission){check(flow.next(true)==REQUEST_PERMISSION,"Permission required before reading associations");check(flow.permissionResult(true,enabled),"Permission accepted");}
                if(!enabled){check(flow.next(true)==REQUEST_ENABLE,"Bluetooth enable still required");check(flow.enabledResult(true),"Bluetooth enabled");}
                check(flow.next(false)==NONE,"Background cannot prepare or register HID");
                check(flow.next(true)==PREPARE_IDENTITY,"Prepare same controller identity");
                check(bond.seedExisting(1000),"Read saved association without creating one");
                check(flow.identityResult(true,false),"Saved host does not need discoverability");
                check(flow.step()==REGISTER&&!flow.awaitingExternal(),"Go straight to registration");
                check(!flow.visibilityResult(true),"Stale discovery result cannot change reconnection");
                check(!flow.registered(),"Registration still requires an issued foreground action");
                check(flow.next(true)==REGISTER_HID,"Register HID once");
                check(flow.next(true)==NONE&&flow.registered(),"One register per attempt");
                check(flow.step()==WAIT_HOST&&!bond.bondedCallback(),"Saved bond is not a new bond or an active HID channel");
                check(!bond.requestLink(5999)&&bond.requestLink(6000),"Allow one connection after stable saved bond");
                check(!bond.requestLink(6001),"No repeated outbound connection");
                check(flow.transport(CONNECTING)&&flow.transport(NEGOTIATING)&&flow.transport(ACTIVE),"Real transport/protocol events complete reconnection");
                flow.cancel();check(!flow.transport(ACTIVE),"Old callback cannot revive ended session");
            }
        }
        PairingFlow failed=new PairingFlow();failed.begin(true,true);failed.next(true);failed.identityResult(false,false);
        check(failed.step()==ERROR&&failed.next(true)==NONE,"Failed identity cannot start reconnection");
        PairingFlow fresh=new PairingFlow();fresh.begin(true,true);fresh.next(true);fresh.identityResult(true,true);
        check(fresh.next(true)==REQUEST_VISIBILITY,"New host keeps the original discoverability flow");
        System.out.println("PASS: "+checks+" reconnection assertions (saved association, repeated sessions, permissions, foreground and stale callbacks)");
    }
}
