import com.switchphone.controller.ControllerState;
import com.switchphone.bluetooth.DiagnosticDescriptor;

/** Executable JVM contracts, independent of Android and third-party test frameworks. */
public final class ControllerStateTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void buttons(ControllerState state,int value){
        byte[] report=state.report();check(report.length==7,"Payload length");
        check(((report[0]&255)|((report[1]&255)<<8))==value,"Button mask "+value);
    }
    public static void main(String[] args){
        ControllerState state=new ControllerState();buttons(state,0);
        check(state.report()[6]==8,"Neutral hat");
        state.set(0,true);buttons(state,1);state.set(1,true);buttons(state,3);
        state.set(0,false);buttons(state,2);state.set(1,false);buttons(state,0);
        for(int i=0;i<14;i++)state.set(i,true);buttons(state,0x3fff);
        state.clear();buttons(state,0);
        state.replace(0x3000,3,-1,1,0.5f,-0.5f);buttons(state,0x3000);
        byte[] r=state.report();
        check(r[2]==-127&&r[3]==127&&r[4]==64&&r[5]==-63&&r[6]==3,"Axes packing and diagonal");
        state.replace(0,8,-20,20,Float.NaN,Float.POSITIVE_INFINITY);
        r=state.report();check(r[2]==-127&&r[3]==127&&r[4]==0&&r[5]==0,"Clamping and invalid axis safety");
        r[0]=3;buttons(state,0);
        for(int invalid:new int[]{-1,14}){
            boolean rejected=false;try{state.set(invalid,true);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Invalid button rejected");
        }
        check(ControllerState.filterAxis(.1f,.12f,1)==0,"Dead zone");
        check(Math.abs(ControllerState.filterAxis(.56f,.12f,1)-.5f)<.0001,"Dead zone rescaling");
        check(ControllerState.filterAxis(-1,.12f,1.5f)==-1,"Sensitivity clamp");
        state.clear();r=state.report();check(r[2]==0&&r[3]==0&&r[4]==0&&r[5]==0&&r[6]==8,"Full reset");
        byte[] descriptor=DiagnosticDescriptor.bytes();
        int size=0,count=0,bits=0,id=0,collections=0;
        for(int i=0;i<descriptor.length;){
            int tag=descriptor[i++]&255,length=tag&3;if(length==3)length=4;
            int value=0;for(int j=0;j<length;j++)value|=(descriptor[i++]&255)<<(8*j);
            switch(tag&252){
                case 0x74:size=value;break;case 0x94:count=value;break;case 0x84:id=value;break;
                case 0x80:bits+=size*count;break;case 0xa0:collections++;break;case 0xc0:collections--;break;default:break;
            }
        }
        check(bits==56&&id==1&&collections==0,"Descriptor 56-bit input report, ID 1, balanced collection");
        System.out.println("PASS: "+checks+" assertions (buttons, axes, hat, reset, dead zone, payload/descriptor)");
    }
}
