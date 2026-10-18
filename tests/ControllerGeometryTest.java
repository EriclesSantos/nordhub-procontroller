import com.switchphone.layouts.ControllerGeometry;
import com.switchphone.layouts.ControllerGeometry.Key;

/** Prevent oversized drawing from producing clipped or overlapping touch targets. */
public final class ControllerGeometryTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean overlaps(Key a,Key b){
        if(a.round&&b.round)return Math.hypot(a.x-b.x,a.y-b.y)<(a.w+b.w)/2;
        if(!a.round&&!b.round)return Math.abs(a.x-b.x)<(a.w+b.w)/2&&Math.abs(a.y-b.y)<(a.h+b.h)/2;
        Key circle=a.round?a:b,rect=a.round?b:a;
        float dx=Math.max(Math.abs(circle.x-rect.x)-rect.w/2,0),dy=Math.max(Math.abs(circle.y-rect.y)-rect.h/2,0);
        return Math.hypot(dx,dy)<circle.w/2;
    }
    static boolean touchesStick(Key key,float x,float y,float radius){
        if(key.round)return Math.hypot(key.x-x,key.y-y)<radius+key.w/2;
        float dx=Math.max(Math.abs(key.x-x)-key.w/2,0),dy=Math.max(Math.abs(key.y-y)-key.h/2,0);
        return Math.hypot(dx,dy)<radius;
    }
    public static void main(String[] args){
        for(int[] viewport:new int[][]{{1000,400},{2400,900},{1600,900},{800,300},{2200,750},{640,320}}){
            for(int layout=0;layout<3;layout++){
                ControllerGeometry g=new ControllerGeometry(viewport[0],viewport[1],layout);
                check(g.keys.size()==18,"All controls present");
                check(g.radius>=69*g.unit,"Larger analog travel area");
                for(Key key:g.keys){
                    String context=viewport[0]+"x"+viewport[1]+" layout "+layout+" key "+key.label;
                    check(key.x-key.w/2>=0&&key.x+key.w/2<=viewport[0]&&key.y-key.h/2>=0&&key.y+key.h/2<=viewport[1],"Clipped: "+context);
                    check(key.contains(key.x,key.y),"Visual center must respond: "+context);
                    check(!touchesStick(key,g.leftX,g.leftY,g.radius*1.1f),"Left stick conflicts: "+context);
                    check(!touchesStick(key,g.rightX,g.rightY,g.radius*1.1f),"Right stick conflicts: "+context);
                    for(Key other:g.keys)if(key.id<other.id)check(!overlaps(key,other),"Overlapping controls: "+context+" / "+other.label);
                    if(key.id<4)check(key.w>=67*g.unit,"Face button size regression");
                }
                check(g.leftX>=g.radius&&g.leftX+g.radius<=viewport[0]&&g.leftY>=g.radius&&g.leftY+g.radius<=viewport[1],"Left stick clipped");
                check(g.rightX>=g.radius&&g.rightX+g.radius<=viewport[0]&&g.rightY>=g.radius&&g.rightY+g.radius<=viewport[1],"Right stick clipped");
            }
        }
        System.out.println("PASS: "+checks+" layout assertions (three themes, six viewports, bounds, spacing, hit targets and stick ownership)");
    }
}
