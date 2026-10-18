package com.switchphone.bluetooth;

import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import android.util.Log;
import com.switchphone.controller.ControllerState;
import com.switchphone.controller.PairingPulse;
import com.switchphone.protocol.NintendoProtocol;

/** HID transport owned by the foreground service. UI follows confirmed callbacks. */
public final class HidSession implements AutoCloseable {
    public enum Phase { IDLE, REQUESTING, REGISTERING, READY, CONNECTING, NEGOTIATING, ACTIVE, ERROR, CLOSED }
    public interface Listener {
        void status(Phase phase,String message);
        void resetInput();
        void notice(String message);
    }
    private final Context context;
    private final Listener listener;
    private final ControllerState state;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final boolean nintendo;
    private final HidProfile hidProfile;
    private final IdentityLease identity;
    private NintendoProtocol protocol;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean requested,registered,closed,failing,streaming;
    private Phase phase=Phase.IDLE;
    private int generation;
    private long batteryChecked;
    private String lastNotice;
    private boolean everRegistered,everConnected;
    private int lastConnectionState=-1,hostRequests,inputReports;
    private final PairingPulse pairingPulse=new PairingPulse();
    public HidSession(Context context,ControllerState state,HidProfile profile,Listener listener){
        this.context=context;this.state=state;this.hidProfile=profile;this.nintendo=hidProfile.nintendoProtocol();this.listener=listener;
        identity=new IdentityLease(context);
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
    }
    public Phase phase(){return phase;}
    public boolean registered(){return registered;}
    public boolean connected(){return host!=null;}
    public boolean inputReady(){return phase==Phase.ACTIVE;}
    public boolean nintendoProtocol(){return nintendo;}
    public boolean canSendInput(){return registered&&host!=null&&(phase==Phase.NEGOTIATING||phase==Phase.ACTIVE);}
    public String diagnostic(){
        return hidProfile.diagnostic()+"\nMarcos da tentativa: HID registrado alguma vez: "+everRegistered+" • canal HID aberto alguma vez: "+everConnected
            +"\nÚltima fase HID: "+phase+" • callback de conexão Android: "+lastConnectionState+" (-1=nenhum; 0=desconectado; 1=conectando; 2=conectado; 3=desconectando)"
            +"\nGET/SET/Interrupt recebidos do host: "+hostRequests+" • relatórios aceitos para envio pelo Android: "+inputReports+" (não confirma consumo no jogo)"
            +(protocol==null?"":"\n"+protocol.diagnostic());
    }
    private void phase(Phase next,String message){
        phase=next;generation++;Log.i("SwitchPhone",next+": "+message);listener.status(next,message);
    }
    private void notice(String message){if(message!=null&&!message.equals(lastNotice)){lastNotice=message;Log.i("SwitchPhone",message);listener.notice(message);}}
    private void timeout(long delay,String message){
        int expected=generation;main.postDelayed(()->{if(!closed&&generation==expected)fail(message);},delay);
    }
    private void fail(String message){
        if(closed||failing)return;failing=true;
        state.clear();pairingPulse.clear();stopStream();main.removeCallbacks(pulseRelease);
        // Best effort neutral packet before disconnect; never leave a held game input intentionally.
        if(hid!=null&&host!=null){
            try{NintendoProtocol.Packet p=packet();hid.sendReport(host,p.id,p.data);hid.disconnect(host);}
            catch(SecurityException|IllegalStateException ignored){}
        }
        host=null;phase(Phase.ERROR,message);listener.resetInput();
        try{if(hid!=null&&registered){registered=false;hid.unregisterApp();}if(adapter!=null)identity.recover(adapter);}
        catch(SecurityException|IllegalStateException e){notice("Não foi possível confirmar a limpeza Bluetooth; use Encerrar e confira a permissão.");}
        failing=false;
    }
    public void start(){
        if(closed||requested)return;
        try{
            if(adapter==null){fail("Este dispositivo não oferece Bluetooth.");return;}
            if(!adapter.isEnabled()){fail("Bluetooth desligado. Ative-o e tente novamente.");return;}
            if(hidProfile.switchIdentity())identity.acquire(adapter);
            else identity.recover(adapter);
            if(nintendo){protocol=new NintendoProtocol(identity.virtualAddress());updateBattery();}
            phase(Phase.REQUESTING,"Carregando perfil Bluetooth HID…");
            requested=adapter.getProfileProxy(context,service,BluetoothProfile.HID_DEVICE);
            if(!requested){fail("Perfil HID Device indisponível neste Android.");return;}
            timeout(8000,"O perfil HID não respondeu em 8 segundos. Encerre e tente novamente.");
        }catch(SecurityException|IllegalStateException e){fail("Falha ao preparar Bluetooth: "+e.getMessage());}
    }
    private final BluetoothProfile.ServiceListener service=new BluetoothProfile.ServiceListener(){
        @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
            if(closed||phase==Phase.ERROR){adapter.closeProfileProxy(profile,proxy);return;}
            hid=(BluetoothHidDevice)proxy;
            try{
                phase(Phase.REGISTERING,"Registrando "+(hidProfile.genericSwitch()?"HID genérico com nome Pro Controller":nintendo?"Pro Controller experimental":"gamepad para PC")+"…");
                BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings(
                    hidProfile.name(),hidProfile.description(),hidProfile.provider(),hidProfile.subclass(),hidProfile.descriptor());
                if(!hid.registerApp(sdp,null,null,command->main.post(command),callback))fail("Android recusou o registro HID. Outro app pode estar usando o perfil.");
                else timeout(8000,"Registro HID sem confirmação. Encerre e tente novamente.");
            }catch(SecurityException|IllegalStateException|IllegalArgumentException e){fail("Registro HID falhou: "+e.getMessage());}
        }
        @Override public void onServiceDisconnected(int profile){
            hid=null;host=null;registered=false;requested=false;stopStream();
            if(!closed){state.clear();phase(Phase.ERROR,"Serviço HID perdido; controles bloqueados.");listener.resetInput();}
        }
    };
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
        @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean value){
            if(closed)return;
            if(phase==Phase.ERROR){
                registered=false;
                if(value&&hid!=null)try{hid.unregisterApp();}catch(SecurityException|IllegalStateException e){notice("Registro tardio não pôde ser encerrado.");}
                return;
            }
            registered=value;
            everRegistered|=value;
            notice("Callback de registro HID: "+value+" • host previamente associado pelo perfil: "+(plugged!=null));
            if(value){
                // A repeated app-status callback must not reset an already-connected session.
                if(host==null&&phase!=Phase.CONNECTING)phase(Phase.READY,"HID registrado; celular aguardando conexão do "+(hidProfile.switchIdentity()?"Switch":"computador")+".");
                else notice("Registro HID confirmado durante a conexão existente.");
            }
            else fail("HID desregistrado pelo Android. Confira a permissão Bluetooth e a restrição de bateria do app; reconecte para retomar.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device,int value){
            if(closed||phase==Phase.ERROR)return;
            lastConnectionState=value;notice("Callback de conexão HID Android: "+value);
            if(value==BluetoothProfile.STATE_CONNECTING){if(phase==Phase.CONNECTING)return;phase(Phase.CONNECTING,"Conectando ao host Bluetooth…");timeout(15000,"O host não confirmou a conexão em 15 segundos.");return;}
            if(value==BluetoothProfile.STATE_CONNECTED){
                if(host!=null&&host.equals(device)&&(phase==Phase.NEGOTIATING||phase==Phase.ACTIVE))return;
                stopStream();host=device;everConnected=true;state.clear();pairingPulse.clear();listener.resetInput();
                if(nintendo){
                    protocol=new NintendoProtocol(identity.virtualAddress());batteryChecked=0;updateBattery();
                    phase(Phase.NEGOTIATING,"HID conectado • aguardando negociação Nintendo. Confirmar L+R disponível.");
                    timeout(45000,"Negociação Nintendo expirou após 45s. Copie o diagnóstico; use Confirmar L+R durante a próxima tentativa.");
                }else phase(Phase.ACTIVE,hidProfile.genericSwitch()?"HID genérico conectado • sem negociação Nintendo. Mapeamento no Switch não confirmado.":"PC conectado • relatórios HID habilitados. Valide no jogo.");
                emit(packet());
            }else if(value==BluetoothProfile.STATE_DISCONNECTED){
                host=null;stopStream();main.removeCallbacks(pulseRelease);pairingPulse.clear();state.clear();
                if(!failing&&phase!=Phase.ERROR)phase(Phase.ERROR,"Canal HID desconectado; comandos bloqueados. Use Parear para uma nova tentativa.");
                listener.resetInput();
            }else if(value==BluetoothProfile.STATE_DISCONNECTING&&phase!=Phase.ERROR){
                stopStream();phase(Phase.CONNECTING,"Encerrando enlace Bluetooth…");state.clear();listener.resetInput();timeout(8000,"Desconexão não foi confirmada pelo host.");
            }
        }
        @Override public void onGetReport(BluetoothDevice device,byte type,byte id,int size){
            if(closed||hid==null)return;
            hostRequest("GET_REPORT",id);
            try{
                byte[] payload=null;int reportId=id&255;
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT){
                    if(!nintendo&&reportId==1)payload=state.report();
                    else if(nintendo&&protocol!=null){
                        if(reportId==0x21)payload=protocol.lastReply();
                        else if(reportId==protocol.mode())payload=protocol.input(report(),SystemClock.uptimeMillis()).data;
                    }
                }else if(nintendo&&protocol!=null&&type==BluetoothHidDevice.REPORT_TYPE_FEATURE&&reportId==2)payload=protocol.lastReply();
                if(payload==null){hid.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);notice("GET_REPORT solicitado não é suportado.");}
                else{if(size>0&&size<payload.length)payload=java.util.Arrays.copyOf(payload,size);if(!hid.replyReport(device,type,id,payload))fail("Android recusou resposta GET_REPORT.");}
            }catch(SecurityException|IllegalStateException e){fail("GET_REPORT falhou: "+e.getClass().getSimpleName());}
        }
        @Override public void onSetReport(BluetoothDevice device,byte type,byte id,byte[] data){
            if(closed||hid==null)return;
            hostRequest("SET_REPORT",id);
            try{
                if(type==BluetoothHidDevice.REPORT_TYPE_OUTPUT&&nintendo&&protocol!=null){
                    if(output(id&255,data))hid.reportError(device,BluetoothHidDevice.ERROR_RSP_SUCCESS);
                    else hid.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM);
                }else{hid.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);notice("SET_REPORT solicitado não é suportado.");}
            }catch(SecurityException|IllegalStateException e){fail("SET_REPORT falhou: "+e.getClass().getSimpleName());}
        }
        @Override public void onInterruptData(BluetoothDevice device,byte reportId,byte[] data){
            if(!closed&&phase!=Phase.ERROR){hostRequest("INTERRUPT",reportId);output(reportId&255,data);}
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device){if(!closed)fail("Host removeu o pareamento HID. Faça o pareamento novamente.");}
    };
    private void hostRequest(String type,byte id){hostRequests++;if(hostRequests<=4)notice("Host enviou "+type+" • relatório "+String.format(java.util.Locale.ROOT,"0x%02X",id&255));}
    private boolean output(int id,byte[] data){
        if(!nintendo||protocol==null){notice("Host enviou comando não suportado no perfil HID genérico.");return false;}
        NintendoProtocol.Reply reply=protocol.output(id,data,report(),SystemClock.uptimeMillis());
        if(reply.packet!=null&&!emit(reply.packet))return false;
        notice(reply.message);
        if(reply.accepted&&protocol.negotiated()&&host!=null){
            if(phase!=Phase.ACTIVE)phase(Phase.ACTIVE,"Modo Nintendo negociado • teste físico ainda necessário.");
            // Output traffic must not restart or duplicate the 60 Hz input pump.
            if(protocol.mode()==0x30){if(!streaming){streaming=true;main.post(tick);}}
            else stopStream();
        }
        return reply.packet!=null||(id==0x10&&reply.accepted);
    }
    private byte[] report(){byte[] data=state.report();return nintendo?pairingPulse.apply(data,SystemClock.uptimeMillis()):data;}
    private NintendoProtocol.Packet packet(){
        byte[] data=report();return nintendo&&protocol!=null?protocol.input(data,SystemClock.uptimeMillis()):new NintendoProtocol.Packet(1,data);
    }
    private boolean emit(NintendoProtocol.Packet packet){
        if(closed||failing||hid==null||host==null||!registered)return false;
        try{if(hid.sendReport(host,packet.id,packet.data)){inputReports++;return true;}fail("Falha ao enviar relatório Bluetooth; controles bloqueados.");}
        catch(SecurityException|IllegalStateException e){fail("Envio Bluetooth falhou: "+e.getClass().getSimpleName());}
        return false;
    }
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!streaming||closed||phase!=Phase.ACTIVE||protocol==null||protocol.mode()!=0x30){stopStream();return;}
            updateBattery();if(emit(packet())&&streaming)main.postDelayed(this,17);
        }
    };
    private void stopStream(){streaming=false;main.removeCallbacks(tick);}
    private void updateBattery(){
        if(protocol==null)return;long now=SystemClock.uptimeMillis();if(now-batteryChecked<1000)return;batteryChecked=now;
        BatteryManager manager=context.getSystemService(BatteryManager.class);
        if(manager!=null)protocol.battery(manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),manager.isCharging());
    }
    public void reconnect(BluetoothDevice device){
        if(!registered||hid==null){notice("Registre HID antes de reconectar.");return;}
        if(host!=null){notice("Já existe um host conectado.");return;}
        if(phase!=Phase.READY){notice("Já existe uma conexão em andamento; nenhuma solicitação duplicada foi enviada.");return;}
        try{
            if(device==null||device.getBondState()!=BluetoothDevice.BOND_BONDED){notice("Host sem vínculo ativo. Nenhum pareamento de saída foi solicitado.");return;}
            phase(Phase.CONNECTING,"Abrindo canal HID a partir do celular…");
            if(!hid.connect(device))fail("Abertura do canal HID recusada pelo Android.");else timeout(15000,"O host não confirmou a abertura do canal HID a partir do celular em 15s.");
        }
        catch(SecurityException|IllegalStateException e){fail("Falha na reconexão: "+e.getClass().getSimpleName());}
    }
    private final Runnable pulseRelease=()->{pairingPulse.clear();if(canSendInput())emit(packet());};
    public void confirmPlayer(){
        if(!nintendo){notice("Confirmar L+R Nintendo não está disponível no perfil HID genérico.");return;}
        if(!canSendInput()){notice("L+R exige um enlace HID conectado; aceite Associer no Android primeiro.");return;}
        pairingPulse.start(SystemClock.uptimeMillis());main.removeCallbacks(pulseRelease);
        if(emit(packet())){notice("L+R enviado por 300ms para confirmar o controle.");main.postDelayed(pulseRelease,300);}
    }
    public void send(){if(canSendInput())emit(packet());}
    public void release(){state.clear();pairingPulse.clear();main.removeCallbacks(pulseRelease);if(canSendInput())emit(packet());}
    @Override public void close(){
        if(closed)return;release();closed=true;stopStream();main.removeCallbacksAndMessages(null);state.clear();
        String cleanupError=null;
        try{
            if(hid!=null){if(host!=null)hid.disconnect(host);hid.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid);}
        }catch(SecurityException|IllegalStateException e){cleanupError="Não foi possível confirmar o encerramento HID: "+e.getClass().getSimpleName();notice(cleanupError);}
        hid=null;host=null;registered=false;listener.resetInput();
        try{if(adapter!=null)identity.recover(adapter);}
        catch(SecurityException|IllegalStateException e){cleanupError="Nome Bluetooth não restaurado. Reabra o app com permissão Bluetooth para recuperar.";notice(cleanupError);}
        phase(cleanupError==null?Phase.CLOSED:Phase.ERROR,cleanupError==null?"Sessão encerrada; controles bloqueados.":cleanupError);
    }
}
