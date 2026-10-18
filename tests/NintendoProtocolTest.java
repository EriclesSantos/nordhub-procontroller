import com.switchphone.controller.ControllerState;
import com.switchphone.protocol.*;
import java.util.HashMap;
import java.util.Map;

/** Published-wire-contract fixtures. These are JVM tests, not Bluetooth/hardware validation. */
public final class NintendoProtocolTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] command(int command,int... args){byte[] data=new byte[10+args.length];data[9]=(byte)command;for(int i=0;i<args.length;i++)data[10+i]=(byte)args[i];return data;}
    private static int[] pair(byte[] data,int offset){return new int[]{(data[offset]&255)|((data[offset+1]&15)<<8),((data[offset+1]&255)>>4)|((data[offset+2]&255)<<4)};}
    public static void main(String[] args){
        byte[] address={2,3,4,5,6,7};NintendoProtocol protocol=new NintendoProtocol(address);
        ControllerState state=new ControllerState();protocol.battery(100,true);
        NintendoProtocol.Packet initial=protocol.input(state.report(),0);
        check(initial.id==0x3f&&initial.data.length==11,"Initial simple report");
        check((initial.data[2]&255)==8&&initial.data[3]==0&&(initial.data[4]&255)==128,"Simple neutral stick and hat");
        NintendoProtocol.Reply reply=protocol.output(1,command(2),state.report(),1000);
        check(reply.packet.id==0x21&&reply.packet.data.length==48,"Subcommand reply report 0x21 length");
        byte[] r=reply.packet.data;
        check((r[0]&255)==200&&(r[1]&255)==0x90,"Timer and real battery encoding");
        check((r[12]&255)==0x82&&r[13]==2&&r[16]==3,"Device info ACK and Pro type");
        for(int i=0;i<6;i++)check(r[18+i]==address[i],"Virtual identity bytes");
        reply=protocol.output(1,command(3,0x30),state.report(),1001);
        check(reply.accepted&&protocol.negotiated()&&protocol.mode()==0x30,"Mode 0x30 negotiation");
        int[] bytes={2,2,2,2,4,2,4,2,3,3,3,3,3,3};
        int[] masks={4,8,1,2,64,64,128,128,1,2,16,32,8,4};
        for(int i=0;i<14;i++){
            state.clear();state.set(i,true);r=protocol.input(state.report(),1100+i).data;
            check((r[bytes[i]]&255)==masks[i],"Nintendo button mapping "+i);
        }
        for(int hat=0;hat<=8;hat++){
            state.replace(0,hat,0,0,0,0);r=protocol.input(state.report(),1200).data;
            int[] expected={2,6,4,5,1,9,8,10,0};check((r[4]&15)==expected[hat],"D-pad "+hat);
        }
        state.replace(0,8,-1,-1,1,1);r=protocol.input(state.report(),1300).data;
        int[] left=pair(r,5),right=pair(r,8);
        check(left[0]==1&&left[1]==4095&&right[0]==4095&&right[1]==1,"12-bit sticks with inverted Y");
        state.clear();r=protocol.input(state.report(),1400).data;left=pair(r,5);check(left[0]==2048&&left[1]==2048,"Native stick neutral");
        reply=protocol.output(1,command(0x10,0x3d,0x60,0,0,18),state.report(),1500);r=reply.packet.data;
        check((r[12]&255)==0x90&&r[13]==0x10&&r[14]==0x3d&&r[18]==18,"SPI ACK/address/size echo");
        int[] max=pair(r,19),center=pair(r,22),min=pair(r,25),rightCenter=pair(r,28);
        check(max[0]==2047&&max[1]==2047&&center[0]==2048&&center[1]==2048&&min[0]==2047&&rightCenter[0]==2048,"Factory virtual calibration");
        reply=protocol.output(1,command(0x10,0xff,0xff,0xff,0xff,29),state.report(),1501);
        check(reply.accepted&&reply.packet.data[47]==(byte)255,"Bounded unmapped flash read");
        reply=protocol.output(1,command(0x10,0,0,0,0,30),state.report(),1502);
        check(!reply.accepted&&reply.packet.data[12]==0,"Oversize SPI read NACK");
        reply=protocol.output(1,command(0x10),state.report(),1503);check(!reply.accepted,"Truncated SPI NACK");
        protocol.output(1,command(0x30,5),state.report(),1600);
        reply=protocol.output(1,command(0x31),state.report(),1601);check((reply.packet.data[12]&255)==0xb0&&reply.packet.data[14]==5,"Player LEDs state round trip");
        for(int unsupported:new int[]{1,0x20,0x21,0x22,0x38,0x41,0x7f}){
            reply=protocol.output(1,command(unsupported,1),state.report(),1700);check(!reply.accepted&&reply.packet.data[12]==0,"Unsupported command NACK "+unsupported);
        }
        for(int optional:new int[]{0x40,0x48}){
            reply=protocol.output(1,command(optional,1),state.report(),1701);check(!reply.accepted,"Optional unavailable feature NACK");
            reply=protocol.output(1,command(optional,0),state.report(),1702);check(reply.accepted,"Optional feature disabled ACK");
        }
        reply=protocol.output(1,command(3,0x31),state.report(),1800);check(!reply.accepted&&protocol.mode()==0x30,"Unsupported report mode preserved");
        check(protocol.output(1,new byte[9],state.report(),1801).packet==null,"Malformed output rejected");
        check(protocol.output(1,null,state.report(),1802).packet==null,"Null output rejected");
        check(protocol.output(0x10,new byte[9],state.report(),1803).packet==null,"Rumble never fabricates reply");
        byte[] snapshot=protocol.lastReply();snapshot[13]=99;check(protocol.lastReply()[13]!=99,"Last reply defensive snapshot");
        NintendoProtocol clocks=new NintendoProtocol(address);state.clear();state.set(4,true);clocks.input(state.report(),2000);
        reply=clocks.output(1,command(4),state.report(),2200);check((reply.packet.data[14]&255)==20,"Trigger elapsed clock in 10ms units");
        state.clear();clocks.input(state.report(),2300);reply=clocks.output(1,command(4),state.report(),2500);check((reply.packet.data[14]&255)==30,"Released trigger elapsed clock preserved");
        reply=protocol.output(1,command(0x10,0x20,0x60,0,0,24),state.report(),2600);
        check(reply.packet.data[26]==0x40&&reply.packet.data[38]==0x34,"Virtual sensor scaling is nonzero, not measured motion");
        java.util.Random random=new java.util.Random(731);
        for(int i=0;i<5000;i++){
            byte[] malformed=new byte[random.nextInt(65)];random.nextBytes(malformed);
            NintendoProtocol.Reply bounded=protocol.output(random.nextBoolean()?1:random.nextInt(256),malformed,state.report(),3000L+i);
            if(bounded.packet!=null)check(bounded.packet.id==0x21&&bounded.packet.data.length==48,"Bounded parser reply");
        }
        checkDescriptor();
        System.out.println("PASS: "+checks+" Nintendo protocol assertions; no hardware simulated as connected");
    }
    private static void checkDescriptor(){
        byte[] d=NintendoDescriptor.bytes();Map<String,Integer> bits=new HashMap<>();int size=0,count=0,id=0,balance=0;
        for(int i=0;i<d.length;){int tag=d[i++]&255,len=tag&3;if(len==3)len=4;int value=0;
            for(int j=0;j<len;j++)value|=(d[i++]&255)<<(8*j);
            switch(tag&252){
                case 0x74:size=value;break;case 0x94:count=value;break;case 0x84:id=value;break;
                case 0x80:case 0x90:case 0xb0:String key=(tag&252)+":"+id;bits.put(key,bits.getOrDefault(key,0)+size*count);break;
                case 0xa0:balance++;break;case 0xc0:balance--;break;default:break;
            }
        }
        check(balance==0,"Descriptor balanced collections");
        check(bits.get("128:63")==88,"Simple report 11 bytes");
        check(bits.get("128:33")==384&&bits.get("128:48")==384,"0x21/0x30 input payload 48 bytes");
        check(bits.get("144:1")==384&&bits.get("144:16")==72,"0x01/0x10 output contracts");
        check(bits.get("176:2")==384,"Last reply feature contract");
    }
}
