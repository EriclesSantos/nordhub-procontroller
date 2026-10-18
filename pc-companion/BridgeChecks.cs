using System;
using System.Threading;
using Nordhub.ProController;
class BridgeChecks {
 sealed class FakeReader:IJoystickReader {
  public bool Available=true;public uint Id=0;public Joystick.State Current=Neutral();public int Lists;
  public bool Read(uint id,out Joystick.Caps caps,out Joystick.State state){caps=Caps();caps.Mid=911;state=Current;return Available&&id==Id;}
  public Joystick.Device[] List(){Lists++;return Available?new[]{new Joystick.Device{Id=Id,Name="Nördhub test",Capabilities=TestCaps()}}:new Joystick.Device[0];}
 }
 sealed class FakeXbox:IXboxTarget {
  public int Sends,Disposals;public XboxReport Last;public uint Index{get{return 0;}}
  public void Send(XboxReport report){Sends++;Last=report;}
  public void Dispose(){Disposals++;}
 }
 static Joystick.Caps TestCaps(){Joystick.Caps caps=Caps();caps.Mid=911;return caps;}
 static void Continuity(){
  FakeReader source=new FakeReader();FakeXbox target=new FakeXbox();
  Bridge bridge=new Bridge(new Joystick.Device{Id=0,Name="Nördhub test",Capabilities=TestCaps()},source,target);
  source.Current.Buttons=1;source.Current.X=65535;bridge.Tick();
  Check(bridge.SourceAvailable&&target.Last.Buttons==0x1000&&target.Last.LeftX==32767,"Live phone forwards held button and stick");
  source.Available=false;bridge.Tick();
  Check(!bridge.SourceAvailable&&target.Last.Equals(new XboxReport()),"Connection loss releases every control, retaining the Xbox");
  for(int i=0;i<50;i++)bridge.Tick();
  Check(target.Disposals==0&&target.Sends==52&&source.Lists==1,"Temporary loss does not delete Xbox or busy-loop device enumeration");
  source.Available=true;source.Current=Neutral();source.Current.Buttons=2;bridge.Tick();
  Check(bridge.SourceAvailable&&target.Last.Buttons==0x2000&&target.Last.LeftX==0,"Same-slot reconnect resumes without stale held controls");
  source.Id=4;bridge.Tick();
  Check(bridge.SourceAvailable&&bridge.Source.Id==4&&target.Last.Buttons==0x2000,"Windows joystick slot change finds the same source");
  bridge.Dispose();bridge.Dispose();Check(target.Disposals==1,"Manual stop removes the virtual target exactly once");
  bool rejected=false;try{bridge.Tick();}catch(ObjectDisposedException){rejected=true;}Check(rejected,"Late timer cannot send after manual stop");
 }
 static int checks;
 static void Check(bool condition,string message){checks++;if(!condition)throw new Exception(message);}
 static Joystick.Caps Caps(){return new Joystick.Caps{XMin=0,XMax=65535,YMin=0,YMax=65535,RMin=0,RMax=65535,UMin=0,UMax=65535,Flags=6};}
 static Joystick.State Neutral(){return new Joystick.State{X=32767,Y=32767,R=32767,U=32767,Pov=65535};}
 static int Main(string[] args){
  try{
   Joystick.Caps caps=Caps();Joystick.State s=Neutral();XboxReport r=Mapper.Convert(s,caps);
   Check(r.Buttons==0&&r.LeftTrigger==0&&r.RightTrigger==0&&r.LeftX==0&&r.LeftY==0&&r.RightX==0&&r.RightY==0,"Neutral state");
   int[] bits={0x1000,0x2000,0x4000,0x8000,0x0100,0x0200,0,0,0x0020,0x0010,0x0400,0,0x0040,0x0080};
   for(int i=0;i<14;i++){s=Neutral();s.Buttons=1u<<i;r=Mapper.Convert(s,caps);Check(r.Buttons==bits[i],"Button mapping "+i);Check(r.LeftTrigger==(i==6?255:0)&&r.RightTrigger==(i==7?255:0),"Trigger mapping "+i);}
   int[] hats={1,9,8,10,2,6,4,5};for(int i=0;i<8;i++){s=Neutral();s.Pov=(uint)i*4500;r=Mapper.Convert(s,caps);Check(r.Buttons==hats[i],"Dpad "+i);}
   foreach(uint neutral in new uint[]{65535,4294967295}){s=Neutral();s.Pov=neutral;Check(Mapper.Convert(s,caps).Buttons==0,"Neutral hat");}
   Check(Mapper.Axis(0,0,65535,false)==short.MinValue&&Mapper.Axis(65535,0,65535,false)==short.MaxValue,"Axis extremes");
   Check(Mapper.Axis(0,0,65535,true)==short.MaxValue&&Mapper.Axis(65535,0,65535,true)==short.MinValue,"Y inversion");
   Check(Mapper.Axis(32767,0,65535,false)==0&&Mapper.Axis(32768,0,65535,true)==0,"Axis center");
   Check(Mapper.Axis(0,0,0,false)==0,"Invalid axis range neutral");
   s=Neutral();s.R=65535;s.U=0;r=Mapper.Convert(s,caps);Check(r.RightX==short.MaxValue&&r.RightY==short.MaxValue,"Rx/Ry map from WinMM R/U");
   s=Neutral();s.Buttons=0x3fff;r=Mapper.Convert(s,caps);Check(r.Buttons==0xf7f0&&r.LeftTrigger==255&&r.RightTrigger==255,"Combined controls");
   Continuity();Console.WriteLine("PASS: "+checks+" Windows mapping and reconnect checks");
   if(args.Length>0&&args[0]=="--virtual-neutral"){
    for(uint slot=0;slot<4;slot++){VirtualXbox.XboxState old;uint err=VirtualXbox.XInputGetState(slot,out old);Console.WriteLine("Before slot "+slot+": "+err+" leftX "+old.Gamepad.LeftX+" buttons "+old.Gamepad.Buttons);}
    uint index;using(VirtualXbox xbox=new VirtualXbox()){
     index=xbox.Index;Thread.Sleep(1200);xbox.Send(new XboxReport());Thread.Sleep(100);VirtualXbox.XboxState live;uint code=VirtualXbox.XInputGetState(index,out live);
     for(int attempt=0;code!=0&&attempt<60;attempt++){Thread.Sleep(100);code=VirtualXbox.XInputGetState(index,out live);}
     Console.WriteLine("XInput index "+index+" result "+code+" buttons "+live.Gamepad.Buttons+" leftX "+live.Gamepad.LeftX+" trigger "+live.Gamepad.LeftTrigger);
     for(uint slot=0;slot<4;slot++){VirtualXbox.XboxState now;uint err=VirtualXbox.XInputGetState(slot,out now);Console.WriteLine("During slot "+slot+": "+err+" leftX "+now.Gamepad.LeftX+" leftY "+now.Gamepad.LeftY+" buttons "+now.Gamepad.Buttons);}
     Check(code==0&&live.Gamepad.Buttons==0&&live.Gamepad.LeftX==0&&live.Gamepad.LeftTrigger==0,"Virtual Xbox visible to XInput, neutral");
     Console.WriteLine("PASS: real XInput Xbox device, player "+(index+1)+", neutral input");
    }
    Thread.Sleep(250);VirtualXbox.XboxState after;Check(VirtualXbox.XInputGetState(index,out after)!=0,"Virtual target removed after disposal");
    Console.WriteLine("PASS: virtual target cleanup");
   }
   if(args.Length>0&&args[0]=="--bridge-seconds"){
    int seconds=Math.Max(1,Math.Min(600,int.Parse(args[1])));Joystick.Device source=null;
    foreach(Joystick.Device device in Joystick.List())if(device.Name.IndexOf("SwitchPhone",StringComparison.OrdinalIgnoreCase)>=0||device.Name.IndexOf("rdhub",StringComparison.OrdinalIgnoreCase)>=0){if(source!=null)throw new Exception("More than one phone gamepad; choose in the graphical application");source=device;}
    if(source==null)throw new Exception("No connected Nördhub/SwitchPhone gamepad");
    using(Bridge bridge=new Bridge(source)){
     DateTime limit=DateTime.UtcNow.AddSeconds(seconds);Console.WriteLine("Xbox bridge active. Source: "+source.Name+"; Xbox player "+(bridge.XboxIndex+1)+"; duration "+seconds+"s");
     long changes=0;XboxReport previous=new XboxReport();uint buttons=0,outputButtons=0;int maxAxis=0,outputAxis=0;bool triggers=false;long xinputReads=0;
     while(DateTime.UtcNow<limit){bridge.Tick();XboxReport now=bridge.Last;
      if(!now.Equals(previous)){changes++;previous=now;buttons|=now.Buttons;maxAxis=Math.Max(maxAxis,Math.Max(Math.Abs((int)now.LeftX),Math.Abs((int)now.RightX)));triggers|=now.LeftTrigger!=0||now.RightTrigger!=0;}
      VirtualXbox.XboxState output;if(VirtualXbox.XInputGetState(bridge.XboxIndex,out output)==0){xinputReads++;outputButtons|=output.Gamepad.Buttons;outputAxis=Math.Max(outputAxis,Math.Max(Math.Abs((int)output.Gamepad.LeftX),Math.Abs((int)output.Gamepad.RightX)));}
      if(bridge.Reports%300==0)Console.WriteLine("Forwarded "+bridge.Reports+" reports; input changes "+changes+"; input buttons "+buttons.ToString("X4")+"; input axis "+maxAxis+"; triggers "+triggers+"; XInput reads "+xinputReads+"; output buttons "+outputButtons.ToString("X4")+"; output axis "+outputAxis);
      Thread.Sleep(16);
     }
     Console.WriteLine("Bridge test ended; Xbox removed. Reports "+bridge.Reports+"; input changes "+changes);
    }
   }
   if(args.Length>0&&args[0]=="--virtual-seconds"){
    int seconds=Math.Max(1,Math.Min(120,int.Parse(args[1])));
    using(VirtualXbox xbox=new VirtualXbox()){Console.WriteLine("Neutral Xbox active for Steam detection, player "+(xbox.Index+1));Thread.Sleep(seconds*1000);}
    Console.WriteLine("Neutral Xbox removed");
   }
   return 0;
  }catch(Exception ex){Console.Error.WriteLine("FAIL: "+ex.Message);return 1;}
 }
}
