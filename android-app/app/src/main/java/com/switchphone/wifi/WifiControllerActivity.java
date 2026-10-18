package com.switchphone.wifi;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.switchphone.AdvancedSettingsActivity;
import com.switchphone.R;
import com.switchphone.bluetooth.HidForegroundService;
import com.switchphone.layouts.ControllerView;
import com.switchphone.settings.Preferences;

public final class WifiControllerActivity extends Activity {
    private Preferences prefs;private WifiForegroundService service;private ServiceConnection connection;private boolean bound,resumed,touchpad;private PairingQr pendingPairing;
    private ControllerView controller;private TextView status;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(service!=null){if(status!=null)status.setText(service.status());if(controller!=null)controller.setInputAvailable(service.connected());if(service.stopped()||!service.failure().isEmpty()){String message=service.failure().isEmpty()?"Sessão Wi-Fi encerrada.":service.failure();detach();setup();Toast.makeText(WifiControllerActivity.this,message,Toast.LENGTH_LONG).show();}}main.postDelayed(this,250);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=new Preferences(this);if(WifiForegroundService.running())bind(false);else setup();main.post(tick);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout root(String title){controller=null;LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(8),dp(2),dp(8),dp(2));root.setBackgroundColor(0xff0c131c);LinearLayout row=new LinearLayout(this);root.addView(row);TextView text=new TextView(this);text.setText(title);text.setTextSize(16);text.setTextColor(0xffa88cff);row.addView(text,new LinearLayout.LayoutParams(0,dp(48),1));button(row,"Mais",this::menu);status=new TextView(this);status.setTextColor(0xffcbd5e1);status.setTextSize(12);root.addView(status);setContentView(root);return root;}
    private void button(LinearLayout row,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);row.addView(b);b.setOnClickListener(v->action.run());}
    private EditText field(LinearLayout root,String hint,String value,int type){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(0xffe8e4f0);e.setHintTextColor(0xff9ca3af);e.setSingleLine();e.setInputType(type);root.addView(e);return e;}
    private void setup(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);touchpad=false;LinearLayout root=root("PC • WI-FI");status.setText(R.string.wifi_setup);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);scroll.addView(form);
        Button scan=new Button(this);scan.setText(R.string.wifi_scan);scan.setAllCaps(false);scan.setTextSize(18);form.addView(scan,new LinearLayout.LayoutParams(-1,dp(64)));scan.setOnClickListener(v->{if(canConnect())startActivityForResult(new Intent(this,QrScannerActivity.class),301);});
        TextView note=new TextView(this);note.setText(R.string.wifi_local_scan);note.setTextColor(0xffcbd5e1);note.setPadding(0,dp(12),0,dp(12));form.addView(note);
        LinearLayout manual=new LinearLayout(this);manual.setOrientation(LinearLayout.VERTICAL);manual.setVisibility(View.GONE);button(form,"Avançado: conexão manual",()->manual.setVisibility(manual.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));form.addView(manual);
        android.content.SharedPreferences remembered=getSharedPreferences("wifi-target",MODE_PRIVATE);
        EditText host=field(manual,"IPv4 exibido no PC",remembered.getString("host",""),android.text.InputType.TYPE_CLASS_PHONE);
        EditText port=field(manual,"Porta",remembered.getString("port","26761"),android.text.InputType.TYPE_CLASS_NUMBER);
        EditText code=field(manual,"Código de pareamento","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);code.setSaveEnabled(false);code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        button(manual,"Conectar manualmente",()->{try{PairingQr pairing=new PairingQr(java.util.Collections.singletonList(host.getText().toString().trim()),Integer.parseInt(port.getText().toString()),code.getText().toString());code.setText("");requestConnect(pairing);}catch(RuntimeException e){status.setText(R.string.wifi_manual_invalid);}});
        LinearLayout row=new LinearLayout(this);form.addView(row);button(row,"Ajustes e perfis",()->startActivity(new Intent(this,AdvancedSettingsActivity.class)));button(row,"Voltar",this::finish);
    }
    private boolean canConnect(){if(HidForegroundService.ownsTransport()){Toast.makeText(this,"Encerre a conexão Bluetooth no app antes de usar Wi-Fi.",Toast.LENGTH_LONG).show();return false;}return connection==null;}
    private void requestConnect(PairingQr pairing){if(!canConnect())return;pendingPairing=pairing;getSharedPreferences("wifi-target",MODE_PRIVATE).edit().putString("host",pairing.hosts.get(0)).putString("port",String.valueOf(pairing.port)).apply();View focus=getCurrentFocus();if(focus!=null)getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(),0);bind(true);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==301&&result==RESULT_OK&&data!=null){try{requestConnect(PairingQr.parse(data.getStringExtra("pairing")));}catch(IllegalArgumentException e){status.setText(e.getMessage());}}}
    private void bind(boolean start){
        if(connection!=null)return;Intent intent=new Intent(this,WifiForegroundService.class);if(start)startForegroundService(intent);
        connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((WifiForegroundService.LocalBinder)b).service();
            try{if(pendingPairing!=null){service.connect(pendingPairing);pendingPairing=null;}service.visible(resumed);control();}catch(Exception e){pendingPairing=null;String message=e.getMessage();service.disconnect();detach();setup();status.setText(message);}}
            public void onServiceDisconnected(ComponentName n){service=null;controller=null;connection=null;bound=false;setup();}};
        bound=bindService(intent,connection,BIND_AUTO_CREATE);if(!bound){connection=null;pendingPairing=null;status.setText(R.string.wifi_start_failed);stopService(intent);}
    }
    private void control(){if(service==null){setup();return;}LinearLayout root=root("PC • "+prefs.profileName());
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if(touchpad){service.motion().stop();root.addView(new TouchpadView(this,service,prefs),new LinearLayout.LayoutParams(-1,0,1));}
        else{if(resumed)service.motion().start();controller=new ControllerView(this,prefs.layout(),service.state,prefs,()->{},false);controller.setInputAvailable(service.connected());root.addView(controller,new LinearLayout.LayoutParams(-1,0,1));}
    }
    private void menu(){if(status==null)return;PopupMenu menu=new PopupMenu(this,status);
        menu.getMenu().add("Trocar perfil").setOnMenuItemClickListener(i->{String[] names=prefs.profiles().toArray(new String[0]);new AlertDialog.Builder(this).setTitle(names.length==0?"Salve um perfil em Ajustes primeiro":"Perfis").setItems(names,(d,index)->{if(controller!=null)controller.release();if(service!=null){service.visible(false);prefs.loadProfile(names[index]);service.visible(resumed);control();}else prefs.loadProfile(names[index]);}).setNegativeButton("Voltar",null).show();return true;});
        menu.getMenu().add("Ajustes e perfis").setOnMenuItemClickListener(i->{startActivity(new Intent(this,AdvancedSettingsActivity.class));return true;});
        menu.getMenu().add("Calibrar giroscópio").setEnabled(service!=null&&service.motion().available()).setOnMenuItemClickListener(i->{if(prefs.gyroMode()==0){Toast.makeText(this,"Ative o giroscópio em Ajustes primeiro.",Toast.LENGTH_LONG).show();return true;}service.motion().model().calibrate();Toast.makeText(this,"Mantenha o celular imóvel por 1 segundo. Movimento reinicia a calibração.",Toast.LENGTH_LONG).show();return true;});
        menu.getMenu().add(touchpad?"Voltar aos botões":"Touchpad").setEnabled(service!=null&&service.connected()).setOnMenuItemClickListener(i->{if(!touchpad&&!service.mouseAllowed()){Toast.makeText(this,"Habilite o touchpad como mouse no auxiliar Windows.",Toast.LENGTH_LONG).show();return true;}service.state.clear();touchpad=!touchpad;control();return true;});
        menu.getMenu().add("Diagnóstico").setOnMenuItemClickListener(i->{String report=service==null?"Wi-Fi desconectado":service.diagnostic()+"\nSensores: "+service.motion().available()+" • calibrado: "+service.motion().model().calibrated()+" • amostras de calibração: "+service.motion().model().progress();new AlertDialog.Builder(this).setTitle("Wi-Fi local").setMessage(report).setPositiveButton("Fechar",null).setNeutralButton("Copiar",(d,w)->getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Diagnóstico",report))).show();return true;});
        menu.getMenu().add("Desconectar").setOnMenuItemClickListener(i->{if(service!=null)service.disconnect();detach();setup();return true;});menu.show();
    }
    private void detach(){if(service!=null)service.visible(false);if(bound&&connection!=null)unbindService(connection);bound=false;connection=null;service=null;controller=null;}
    @Override protected void onResume(){super.onResume();resumed=true;if(service!=null){service.visible(true);control();}}
    @Override protected void onPause(){resumed=false;if(controller!=null)controller.release();if(service!=null)service.visible(false);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);pendingPairing=null;detach();super.onDestroy();}
    @Override public void onBackPressed(){if(touchpad){touchpad=false;control();}else super.onBackPressed();}
}
