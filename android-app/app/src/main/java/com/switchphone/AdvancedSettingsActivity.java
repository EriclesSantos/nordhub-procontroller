package com.switchphone;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import com.switchphone.settings.Preferences;

/** Advanced options stay off the control surface. No extra game buttons or macros. */
public final class AdvancedSettingsActivity extends Activity {
    private Preferences prefs;private LinearLayout content;
    private static final String[] BUTTONS={"Sul • A/×/B","Leste • B/○/A","Oeste • X/□/Y","Norte • Y/△/X","L / LB / L1","R / RB / R1","ZL / LT / L2","ZR / RT / R2","Menos / View / Share","Mais / Menu / Options","Home","Capture","L3","R3","Desativado"};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=new Preferences(this);show();}
    private void label(String text){TextView view=new TextView(this);view.setText(text);view.setTextSize(16);view.setTextColor(0xffe8e4f0);view.setPadding(12,12,12,8);content.addView(view);}
    private void button(String label,Runnable action){Button button=new Button(this);button.setAllCaps(false);button.setText(label);content.addView(button);button.setOnClickListener(v->action.run());}
    private void slider(String title,String key,int value,int min,int max){TextView label=new TextView(this);label.setText(getString(R.string.advanced_percentage,title,value));label.setTextColor(0xffe8e4f0);content.addView(label);SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(Math.max(0,value-min));content.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){label.setText(getString(R.string.advanced_percentage,title,p+min));if(user)prefs.put(key,p+min);}});}
    private void toggle(String title,String key,boolean value){Switch control=new Switch(this);control.setText(title);control.setTextColor(0xffe8e4f0);control.setChecked(value);content.addView(control);control.setOnCheckedChangeListener((b,on)->prefs.put(key,on?1:0));}
    private void choice(String title,String key,String[] items,int selected){button(title+": "+items[Math.max(0,Math.min(items.length-1,selected))],()->new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items,selected,(d,index)->{prefs.put(key,index);d.dismiss();show();}).setNegativeButton("Voltar",null).show());}
    private void show(){
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,12,24,24);content.setBackgroundColor(0xff0c131c);scroll.addView(content);setContentView(scroll);
        label("NÖRDHUB • Ajustes e perfis");button("Voltar ao controle",this::finish);
        label("Perfil atual: "+prefs.profileName());button("Salvar perfil",()->{EditText name=new EditText(this);name.setSingleLine();new AlertDialog.Builder(this).setTitle("Nome do perfil").setView(name).setPositiveButton("Salvar",(d,w)->{try{prefs.saveProfile(name.getText().toString());show();}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancelar",null).show();});
        button("Carregar perfil",()->{String[] names=prefs.profiles().toArray(new String[0]);if(names.length==0){Toast.makeText(this,"Salve um perfil primeiro.",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("Perfis").setItems(names,(d,i)->{prefs.loadProfile(names[i]);show();}).show();});
        button("Remapear botões",()->new AlertDialog.Builder(this).setTitle("Posição original").setItems(java.util.Arrays.copyOf(BUTTONS,14),(d,source)->new AlertDialog.Builder(this).setTitle("Enviar qual comando?").setSingleChoiceItems(BUTTONS,prefs.mapping()[source]<0?14:prefs.mapping()[source],(dialog,target)->{prefs.put("map"+source,target==14?-1:target);dialog.dismiss();}).setNegativeButton("Cancelar",null).show()).show());
        button("Restaurar mapeamento padrão",()->{prefs.resetMapping();Toast.makeText(this,"Mapeamento restaurado.",Toast.LENGTH_SHORT).show();});
        label("Analógicos • resposta direta, sem atraso artificial");slider("Zona morta esquerda","leftDeadZone",prefs.leftDeadZone(),0,30);slider("Zona morta direita","rightDeadZone",prefs.rightDeadZone(),0,30);slider("Sensibilidade esquerda","leftGain",prefs.leftGain(),50,200);slider("Sensibilidade direita","rightGain",prefs.rightGain(),50,200);
        choice("Curva de resposta","curve",new String[]{"Linear","Precisão no centro","Resposta rápida"},prefs.curve());toggle("Inverter X dos analógicos","invertX",prefs.invertX());toggle("Inverter Y dos analógicos","invertY",prefs.invertY());
        label("Gatilhos");toggle("Gatilhos graduais: toque e arraste para baixo","gradual",prefs.gradual());label("Graduação nativa no PC por Wi-Fi. No Bluetooth atual, o jogo recebe acionamento digital. Desative para acionamento total imediato.");
        label("Movimento");choice("Giroscópio","gyroMode",new String[]{"Desligado","Mira sempre ativa","Mira segurando L2/ZL","Somente movimento nativo (DSU no PC)"},prefs.gyroMode());slider("Sensibilidade do giroscópio","gyroGain",prefs.gyroGain(),25,200);toggle("Inverter mira horizontal","gyroInvertX",prefs.gyroInvertX());toggle("Inverter mira vertical","gyroInvertY",prefs.gyroInvertY());label("Calibre pelo menu do controle, com o celular imóvel. A mira usa o analógico direito; DSU transmite movimento aos emuladores compatíveis por Wi-Fi.");
        label("Feedback e touchpad");slider("Vibração ao tocar","vibration",prefs.vibration(),0,100);slider("Vibração recebida do jogo (Wi-Fi)","gameVibration",prefs.gameVibration(),0,100);slider("Velocidade do touchpad","touchSensitivity",prefs.touchSensitivity(),25,200);label("O touchpad fica no menu Mais e exige habilitação de mouse no auxiliar. Efeitos táteis dependem do vibrador do celular; não simulam resistência física de gatilhos.");
    }
}
