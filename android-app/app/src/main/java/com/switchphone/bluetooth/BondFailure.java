package com.switchphone.bluetooth;

/** Best-effort Android broadcast metadata. No hidden API calls, key access, or inferred culprit. */
public final class BondFailure {
    private BondFailure(){}
    // The extra is internal Android metadata, so OEMs may omit it. It never controls connection policy.
    public static final String EXTRA_REASON="android.bluetooth.device.extra.REASON";
    public static final String ACTION_KEY_MISSING="android.bluetooth.device.action.KEY_MISSING";
    public static final int UNAVAILABLE=Integer.MIN_VALUE;
    public static String describe(int code){
        String detail=switch(code){
            case 1->"falha de autenticação";
            case 2->"autenticação rejeitada";
            case 3->"autenticação cancelada localmente";
            case 4->"host não pôde ser contatado";
            case 5->"descoberta Bluetooth em andamento";
            case 6->"autenticação expirou";
            case 7->"limite de tentativas repetidas";
            case 8->"autenticação cancelada pelo lado remoto";
            // Android also uses REMOVED as a fallback; this does not prove an explicit user removal.
            case 9->"vínculo removido; esse código também é usado como motivo genérico";
            case UNAVAILABLE->"não disponibilizado pelo Android/fabricante";
            default->"código sem descrição conhecida";
        };
        return code==UNAVAILABLE?detail:code+" ("+detail+")";
    }
}
