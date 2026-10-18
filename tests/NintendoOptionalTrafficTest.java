import com.switchphone.controller.ControllerState;
import com.switchphone.protocol.NintendoProtocol;

/** Regression for the alternating IMU/rumble traffic reported inside DuckStation. */
public final class NintendoOptionalTrafficTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static byte[] command(int id,int value){byte[] data=new byte[11];data[9]=(byte)id;data[10]=(byte)value;return data;}
    public static void main(String[] args){
        NintendoProtocol protocol=new NintendoProtocol(new byte[]{2,3,4,5,6,7});
        ControllerState state=new ControllerState();state.replace(1,2,1,-1,-1,1);
        protocol.output(1,command(3,0x30),state.report(),0);
        int notices=0;boolean repliesCorrect=true,rumbleCorrect=true,inputsIntact=true;
        byte[] expected=protocol.input(state.report(),0).data;
        for(int i=0;i<2000;i++){
            NintendoProtocol.Reply imu=protocol.output(1,command(0x40,1),state.report(),i*17L);
            NintendoProtocol.Reply rumble=protocol.output(0x10,new byte[9],state.report(),i*17L);
            if(imu.message!=null)notices++;if(rumble.message!=null)notices++;
            repliesCorrect&=!imu.accepted&&imu.packet.id==0x21&&imu.packet.data[12]==0&&imu.packet.data[13]==0x40;
            rumbleCorrect&=rumble.accepted&&rumble.packet==null;
            NintendoProtocol.Packet input=protocol.input(state.report(),i*17L);
            inputsIntact&=input.id==0x30&&input.data.length==48;
            for(int j=2;j<=11;j++)inputsIntact&=input.data[j]==expected[j];
            for(int j=12;j<48;j++)inputsIntact&=input.data[j]==0;
        }
        check(notices==2,"Alternating traffic produces exactly one notice per absent feature");
        check(repliesCorrect,"Repeated IMU enable stays an explicit NACK; no false sensor capability");
        check(rumbleCorrect,"Valid rumble traffic is consumed without a reply or physical vibration claim");
        check(inputsIntact&&protocol.negotiated(),"Buttons, hat, sticks and report mode survive repeated optional traffic");
        check(protocol.diagnostic().contains("pedidos IMU: 2000"),"IMU retries remain visible in the summary");
        check(protocol.diagnostic().contains("ignorados: 2000"),"Rumble count remains visible without log flooding");
        NintendoProtocol.Reply enable=protocol.output(1,command(0x48,1),state.report(),34001);
        check(!enable.accepted&&enable.packet.data[12]==0&&enable.message==null,"Vibration enable NACK shares the once-per-session notice");
        check(protocol.diagnostic().contains("ativação da vibração: 1"),"Vibration enable requests counted separately");
        for(int id:new int[]{0x40,0x48}){
            NintendoProtocol.Reply disable=protocol.output(1,command(id,0),state.report(),34002);
            check(disable.accepted&&(disable.packet.data[12]&255)==0x80,"Disabling unavailable feature is valid");
            check(!protocol.output(1,command(id,2),state.report(),34003).accepted,"Invalid enable flag rejected");
        }
        for(int length:new int[]{0,8,10,64})check(!protocol.output(0x10,new byte[length],state.report(),34004).accepted,"Malformed rumble length rejected");
        check(!protocol.output(0x10,null,state.report(),34005).accepted,"Null rumble rejected");
        check(protocol.diagnostic().contains("ignorados: 2000"),"Malformed rumble not counted as valid traffic");
        NintendoProtocol next=new NintendoProtocol(new byte[]{2,3,4,5,6,7});
        check(next.output(1,command(0x40,1),state.report(),0).message!=null,"New session restores first IMU warning");
        check(next.output(0x10,new byte[9],state.report(),0).message!=null,"New session restores first vibration warning");
        check(next.diagnostic().contains("pedidos IMU: 1")&&next.diagnostic().contains("ignorados: 1"),"New session counters are isolated");
        System.out.println("PASS: "+checks+" Nintendo optional traffic assertions; no game or hardware success claimed");
    }
}
