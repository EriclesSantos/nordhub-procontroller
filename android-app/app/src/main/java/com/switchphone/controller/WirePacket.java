package com.switchphone.controller;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;

/** Original LAN protocol: encrypt-then-MAC, bounded datagrams, per-session sequence numbers. */
public final class WirePacket {
    public static final int INPUT=1,FEEDBACK=2,STOP=3,HELLO=4;
    private final byte[] encryption,authentication;private final SecureRandom random=new SecureRandom();
    public record Decoded(int type,byte[] session,long sequence,byte[] payload){}
    public WirePacket(String code){
        try{
            String normalized=code.replace("-","").replace(" ","").toUpperCase(java.util.Locale.ROOT);
            if(!normalized.matches("[0-9A-F]{20}"))throw new IllegalArgumentException("Use os cinco grupos de quatro caracteres mostrados no PC.");
            byte[] master=MessageDigest.getInstance("SHA-256").digest(("Nordhub local Wi-Fi v1:"+normalized).getBytes(StandardCharsets.UTF_8));
            encryption=mac(master,"encryption".getBytes(StandardCharsets.UTF_8));authentication=mac(master,"authentication".getBytes(StandardCharsets.UTF_8));
        }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private static byte[] mac(byte[] key,byte[] bytes)throws GeneralSecurityException{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(bytes);}
    public byte[] encode(int type,byte[] session,long sequence,byte[] payload){byte[] iv=new byte[16];random.nextBytes(iv);return encode(type,session,sequence,payload,iv);}
    public byte[] encode(int type,byte[] session,long sequence,byte[] payload,byte[] iv){
        if(type<1||type>4||session.length!=16||sequence<1||iv.length!=16||payload.length>128)throw new IllegalArgumentException("Packet fields");
        try{
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryption,"AES"),new IvParameterSpec(iv));byte[] ciphertext=cipher.doFinal(payload);
            ByteBuffer b=ByteBuffer.allocate(48+ciphertext.length).order(ByteOrder.LITTLE_ENDIAN);b.put(new byte[]{78,72,87,49,1,(byte)type,0,0}).putLong(sequence).put(session).put(iv).put(ciphertext);
            byte[] body=b.array(),result=Arrays.copyOf(body,body.length+32);System.arraycopy(mac(authentication,body),0,result,body.length,32);return result;
        }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public Decoded decode(byte[] packet){
        if(packet==null||packet.length<96||packet.length>224||(packet.length-80)%16!=0)return null;
        try{
            byte[] body=Arrays.copyOf(packet,packet.length-32);if(!MessageDigest.isEqual(mac(authentication,body),Arrays.copyOfRange(packet,body.length,packet.length)))return null;
            ByteBuffer b=ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);if(b.get()!=78||b.get()!=72||b.get()!=87||b.get()!=49||b.get()!=1)return null;int type=b.get()&255;if(type<1||type>4||b.getShort()!=0)return null;
            long seq=b.getLong();if(seq<1)return null;byte[] session=new byte[16],iv=new byte[16];b.get(session).get(iv);byte[] ciphertext=new byte[b.remaining()];b.get(ciphertext);
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryption,"AES"),new IvParameterSpec(iv));byte[] payload=cipher.doFinal(ciphertext);
            return new Decoded(type,session,seq,payload);
        }catch(GeneralSecurityException|RuntimeException e){return null;}
    }
}
