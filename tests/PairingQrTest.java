import com.switchphone.wifi.PairingQr;
import java.util.*;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

public final class PairingQrTest {
    static int checks;
    static final String GOOD="nordhub://pair?v=1&hosts=192.168.1.20,10.0.0.20&port=26761&code=00112233445566778899";
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void reject(String text){boolean rejected=false;try{PairingQr.parse(text);}catch(IllegalArgumentException e){rejected=true;check(!e.getMessage().contains("001122"),"exception hides code");}check(rejected,"reject invalid pairing");}
    public static void main(String[] args)throws Exception{
        PairingQr p=PairingQr.parse(GOOD);check(p.hosts.equals(Arrays.asList("192.168.1.20","10.0.0.20"))&&p.port==26761&&p.code.equals("00112233445566778899"),"parse PC schema");check(!p.toString().contains(p.code),"debug representation hides credential");
        reject(null);reject("");reject(GOOD.replace("nordhub:","https:"));reject(GOOD.replace("//pair?","//user@pair?"));reject(GOOD+"#fragment");reject(GOOD+"&code=00000000000000000000");reject(GOOD+"&unknown=1");reject(GOOD.replace("v=1","v=2"));reject(GOOD.replace("port=26761","port=80"));reject(GOOD.replace("port=26761","port=65536"));reject(GOOD.replace("code=00112233445566778899","code=1234"));reject(GOOD.replace("hosts=192.168.1.20,10.0.0.20","hosts=8.8.8.8"));reject(GOOD.replace("hosts=192.168.1.20,10.0.0.20","hosts=192.168.1.20,192.168.1.20"));reject(GOOD.replace("//pair?","//pair/path?"));reject(GOOD.replace("code=00112233445566778899","code=%xx"));
        for(String host:new String[]{"127.0.0.1","localhost","::1","172.32.1.1","192.168.1.256","192.168.001.2","10.1.2.3.evil","0.0.0.0","10.1.2.-1"})check(!PairingQr.local(host),"invalid IP");
        for(String host:new String[]{"10.1.2.3","172.16.0.1","172.31.255.254","169.254.1.2","192.168.0.1"})check(PairingQr.local(host),"local IP");
        for(int i=0;i<GOOD.length();i++)reject(GOOD.substring(0,i));
        if(args.length==1){BufferedImage image=ImageIO.read(new File(args[0]));int w=image.getWidth(),h=image.getHeight();int[] pixels=image.getRGB(0,0,w,h,null,0,w);Result decoded=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))));PairingQr actual=PairingQr.parse(decoded.getText());check(actual.hosts.equals(p.hosts)&&actual.code.equals(p.code)&&actual.port==p.port,"C# displayed QR decoded and validated by Java");System.out.println("C# QR → Android decoder interoperability passed");}
        System.out.println("QR pairing assertions: "+checks);
    }
}
