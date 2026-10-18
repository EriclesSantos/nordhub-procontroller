package com.switchphone.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class Preferences {
    private final SharedPreferences store;
    public Preferences(Context context) { store=context.getSharedPreferences("controller",Context.MODE_PRIVATE); }
    public boolean nintendo() { return store.getInt("protocol",1)==1; }
    public int switchTest() { int value=store.getInt("switchTest",0);return value>=0&&value<=4?value:0; }
    public int layout() { return store.getInt("layout",0); }
    public int deadZone() { return store.getInt("deadZone",12); }
    public int sensitivity() { return store.getInt("sensitivity",100); }
    public int vibration() { return store.getInt("vibration",35); }
    public int leftDeadZone(){return store.getInt("leftDeadZone",deadZone());}
    public int rightDeadZone(){return store.getInt("rightDeadZone",deadZone());}
    public int leftGain(){return store.getInt("leftGain",sensitivity());}
    public int rightGain(){return store.getInt("rightGain",sensitivity());}
    public int curve(){return store.getInt("curve",0);}
    public boolean invertX(){return store.getInt("invertX",0)==1;}
    public boolean invertY(){return store.getInt("invertY",0)==1;}
    public boolean gradual(){return store.getInt("gradual",1)==1;}
    public int gyroMode(){return store.getInt("gyroMode",0);}
    public int gyroGain(){return store.getInt("gyroGain",100);}
    public boolean gyroInvertX(){return store.getInt("gyroInvertX",0)==1;}
    public boolean gyroInvertY(){return store.getInt("gyroInvertY",0)==1;}
    public int gameVibration(){return store.getInt("gameVibration",70);}
    public int touchSensitivity(){return store.getInt("touchSensitivity",100);}
    public int[] mapping(){int[] m=new int[14];for(int i=0;i<m.length;i++)m[i]=Math.max(-1,Math.min(13,store.getInt("map"+i,i)));return m;}
    private static final String[] PROFILE_KEYS={"layout","deadZone","sensitivity","vibration","leftDeadZone","rightDeadZone","leftGain","rightGain","curve","invertX","invertY","gradual","gyroMode","gyroGain","gyroInvertX","gyroInvertY","gameVibration","touchSensitivity"};
    public String profileName(){return store.getString("selectedProfile","Padrão");}
    public java.util.Set<String> profiles(){return new java.util.TreeSet<>(store.getStringSet("profiles",new java.util.HashSet<>()));}
    public void saveProfile(String name){
        name=name.trim();if(name.isEmpty()||name.length()>32)throw new IllegalArgumentException("Use um nome entre 1 e 32 caracteres.");
        org.json.JSONObject json=new org.json.JSONObject();try{
            for(String k:PROFILE_KEYS)if(store.contains(k))json.put(k,store.getInt(k,0));
            for(int i=0;i<14;i++)json.put("map"+i,mapping()[i]);
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
        java.util.Set<String> names=profiles();names.add(name);store.edit().putStringSet("profiles",names).putString("profile:"+name,json.toString()).putString("selectedProfile",name).apply();
    }
    public void loadProfile(String name){
        try{org.json.JSONObject json=new org.json.JSONObject(store.getString("profile:"+name,"{}"));android.content.SharedPreferences.Editor edit=store.edit();
            for(String k:PROFILE_KEYS){edit.remove(k);if(json.has(k))edit.putInt(k,json.getInt(k));}
            for(int i=0;i<14;i++)edit.putInt("map"+i,json.optInt("map"+i,i));edit.putString("selectedProfile",name).apply();
        }catch(org.json.JSONException e){throw new IllegalArgumentException("Perfil inválido.",e);}
    }
    public void resetMapping(){android.content.SharedPreferences.Editor edit=store.edit();for(int i=0;i<14;i++)edit.remove("map"+i);edit.apply();}
    public void put(String key, int value) { store.edit().putInt(key,value).apply(); }
}
