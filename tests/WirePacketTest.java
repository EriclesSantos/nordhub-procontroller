import com.switchphone.controller.WirePacket;
import java.util.*;
import java.nio.file.*;

public final class WirePacketTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static byte[] vector(){byte[] s=new byte[16],iv=new byte[16],payload=new byte[64];for(int i=0;i<16;i++){s[i]=(byte)(i+16);iv[i]=(byte)i;}for(int i=0;i<64;i++)payload[i]=(byte)i;return new WirePacket("0011-2233-4455-6677-8899").encode(1,s,0x0102030405060708L,payload,iv);}
    public static void main(String[] args)throws Exception{
        WirePacket codec=new WirePacket("0011-2233-4455-6677-8899"),wrong=new WirePacket("00112233445566778898");byte[] session=new byte[16];
        for(int length=0;length<=128;length++){byte[] data=new byte[length];new Random(length).nextBytes(data);byte[] packet=codec.encode(1,session,length+1,data);WirePacket.Decoded d=codec.decode(packet);check(d!=null&&Arrays.equals(data,d.payload())&&d.sequence()==length+1&&Arrays.equals(session,d.session()),"round trip "+length);check(wrong.decode(packet)==null,"wrong code "+length);}
        byte[] good=vector();for(int i=0;i<good.length;i++){byte[] changed=good.clone();changed[i]^=1;check(codec.decode(changed)==null,"tamper byte "+i);}
        for(int length=0;length<good.length;length++)check(codec.decode(Arrays.copyOf(good,length))==null,"truncation "+length);
        check(!Arrays.equals(codec.encode(1,session,1,new byte[64]),codec.encode(1,session,1,new byte[64])),"fresh IV for identical state");check(codec.decode(new byte[256])==null,"oversized datagram");
        boolean invalid=false;try{new WirePacket("1234");}catch(IllegalArgumentException e){invalid=true;}check(invalid,"short code rejected");invalid=false;try{codec.encode(1,session,0,new byte[64]);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"zero sequence rejected");
        System.out.println("Authenticated Wi-Fi checks: "+checks);
        if(args.length==2&&args[0].equals("export")){
            Files.write(Path.of(args[1]),good);
            com.switchphone.controller.ControllerState state=new com.switchphone.controller.ControllerState();state.replace(0x30c1,1,.3f,-.7f,.25f,-.5f);state.triggers(.25f,.75f);
            byte[] frame=com.switchphone.controller.ControlFrame.encode(state,new float[]{.5f,-1,2,0,0,9.80665f},12,-7,120,0,85);
            Files.write(Path.of(args[1]+".frame"),codec.encode(1,new byte[16],7,frame));
        }
        if(args.length==2&&args[0].equals("feedback")){WirePacket.Decoded p=codec.decode(Files.readAllBytes(Path.of(args[1])));check(p!=null&&p.type()==2&&p.sequence()==0x0102030405060709L&&p.payload().length==8&&com.switchphone.controller.PcFeedback.valid(p.payload())&&(p.payload()[0]&255)==160&&(p.payload()[1]&255)==80,"C# feedback accepted by Java");System.out.println("C# → Java feedback interoperability passed");}
    }
}
