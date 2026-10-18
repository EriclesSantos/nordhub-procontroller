package com.switchphone.protocol;

import java.util.Arrays;

/** Original, bounded implementation from published packet facts, not copied firmware/code.
 * Virtual calibration belongs to our touchscreen controller, never to physical Nintendo hardware.
 * IMU, NFC, rumble and transport pairing/link keys are deliberately not fabricated.
 */
public final class NintendoProtocol {
    public static final class Packet {
        public final int id; public final byte[] data;
        public Packet(int id,byte[] data){this.id=id;this.data=data;}
    }
    public static final class Reply {
        public final Packet packet; public final String message; public final boolean accepted;
        Reply(Packet packet,String message,boolean accepted){this.packet=packet;this.message=message;this.accepted=accepted;}
    }
    private static final int[] TRIGGERS={4,5,6,7,-1,-1,10};
    private final byte[] identity;
    private final byte[] flash=new byte[0x10000];
    private int mode=0x3f,lights,battery;
    private boolean negotiated;
    private long rumblePackets,imuRequests,vibrationRequests;
    private boolean imuNotice,vibrationNotice;
    private final long[] elapsed=new long[7],pressedAt={-1,-1,-1,-1,-1,-1,-1};
    private byte[] lastReply=new byte[48];
    public NintendoProtocol(byte[] identity){
        if(identity==null||identity.length!=6)throw new IllegalArgumentException("Virtual identity requires 6 bytes");
        this.identity=identity.clone();
        Arrays.fill(flash,(byte)0xff);
        // Virtual touchscreen factory calibration, center 2048 and +/-2047 travel.
        flash[0x6012]=3;flash[0x6013]=(byte)0xa0;flash[0x601b]=1;
        pair(flash,0x603d,2047,2047);pair(flash,0x6040,2048,2048);pair(flash,0x6043,2047,2047);
        pair(flash,0x6046,2048,2048);pair(flash,0x6049,2047,2047);pair(flash,0x604c,2047,2047);
        byte[] colors={20,31,43,77,(byte)220,(byte)197,20,31,43,20,31,43};
        System.arraycopy(colors,0,flash,0x6050,colors.length);
        Arrays.fill(flash,0x6020,0x6038,(byte)0); // No real IMU measurements claimed.
        for(int axis=0;axis<3;axis++){
            flash[0x6027+axis*2]=0x40; // Nonzero virtual scaling avoids zero-divisor calibration.
            flash[0x6032+axis*2]=0x3b;flash[0x6033+axis*2]=0x34;
        }
        Arrays.fill(flash,0x6080,0x6086,(byte)0);
        // Controller parameters are synthetic configuration for our virtual axes.
        for(int offset:new int[]{0x6086,0x6098}){
            pair(flash,offset,0,0);pair(flash,offset+3,0,4095);
            pair(flash,offset+6,2047,2047);pair(flash,offset+9,2047,2047);
            pair(flash,offset+12,4095,4095);pair(flash,offset+15,2047,2047);
        }
    }
    public boolean negotiated(){return negotiated;}
    public int mode(){return mode;}
    public int lights(){return lights;}
    public String diagnostic(){return "Recursos Nintendo ausentes: giroscópio e vibração do jogo • pedidos IMU: "+imuRequests
        +" • pedidos de ativação da vibração: "+vibrationRequests+" • pacotes de vibração ignorados: "+rumblePackets
        +". Avisos agrupados por sessão; ausência desses recursos não encerra o canal HID.";}
    public byte[] lastReply(){return lastReply.clone();}
    public void battery(int percent,boolean charging){
        int level=percent<0?0:Math.min(4,(percent+24)/25)*2;
        battery=(level|(charging?1:0))<<4;
    }
    public Packet input(byte[] state,long millis){
        validateState(state);trackTriggers(state,millis);
        return new Packet(mode,mode==0x30?standard(state,millis):simple(state));
    }
    private byte[] standard(byte[] state,long millis){
        byte[] out=new byte[48];out[0]=(byte)(millis/5);out[1]=(byte)battery;
        int b=(state[0]&255)|((state[1]&255)<<8),h=state[6]&255;
        out[2]=(byte)(bit(b,2,0)|bit(b,3,1)|bit(b,0,2)|bit(b,1,3)|bit(b,5,6)|bit(b,7,7));
        out[3]=(byte)(bit(b,8,0)|bit(b,9,1)|bit(b,13,2)|bit(b,12,3)|bit(b,10,4)|bit(b,11,5));
        int d=0;
        if(h==3||h==4||h==5)d|=1;if(h==7||h==0||h==1)d|=2;
        if(h==1||h==2||h==3)d|=4;if(h==5||h==6||h==7)d|=8;
        out[4]=(byte)(d|bit(b,4,6)|bit(b,6,7));
        // Native Y increases upwards, unlike Canvas/generic HID Y.
        pair(out,5,axis12(state[2]),axis12(-(int)state[3]));
        pair(out,8,axis12(state[4]),axis12(-(int)state[5]));
        out[11]=(byte)0x80;return out;
    }
    private byte[] simple(byte[] state){
        int b=(state[0]&255)|((state[1]&255)<<8);
        byte[] out=new byte[11];
        // Pro Controller simple mode face buttons: B,A,Y,X, then shoulders.
        out[0]=(byte)(bit(b,0,0)|bit(b,1,1)|bit(b,2,2)|bit(b,3,3)|bit(b,4,4)|bit(b,5,5)|bit(b,6,6)|bit(b,7,7));
        out[1]=(byte)(bit(b,8,0)|bit(b,9,1)|bit(b,12,2)|bit(b,13,3)|bit(b,10,4)|bit(b,11,5));
        out[2]=state[6];
        for(int i=0;i<4;i++){
            int value=Math.max(0,Math.min(65535,32768+state[i+2]*32767/127));
            out[3+i*2]=(byte)value;out[4+i*2]=(byte)(value>>8);
        }
        return out;
    }
    private static int bit(int buttons,int source,int target){return ((buttons>>source)&1)<<target;}
    private static int axis12(int value){return Math.max(0,Math.min(4095,2048+Math.round(value*2047f/127)));}
    private static void pair(byte[] out,int offset,int x,int y){out[offset]=(byte)x;out[offset+1]=(byte)((x>>8)|((y&15)<<4));out[offset+2]=(byte)(y>>4);}
    private void trackTriggers(byte[] state,long millis){
        int buttons=(state[0]&255)|((state[1]&255)<<8);
        for(int i=0;i<TRIGGERS.length;i++){
            boolean down=TRIGGERS[i]>=0&&(buttons&(1<<TRIGGERS[i]))!=0;
            if(down&&pressedAt[i]<0)pressedAt[i]=millis;
            else if(!down&&pressedAt[i]>=0){elapsed[i]+=Math.max(0,millis-pressedAt[i]);pressedAt[i]=-1;}
        }
    }
    private static void validateState(byte[] state){if(state==null||state.length!=7)throw new IllegalArgumentException("State length");}
    /** Android strips the report ID: subcommand is payload byte 9, args start at 10. */
    public Reply output(int id,byte[] data,byte[] state,long millis){
        validateState(state);trackTriggers(state,millis);
        if(id==0x10){
            if(data==null||data.length!=9)return new Reply(null,"Pacote de vibração Nintendo inválido.",false);
            rumblePackets++;
            return new Reply(null,optionalNotice(false),true);
        }
        if(id!=1||data==null||data.length<10)return new Reply(null,"Relatório Nintendo inválido ou não suportado.",false);
        int command=data[9]&255;byte[] out=standard(state,millis);
        out[12]=(byte)0x80;out[13]=(byte)command;
        boolean accepted=true;String message=null;
        switch(command){
            case 0x00:out[14]=3;break;
            case 0x02:
                out[12]=(byte)0x82;
                out[14]=1;out[15]=0;out[16]=3;out[17]=2;
                System.arraycopy(identity,0,out,18,6);out[24]=1;out[25]=1;
                message="Identificação Pro Controller virtual enviada.";break;
            case 0x03:
                if(data.length<11||((data[10]&255)!=0x30&&(data[10]&255)!=0x3f)){accepted=false;message="Modo de relatório solicitado não suportado.";break;}
                mode=data[10]&255;negotiated=true;message=mode==0x30?"Modo Nintendo 0x30 negociado; valide as entradas no console.":"Modo Nintendo simples negociado; valide no console.";break;
            case 0x04:
                out[12]=(byte)0x83;
                for(int i=0;i<7;i++){
                    long total=elapsed[i]+(pressedAt[i]<0?0:Math.max(0,millis-pressedAt[i]));
                    int ticks=(int)Math.min(65535,total/10);out[14+i*2]=(byte)ticks;out[15+i*2]=(byte)(ticks>>8);
                }
                break;
            case 0x08:
                if(data.length<11){accepted=false;break;}
                flash[0x5000]=data[10]==0?(byte)0xff:1;break;
            case 0x10:
                if(data.length<15){accepted=false;message="Leitura SPI truncada.";break;}
                long address=0;for(int i=0;i<4;i++)address|=(long)(data[10+i]&255)<<(i*8);
                int length=data[14]&255;
                if(length>29){accepted=false;message="Leitura SPI excedeu 29 bytes.";break;}
                out[12]=(byte)0x90;System.arraycopy(data,10,out,14,5);
                for(int i=0;i<length;i++)out[19+i]=address+i<flash.length?flash[(int)address+i]:(byte)0xff;
                break;
            case 0x11:case 0x12:
                out[14]=1;message="Calibração virtual protegida contra gravação/erase.";break;
            case 0x30:
                if(data.length<11){accepted=false;break;}
                lights=data[10]&255;message=String.format(java.util.Locale.ROOT,"LEDs de jogador recebidos: 0x%02X.",lights);break;
            case 0x31:out[12]=(byte)0xb0;out[14]=(byte)lights;break;
            case 0x38:
                // This app has no physical Home LED. Keep operation explicitly unsupported.
                accepted=false;message="LED Home físico não disponível.";break;
            case 0x40:case 0x48:
                if(data.length<11){accepted=false;break;}
                if((data[10]&255)>1){accepted=false;message="Valor inválido para recurso Nintendo opcional.";break;}
                if(command==0x40)imuRequests++;else vibrationRequests++;
                if(data[10]!=0){accepted=false;message=optionalNotice(command==0x40);}
                break;
            default:
                accepted=false;message=String.format(java.util.Locale.ROOT,"Subcomando 0x%02X não implementado (NACK).",command);
        }
        if(!accepted)out[12]=0;
        lastReply=out.clone();return new Reply(new Packet(0x21,out),message,accepted);
    }
    private String optionalNotice(boolean imu){
        if(imu){
            if(imuNotice)return null;imuNotice=true;
            return "Aviso de recurso: giroscópio indisponível; pedido não atendido. Botões e analógicos continuam habilitados. Repetições no resumo do diagnóstico.";
        }
        if(vibrationNotice)return null;vibrationNotice=true;
        return "Aviso de recurso: vibração do jogo indisponível. Pacotes são ignorados sem encerrar a conexão; repetições no resumo do diagnóstico.";
    }
}
