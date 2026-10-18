package com.switchphone.bluetooth;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.switchphone.MainActivity;
import com.switchphone.R;
import com.switchphone.controller.InputRouter;

/** Owns the HID transport independently of the screen. No scanning or automatic pairing. */
public final class HidForegroundService extends Service {
    public interface Listener { void stop(String reason,boolean error); }
    public final class LocalBinder extends Binder { public HidForegroundService service(){return HidForegroundService.this;} }
    private static final int NOTIFICATION=41;
    private static final String CHANNEL="bluetooth_controller",STOP="com.switchphone.STOP_HID";
    private static volatile boolean transportOwned;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LocalBinder binder=new LocalBinder();
    private Listener listener;
    private HidSession.Listener observer;
    private HidSession session;
    private InputRouter inputs;
    private HidProfile profile;
    private PowerManager.WakeLock wakeLock;
    private String startupError,lastMessage="Aguardando conexão Bluetooth.",lastNotice="";
    private long deadlineAt;
    private int generation;
    private boolean ending;
    private final Runnable deadline=()->end("Prazo de pareamento encerrado sem conexão do host.",true);
    private final Runnable renewCpu=()->{if(session!=null&&session.canSendInput())holdCpu();else releaseCpu();};
    public static boolean ownsTransport(){return transportOwned;}
    @Override public void onCreate(){
        super.onCreate();
        try{
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Conexão do controle",NotificationManager.IMPORTANCE_LOW));
            Notification notification=notification("Pareamento Bluetooth em andamento; aguardando o host.");
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION,notification);
            PowerManager power=getSystemService(PowerManager.class);
            wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Nordhub:BluetoothSession");
            wakeLock.setReferenceCounted(false);
            deadline(120000);
        }catch(RuntimeException e){startupError="Não foi possível manter a sessão Bluetooth: "+e.getClass().getSimpleName();stopSelf();}
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,HidForegroundService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_status_controller).setContentTitle("Nördhub ProController • Bluetooth")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"Desconectar",cancel).build()).build();
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&STOP.equals(intent.getAction()))end("Sessão encerrada pela notificação.",false);return START_NOT_STICKY;}
    public String startupError(){return startupError;}
    public void listen(Listener listener){this.listener=listener;}
    public void observe(HidSession.Listener observer){this.observer=observer;}
    public HidSession session(){return session;}
    public InputRouter inputs(){return inputs;}
    public HidProfile profile(){return profile;}
    public String lastMessage(){return lastMessage;}
    public String lastNotice(){return lastNotice;}
    public long deadlineAt(){return deadlineAt;}
    public boolean cpuHeld(){return wakeLock!=null&&wakeLock.isHeld();}
    public HidSession createSession(InputRouter router,HidProfile selected,HidSession.Listener ui){
        if(session!=null)throw new IllegalStateException("Já existe uma sessão Bluetooth no serviço.");
        inputs=router;profile=selected;observer=ui;ending=false;
        final int token=++generation;
        session=new HidSession(this,router.transport(),selected,new HidSession.Listener(){
            private boolean valid(){return generation==token&&session!=null;}
            @Override public void status(HidSession.Phase phase,String text){
                if(!valid())return;lastMessage=text;
                if(phase==HidSession.Phase.CONNECTING)connectionStarted();
                if(phase==HidSession.Phase.NEGOTIATING||phase==HidSession.Phase.ACTIVE){connectionStarted();holdCpu();}
                if(phase==HidSession.Phase.ACTIVE)connected();
                HidSession.Listener current=observer;
                if(current!=null)current.status(phase,text);
                else if(phase==HidSession.Phase.ERROR)end(text,true);
            }
            @Override public void resetInput(){if(!valid())return;inputs.clear();HidSession.Listener current=observer;if(current!=null)current.resetInput();}
            @Override public void notice(String text){if(!valid())return;lastNotice=text;HidSession.Listener current=observer;if(current!=null)current.notice(text);}
        });
        transportOwned=true;return session;
    }
    public void deadline(long remaining){main.removeCallbacks(deadline);deadlineAt=SystemClock.elapsedRealtime()+Math.max(1,remaining);main.postDelayed(deadline,Math.max(1,remaining));}
    public void connectionStarted(){main.removeCallbacks(deadline);}
    // Bounded leases are renewed only while a real channel exists. A stalled service cannot
    // retain the CPU indefinitely; explicit stop/destruction also releases immediately.
    private void holdCpu(){if(wakeLock!=null){wakeLock.acquire(120000);main.removeCallbacks(renewCpu);main.postDelayed(renewCpu,60000);}}
    private void releaseCpu(){main.removeCallbacks(renewCpu);if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}
    public void connected(){connectionStarted();holdCpu();startForeground(NOTIFICATION,notification("Controle conectado, inclusive com a tela bloqueada. Toque Desconectar para encerrar."));}
    public void releaseInputs(){if(inputs!=null)inputs.clear();if(session!=null)session.release();}
    public void closeSession(){
        HidSession previous=session;session=null;transportOwned=false;generation++;observer=null;
        if(previous!=null)previous.close();
        if(inputs!=null)inputs.clear();
        releaseCpu();
    }
    private void end(String reason,boolean error){
        if(ending)return;ending=true;Listener current=listener;listener=null;
        if(current!=null)current.stop(reason,error);
        closeSession();stopSelf();
    }
    @Override public void onTaskRemoved(Intent rootIntent){releaseInputs();super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){
        main.removeCallbacksAndMessages(null);
        Listener current=listener;listener=null;
        if(!ending&&current!=null)current.stop("Serviço Bluetooth encerrado pelo Android.",true);
        closeSession();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
