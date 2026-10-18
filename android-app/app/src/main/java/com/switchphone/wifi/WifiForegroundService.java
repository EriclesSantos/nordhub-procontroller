package com.switchphone.wifi;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.switchphone.R;
import com.switchphone.controller.*;
import com.switchphone.haptics.Haptics;
import com.switchphone.settings.Preferences;
import java.net.*;
import java.util.*;

/** One local peer, encrypted/authenticated UDP, foreground ownership independent of the UI. */
public final class WifiForegroundService extends Service implements PointerSession {
    public final class LocalBinder extends Binder{public WifiForegroundService service(){return WifiForegroundService.this;}}
    private static volatile WifiForegroundService active;
    public static boolean running(){return active!=null&&active.worker!=null;}
    private final Handler main=new Handler(Looper.getMainLooper());private final LocalBinder binder=new LocalBinder();
    public final ControllerState state=new ControllerState();private Preferences prefs;private ControllerFeatures features;
    private volatile DatagramSocket socket;private volatile Thread worker;private volatile boolean closed,connected;
    private volatile String status="Wi-Fi desconectado",failure="";private volatile long received,sent,ackAt;
    private final byte[] session=new byte[16];private long sequence,feedbackSequence;
    private PowerManager.WakeLock wake;private final Runnable renew=()->{if(!closed&&worker!=null)holdCpu();};
    public boolean stopped(){return closed&&worker==null;}
    public String status(){return status;}public String failure(){return failure;}public boolean connected(){return connected;}public boolean mouseAllowed(){return features.mouseAllowed();}public MotionInput motion(){return features.motion();}
    public String diagnostic(){return status+"\nEnvios: "+sent+" • respostas autenticadas: "+received+" • mouse habilitado no PC: "+mouseAllowed()+"\nCódigo de pareamento não incluído.";}
    @Override public void onCreate(){super.onCreate();active=this;prefs=new Preferences(this);features=new ControllerFeatures(this,state,prefs,()->{});new java.security.SecureRandom().nextBytes(session);
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("wifi_controller","Controle Wi-Fi",NotificationManager.IMPORTANCE_LOW));
        PowerManager power=getSystemService(PowerManager.class);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Nordhub:WifiSession");wake.setReferenceCounted(false);
        Notification notification=notification("Preparando conexão local.");if(Build.VERSION.SDK_INT>=29)startForeground(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(42,notification);
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,WifiControllerActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,WifiForegroundService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"wifi_controller").setSmallIcon(R.drawable.ic_status_controller).setContentTitle("Nördhub • Wi-Fi local").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Desconectar",stop).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&"STOP".equals(intent.getAction()))disconnect();return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent intent){return binder;}
    public void connect(String host,int port,String code)throws Exception{connect(new PairingQr(Collections.singletonList(host),port,code));}
    public void connect(PairingQr pairing)throws Exception{
        if(worker!=null)throw new IllegalStateException("Desconecte a sessão atual primeiro.");
        List<InetAddress> candidates=new ArrayList<>();for(String host:pairing.hosts)candidates.add(InetAddress.getByName(host));
        WirePacket codec=new WirePacket(pairing.code);closed=false;failure="";feedbackSequence=sequence=sent=received=0;ackAt=0;connected=false;
        socket=new DatagramSocket();socket.setSoTimeout(5);status="Conectando ao computador…";holdCpu();
        worker=new Thread(()->run(codec,candidates,pairing.port),"Nordhub-local-wifi");worker.start();
    }
    private void holdCpu(){wake.acquire(120000);main.removeCallbacks(renew);main.postDelayed(renew,60000);}
    @Override public void pointer(int x,int y,int scroll){features.pointer(x,y,scroll);}
    @Override public void mouseHeld(boolean down){features.mouseHeld(down);}
    @Override public void click(boolean right){features.click(right);}
    @Override public void clearPointerState(){state.clear();features.clearPointerState();}
    public void visible(boolean value){features.visible(value);}
    private void run(WirePacket codec,List<InetAddress> candidates,int port){
        long started=SystemClock.elapsedRealtime(),metadataAt=0;byte[] buffer=new byte[256];DatagramPacket incoming=new DatagramPacket(buffer,buffer.length);
        try{while(!closed){long iteration=SystemClock.elapsedRealtime(),now=iteration;byte[] bytes=codec.encode(WirePacket.INPUT,session,++sequence,features.frame());if(socket.isConnected())socket.send(new DatagramPacket(bytes,bytes.length));else for(InetAddress candidate:candidates)try{socket.send(new DatagramPacket(bytes,bytes.length,candidate,port));}catch(java.io.IOException ignored){if(closed)break;}sent++;
            try{incoming.setLength(buffer.length);socket.receive(incoming);WirePacket.Decoded p=codec.decode(Arrays.copyOf(buffer,incoming.getLength()));
                if(candidates.contains(incoming.getAddress())&&incoming.getPort()==port&&p!=null&&p.type()==WirePacket.FEEDBACK&&Arrays.equals(p.session(),session)&&p.sequence()>feedbackSequence&&PcFeedback.valid(p.payload())){if(!socket.isConnected())socket.connect(incoming.getAddress(),port);feedbackSequence=p.sequence();received++;ackAt=SystemClock.elapsedRealtime();features.feedback(p.payload());}

            }catch(SocketTimeoutException ignored){}
            now=SystemClock.elapsedRealtime();boolean alive=ackAt>0&&now-ackAt<750;
            if(alive!=connected){connected=alive;state.clear();if(!alive)main.post(()->features.clearPointerState());main.post(()->{if(!closed)startForeground(42,notification(connected?"Controle conectado ao PC. Desconectar encerra a sessão.":"Sem resposta do PC; entradas liberadas."));});}
            if(connected&&now-metadataAt>1000){metadataAt=now;String name=(Build.MANUFACTURER+" "+Build.MODEL).replaceAll("[\\p{Cntrl}]"," ").trim();byte[] model=name.getBytes(java.nio.charset.StandardCharsets.UTF_8);while(model.length>96){name=name.substring(0,name.length()-1);model=name.getBytes(java.nio.charset.StandardCharsets.UTF_8);}byte[] payload=new byte[model.length+1];payload[0]=1;System.arraycopy(model,0,payload,1,model.length);byte[] hello=codec.encode(WirePacket.HELLO,session,++sequence,payload);socket.send(new DatagramPacket(hello,hello.length));}
            status=connected?"Wi-Fi conectado • jogador Xbox confirmado pelo PC":"Aguardando PC: confira rede, código e porta.";
            if(ackAt==0&&now-started>15000)throw new java.io.IOException("O PC não respondeu. Use a mesma rede, leia um QR novo e permita o auxiliar nas redes privadas do firewall.");
            Thread.sleep(Math.max(1,17-(SystemClock.elapsedRealtime()-iteration)));
        }}catch(Exception e){if(!closed){failure=e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Conexão local interrompida: "+e.getClass().getSimpleName();status=failure;}}
        finally{closed=true;connected=false;state.clear();DatagramSocket current=socket;if(current!=null)current.close();worker=null;main.post(()->{features.close();stopSelf();});}
    }
    public void disconnect(){if(closed)return;closed=true;connected=false;state.clear();DatagramSocket current=socket;if(current!=null)current.close();stopSelf();}
    @Override public void onTaskRemoved(Intent intent){visible(false);super.onTaskRemoved(intent);}
    @Override public void onDestroy(){closed=true;connected=false;active=null;main.removeCallbacksAndMessages(null);if(socket!=null)socket.close();features.close();state.clear();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
