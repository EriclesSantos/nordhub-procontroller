import com.switchphone.controller.*;
import java.nio.*;

public final class AdvancedInputTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void near(float expected,float actual,String message){check(Math.abs(expected-actual)<.0001,message+": "+actual);}
    public static void main(String[] args){
        byte[] feedback={0,0,78,72,0,0,1,0};check(PcFeedback.valid(feedback),"shared feedback envelope");for(int at:new int[]{2,3,4,5,6,7}){byte[] bad=feedback.clone();bad[at]=(byte)255;check(!PcFeedback.valid(bad),"feedback field bound "+at);}check(!PcFeedback.valid(new byte[8]),"unidentified output is not feedback");
        float[] a=AdvancedInput.stick(.05f,.03f,.12f,1,0,false,false);near(0,a[0],"radial deadzone");near(0,a[1],"radial deadzone y");
        a=AdvancedInput.stick(1,1,.12f,2,0,false,false);near(1,(float)Math.hypot(a[0],a[1]),"diagonal normalized");
        float linear=AdvancedInput.stick(.5f,0,0,1,0,false,false)[0],precision=AdvancedInput.stick(.5f,0,0,1,1,false,false)[0],fast=AdvancedInput.stick(.5f,0,0,1,2,false,false)[0];
        check(precision<linear&&linear<fast,"three usable response curves");
        a=AdvancedInput.stick(.6f,-.4f,0,1,0,true,true);near(-.6f,a[0],"invert x");near(.4f,a[1],"invert y");
        a=AdvancedInput.stick(Float.NaN,Float.POSITIVE_INFINITY,.12f,1,0,false,false);near(0,a[0],"nonfinite x");near(0,a[1],"nonfinite y");
        near(.15f,AdvancedInput.trigger(0,100,false),"initial trigger");near(.65f,AdvancedInput.trigger(50,100,false),"gradual drag");near(1,AdvancedInput.trigger(999,100,false),"trigger saturation");near(.05f,AdvancedInput.trigger(-999,100,false),"held trigger minimum");near(1,AdvancedInput.trigger(-999,100,true),"instant digital");
        int[] map=new int[14];for(int i=0;i<14;i++)map[i]=i;map[0]=7;map[1]=7;map[2]=-1;
        check(AdvancedInput.remap(7,map)==128,"many-to-one and disabled mapping");
        boolean invalid=false;map[0]=14;try{AdvancedInput.remap(1,map);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"reject invalid mapping");
        near(0,AdvancedInput.gyroAxis(.01f,1,false),"sensor noise floor");near(-1,AdvancedInput.gyroAxis(5,1,true),"gyro saturation and inversion");
        ControllerState state=new ControllerState();state.replace((1<<6)|(1<<7),8,.001f,-1,.4f,.2f);state.triggers(.25f,.75f);state.gyroAim(.3f,-.4f);
        check(state.leftTrigger()==64&&state.rightTrigger()==191,"trigger native resolution");check(state.report().length==7&&state.report()[2]==0,"legacy report unchanged quantization");
        byte[] frame=ControlFrame.encode(state,new float[]{1,2,3,0,0,9.80665f},900,-900,9000,3,140);ByteBuffer b=ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        check(frame.length==64&&b.getShort(4)==33,"Wi-Fi preserves sub-HID stick movement");check(b.getShort(8)==Math.round(.7f*32767),"gyro blends with touch");check(b.getShort(10)==Math.round(-.2f*32767),"gyro vertical blend");check((frame[12]&255)==64&&(frame[13]&255)==191,"gradual triggers on wire");check(b.getShort(16)==300&&b.getShort(18)==-300&&b.getShort(20)==1200,"pointer bounds");check((frame[48]&255)==100&&frame[49]==1,"battery and motion capability");
        for(int i=50;i<64;i++)check(frame[i]==0,"reserved wire byte "+i);
        state.clear();check(state.leftTrigger()==0&&state.rightTrigger()==0,"clear triggers");for(short v:state.preciseAxes())check(v==0,"clear motion and sticks");
        state.replace(0,8,0,0,0,0);state.triggers(1,1);check(state.leftTrigger()==0&&state.rightTrigger()==0,"unpressed remapped triggers are zero");
        MotionModel motion=new MotionModel();motion.calibrate();for(int i=1;i<=50;i++)motion.feed(i*10,.05f,-.03f,.01f,0,0,9.80665f);check(motion.progress()==50&&motion.calibrating(),"stationary progress");
        motion.feed(510,1,0,0,0,0,9.80665f);check(motion.progress()==0,"moving resets calibration");for(int i=52;i<=151;i++)motion.feed(i*10,.05f,-.03f,.01f,0,0,9.80665f);check(motion.calibrated()&&!motion.calibrating(),"100 stationary samples calibrate");near(0,motion.snapshot(1510)[0],"bias removed");
        motion.feed(1520,.55f,-.03f,.01f,0,0,9.80665f);near(.5f,motion.snapshot(1520)[0],"calibrated movement");motion.feed(1519,4,0,0,0,0,9.8f);near(.5f,motion.snapshot(1520)[0],"old sample rejected");motion.feed(1530,Float.NaN,0,0,0,0,9.8f);near(.5f,motion.snapshot(1530)[0],"invalid sample rejected");near(0,motion.snapshot(1700)[0],"stale motion released");
        a=MotionModel.rotate(1,2,3,1);near(-2,a[0],"landscape x");near(1,a[1],"landscape y");near(3,a[2],"display z");motion.clear();for(float v:motion.snapshot(1))near(0,v,"clear sensor");
        System.out.println("Advanced input checks: "+checks);
    }
}
