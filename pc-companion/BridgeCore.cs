using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using Microsoft.Win32;

namespace Nordhub.ProController {
 public static class Joystick {
  [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] public struct Caps {
   public ushort Mid,Pid;
   [MarshalAs(UnmanagedType.ByValTStr,SizeConst=32)] public string Name;
   public uint XMin,XMax,YMin,YMax,ZMin,ZMax,NumButtons,PeriodMin,PeriodMax,RMin,RMax,UMin,UMax,VMin,VMax,Flags,MaxAxes,NumAxes,MaxButtons;
   [MarshalAs(UnmanagedType.ByValTStr,SizeConst=32)] public string RegKey;
   [MarshalAs(UnmanagedType.ByValTStr,SizeConst=260)] public string OemDriver;
  }
  [StructLayout(LayoutKind.Sequential)] public struct State {
   public uint Size,Flags,X,Y,Z,R,U,V,Buttons,ButtonNumber,Pov,Reserved1,Reserved2;
  }
  public sealed class Device {
   public uint Id;public Caps Capabilities;public string Name;
   public override string ToString(){return Name;}
  }
  [DllImport("winmm.dll")] static extern uint joyGetNumDevs();
  [DllImport("winmm.dll",CharSet=CharSet.Unicode)] public static extern uint joyGetDevCapsW(UIntPtr id,out Caps caps,uint size);
  [DllImport("winmm.dll")] static extern uint joyGetPosEx(uint id,ref State state);
  public static bool Read(uint id,out State state){state=new State();state.Size=(uint)Marshal.SizeOf(typeof(State));state.Flags=255;return joyGetPosEx(id,ref state)==0;}
  public static bool IsXbox(Caps caps){return caps.Mid==0x045e&&(caps.Pid==0x028e||caps.Pid==0x02a1);}
  public static Device[] List(){
   List<Device> devices=new List<Device>();
   for(uint id=0;id<joyGetNumDevs();id++){
    Caps caps;State state;if(joyGetDevCapsW((UIntPtr)id,out caps,(uint)Marshal.SizeOf(typeof(Caps)))!=0||!Read(id,out state)||IsXbox(caps))continue;
    string name=caps.Name,key=String.Format("VID_{0:X4}&PID_{1:X4}",caps.Mid,caps.Pid);
    foreach(RegistryKey hive in new RegistryKey[]{Registry.CurrentUser,Registry.LocalMachine}){
     using(RegistryKey entry=hive.OpenSubKey("System\\CurrentControlSet\\Control\\MediaProperties\\PrivateProperties\\Joystick\\OEM\\"+key)){
      if(entry!=null&&entry.GetValue("OEMName") is string){name=(string)entry.GetValue("OEMName");break;}
     }
    }
    devices.Add(new Device{Id=id,Capabilities=caps,Name=name});
   }return devices.ToArray();
  }
 }
 [StructLayout(LayoutKind.Sequential)] public struct XboxReport {
  public ushort Buttons;public byte LeftTrigger,RightTrigger;public short LeftX,LeftY,RightX,RightY;
 }
 public static class Mapper {
  public static short Axis(uint value,uint min,uint max,bool invert){
   if(max<=min)return 0;
   double normalized=Math.Max(-1,Math.Min(1,2.0*(Math.Max(min,Math.Min(max,value))-min)/(max-min)-1));
   if(Math.Abs(normalized)<0.002)return 0;if(invert)normalized=-normalized;
   return (short)Math.Round(normalized*(normalized<0?32768:32767));
  }
  public static XboxReport Convert(Joystick.State state,Joystick.Caps caps){
   XboxReport r=new XboxReport();
   int[] bits={0x1000,0x2000,0x4000,0x8000,0x0100,0x0200,0,0,0x0020,0x0010,0x0400,0,0x0040,0x0080};
   for(int i=0;i<bits.Length;i++)if((state.Buttons&(1u<<i))!=0)r.Buttons|=(ushort)bits[i];
   r.LeftTrigger=(byte)((state.Buttons&(1u<<6))!=0?255:0);r.RightTrigger=(byte)((state.Buttons&(1u<<7))!=0?255:0);
   if(state.Pov<=35999){int direction=(int)((state.Pov+2250)/4500)%8;int[] hats={1,9,8,10,2,6,4,5};r.Buttons|=(ushort)hats[direction];}
   r.LeftX=Axis(state.X,caps.XMin,caps.XMax,false);r.LeftY=Axis(state.Y,caps.YMin,caps.YMax,true);
   // The Android descriptor exposes X,Y,Rx,Ry, which WinMM reports as X,Y,R,U.
   if((caps.Flags&6)==6){r.RightX=Axis(state.R,caps.RMin,caps.RMax,false);r.RightY=Axis(state.U,caps.UMin,caps.UMax,true);}
   else if((caps.Flags&3)==3){r.RightX=Axis(state.Z,caps.ZMin,caps.ZMax,false);r.RightY=Axis(state.R,caps.RMin,caps.RMax,true);}
   else throw new InvalidOperationException("O controle não oferece os quatro eixos esperados. Selecione o gamepad Nördhub/SwitchPhone.");
   return r;
  }
 }
 public interface IXboxTarget:IDisposable { uint Index {get;} void Send(XboxReport report); }
 public interface IJoystickReader { bool Read(uint id,out Joystick.Caps caps,out Joystick.State state); Joystick.Device[] List(); }
 public sealed class WinMMReader:IJoystickReader {
  public bool Read(uint id,out Joystick.Caps caps,out Joystick.State state){state=new Joystick.State();return Joystick.joyGetDevCapsW((UIntPtr)id,out caps,(uint)Marshal.SizeOf(typeof(Joystick.Caps)))==0&&Joystick.Read(id,out state);}
  public Joystick.Device[] List(){return Joystick.List();}
 }
 public sealed class VirtualXbox:IXboxTarget {
  [UnmanagedFunctionPointer(CallingConvention.StdCall)]delegate void Notification(IntPtr client,IntPtr target,byte large,byte small,byte led,IntPtr user);
  [DllImport("ViGEmClient.dll",CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_target_x360_register_notification(IntPtr client,IntPtr target,Notification callback,IntPtr user);
  [DllImport("ViGEmClient.dll",CallingConvention=CallingConvention.Cdecl)]static extern void vigem_target_x360_unregister_notification(IntPtr target);
  readonly object feedbackLock=new object();byte largeMotor,smallMotor;Notification notification;bool feedbackRegistered;
  public void EnableFeedback(){if(feedbackRegistered)return;notification=(c,t,l,s,led,u)=>{lock(feedbackLock){largeMotor=l;smallMotor=s;}};Check(vigem_target_x360_register_notification(client,target,notification,IntPtr.Zero));feedbackRegistered=true;}
  public byte[] Feedback(){lock(feedbackLock)return new[]{largeMotor,smallMotor};}
  const string Library="ViGEmClient.dll";
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern IntPtr LoadLibraryEx(string path,IntPtr file,uint flags);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern IntPtr vigem_alloc();
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern void vigem_free(IntPtr c);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_connect(IntPtr c);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern void vigem_disconnect(IntPtr c);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern IntPtr vigem_target_x360_alloc();
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern void vigem_target_free(IntPtr target);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_target_add(IntPtr c,IntPtr target);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_target_remove(IntPtr c,IntPtr target);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_target_x360_update(IntPtr c,IntPtr target,XboxReport report);
  [DllImport(Library,CallingConvention=CallingConvention.Cdecl)]static extern uint vigem_target_x360_get_user_index(IntPtr c,IntPtr target,out uint index);
  [StructLayout(LayoutKind.Sequential)] public struct XboxState {public uint Packet;public XboxReport Gamepad;}
  [DllImport("xinput1_4.dll")]public static extern uint XInputGetState(uint index,out XboxState state);
  IntPtr client,target;bool attached;
  public uint Index {get;private set;}
  static void Check(uint error){if(error!=0x20000000)throw new InvalidOperationException(error==0xE0000001?"Driver ViGEmBus não encontrado. Este auxiliar precisa de um driver compatível já instalado; nenhum driver foi instalado automaticamente.":"Falha no controle virtual: 0x"+error.ToString("X8"));}
  public VirtualXbox(){
   string file=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,Library);
   if(!File.Exists(file))throw new FileNotFoundException("Extraia o ZIP completo antes de abrir o auxiliar.",Library);
   using(SHA256 sha=SHA256.Create())using(FileStream stream=File.OpenRead(file)){
    string hash=BitConverter.ToString(sha.ComputeHash(stream)).Replace("-","").ToLowerInvariant();
    if(hash!="00303f99e1968d523fbd08a7c691d60d145b7335059ec593947841d124c10875")throw new InvalidOperationException("A biblioteca do controle virtual não corresponde ao pacote validado.");
   }
   if(LoadLibraryEx(file,IntPtr.Zero,8)==IntPtr.Zero)throw new InvalidOperationException("Não foi possível carregar a biblioteca x64 do controle virtual.");
   try{client=vigem_alloc();if(client==IntPtr.Zero)throw new OutOfMemoryException();Check(vigem_connect(client));target=vigem_target_x360_alloc();if(target==IntPtr.Zero)throw new OutOfMemoryException();Check(vigem_target_add(client,target));attached=true;uint index;Check(vigem_target_x360_get_user_index(client,target,out index));Index=index;
    XboxState state;uint code=XInputGetState(Index,out state);
    for(int attempt=0;code!=0&&attempt<60;attempt++){System.Threading.Thread.Sleep(100);code=XInputGetState(Index,out state);}
    if(code!=0)throw new InvalidOperationException("O driver criou o dispositivo, mas o Windows não o disponibilizou por XInput.");
    // ViGEm's XUSB init blob contains nonzero stick samples while its report cache is zero.
    // An exact-zero first report is discarded as unchanged. A one-unit axis sample (below
    // the standard dead zone) followed by zero flushes that startup sample without buttons.
    XboxReport seed=new XboxReport();seed.LeftX=1;
    for(int attempt=0;attempt<3;attempt++){Check(vigem_target_x360_update(client,target,seed));System.Threading.Thread.Sleep(16);Check(vigem_target_x360_update(client,target,new XboxReport()));System.Threading.Thread.Sleep(16);}
   }
   catch{Dispose();throw;}
  }
  public void Send(XboxReport report){if(!attached)throw new ObjectDisposedException("VirtualXbox");Check(vigem_target_x360_update(client,target,report));}
  public void Dispose(){
   if(feedbackRegistered){vigem_target_x360_unregister_notification(target);feedbackRegistered=false;}
   if(attached){vigem_target_x360_update(client,target,new XboxReport());vigem_target_remove(client,target);attached=false;}
   if(target!=IntPtr.Zero){vigem_target_free(target);target=IntPtr.Zero;}
   if(client!=IntPtr.Zero){vigem_disconnect(client);vigem_free(client);client=IntPtr.Zero;}
  }
 }
 public sealed class Bridge:IDisposable {
  public readonly Joystick.Device Source;readonly IXboxTarget xbox;readonly IJoystickReader reader;public long Reports;public uint XboxIndex {get{return xbox.Index;}}
  public bool SourceAvailable {get;private set;} public string SourceStatus {get;private set;}
  bool disposed;DateTime nextLookup;
  public XboxReport Last {get;private set;}
  public Bridge(Joystick.Device source):this(source,new WinMMReader(),new VirtualXbox()){}
  public Bridge(Joystick.Device source,IJoystickReader reader,IXboxTarget xbox){Source=source;this.reader=reader;this.xbox=xbox;}
  bool Matches(Joystick.Caps caps){return caps.Mid==Source.Capabilities.Mid&&caps.Pid==Source.Capabilities.Pid&&!Joystick.IsXbox(caps);}
  public void Tick(){
   if(disposed)throw new ObjectDisposedException("Bridge");
   Joystick.Caps current;Joystick.State state;
   bool same=reader.Read(Source.Id,out current,out state)&&Matches(current);
   if(!same&&DateTime.UtcNow>=nextLookup){
    nextLookup=DateTime.UtcNow.AddSeconds(1);Joystick.Device found=null;bool ambiguous=false;
    foreach(Joystick.Device device in reader.List())if(Matches(device.Capabilities)&&device.Name==Source.Name){if(found!=null){ambiguous=true;break;}found=device;}
    if(found!=null&&!ambiguous){Source.Id=found.Id;same=reader.Read(Source.Id,out current,out state)&&Matches(current);}
    SourceStatus=ambiguous?"Há mais de um celular correspondente. Desconecte o adicional e selecione novamente.":"Celular indisponível. Xbox preservado em neutro; aguardando reconexão.";
   }
   SourceAvailable=same;
   if(same){Source.Capabilities=current;SourceStatus="Celular conectado; comandos encaminhados.";nextLookup=DateTime.MinValue;}
   XboxReport report=same?Mapper.Convert(state,current):new XboxReport();xbox.Send(report);Last=report;Reports++;
  }
  public void Dispose(){if(disposed)return;disposed=true;xbox.Dispose();SourceAvailable=false;}
 }
}
