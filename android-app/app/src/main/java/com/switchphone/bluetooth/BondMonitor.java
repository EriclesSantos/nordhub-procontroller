package com.switchphone.bluetooth;

/** A Bluetooth bond is not a HID connection. Does not initiate Android bonding. */
public final class BondMonitor {
    public enum State { UNKNOWN, BONDING, BONDED, LOST }
    public enum Change { IGNORED, BONDING, BONDED, FAILED, REMOVED }
    public static final long MANUAL_LINK_GRACE_MS=5000;
    private State state=State.UNKNOWN;
    private boolean everBonded,outboundRequested,existingFound,bondedCallback,keyMissing;
    private int lossReason=BondFailure.UNAVAILABLE;
    private long bondedAt;
    public void reset(){state=State.UNKNOWN;everBonded=false;outboundRequested=false;bondedAt=0;existingFound=false;bondedCallback=false;keyMissing=false;lossReason=BondFailure.UNAVAILABLE;}
    public State state(){return state;}
    public boolean everBonded(){return everBonded;}
    public boolean outboundRequested(){return outboundRequested;}
    public boolean existingFound(){return existingFound;}
    public boolean bondedCallback(){return bondedCallback;}
    public boolean keyMissingObserved(){return keyMissing;}
    public int lossReason(){return lossReason;}
    /** A saved bond is a snapshot, not a new BOND_BONDED confirmation from the system. */
    public boolean seedExisting(long now){
        if(state!=State.UNKNOWN)return false;
        state=State.BONDED;everBonded=true;existingFound=true;bondedAt=now;return true;
    }
    public boolean observeKeyMissing(){if(state==State.LOST||keyMissing)return false;keyMissing=true;return true;}
    public void lossReason(int reason){if(state==State.LOST)lossReason=reason;}
    public Change observe(State next,long now){
        if(state==State.LOST)return Change.IGNORED;
        if(next==State.BONDED)bondedCallback=true;
        if(state==next||next==State.UNKNOWN)return Change.IGNORED;
        if(next==State.LOST){
            if(state==State.UNKNOWN)return Change.IGNORED;
            state=State.LOST;return everBonded?Change.REMOVED:Change.FAILED;
        }
        state=next;
        if(next==State.BONDED){everBonded=true;bondedAt=now;return Change.BONDED;}
        return Change.BONDING;
    }
    public boolean canRequestLink(long now){return state==State.BONDED&&!outboundRequested&&now-bondedAt>=MANUAL_LINK_GRACE_MS;}
    /** Only called while an opt-in recovery window is live; terminal attempts stay terminal. */
    public Change resumeAfterLoss(State next,long now){
        if(state!=State.LOST||(next!=State.BONDING&&next!=State.BONDED))return Change.IGNORED;
        state=State.UNKNOWN;return observe(next,now);
    }
    /** One request after the host has had time to initiate its own HID channel. */
    public boolean requestLink(long now){if(!canRequestLink(now))return false;outboundRequested=true;return true;}
}
