package com.switchphone.bluetooth;

/** Android-free one-shot preflight. A callback can only advance its expected, issued step. */
public final class PairingFlow {
    public enum Step { IDLE, PERMISSION, ENABLE, IDENTITY, VISIBILITY, REGISTER, WAIT_HOST, CONNECTING, NEGOTIATING, ACTIVE, ERROR, CANCELLED }
    public enum Action { NONE, REQUEST_PERMISSION, REQUEST_ENABLE, PREPARE_IDENTITY, REQUEST_VISIBILITY, REGISTER_HID }
    private Step step=Step.IDLE;
    private boolean issued;
    private int attempt;
    public int attempt(){return attempt;}
    public Step step(){return step;}
    public void begin(boolean permission,boolean enabled){attempt++;move(!permission?Step.PERMISSION:!enabled?Step.ENABLE:Step.IDENTITY);}
    private void move(Step next){step=next;issued=false;}
    public Action next(boolean foreground){
        if(!foreground||issued)return Action.NONE;
        Action action=switch(step){
            case PERMISSION->Action.REQUEST_PERMISSION;case ENABLE->Action.REQUEST_ENABLE;
            case IDENTITY->Action.PREPARE_IDENTITY;case VISIBILITY->Action.REQUEST_VISIBILITY;
            case REGISTER->Action.REGISTER_HID;default->Action.NONE;
        };
        if(action!=Action.NONE)issued=true;
        return action;
    }
    public boolean permissionResult(boolean granted,boolean enabled){if(step!=Step.PERMISSION||!issued)return false;move(!granted?Step.ERROR:!enabled?Step.ENABLE:Step.IDENTITY);return true;}
    public boolean enabledResult(boolean enabled){if(step!=Step.ENABLE||!issued)return false;move(enabled?Step.IDENTITY:Step.ERROR);return true;}
    public boolean identityResult(boolean success){return identityResult(success,true);}
    /** An existing bonded host can reconnect without requesting discoverability again. */
    public boolean identityResult(boolean success,boolean needsVisibility){if(step!=Step.IDENTITY||!issued)return false;move(success?(needsVisibility?Step.VISIBILITY:Step.REGISTER):Step.ERROR);return true;}
    public boolean visibilityResult(boolean accepted){if(step!=Step.VISIBILITY||!issued)return false;move(accepted?Step.REGISTER:Step.ERROR);return true;}
    public boolean registered(){if(step!=Step.REGISTER||!issued)return false;move(Step.WAIT_HOST);return true;}
    public boolean transport(Step next){
        if(!liveTransport())return false;
        if(next!=Step.CONNECTING&&next!=Step.NEGOTIATING&&next!=Step.ACTIVE)return false;
        move(next);return true;
    }
    /** Attach a recreated screen without issuing pairing or HID registration again. */
    public boolean restoreTransport(Step current){
        if(step!=Step.IDLE&&step!=Step.ERROR&&step!=Step.CANCELLED)return false;
        if(current!=Step.REGISTER&&current!=Step.WAIT_HOST&&current!=Step.CONNECTING&&current!=Step.NEGOTIATING&&current!=Step.ACTIVE)return false;
        attempt++;move(current);issued=current==Step.REGISTER;return true;
    }
    public boolean awaitingExternal(){return issued&&(step==Step.PERMISSION||step==Step.ENABLE||step==Step.VISIBILITY);}
    public boolean liveTransport(){return step==Step.REGISTER||step==Step.WAIT_HOST||step==Step.CONNECTING||step==Step.NEGOTIATING||step==Step.ACTIVE;}
    public boolean waitingHost(){return step==Step.WAIT_HOST||step==Step.REGISTER;}
    public boolean busy(){return step!=Step.IDLE&&step!=Step.ERROR&&step!=Step.CANCELLED&&step!=Step.ACTIVE;}
    public void fail(){move(Step.ERROR);}
    public void cancel(){attempt++;move(Step.CANCELLED);}
}
