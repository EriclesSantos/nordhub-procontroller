package com.switchphone.bluetooth;

/** Opt-in, bounded app-side experiments. Never creates bonds or changes security policy. */
public final class LinkExperiment {
    public static final long RECOVERY_MS=12000;
    private final int variant;
    private boolean recoveryUsed,pending,automaticRequested,finished;
    private long deadline;
    private int losses;
    public LinkExperiment(int variant){this.variant=variant>=0&&variant<=4?variant:0;}
    public int variant(){return variant;}
    public static String label(int variant){return switch(variant){case 1->"Teste 1 • vínculo (12s)";case 2->"Teste 2 • abrir HID (5s)";case 3->"Teste 3 • vínculo + HID";case 4->"Teste 4 • HID genérico";default->"Padrão • aguardar Switch";};}
    public boolean deferLoss(long now,boolean registeredAndWaiting){
        if(finished)return false;losses++;
        if((variant&1)==0||!registeredAndWaiting||recoveryUsed)return false;
        recoveryUsed=true;pending=true;deadline=now+RECOVERY_MS;return true;
    }
    public boolean pending(){return pending&&!finished;}
    public boolean canResume(long now){return pending()&&now<deadline;}
    public boolean expired(long now){return pending()&&now>=deadline;}
    public long remaining(long now){return pending()?Math.max(0,deadline-now):0;}
    public void restored(){pending=false;}
    public boolean automaticEnabled(){return !finished&&(variant&2)!=0;}
    public boolean claimAutomaticOpen(boolean stableBondAndReady){
        if(!automaticEnabled()||automaticRequested||pending()||!stableBondAndReady)return false;
        automaticRequested=true;return true;
    }
    public boolean automaticRequested(){return automaticRequested;}
    public void finish(){finished=true;pending=false;}
    public String diagnostic(){return label(variant)+" • perdas observadas: "+losses+" • janela usada: "+recoveryUsed+" • aguardando recomposição: "+pending()+" • abertura HID automática solicitada: "+automaticRequested;}
}
