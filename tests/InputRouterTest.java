import com.switchphone.controller.*;
import com.switchphone.protocol.NintendoProtocol;

/** Regression: local testing cannot leak a held button into periodic Nintendo Bluetooth reports. */
public final class InputRouterTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        InputRouter router=new InputRouter();
        router.testing(true);router.touch().replace(0x3fff,3,-1,1,1,-1);
        check(router.testing()&&router.touch()!=router.transport(),"Separate local state");
        byte[] r=router.transport().report();check(r[0]==0&&r[1]==0&&r[2]==0&&r[3]==0&&r[6]==8,"Generic Bluetooth stays neutral during local touch");
        NintendoProtocol protocol=new NintendoProtocol(new byte[]{2,3,4,5,6,7});byte[] command=new byte[11];command[9]=3;command[10]=0x30;
        protocol.output(1,command,router.transport().report(),0);
        for(int i=0;i<100;i++){
            r=protocol.input(router.transport().report(),i*17L).data;
            check(r[2]==0&&r[3]==0&&r[4]==0,"Periodic Nintendo reports never contain local test buttons");
        }
        router.testing(false);r=router.touch().report();check(router.touch()==router.transport()&&r[0]==0&&r[1]==0,"Exiting local mode releases held inputs");
        router.touch().set(1,true);check(router.transport().report()[0]==2,"Live controls route normally");
        router.testing(true);check(router.transport().report()[0]==0,"Entering local test releases live inputs");
        router.touch().set(0,true);router.clear();check(router.touch().report()[0]==0&&router.transport().report()[0]==0,"Stop/pause clears both states");
        System.out.println("PASS: "+checks+" input-isolation assertions (local touch never reaches Bluetooth)");
    }
}
