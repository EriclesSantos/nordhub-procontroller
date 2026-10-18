package com.switchphone;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.IBinder;
import android.view.*;
import android.widget.*;
import com.switchphone.bluetooth.*;
import com.switchphone.controller.ControllerState;
import com.switchphone.controller.InputRouter;
import com.switchphone.layouts.*;
import com.switchphone.settings.Preferences;
import java.util.ArrayList;
import java.util.ArrayDeque;

/** Native offline UI; indicators follow confirmed transport/protocol callbacks. */
public final class MainActivity extends Activity {
    private static final int PERMISSIONS=1,ENABLE_BT=2,VISIBLE_BT=3;
    private InputRouter inputs=new InputRouter();
    private ControllerState state=inputs.transport();
    private final PairingFlow flow=new PairingFlow();
    private final BondMonitor bond=new BondMonitor();
    private LinkExperiment experiment=new LinkExperiment(0);
    private final ArrayDeque<String> diagnostics=new ArrayDeque<>();
    private Preferences prefs;
    private HidSession session;
    private com.switchphone.controller.MotionInput motion;
    private TextView status,detail;
    private ConnectionIndicator indicator;
    private Button pairButton,reconnectButton,stopButton,confirmButton;
    private int previewLayout=-1;
    private ControllerView controller;
    private BluetoothAdapter adapter;
    private HidSession.Phase phase=HidSession.Phase.IDLE;
    private String message="Desconectado • escolha PC ou Switch e toque Parear";
    private String notice="Botões cinzas ficam bloqueados até a conexão ser confirmada.";
    private String screen="home";
    private boolean resumed,serviceBound,serviceRequested,receiverRegistered,nameConfirmed,savedReconnect,freshPairing;
    private BluetoothDevice requestedReconnectHost;
    private BluetoothDevice pairingHost;
    private String lastTransport="Nenhum perfil HID iniciado nesta tentativa.";
    private HidForegroundService foregroundService;
    private ServiceConnection binding;
    private IdentityLease pairingIdentity;
    private long visibleUntil,attemptStarted;
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable preflightTimeout,identityPoll,hostTick,bondRecoveryTimeout;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=new Preferences(this);pairingIdentity=new IdentityLease(this);
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        getWindow().setStatusBarColor(Color.rgb(12,19,28));getWindow().setNavigationBarColor(Color.rgb(12,19,28));
        if(adapter!=null&&permitted()&&!HidForegroundService.ownsTransport()){
            try{new IdentityLease(this).recover(adapter);}catch(SecurityException|IllegalStateException e){notice="Não foi possível restaurar o nome Bluetooth. Tente Parear com Bluetooth ligado.";phase=HidSession.Phase.ERROR;message="Falha na recuperação do nome Bluetooth.";}
        }
        registerBluetoothEvents();home();
        if(HidForegroundService.ownsTransport())restoreExistingSession();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout base(String title,String selected){
        if(motion!=null)motion.stop();
        release();controller=null;screen=selected;previewLayout=-1;reconnectButton=stopButton=confirmButton=null;
        getWindow().getDecorView().setSystemUiVisibility(selected.equals("control")
            ?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:View.SYSTEM_UI_FLAG_VISIBLE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        final int horizontal=selected.equals("control")?4:14,vertical=selected.equals("control")?0:6;
        root.setPadding(dp(horizontal),dp(vertical),dp(horizontal),dp(vertical));root.setBackgroundColor(Color.rgb(12,19,28));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(dp(horizontal)+insets.getSystemWindowInsetLeft(),dp(vertical)+insets.getSystemWindowInsetTop(),dp(horizontal)+insets.getSystemWindowInsetRight(),dp(vertical)+insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        TextView name=text(title,selected.equals("control")?13:18);name.setTextColor(Color.rgb(168,140,255));header.addView(name,new LinearLayout.LayoutParams(0,dp(40),1));
        indicator=new ConnectionIndicator(this);header.addView(indicator,new LinearLayout.LayoutParams(dp(36),dp(18)));
        pairButton=button(header,"Parear",this::pair);
        if(selected.equals("control"))button(header,"Mais",this::controlMenu);
        else if(!selected.equals("home"))button(header,"Início",this::home);
        status=text(message,12);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);if(selected.equals("control"))status.setSingleLine(true);root.addView(status);
        detail=text(notice,11);detail.setMaxLines(2);detail.setTextColor(Color.rgb(166,181,197));if(!selected.equals("control"))root.addView(detail);
        setContentView(root);refresh();return root;
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(220,231,240));t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private void info(LinearLayout root,String value){TextView t=text(value,14);t.setPadding(0,dp(6),0,dp(6));root.addView(t);}
    private Button button(LinearLayout parent,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());return b;
    }
    private void home(){
        LinearLayout root=base("NÖRDHUB PROCONTROLLER","home");
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        info(content,"Destino Bluetooth: "+(prefs.nintendo()?"Nintendo Switch (experimental)":"Computador (HID)")+" • offline • sem root");
        LinearLayout modes=new LinearLayout(this);content.addView(modes);
        button(modes,prefs.nintendo()?"PC":"✓ PC",()->mode(false));
        button(modes,prefs.nintendo()?"✓ Switch experimental":"Switch experimental",()->mode(true));
        if(prefs.nintendo()){
            info(content,"Conexão: "+LinkExperiment.label(prefs.switchTest()));
            button(content,"Escolher teste de conexão",()->{
                String[] choices=new String[5];for(int i=0;i<choices.length;i++)choices[i]=LinkExperiment.label(i);
                new AlertDialog.Builder(this).setTitle("Teste de vínculo e canal HID")
                    .setSingleChoiceItems(choices,prefs.switchTest(),(dialog,which)->{stop();prefs.put("switchTest",which);dialog.dismiss();home();})
                    .setNegativeButton("Cancelar",null).show();
            });
            info(content,"Teste 4 mantém o nome Pro Controller com perfil HID genérico. Verifica descoberta e canal; mapeamento dos botões no Switch não confirmado. Testes 1–3 são as alternativas anteriores de vínculo/conexão.");
        }
        info(content,prefs.nintendo()?"Pro Controller experimental para Switch com MissionControl. Primeiro funcionamento confirmado pelo usuário com MissionControl 0.16.0. Com vínculo salvo, Conectar reutiliza a associação; a sessão continua com a tela bloqueada até Desconectar.":"Gamepad genérico para computador. Reconhecimento e entradas confirmados pelo usuário no Windows com Xiaomi/Android 16. Jogos e adaptadores Bluetooth precisam de validação.");
        LinearLayout row=new LinearLayout(this);content.addView(row);
        reconnectButton=button(row,"Reconectar",this::reconnect);button(row,"Parear novo",()->pair(null,true));stopButton=button(row,"Encerrar",this::stop);button(row,"Diagnóstico",this::diagnostics);
        LinearLayout nav=new LinearLayout(this);content.addView(nav);
        button(nav,"Abrir controle",this::control);button(nav,"Testar toque",this::testTouch);button(nav,"Layouts",this::layouts);button(nav,"Configurações",this::settings);
        if(!prefs.nintendo()){
            info(content,"Escolha a conexão com o PC:");LinearLayout transport=new LinearLayout(this);content.addView(transport);
            button(transport,"Bluetooth",this::pair);button(transport,"Wi-Fi / Wireless",()->{stop();startActivity(new Intent(this,com.switchphone.wifi.WifiControllerActivity.class));});
        }
        info(content,"Layout: "+ControllerView.NAMES[prefs.layout()]+". Toque Parear no topo, aceite os pedidos do Android e inicie o pareamento pelo "+(prefs.nintendo()?"Switch em Controles → Mudar ordem/modo de segurar.":"computador. No Windows, confira entradas em Win+R → joy.cpl."));
        if(adapter==null)info(content,"Este dispositivo não oferece adaptador Bluetooth.");refresh();
    }
    private void mode(boolean nintendo){stop();inputs.testing(false);prefs.put("protocol",nintendo?1:0);home();}
    private void control(){
        LinearLayout root=base(prefs.nintendo()?"SWITCH • EXPERIMENTAL":"CONTROLE • PC","control");
        final boolean testing=inputs.testing();
        if(prefs.nintendo()&&prefs.switchTest()==4){TextView banner=text("TESTE 4 • nome Pro Controller + HID genérico • verificar conexão • sem confirmação L+R Nintendo",11);banner.setTextColor(Color.rgb(255,205,110));root.addView(banner);}
        if(testing){TextView banner=text("TESTE LOCAL • toque e mova os controles • nenhum comando vai por Bluetooth",12);banner.setTextColor(Color.rgb(255,205,110));root.addView(banner);}
        controller=new ControllerView(this,prefs.layout(),inputs.touch(),prefs,()->{if(!testing)send();},false);
        root.addView(controller,new LinearLayout.LayoutParams(-1,0,1));refresh();
        startMotion();
    }
    private void startMotion(){
        if(motion!=null)motion.stop();
        motion=new com.switchphone.controller.MotionInput(this,state,prefs,this::send);
        if(resumed&&screen.equals("control")&&session!=null&&session.canSendInput()&&!inputs.testing())motion.start();
    }
    private void controlMenu(){
        PopupMenu menu=new PopupMenu(this,pairButton);
        if(prefs.nintendo())menu.getMenu().add("Confirmar L+R").setEnabled(session!=null&&session.nintendoProtocol()&&session.canSendInput()&&!inputs.testing()).setOnMenuItemClickListener(item->{if(session!=null)session.confirmPlayer();return true;});
        menu.getMenu().add("Reconectar").setEnabled(canReconnect()).setOnMenuItemClickListener(item->{reconnect();return true;});
        menu.getMenu().add("Parear novo aparelho").setOnMenuItemClickListener(item->{pair(null,true);return true;});
        menu.getMenu().add("Diagnóstico").setOnMenuItemClickListener(item->{diagnostics();return true;});
        menu.getMenu().add("Encerrar conexão").setOnMenuItemClickListener(item->{stop();return true;});
        menu.getMenu().add(inputs.testing()?"Sair do teste local":"Testar toque").setOnMenuItemClickListener(item->{testTouch();return true;});
        menu.getMenu().add("Escolher layout").setOnMenuItemClickListener(item->{layouts();return true;});
        menu.getMenu().add("Ajustes").setOnMenuItemClickListener(item->{settings();return true;});
        menu.getMenu().add("Trocar perfil").setOnMenuItemClickListener(item->{String[] names=prefs.profiles().toArray(new String[0]);new AlertDialog.Builder(this).setTitle(names.length==0?"Salve um perfil em Ajustes primeiro":"Perfis").setItems(names,(d,i)->{release();prefs.loadProfile(names[i]);control();}).setNegativeButton("Voltar",null).show();return true;});
        menu.getMenu().add("Perfis e recursos avançados").setOnMenuItemClickListener(item->{startActivity(new Intent(this,AdvancedSettingsActivity.class));return true;});
        menu.getMenu().add("Calibrar giroscópio").setEnabled(motion!=null&&motion.available()&&prefs.gyroMode()!=0).setOnMenuItemClickListener(item->{motion.model().calibrate();Toast.makeText(this,"Mantenha o celular imóvel por 1 segundo.",Toast.LENGTH_LONG).show();return true;});
        menu.getMenu().add("Início").setOnMenuItemClickListener(item->{home();return true;});
        menu.show();
    }
    private void layouts(){
        LinearLayout root=base("LAYOUTS","layouts");LinearLayout row=new LinearLayout(this);root.addView(row);
        final int[] selected={prefs.layout()};previewLayout=prefs.layout();
        info(root,"Prévia sem toque. Use Selecionar ou Parear para abrir o controle.");FrameLayout preview=new FrameLayout(this);
        for(int i=0;i<3;i++){
            final int choice=i;button(row,ControllerView.NAMES[i],()->{selected[0]=choice;previewLayout=choice;preview.removeAllViews();preview.addView(new ControllerView(this,choice,new ControllerState(),prefs,()->{},true));});
        }
        button(row,"Selecionar",()->{prefs.put("layout",selected[0]);notice("Layout visual aplicado. Destino Bluetooth: "+(prefs.nintendo()?"Switch":"PC")+"; altere o destino no Início.");control();});
        preview.addView(new ControllerView(this,selected[0],new ControllerState(),prefs,()->{},true));root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void settings(){
        LinearLayout root=base("CONFIGURAÇÕES","settings");ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        slider(content,"Zona morta","deadZone",prefs.deadZone(),0,30);slider(content,"Sensibilidade","sensitivity",prefs.sensitivity(),50,150);slider(content,"Vibração local","vibration",prefs.vibration(),0,100);
        LinearLayout row=new LinearLayout(this);content.addView(row);button(row,"Layout padrão",this::layouts);button(row,"Diagnóstico",this::diagnostics);
        button(content,"Perfis, remapeamento e recursos avançados",()->startActivity(new Intent(this,AdvancedSettingsActivity.class)));
        button(row,"Compatibilidade",()->new AlertDialog.Builder(this).setTitle("Compatibilidade experimental")
            .setMessage("Android 9+ com perfil HID Device habilitado pelo fabricante. PC: HID genérico; entradas confirmadas no painel Windows, compatibilidade com jogos e dongles ainda em teste. Switch: primeiro funcionamento confirmado pelo usuário após MissionControl 0.16.0; reconexão e demais aparelhos precisam de validação. MAC/VID/PID reais são controlados pelo Android. Giroscópio como mira usa o analógico direito. No PC por Wi-Fi, o auxiliar experimental oferece gatilhos graduais, retorno de vibração, touchpad e movimento DSU. O protocolo Nintendo atual não implementa movimento nativo ou rumble. NFC não implementado. Pontos verdes indicam canal HID; valide os comandos no console.").setPositiveButton("Entendi",null).show());
    }
    private void slider(LinearLayout parent,String label,String key,int value,int minimum,int maximum){
        TextView caption=text(String.format(java.util.Locale.getDefault(),"%s: %d%%",label,value),15);parent.addView(caption);
        SeekBar seek=new SeekBar(this);seek.setMax(maximum-minimum);seek.setProgress(value-minimum);seek.setContentDescription(label);parent.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){int v=progress+minimum;caption.setText(String.format(java.util.Locale.getDefault(),"%s: %d%%",label,v));if(user)prefs.put(key,v);}
            @Override public void onStartTrackingTouch(SeekBar bar){} @Override public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    private void testTouch(){
        if(session!=null&&session.canSendInput()){notice("Encerre a conexão antes do teste local de toque.");return;}
        release();inputs.testing(!inputs.testing());control();
    }
    private boolean busy(){return flow.busy();}
    private void refresh(){
        boolean linked=session!=null&&session.canSendInput();
        boolean active=phase==HidSession.Phase.ACTIVE&&session!=null&&session.inputReady();
        if(indicator!=null)indicator.status(busy()&&!linked,linked,phase==HidSession.Phase.ERROR);
        if(status!=null){status.setText(message);status.setTextColor(phase==HidSession.Phase.ERROR?Color.rgb(255,128,123):active?Color.rgb(74,222,128):linked?Color.rgb(255,205,110):Color.rgb(220,231,240));}
        if(detail!=null)detail.setText(notice);
        if(status!=null&&screen.equals("control"))status.setVisibility(active?View.GONE:View.VISIBLE);
        if(pairButton!=null){pairButton.setText(linked?"Conectado":busy()?savedReconnect?"Reconectando…":"Conectando…":"Conectar");pairButton.setEnabled(!busy()&&!linked&&adapter!=null);}
        if(reconnectButton!=null){
            reconnectButton.setEnabled(canReconnect());
        }
        if(stopButton!=null){stopButton.setText(busy()&&!linked?"Cancelar":"Encerrar");stopButton.setEnabled(session!=null||flow.busy());}
        if(confirmButton!=null)confirmButton.setEnabled(session!=null&&session.nintendoProtocol()&&session.canSendInput()&&!inputs.testing());
        if(controller!=null)controller.setInputAvailable(inputs.testing()||(session!=null&&session.canSendInput()));
    }
    private boolean canReconnect(){
        if(session!=null&&session.connected())return false;
        if(!busy())return adapter!=null;
        return session!=null&&session.registered()&&flow.waitingHost()&&!experiment.pending()
            &&bond.state()!=BondMonitor.State.BONDING&&!bond.outboundRequested()
            &&(bond.state()!=BondMonitor.State.BONDED||bond.canRequestLink(SystemClock.elapsedRealtime()));
    }
    private void log(String value){
        if(diagnostics.size()>=80)diagnostics.removeFirst();
        long seconds=attemptStarted==0?0:(SystemClock.elapsedRealtime()-attemptStarted)/1000;
        diagnostics.addLast("+"+seconds+"s • "+value);
    }
    private void update(HidSession.Phase value,String text){phase=value;message=text;log(text);refresh();}
    private void notice(String value){notice=value;log(value);refresh();}
    private String report(){
        StringBuilder report=new StringBuilder("Nördhub ProController "+BuildConfig.VERSION_NAME+"\n");
        report.append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append(" • Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
        report.append("\nDestino: ").append(prefs.nintendo()?"Switch":"PC").append(" • layout: ").append(ControllerView.NAMES[prefs.layout()]);
        report.append("\nDispositivos próximos: ").append(permitted()?"permitido":"não permitido");
        report.append("\nEtapa: ").append(flow.step()).append(" / ").append(phase);
        report.append("\nEstado atual: HID registrado: ").append(session!=null&&session.registered()).append(" • enlace: ").append(session!=null&&session.connected());
        if(prefs.nintendo())report.append("\nModo Nintendo negociado: ").append(experiment.variant()==4?"não se aplica ao perfil HID genérico":String.valueOf(phase==HidSession.Phase.ACTIVE&&session!=null&&session.nintendoProtocol()&&session.inputReady())).append(" • pontos verdes indicam somente canal HID");
        report.append("\nVínculo observado: ").append(bond.state()).append(" • vínculo salvo encontrado: ").append(bond.existingFound());
        report.append("\nCallback BONDED recebido nesta tentativa: ").append(bond.bondedCallback());
        report.append("\nReconexão usando vínculo salvo: ").append(savedReconnect);
        report.append("\nExperimento da tentativa: ").append(experiment.diagnostic());
        report.append("\nMotivo de remoção informado pelo Android: ").append(BondFailure.describe(bond.lossReason()));
        report.append("\nSinal KEY_MISSING observado: ").append(bond.keyMissingObserved()).append(" (ausência do sinal não descarta falha de chave)");
        report.append("\nConexão HID de saída: ").append(bond.outboundRequested()?(experiment.automaticRequested()?"solicitada pelo teste após vínculo estável":"solicitada pelo usuário"):"não solicitada; aguardando entrada do host");
        report.append("\n").append(session!=null?session.diagnostic():lastTransport);
        report.append("\nSessão no serviço: ").append(foregroundService!=null&&foregroundService.session()==session&&session!=null).append(" • CPU mantida durante conexão: ").append(foregroundService!=null&&foregroundService.cpuHeld());
        try{
            if(adapter!=null&&permitted()){
                report.append("\nBluetooth ligado: ").append(adapter.isEnabled());
                report.append(" • nome Pro Controller agora: ").append("Pro Controller".equals(adapter.getName()));
                report.append(" • confirmado na tentativa: ").append(nameConfirmed);
            }
        }catch(SecurityException e){report.append("\nPermissão revogada durante o diagnóstico.");}
        report.append("\nSem busca ativa. O teste HID pode solicitar um canal após vínculo estável; vínculo sozinho não libera L+R. Nome/perfil são restaurados ao encerrar.\n\n");
        report.append(String.join("\n",diagnostics));return report.toString();
    }
    private void diagnostics(){
        final String report=report();TextView body=text(report,13);body.setTextIsSelectable(true);body.setPadding(dp(16),dp(10),dp(16),dp(10));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle("Diagnóstico da tentativa").setView(scroll)
            .setNeutralButton("Copiar",(dialog,which)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Nördhub ProController diagnóstico",report));Toast.makeText(this,"Diagnóstico copiado",Toast.LENGTH_SHORT).show();})
            .setPositiveButton("Fechar",null).show();
    }
    private boolean permitted(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    private boolean enabled(){try{return adapter!=null&&permitted()&&adapter.isEnabled();}catch(SecurityException e){return false;}}
    private void pair(){
        pair(null);
    }
    private void pair(BluetoothDevice selectedHost){
        pair(selectedHost,false);
    }
    private void pair(BluetoothDevice selectedHost,boolean newPairing){
        if(com.switchphone.wifi.WifiForegroundService.running()){notice("Desconecte o Wi-Fi pela notificação antes de iniciar Bluetooth.");return;}
        if(screen.equals("layouts")&&previewLayout>=0)prefs.put("layout",previewLayout);
        stop();experiment=new LinkExperiment(prefs.nintendo()?prefs.switchTest():0);inputs.testing(false);control();
        attemptStarted=SystemClock.elapsedRealtime();diagnostics.clear();bond.reset();pairingHost=null;nameConfirmed=false;savedReconnect=false;requestedReconnectHost=selectedHost;freshPairing=newPairing;
        lastTransport="Nenhum perfil HID iniciado nesta tentativa.";
        log("Conexão selecionada: "+LinkExperiment.label(experiment.variant()));
        log(HidProfile.forAttempt(prefs.nintendo(),experiment.variant()).diagnostic());
        if(adapter==null){failPairing("Bluetooth indisponível neste aparelho.");return;}
        flow.begin(permitted(),enabled());
        notice(prefs.nintendo()?"No Switch: Controles → Mudar ordem/modo de segurar. MissionControl precisa estar ativo.":"Inicie o pareamento pelo computador; o celular aguardará a conexão.");
        drive();
    }
    private void queueDrive(){ui.post(this::drive);}
    private void drive(){
        PairingFlow.Action action=flow.next(resumed&&hasWindowFocus());
        try{
            switch(action){
                case REQUEST_PERMISSION:
                    update(HidSession.Phase.REQUESTING,"1/4 • autorize dispositivos próximos no Android.");
                    armPreflight(60000,"A permissão não foi confirmada. Toque Parear para tentar novamente.");
                    if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},PERMISSIONS);
                    else{flow.permissionResult(true,enabled());queueDrive();}break;
                case REQUEST_ENABLE:
                    update(HidSession.Phase.REQUESTING,"1/4 • confirme a ativação do Bluetooth.");
                    armPreflight(60000,"Ativação do Bluetooth sem confirmação. Tente novamente.");
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BT);break;
                case PREPARE_IDENTITY:
                    update(HidSession.Phase.REQUESTING,"2/4 • preparando a identificação do controle…");
                    if(prefs.nintendo())pairingIdentity.acquire(adapter);else pairingIdentity.recover(adapter);
                    final int attempt=flow.attempt();final long limit=SystemClock.elapsedRealtime()+5000;
                    identityPoll=new Runnable(){@Override public void run(){
                        if(flow.attempt()!=attempt||flow.step()!=PairingFlow.Step.IDENTITY)return;
                        try{
                            if(!prefs.nintendo()||"Pro Controller".equals(adapter.getName())){
                                nameConfirmed=prefs.nintendo();log("Identificação Bluetooth confirmada; ainda não foi registrado HID.");
                                BluetoothDevice known=requestedReconnectHost;
                                if(known==null&&!freshPairing&&prefs.nintendo()&&experiment.variant()==0){
                                    ArrayList<BluetoothDevice> switches=knownHosts();if(switches.size()==1)known=switches.get(0);
                                }
                                if(known!=null&&known.getBondState()==BluetoothDevice.BOND_BONDED){
                                    savedReconnect=true;pairingHost=known;bond.seedExisting(SystemClock.elapsedRealtime());
                                    visibleUntil=SystemClock.elapsedRealtime()+120000;
                                    log("Vínculo salvo reutilizado. Preparando HID sem pedir nova visibilidade ou novo pareamento.");
                                }
                                flow.identityResult(true,!savedReconnect);queueDrive();
                            }else if(SystemClock.elapsedRealtime()>=limit)failPairing("O Android não confirmou o nome Pro Controller. Confira o diagnóstico.");
                            else ui.postDelayed(this,150);
                        }catch(SecurityException e){failPairing("Permissão Bluetooth revogada ao preparar o nome.");}
                    }};identityPoll.run();break;
                case REQUEST_VISIBILITY:
                    update(HidSession.Phase.REQUESTING,"3/4 • permita que o celular fique visível por 120 segundos.");
                    armPreflight(60000,"A visibilidade não foi confirmada. Toque Parear para tentar novamente.");
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120),VISIBLE_BT);break;
                case REGISTER_HID:
                    update(HidSession.Phase.REQUESTING,"4/4 • iniciando a sessão HID em primeiro plano…");
                    startSessionService();break;
                case NONE:refresh();break;
            }
        }catch(SecurityException e){failPairing("Permissão Bluetooth ausente ou revogada. Autorize Dispositivos próximos nas configurações do app.");}
        catch(android.content.ActivityNotFoundException|IllegalStateException e){failPairing("Não foi possível abrir a etapa Bluetooth: "+e.getClass().getSimpleName());}
    }
    private void armPreflight(long delay,String error){
        if(preflightTimeout!=null)ui.removeCallbacks(preflightTimeout);
        final int attempt=flow.attempt();final PairingFlow.Step expected=flow.step();
        preflightTimeout=()->{if(flow.attempt()==attempt&&flow.step()==expected)failPairing(error);};ui.postDelayed(preflightTimeout,delay);
    }
    private void startSessionService(){
        final int attempt=flow.attempt();Intent intent=new Intent(this,HidForegroundService.class);
        serviceRequested=true;startForegroundService(intent);
        binding=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(flow.attempt()!=attempt||binding!=this||flow.step()!=PairingFlow.Step.REGISTER||session!=null)return;
                foregroundService=((HidForegroundService.LocalBinder)binder).service();
                if(foregroundService.startupError()!=null){failPairing(foregroundService.startupError());return;}
                foregroundService.listen((reason,error)->{if(error)failPairing(reason);else stop();});
                foregroundService.deadline(visibleUntil-SystemClock.elapsedRealtime());
                if(preflightTimeout!=null)ui.removeCallbacks(preflightTimeout);
                session=foregroundService.createSession(inputs,HidProfile.forAttempt(prefs.nintendo(),experiment.variant()),transportListener(attempt));
                session.start();
            }
            @Override public void onServiceDisconnected(ComponentName name){if(binding==this&&flow.liveTransport())failPairing("Serviço de conexão interrompido. Toque Parear para tentar novamente.");}
        };
        serviceBound=bindService(intent,binding,BIND_AUTO_CREATE);
        if(!serviceBound){failPairing("Android recusou o serviço de conexão Bluetooth.");return;}
        armPreflight(8000,"O serviço de conexão não iniciou em 8 segundos.");
    }
    private HidSession.Listener transportListener(final int attempt){
        return new HidSession.Listener(){
                    private boolean valid(){return flow.attempt()==attempt&&flow.liveTransport();}
                    @Override public void status(HidSession.Phase next,String text){
                        if(!valid())return;
                        if(next==HidSession.Phase.ERROR){failPairing(text);return;}
                        if(next==HidSession.Phase.READY){
                            flow.registered();waitForHost();
                            ui.post(()->{if(flow.attempt()==attempt)rememberKnownSwitch();});
                        }
                        else if(next==HidSession.Phase.CONNECTING){flow.transport(PairingFlow.Step.CONNECTING);connectionStarted();}
                        else if(next==HidSession.Phase.NEGOTIATING){
                            restoredBondExperiment();
                            flow.transport(PairingFlow.Step.NEGOTIATING);connectionStarted();inputs.testing(false);control();
                        }
                        else if(next==HidSession.Phase.ACTIVE){
                            restoredBondExperiment();
                            flow.transport(PairingFlow.Step.ACTIVE);connectionStarted();
                            if(foregroundService!=null)foregroundService.connected();
                            if(inputs.testing()||!screen.equals("control")){inputs.testing(false);control();}else startMotion();
                        }
                        update(next,text);
                    }
                    @Override public void resetInput(){if(valid()&&controller!=null)controller.release();}
                    @Override public void notice(String text){if(valid())MainActivity.this.notice(text);}
                };
    }
    private void detachTransportUi(){
        release();
        if(foregroundService!=null){foregroundService.observe(null);foregroundService.listen(null);}
        if(serviceBound&&binding!=null)unbindService(binding);
        serviceBound=false;serviceRequested=false;binding=null;foregroundService=null;session=null;
    }
    private void restoreExistingSession(){
        if(binding!=null||session!=null)return;
        serviceRequested=true;
        binding=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(binding!=this)return;
                foregroundService=((HidForegroundService.LocalBinder)binder).service();
                HidSession existing=foregroundService.session();
                if(existing==null){detachTransportUi();home();return;}
                PairingFlow.Step restored=switch(existing.phase()){
                    case READY->PairingFlow.Step.WAIT_HOST;
                    case CONNECTING->PairingFlow.Step.CONNECTING;
                    case NEGOTIATING->PairingFlow.Step.NEGOTIATING;
                    case ACTIVE->PairingFlow.Step.ACTIVE;
                    default->PairingFlow.Step.REGISTER;
                };
                if(!flow.restoreTransport(restored)){detachTransportUi();return;}
                session=existing;inputs=foregroundService.inputs();state=inputs.transport();
                visibleUntil=foregroundService.deadlineAt();
                nameConfirmed=foregroundService.profile().switchIdentity();
                experiment=new LinkExperiment(prefs.nintendo()?prefs.switchTest():0);
                foregroundService.observe(transportListener(flow.attempt()));
                foregroundService.listen((reason,error)->{if(error)failPairing(reason);else stop();});
                notice=foregroundService.lastNotice();
                log("Tela retomada: sessão HID existente preservada, sem novo pareamento.");
                control();update(existing.phase(),foregroundService.lastMessage());
                if(restored==PairingFlow.Step.WAIT_HOST)waitForHost();
            }
            @Override public void onServiceDisconnected(ComponentName name){
                if(binding==this)failPairing("Serviço Bluetooth interrompido pelo Android.");
            }
        };
        serviceBound=bindService(new Intent(this,HidForegroundService.class),binding,BIND_AUTO_CREATE);
        if(!serviceBound){binding=null;serviceRequested=false;notice("Não foi possível retomar a tela da sessão Bluetooth.");}
    }
    private void connectionStarted(){if(hostTick!=null)ui.removeCallbacks(hostTick);if(foregroundService!=null)foregroundService.connectionStarted();}
    private void waitForHost(){
        if(hostTick!=null)ui.removeCallbacks(hostTick);
        final int attempt=flow.attempt();
        hostTick=new Runnable(){@Override public void run(){
            if(flow.attempt()!=attempt||!flow.waitingHost())return;
            long now=SystemClock.elapsedRealtime();
            if(experiment.expired(now)){expireBondExperiment();return;}
            long remaining=(visibleUntil-now+999)/1000;
            if(remaining<=0){failPairing(bond.everBonded()?"Vínculo Android aceito, mas o canal HID não abriu no prazo. L+R não pode ser transmitido. Copie o diagnóstico e confira as versões HOS/Atmosphère/MissionControl.":"Nenhuma conexão Bluetooth recebida no prazo. Confirme o destino e Mudar ordem/modo de segurar no Switch. Copie o diagnóstico.");return;}
            if((savedReconnect||experiment.automaticEnabled())&&pairingHost!=null&&session!=null&&session.phase()==HidSession.Phase.READY&&bond.canRequestLink(now)){
                requestLink(pairingHost,!savedReconnect);if(!flow.waitingHost())return;
            }
            message=experiment.pending()?"Teste vínculo • aguardando recomposição por "+((experiment.remaining(now)+999)/1000)+"s":(bond.state()==BondMonitor.State.BONDING?"Confirme o vínculo no Android":bond.state()==BondMonitor.State.BONDED?"Vínculo aceito • aguardando canal HID":"HID registrado • aguardando "+(prefs.nintendo()?"Switch":"computador"))+" • "+remaining+"s restantes";
            refresh();ui.postDelayed(this,1000);
        }};ui.post(hostTick);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        boolean handled=false;
        if(request==ENABLE_BT){handled=flow.enabledResult(result==RESULT_OK&&enabled());if(handled&&flow.step()==PairingFlow.Step.ERROR){failPairing("Ativação do Bluetooth cancelada ou não confirmada.");return;}}
        else if(request==VISIBLE_BT){
            handled=flow.visibilityResult(result>0);
            if(handled){
                if(result<=0){failPairing("Visibilidade cancelada. Nenhum perfil HID foi aberto.");return;}
                visibleUntil=SystemClock.elapsedRealtime()+Math.min(result,120)*1000L;
                log("Android aceitou visibilidade por "+Math.min(result,120)+"s. Registro HID só ocorrerá após o retorno à tela do app.");
            }
        }
        if(handled){if(preflightTimeout!=null)ui.removeCallbacks(preflightTimeout);queueDrive();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=PERMISSIONS||!flow.permissionResult(permitted(),enabled()))return;
        if(preflightTimeout!=null)ui.removeCallbacks(preflightTimeout);
        if(flow.step()==PairingFlow.Step.ERROR){failPairing("Permissão negada. Autorize Dispositivos próximos em Configurações → Apps → Nördhub ProController → Permissões; depois toque Conectar.");return;}
        log("Permissões concedidas; não serão solicitadas novamente nesta tentativa.");queueDrive();
    }
    private void rememberKnownSwitch(){
        if(!prefs.nintendo()||pairingHost!=null||!flow.waitingHost()||session==null||!session.registered())return;
        try{
            BluetoothDevice target=null;int matches=0;
            for(BluetoothDevice device:adapter.getBondedDevices())if("Nintendo Switch".equalsIgnoreCase(device.getName())){target=device;matches++;}
            if(matches>1){notice("Há mais de um Switch vinculado. Aguarde o console ou use Abrir enlace HID para escolher.");return;}
            if(target!=null){pairingHost=target;bond.seedExisting(SystemClock.elapsedRealtime());notice("Switch já associado. Use Reconectar para abrir o canal com o vínculo salvo.");}
        }catch(SecurityException e){failPairing("Permissão revogada ao consultar o Switch já vinculado.");}
    }
    private void reconnect(){
        if(session!=null&&session.connected()){notice("Controle já conectado.");return;}
        if(!permitted()){pair();return;}
        try{
            ArrayList<BluetoothDevice> devices=knownHosts();
            if(devices.isEmpty()){pair();return;}
            if(devices.size()==1){reconnectTo(devices.get(0));return;}
            String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=devices.get(i).getName()==null?"Dispositivo pareado "+(i+1):devices.get(i).getName();
            new AlertDialog.Builder(this).setTitle("Reconectar ao dispositivo salvo").setItems(names,(dialog,which)->reconnectTo(devices.get(which))).show();
        }catch(SecurityException e){failPairing("Permissão Bluetooth revogada.");}
    }
    private ArrayList<BluetoothDevice> knownHosts(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Permissão Bluetooth ausente.");
        ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        ArrayList<BluetoothDevice> matches=new ArrayList<>();
        for(BluetoothDevice device:devices)if(!prefs.nintendo()||"Nintendo Switch".equalsIgnoreCase(device.getName()))matches.add(device);
        return matches;
    }
    private void reconnectTo(BluetoothDevice device){
        if(session!=null&&session.registered()&&flow.waitingHost())requestLink(device);
        else pair(device);
    }
    private void requestLink(BluetoothDevice device){
        requestLink(device,false);
    }
    private void requestLink(BluetoothDevice device,boolean automatic){
        if(session==null||!session.registered()||!flow.waitingHost())return;
        try{
            if(device.getBondState()!=BluetoothDevice.BOND_BONDED){
                if(automatic)failPairing("Teste HID: o Android não confirma vínculo ativo; nenhuma abertura foi solicitada.");
                else notice("O vínculo não está ativo. Nenhuma conexão foi solicitada.");return;
            }
            if(pairingHost==null||!pairingHost.equals(device)){pairingHost=device;bond.reset();bond.seedExisting(SystemClock.elapsedRealtime());}
            if(automatic&&!experiment.claimAutomaticOpen(bond.canRequestLink(SystemClock.elapsedRealtime())))return;
            if(!bond.requestLink(SystemClock.elapsedRealtime())){notice("Aguarde 5s após o vínculo antes de Abrir enlace HID. É uma tentativa manual, sem repetir o pareamento.");return;}
            notice(automatic?"Teste HID: uma abertura solicitada após 5s de vínculo estável. Sem repetir pareamento.":"Conexão HID solicitada pelo usuário com vínculo ativo. Sem repetição automática.");session.reconnect(device);
        }catch(SecurityException e){failPairing("Permissão revogada ao solicitar o enlace HID.");}
    }
    private void clearTimers(){
        if(bondRecoveryTimeout!=null)ui.removeCallbacks(bondRecoveryTimeout);
        if(preflightTimeout!=null)ui.removeCallbacks(preflightTimeout);if(identityPoll!=null)ui.removeCallbacks(identityPoll);if(hostTick!=null)ui.removeCallbacks(hostTick);
    }
    private void teardown(){
        release();HidSession old=session;session=null;
        if(old!=null){lastTransport=old.diagnostic();if(foregroundService!=null&&foregroundService.session()==old)foregroundService.closeSession();else old.close();}
        if(foregroundService!=null){foregroundService.listen(null);foregroundService=null;}
        if(serviceBound&&binding!=null){unbindService(binding);serviceBound=false;}binding=null;
        if(serviceRequested){stopService(new Intent(this,HidForegroundService.class));serviceRequested=false;}
        if(adapter!=null&&permitted())try{pairingIdentity.recover(adapter);}catch(SecurityException|IllegalStateException e){notice("Não foi possível confirmar restauração do nome Bluetooth; reabra o app com Bluetooth ligado.");}
    }
    private void failPairing(String error){experiment.finish();flow.fail();clearTimers();teardown();notice="Tentativa encerrada; L+R exige canal HID. Use Diagnóstico → Copiar ou Testar toque para conferir somente a tela.";update(HidSession.Phase.ERROR,error);}
    private void send(){if(session!=null)session.send();}
    private void release(){if(controller!=null)controller.release();inputs.clear();if(session!=null)session.release();}
    private void stop(){if(motion!=null)motion.stop();experiment.finish();flow.cancel();clearTimers();teardown();update(HidSession.Phase.CLOSED,"Sessão encerrada; controles bloqueados fora do teste local.");}
    @Override protected void onPostResume(){super.onPostResume();resumed=true;queueDrive();if(controller!=null&&controller.layoutIndex()!=prefs.layout())control();else if(screen.equals("control"))startMotion();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&resumed)queueDrive();}
    @Override protected void onPause(){resumed=false;if(motion!=null)motion.stop();release();super.onPause();}
    @Override protected void onStop(){
        // onPause releases held touches. The started foreground service owns the connection.
        super.onStop();
    }
    private final BroadcastReceiver bluetoothEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
                if(state==BluetoothAdapter.STATE_OFF&&flow.liveTransport())failPairing("Bluetooth foi desligado; tentativa encerrada.");
            }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())&&flow.liveTransport()){
                BluetoothDevice peer=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class):intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                observeBond(peer,intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR),intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,BluetoothDevice.ERROR),intent.getIntExtra(BondFailure.EXTRA_REASON,BondFailure.UNAVAILABLE));
            }else if(Build.VERSION.SDK_INT>=36&&BondFailure.ACTION_KEY_MISSING.equals(intent.getAction())&&flow.liveTransport()){
                BluetoothDevice peer=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
                if(relevantPeer(peer)&&bond.observeKeyMissing())notice("Android sinalizou falta de chave para criptografar o enlace com o host. Não houve acesso às chaves. Encerre e refaça somente o vínculo Nintendo Switch no celular.");
            }
        }
    };
    private boolean relevantPeer(BluetoothDevice peer){
        if(peer==null)return false;
        try{
            if(pairingHost!=null)return peer.equals(pairingHost);
            if(!prefs.nintendo()||!"Nintendo Switch".equalsIgnoreCase(peer.getName()))return false;
            pairingHost=peer;return true;
        }catch(SecurityException e){failPairing("Permissão Bluetooth revogada ao observar o host.");return false;}
    }
    private void observeBond(BluetoothDevice peer,int value,int previous,int reason){
        if(!relevantPeer(peer))return;
        try{
            long now=SystemClock.elapsedRealtime();
            if(experiment.expired(now)){expireBondExperiment();return;}
            // A first observed NONE can include the lost previous state in the same protected broadcast.
            if(value==BluetoothDevice.BOND_NONE&&bond.state()==BondMonitor.State.UNKNOWN){
                if(previous==BluetoothDevice.BOND_BONDED)bond.seedExisting(now);
                else if(previous==BluetoothDevice.BOND_BONDING)bond.observe(BondMonitor.State.BONDING,now);
            }
            BondMonitor.State next=value==BluetoothDevice.BOND_BONDING?BondMonitor.State.BONDING:value==BluetoothDevice.BOND_BONDED?BondMonitor.State.BONDED:value==BluetoothDevice.BOND_NONE?BondMonitor.State.LOST:BondMonitor.State.UNKNOWN;
            BondMonitor.Change change=bond.state()==BondMonitor.State.LOST&&experiment.canResume(now)?bond.resumeAfterLoss(next,now):bond.observe(next,now);
            if(next==BondMonitor.State.LOST)bond.lossReason(reason);
            if(change!=BondMonitor.Change.IGNORED)log("Evento de vínculo Android: "+previous+" → "+value+" • HID conectado: "+(session!=null&&session.connected()));
            switch(change){
                case BONDING:notice("Android iniciou vínculo com o Switch; confirme Associer sem contatos. L+R aguarda o canal HID.");break;
                case BONDED:restoredBondExperiment();notice(experiment.automaticEnabled()?"Vínculo aceito. Teste HID aguardará 5s estáveis antes de uma abertura a partir do celular.":"Vínculo aceito pelo Android. Aguardando canal HID do Switch; nenhuma chamada automática de conexão.");break;
                case REMOVED:
                    log("Motivo Android da perda do vínculo: "+BondFailure.describe(reason));
                    if(deferBondLoss(now))break;
                    String context=bond.existingFound()&&!bond.bondedCallback()?"Vínculo salvo anteriormente foi removido":"Android informou remoção do vínculo com o Switch";
                    failPairing(context+(session!=null&&session.connected()?" após abrir o canal HID.":" antes de abrir o canal HID. Nenhum comando pode ser transmitido.")+" Motivo Android: "+BondFailure.describe(reason)+". A causa não foi identificada; copie o diagnóstico desta tentativa.");break;
                case FAILED:
                    log("Motivo Android da falha do vínculo: "+BondFailure.describe(reason));
                    if(deferBondLoss(now))break;
                    failPairing("Vínculo Bluetooth não concluído. Motivo Android: "+BondFailure.describe(reason)+". Tentativa encerrada; copie o diagnóstico.");break;
                case IGNORED:break;
            }
        }catch(SecurityException e){failPairing("Permissão Bluetooth revogada ao observar o vínculo.");}
    }
    private void restoredBondExperiment(){
        if(experiment.pending())log("Teste vínculo: novo vínculo ou canal HID observado antes do prazo; janela encerrada.");
        experiment.restored();if(bondRecoveryTimeout!=null)ui.removeCallbacks(bondRecoveryTimeout);
    }
    private boolean deferBondLoss(long now){
        boolean eligible=session!=null&&session.registered()&&!session.connected()&&session.phase()==HidSession.Phase.READY&&flow.waitingHost();
        if(!experiment.deferLoss(now,eligible))return false;
        final int attempt=flow.attempt();
        notice("Teste vínculo: perda observada; mantendo nome e HID por até 12s. Sem criar vínculo ou repetir permissões; controles bloqueados.");
        bondRecoveryTimeout=()->{if(flow.attempt()==attempt&&experiment.expired(SystemClock.elapsedRealtime()))expireBondExperiment();};
        ui.postDelayed(bondRecoveryTimeout,LinkExperiment.RECOVERY_MS);return true;
    }
    private void expireBondExperiment(){
        failPairing("Teste vínculo: 12s sem recomposição confirmada. O app manteve o HID registrado após a perda; não foi observado novo vínculo nem canal HID. Copie o diagnóstico do app.");
    }
    private void registerBluetoothEvents(){
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=36)filter.addAction(BondFailure.ACTION_KEY_MISSING);
        String permission=Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_CONNECT:Manifest.permission.BLUETOOTH;
        try{if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothEvents,filter,permission,null,Context.RECEIVER_EXPORTED);else registerReceiver(bluetoothEvents,filter,permission,null);receiverRegistered=true;}
        catch(SecurityException e){log("Não foi possível observar eventos de pareamento Android.");}
    }
    @Override protected void onDestroy(){
        if(HidForegroundService.ownsTransport()&&serviceRequested)detachTransportUi();else stop();
        ui.removeCallbacksAndMessages(null);
        if(receiverRegistered)unregisterReceiver(bluetoothEvents);
        super.onDestroy();
    }
    @Override public void onBackPressed(){if(!screen.equals("home"))home();else super.onBackPressed();}
}
