package com.switchphone.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

/** Persisted recovery of the user's Bluetooth name across crashes; never reads the radio MAC. */
public final class IdentityLease {
    private final SharedPreferences store;
    private final Context context;
    public IdentityLease(Context context){this.context=context;store=context.getSharedPreferences("identity",Context.MODE_PRIVATE);}
    public void recover(BluetoothAdapter adapter){
        if(android.os.Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Permissão Bluetooth ausente.");
        String old=store.getString("previousName",null);
        if(old!=null){
            if(!old.equals(adapter.getName())){
                if(!adapter.setName(old))throw new IllegalStateException("Não foi possível restaurar o nome Bluetooth anterior.");
                // setName is asynchronous: retain the recovery marker until observation confirms restoration.
                return;
            }
            if(!store.edit().remove("previousName").commit())throw new IllegalStateException("Falha ao confirmar recuperação do nome Bluetooth.");
        }
    }
    public void acquire(BluetoothAdapter adapter){
        if(android.os.Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Permissão Bluetooth ausente.");
        String old=store.getString("previousName",null);
        if(old==null)old=adapter.getName();
        if(old==null)throw new IllegalStateException("Não foi possível obter o nome Bluetooth para restauração.");
        if(!store.edit().putString("previousName",old).commit())throw new IllegalStateException("Não foi possível salvar recuperação do nome Bluetooth.");
        if(!"Pro Controller".equals(adapter.getName())&&!adapter.setName("Pro Controller"))throw new IllegalStateException("Android recusou nome Bluetooth Pro Controller.");
    }
    public byte[] virtualAddress(){
        String saved=store.getString("virtualAddress",null);
        byte[] bytes=new byte[6];
        if(saved==null||!saved.matches("[0-9a-fA-F]{12}")){
            new SecureRandom().nextBytes(bytes);bytes[0]=(byte)((bytes[0]&0xfc)|2);
            StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            store.edit().putString("virtualAddress",hex.toString()).apply();
        }else for(int i=0;i<6;i++)bytes[i]=(byte)Integer.parseInt(saved.substring(i*2,i*2+2),16);
        return bytes;
    }
}
