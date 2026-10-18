import com.switchphone.bluetooth.*;
import com.switchphone.controller.ControllerState;
import com.switchphone.protocol.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** The comparison must change reports without accidentally restoring the phone radio name. */
public final class HidProfileTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Map<Integer,Integer> inputBits(byte[] descriptor){
        Map<Integer,Integer> bits=new HashMap<>();int size=0,count=0,id=0;
        for(int i=0;i<descriptor.length;){
            int prefix=descriptor[i++]&255,length=prefix&3;if(length==3)length=4;
            int value=0;for(int j=0;j<length;j++)value|=(descriptor[i++]&255)<<(8*j);
            switch(prefix&252){
                case 0x74:size=value;break;case 0x94:count=value;break;case 0x84:id=value;break;
                case 0x80:bits.put(id,bits.getOrDefault(id,0)+size*count);break;default:break;
            }
        }
        return bits;
    }
    public static void main(String[] args){
        HidProfile normal=HidProfile.forAttempt(true,0),generic=HidProfile.forAttempt(true,4);
        check(normal.switchIdentity()&&generic.switchIdentity(),"Both Switch profiles acquire and preserve the same radio name lease");
        check(normal.name().equals(generic.name())&&generic.name().equals("Pro Controller"),"Both advertise identical SDP names");
        check(normal.description().equals(generic.description())&&normal.provider().equals(generic.provider()),"Only protocol changes, not SDP text");
        check(normal.subclass()==generic.subclass()&&generic.subclass()==8,"Gamepad subclass stays identical");
        check(normal.nintendoProtocol()&&!generic.nintendoProtocol()&&generic.genericSwitch(),"Generic channel cannot be claimed as negotiated Nintendo");
        check(!Arrays.equals(normal.descriptor(),generic.descriptor()),"Descriptors actually differ");
        check(Arrays.equals(generic.descriptor(),DiagnosticDescriptor.bytes()),"New comparison uses exact existing PC descriptor");
        Map<Integer,Integer> bits=inputBits(generic.descriptor());
        check(bits.size()==1&&bits.containsKey(1),"Generic descriptor only declares input ID 1");
        check(bits.get(1)==new ControllerState().report().length*8,"Generic descriptor matches actual controller payload size");
        check(!bits.containsKey(0x3f)&&!bits.containsKey(0x30),"Generic profile cannot falsely advertise Nintendo input families");
        NintendoProtocol.Packet packet=new NintendoProtocol(new byte[]{2,0,0,0,0,1}).input(new ControllerState().report(),0);
        check(inputBits(normal.descriptor()).get(packet.id)==packet.data.length*8,"Existing Nintendo descriptor still matches native initial packet");
        byte[] first=generic.descriptor();first[0]=0;
        check(generic.descriptor()[0]==5,"Descriptor cannot be changed across attempts by a caller");
        for(int mode=0;mode<=4;mode++){
            HidProfile pc=HidProfile.forAttempt(false,mode);
            check(!pc.switchIdentity()&&!pc.nintendoProtocol()&&!pc.genericSwitch(),"PC keeps original radio name even with saved Switch test "+mode);
            check(pc.name().equals("Nördhub ProController")&&Arrays.equals(pc.descriptor(),generic.descriptor()),"PC brand changes while wire descriptor stays unchanged: "+mode);
            if(mode<4){
                HidProfile p=HidProfile.forAttempt(true,mode);
                check(p.nintendoProtocol()&&Arrays.equals(p.descriptor(),normal.descriptor()),"Previous experiments keep original Nintendo wire profile: "+mode);
            }
        }
        LinkExperiment e=new LinkExperiment(4);
        check(e.variant()==4&&!e.deferLoss(0,true)&&!e.claimAutomaticOpen(true),"Descriptor comparison does not also enable recovery or automatic connect");
        System.out.println("PASS: "+checks+" HID-profile assertions (same identity, alternate descriptor, payload agreement, PC regression)");
    }
}
