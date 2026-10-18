import com.switchphone.bluetooth.BondMonitor;
import com.switchphone.bluetooth.BondFailure;
import com.switchphone.bluetooth.PairingFlow;
import static com.switchphone.bluetooth.BondMonitor.State.*;
import static com.switchphone.bluetooth.BondMonitor.Change.*;

/** Replays the physical 0.2.2 bond/drop trace and separates bonding from native HID readiness. */
public final class BondMonitorTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static PairingFlow waiting(){
        PairingFlow f=new PairingFlow();f.begin(true,true);f.next(true);f.identityResult(true);
        f.next(true);f.visibilityResult(true);f.next(true);f.registered();return f;
    }
    public static void main(String[] args){
        BondMonitor b=new BondMonitor();PairingFlow f=waiting();
        check(b.observe(BondMonitor.State.BONDING,4000)==BondMonitor.Change.BONDING,"4s: Android starts bonding");
        check(!b.canRequestLink(10000),"No outbound request while bonding");
        check(b.observe(BondMonitor.State.BONDED,10000)==BondMonitor.Change.BONDED,"10s: Android accepts bond");
        check(f.step()==PairingFlow.Step.WAIT_HOST,"Bonding does not advance HID state or enable L+R");
        check(!b.outboundRequested(),"BOND_BONDED never automatically requests a connection");
        check(!b.canRequestLink(10000)&&!b.requestLink(11000),"No immediate outbound race with the host");
        check(b.observe(LOST,11000)==REMOVED,"11s: loss of an accepted bond is diagnosed as removal");
        f.fail();
        check(b.everBonded()&&b.state()==LOST,"Keep last-attempt evidence after teardown");
        check(!b.requestLink(20000),"A lost bond cannot start a new connection");
        check(b.observe(BondMonitor.State.BONDING,11001)==IGNORED,"Repeated system pairing cannot restart this attempt");
        check(b.observe(BondMonitor.State.BONDED,20000)==IGNORED,"A late success cannot revive this attempt");
        check(!f.transport(PairingFlow.Step.ACTIVE)&&f.next(true)==PairingFlow.Action.NONE,"No green status or automatic restart after failure");
        b.reset();check(b.state()==UNKNOWN&&!b.everBonded(),"Only explicit new attempt clears evidence");
        check(b.observe(LOST,0)==IGNORED,"Unrelated unbonded state with no attempt is ignored");
        b.observe(BondMonitor.State.BONDING,100);
        check(b.observe(LOST,200)==FAILED,"Failed initial bonding differs from removal of an accepted bond");
        b.reset();b.observe(BondMonitor.State.BONDED,1000);
        check(!b.canRequestLink(5999)&&b.canRequestLink(6000),"Manual fallback needs a stable 5s bond");
        check(!b.outboundRequested(),"Elapsed time alone never initiates a connection");
        check(b.observe(BondMonitor.State.BONDED,6500)==IGNORED,"Duplicate bond broadcast does not restart grace period");
        check(b.requestLink(6500)&&!b.requestLink(6501),"One explicit outbound request per attempt");
        check(b.outboundRequested(),"Diagnostic records user-requested connection");
        b.observe(LOST,7000);check(!b.canRequestLink(100000),"No reconnection loop after manual request loses the bond");
        b.reset();b.observe(BondMonitor.State.BONDING,100);b.observe(BondMonitor.State.BONDED,200);
        b.observe(BondMonitor.State.BONDING,400);check(!b.canRequestLink(9000),"New bonding in progress blocks manual fallback");
        b.observe(BondMonitor.State.BONDED,10000);check(!b.canRequestLink(14999)&&b.canRequestLink(15000),"Newly accepted bond must stabilise again");
        // 0.2.3 physical trace: starts with a saved bond; no new BOND_BONDED callback, then 12 -> 10.
        b.reset();f=waiting();
        check(b.seedExisting(1000)&&b.existingFound()&&b.everBonded(),"Saved bond snapshot is recorded");
        check(!b.bondedCallback(),"A saved bond must not be presented as confirmation in this attempt");
        check(!b.seedExisting(4000)&&b.canRequestLink(6000),"Duplicate discovery cannot reset the manual grace period");
        check(!b.outboundRequested()&&f.step()==PairingFlow.Step.WAIT_HOST,"An existing bond does not open HID or enable inputs");
        check(b.observe(LOST,7000)==REMOVED,"7s: lost previous bond is a removal, not a newly confirmed pairing");
        b.lossReason(1);f.fail();
        check(b.lossReason()==1&&b.existingFound()&&!b.bondedCallback(),"Retain raw Android reason and old-bond evidence after teardown");
        check(!b.seedExisting(8000)&&b.observe(BondMonitor.State.BONDED,9000)==IGNORED&&!b.bondedCallback(),"Late snapshots/callbacks cannot rewrite terminal evidence");
        check(!b.requestLink(10000)&&!f.transport(PairingFlow.Step.ACTIVE),"Loss reason never turns connection green");
        b.reset();check(b.lossReason()==BondFailure.UNAVAILABLE&&!b.existingFound()&&!b.bondedCallback()&&!b.keyMissingObserved(),"New attempt clears previous reason and milestones");
        b.lossReason(9);check(b.lossReason()==BondFailure.UNAVAILABLE,"Reason before any loss is ignored");
        b.seedExisting(1000);b.observe(BondMonitor.State.BONDED,2000);
        check(b.existingFound()&&b.bondedCallback()&&b.canRequestLink(6000),"Real BONDED callback is independent of a snapshot, without changing stable time");
        b.reset();b.observe(BondMonitor.State.BONDING,1000);b.observe(BondMonitor.State.BONDED,2000);
        check(!b.existingFound()&&b.bondedCallback(),"Fresh host pairing is distinguished from a saved bond");
        check(b.observeKeyMissing()&&b.keyMissingObserved()&&!b.observeKeyMissing(),"Key-missing signal is recorded once");
        check(b.state()==BondMonitor.State.BONDED&&!b.outboundRequested(),"Key-missing signal cannot change pairing or initiate a connection");
        b.observe(LOST,3000);b.lossReason(9);check(b.lossReason()==9&&!b.observeKeyMissing(),"Terminal attempt ignores late key-missing signals");
        b.reset();check(!b.keyMissingObserved(),"Key-missing evidence cannot leak into next attempt");
        System.out.println("PASS: "+checks+" bond-monitor assertions (physical trace, no automatic connect, terminal loss, manual fallback)");
    }
}
