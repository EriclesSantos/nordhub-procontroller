import com.switchphone.bluetooth.*;
import static com.switchphone.bluetooth.BondMonitor.State.*;
import static com.switchphone.bluetooth.BondMonitor.Change.IGNORED;
import static com.switchphone.bluetooth.BondMonitor.Change.REMOVED;
import static com.switchphone.bluetooth.BondMonitor.Change.FAILED;

/** Replays the supplied fresh/saved-bond failures under each opt-in transport policy. */
public final class LinkExperimentTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static PairingFlow waiting(){
        PairingFlow f=new PairingFlow();f.begin(true,true);f.next(true);f.identityResult(true);
        f.next(true);f.visibilityResult(true);f.next(true);f.registered();return f;
    }
    private static BondMonitor.Change event(LinkExperiment e,BondMonitor b,BondMonitor.State next,long now){
        return b.state()==LOST&&e.canResume(now)?b.resumeAfterLoss(next,now):b.observe(next,now);
    }
    public static void main(String[] args){
        // Saved-bond trace: registered at 1s, removed at 7s, no native HID callback.
        BondMonitor b=new BondMonitor();b.seedExisting(1000);
        LinkExperiment e=new LinkExperiment(1);PairingFlow f=waiting();
        check(event(e,b,LOST,7000)==REMOVED,"Physical saved bond removal retained");b.lossReason(0);
        check(e.deferLoss(7000,true),"Opt-in retains registered transport after first loss");
        check(e.pending()&&e.remaining(7000)==12000,"Recovery is bounded to twelve seconds");
        check(f.step()==PairingFlow.Step.WAIT_HOST,"Loss experiment cannot invent a native connection or unlock L+R");
        check(!b.canRequestLink(10000),"LOST cannot initiate an HID connection");
        check(event(e,b,LOST,8000)==IGNORED&&e.remaining(8000)==11000,"Duplicate removal does not extend deadline");
        check(e.canResume(18999)&&!e.expired(18999),"Recovery remains possible just before boundary");
        check(e.expired(19000)&&!e.canResume(19000),"At boundary recovery is terminal");
        check(event(e,b,BONDED,19000)==IGNORED,"Late BONDED cannot revive lost monitor");
        e.finish();f.fail();
        check(!e.pending()&&!e.claimAutomaticOpen(true)&&!f.transport(PairingFlow.Step.ACTIVE),"Teardown prevents late completion");
        check(b.existingFound()&&!b.bondedCallback()&&b.lossReason()==0,"Evidence survives an unsuccessful experiment");

        // Fresh trace: BONDING at 38s, NONE at 46s; allow one actual new system attempt.
        b=new BondMonitor();e=new LinkExperiment(3);f=waiting();
        check(event(e,b,BONDING,38000)==BondMonitor.Change.BONDING,"Fresh trace begins with actual Android event");
        check(event(e,b,LOST,46000)==FAILED&&!b.everBonded(),"Initial failure is not a removed confirmed bond");
        check(e.deferLoss(46000,true),"Combined mode permits one recovery window");
        check(event(e,b,BONDING,47000)==BondMonitor.Change.BONDING,"Actual new BONDING accepted during window");
        check(!e.claimAutomaticOpen(b.canRequestLink(55000)),"No outbound HID while rebonding");
        check(event(e,b,BONDED,48000)==BondMonitor.Change.BONDED,"Actual new BONDED accepted during window");e.restored();
        check(!e.pending()&&b.bondedCallback()&&!b.existingFound(),"Real bond event closes window without faking snapshot");
        check(f.waitingHost(),"Recomposed bond alone still does not open HID");
        check(!e.claimAutomaticOpen(b.canRequestLink(52999)),"Five second stable bond grace is enforced");
        check(event(e,b,BONDED,52500)==IGNORED,"Duplicate BONDED does not reset stable grace");
        check(e.claimAutomaticOpen(b.canRequestLink(53000))&&b.requestLink(53000),"Exactly one outbound request after stable grace");
        check(e.automaticRequested()&&b.outboundRequested(),"Diagnostics distinguish outbound experiment");
        check(!e.claimAutomaticOpen(true)&&!b.requestLink(54000),"No repeated HID connect even if timer ticks again");
        check(event(e,b,LOST,55000)==REMOVED&&!e.deferLoss(55000,true),"Second failure ends rather than restarting recovery");
        check(b.everBonded()&&b.bondedCallback(),"History of a confirmed bond is retained");

        // Bonding restarts inside window but does not finish before its original deadline.
        b=new BondMonitor();b.seedExisting(0);b.observe(LOST,1000);e=new LinkExperiment(1);e.deferLoss(1000,true);
        check(event(e,b,BONDING,12000)==BondMonitor.Change.BONDING,"Late BONDING is observed inside window");
        check(e.expired(13000)&&e.remaining(13000)==0,"BONDING does not reset recovery deadline");
        e.finish();f=waiting();f.cancel();
        check(!e.canResume(12500)&&!e.deferLoss(14000,true),"Cancel cannot be undone by delayed callbacks");
        check(!f.transport(PairingFlow.Step.ACTIVE),"Cancelled PairingFlow cannot become connected");

        // Stable-bond mode does not change bond-loss handling; inbound host can win grace.
        b=new BondMonitor();b.seedExisting(1000);e=new LinkExperiment(2);
        check(!e.deferLoss(2000,true),"HID-only mode keeps baseline loss behavior");
        check(!e.claimAutomaticOpen(b.canRequestLink(5999)),"Saved bond also gets five second grace");
        check(e.claimAutomaticOpen(b.canRequestLink(6000))&&b.requestLink(6000),"Saved bond may be used without synthesizing BONDED");
        check(!b.bondedCallback()&&b.existingFound(),"Outbound request does not rewrite actual callbacks");
        b=new BondMonitor();b.observe(BONDED,0);e=new LinkExperiment(2);f=waiting();
        check(f.transport(PairingFlow.Step.CONNECTING)&&!f.waitingHost(),"Native inbound CONNECTING suppresses scheduler eligibility");
        check(f.transport(PairingFlow.Step.NEGOTIATING)&&!b.outboundRequested()&&!e.automaticRequested(),"Inbound connection is never replaced by automatic outbound call");

        for(int mode:new int[]{0,1,2,3,4,-1,99}){
            e=new LinkExperiment(mode);
            check(!e.deferLoss(1000,false),"No recovery without registered READY transport: "+mode);
            check(!e.claimAutomaticOpen(false),"No automatic connect without stable bond: "+mode);
            e.finish();check(!e.claimAutomaticOpen(true)&&!e.deferLoss(2000,true),"Finished experiment is terminal: "+mode);
        }
        e=new LinkExperiment(0);check(!e.deferLoss(0,true)&&!e.claimAutomaticOpen(true),"Default stays passive and loss is terminal");
        e=new LinkExperiment(1);check(!e.claimAutomaticOpen(true),"Bond-only test cannot open HID automatically");
        e=new LinkExperiment(3);e.deferLoss(0,true);check(!e.claimAutomaticOpen(true),"Even combined test cannot open HID during pending loss");
        e.restored();check(!e.deferLoss(1000,true),"Successful recovery cannot allocate a second window");
        System.out.println("PASS: "+checks+" link-experiment assertions (physical traces, deadlines, terminal callbacks, one outbound request)");
    }
}
