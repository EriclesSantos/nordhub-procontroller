import com.switchphone.bluetooth.PairingFlow;
import static com.switchphone.bluetooth.PairingFlow.Step.*;
import static com.switchphone.bluetooth.PairingFlow.Action.*;

/** Recreated UI must attach to the service, without discovering/registering again. */
public final class SessionResumeTest {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args){
        for(PairingFlow.Step current:new PairingFlow.Step[]{REGISTER,WAIT_HOST,CONNECTING,NEGOTIATING,ACTIVE}){
            PairingFlow flow=new PairingFlow();int old=flow.attempt();
            check(flow.restoreTransport(current),"Attach live service "+current);
            check(flow.attempt()!=old&&flow.liveTransport(),"Invalidate stale UI callbacks");
            for(int i=0;i<10;i++)check(flow.next(i%2==0)==NONE,"No system prompt or duplicate HID registration on resume");
            check(!flow.visibilityResult(true)&&!flow.permissionResult(true,true),"Stale permission results ignored");
            check(!flow.restoreTransport(ACTIVE),"Cannot attach a second service over a live attempt");
            if(current==REGISTER)check(flow.registered()&&flow.step()==WAIT_HOST,"Accept pending real registration callback");
            check(flow.transport(ACTIVE)&&flow.step()==ACTIVE,"Continue with real connection callback");
            flow.cancel();check(!flow.transport(ACTIVE),"Manual disconnect rejects late connection callback");
            check(flow.restoreTransport(ACTIVE),"Fresh UI can attach to a subsequent live service");
            flow.fail();check(flow.restoreTransport(WAIT_HOST),"Error UI can attach to a subsequent waiting service");
        }
        for(PairingFlow.Step invalid:new PairingFlow.Step[]{IDLE,PERMISSION,ENABLE,IDENTITY,VISIBILITY,ERROR,CANCELLED}){
            PairingFlow flow=new PairingFlow();int attempt=flow.attempt();
            check(!flow.restoreTransport(invalid)&&flow.step()==IDLE&&flow.attempt()==attempt,"Reject non-transport state "+invalid);
        }
        PairingFlow flow=new PairingFlow();flow.begin(true,true);
        check(!flow.restoreTransport(ACTIVE)&&flow.next(true)==PREPARE_IDENTITY,"Do not replace another pending pairing attempt");
        System.out.println("PASS: "+checks+" service-session resume assertions");
    }
}
