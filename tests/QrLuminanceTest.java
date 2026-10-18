import com.switchphone.wifi.QrLuminance;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class QrLuminanceTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Camera Y plane check failed");}
    static void rejects(ByteBuffer buffer,int w,int h,int row,int pixel){try{QrLuminance.read(buffer,w,h,row,pixel);throw new AssertionError("Invalid plane accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        ByteBuffer packed=ByteBuffer.wrap(new byte[]{1,2,3,4,5,6});
        check(Arrays.equals(QrLuminance.read(packed,3,2,3,1),new byte[]{1,2,3,4,5,6}));
        ByteBuffer padded=ByteBuffer.wrap(new byte[]{99,1,99,2,99,3,99,99,99,4,99,5,99,6});padded.position(1);
        check(Arrays.equals(QrLuminance.read(padded,3,2,8,2),new byte[]{1,2,3,4,5,6}));check(padded.position()==1);
        ByteBuffer direct=ByteBuffer.allocateDirect(9);direct.put(new byte[]{1,2,3,99,99,4,5,6,99});direct.flip();direct.limit(8);
        check(Arrays.equals(QrLuminance.read(direct.asReadOnlyBuffer(),3,2,5,1),new byte[]{1,2,3,4,5,6}));
        rejects(null,3,2,3,1);rejects(packed,0,2,3,1);rejects(packed,3,0,3,1);rejects(packed,3,2,2,1);rejects(packed,3,2,3,0);rejects(packed,3,2,3,2);rejects(packed,3,3,3,1);rejects(packed,8000001,1,8000001,1);rejects(packed,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,1);
        padded.limit(13);rejects(padded,3,2,8,2);
        System.out.println("Camera Y plane assertions: "+checks);
    }
}
