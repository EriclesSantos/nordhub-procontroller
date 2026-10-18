import com.switchphone.bluetooth.PairingFlow;
import static com.switchphone.bluetooth.PairingFlow.Action.*;
import static com.switchphone.bluetooth.PairingFlow.Step.*;

/** Regression: system permission/visibility screens must not register, tear down or restart HID. */
public final class PairingFlowTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void noRepeatedAction(PairingFlow flow){for(int i=0;i<20;i++)check(flow.next(i%2==0)==NONE,"Focus/resume cannot repeat a system request");}
    private static PairingFlow beforeVisibility(){
        PairingFlow flow=new PairingFlow();flow.begin(true,true);
        check(flow.next(true)==PREPARE_IDENTITY,"Identity before discoverability");
        check(flow.identityResult(true),"Name confirmed");
        check(!flow.registered(),"No HID registration before visibility approval");
        check(flow.next(true)==REQUEST_VISIBILITY,"Exactly one visibility prompt");return flow;
    }
    public static void main(String[] args){
        PairingFlow flow=new PairingFlow();flow.begin(false,false);
        check(flow.next(false)==NONE,"Never open a system screen while background");
        check(flow.next(true)==REQUEST_PERMISSION,"Permission first");noRepeatedAction(flow);
        check(flow.awaitingExternal(),"Activity stop during permission dialog is expected");
        check(flow.permissionResult(true,false),"Permission result accepted once");
        check(!flow.permissionResult(true,false),"Duplicate permission result ignored");
        check(flow.next(true)==REQUEST_ENABLE,"Enable Bluetooth only after permission");noRepeatedAction(flow);
        check(flow.enabledResult(true),"Bluetooth enabled");
        check(flow.next(true)==PREPARE_IDENTITY,"Name preparation after Bluetooth enable");
        check(flow.identityResult(true),"Identity confirmed");
        check(flow.next(true)==REQUEST_VISIBILITY,"Visibility precedes HID");noRepeatedAction(flow);
        check(flow.awaitingExternal()&&!flow.liveTransport(),"No HID to close during visibility dialog");
        check(flow.visibilityResult(true),"Visibility approved");
        check(!flow.awaitingExternal(),"System screen finished");
        check(flow.next(false)==NONE,"Wait for foreground AFTER onActivityResult");
        check(!flow.registered(),"Register command not yet issued");
        check(flow.next(true)==REGISTER_HID,"Register once after returning to foreground");noRepeatedAction(flow);
        check(flow.registered()&&flow.step()==WAIT_HOST,"Wait for inbound host connection");
        check(!flow.registered()&&flow.next(true)==NONE,"Duplicate HID callback cannot relaunch discoverability");
        check(flow.transport(CONNECTING)&&flow.transport(NEGOTIATING),"Real host callbacks advance negotiation");
        check(flow.busy()&&!flow.awaitingExternal(),"Negotiation keeps progress without another permission dialog");
        check(flow.transport(ACTIVE)&&!flow.busy(),"Only transport confirmation completes pairing");
        flow.fail();check(!flow.transport(ACTIVE)&&flow.next(true)==NONE,"Late callbacks cannot turn error green");
        flow=new PairingFlow();flow.begin(false,true);flow.next(true);flow.permissionResult(false,false);
        check(flow.step()==ERROR&&flow.next(true)==NONE,"Denied permission never loops into another request");
        check(!flow.permissionResult(true,true),"Late permission success ignored after failure");
        flow=new PairingFlow();flow.begin(true,false);flow.next(true);flow.enabledResult(false);
        check(flow.step()==ERROR&&flow.next(true)==NONE,"Cancelled Bluetooth enable ends attempt");
        flow=beforeVisibility();flow.visibilityResult(false);
        check(flow.step()==ERROR&&!flow.visibilityResult(true)&&flow.next(true)==NONE,"Cancelled visibility never opens HID or retries");
        flow=beforeVisibility();int old=flow.attempt();flow.cancel();
        check(flow.attempt()!=old&&!flow.visibilityResult(true)&&!flow.busy(),"Cancellation invalidates stale work");
        flow.begin(true,true);check(flow.attempt()!=old&&flow.next(true)==PREPARE_IDENTITY,"Only explicit fresh attempt can retry");
        flow=beforeVisibility();flow.fail();check(!flow.visibilityResult(true)&&flow.next(true)==NONE,"Timeout invalidates late system result");
        flow=beforeVisibility();flow.visibilityResult(true);flow.next(true);flow.fail();
        check(!flow.registered()&&!flow.transport(CONNECTING),"Stale binding/HID callback cannot restart a failed attempt");
        System.out.println("PASS: "+checks+" pairing-flow assertions (single requests, foreground gate, cancellation, late callbacks)");
    }
}
