using System;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Nordhub.ProController {
 public sealed class MouseOutput:IDisposable {
  [StructLayout(LayoutKind.Sequential)]struct MouseData {public int X,Y;public uint Data,Flags,Time;public UIntPtr Extra;}
  [StructLayout(LayoutKind.Explicit,Size=40)]struct Input {[FieldOffset(0)]public uint Type;[FieldOffset(8)]public MouseData Mouse;}
  [DllImport("user32.dll",SetLastError=true)]static extern uint SendInput(uint count,Input[] input,int size);
  byte buttons;bool enabled;
  public bool Enabled{get{return enabled;}set{if(!value)Release();enabled=value;}}
  static void Send(uint flags,int x,int y,uint data){Input i=new Input{Mouse=new MouseData{X=x,Y=y,Data=data,Flags=flags}};if(SendInput(1,new[]{i},40)!=1)throw new InvalidOperationException("O Windows bloqueou a entrada de mouse. Confira se o jogo e o auxiliar usam o mesmo nível de privilégio.");}
  public void Apply(ControlFrame frame){if(!enabled)return;if(frame.MouseX!=0||frame.MouseY!=0)Send(1,frame.MouseX,frame.MouseY,0);if(frame.Wheel!=0)Send(0x800,0,0,unchecked((uint)(int)frame.Wheel));SetButtons(frame.MouseButtons);}
  void SetButtons(byte next){for(int i=0;i<2;i++)if(((buttons^next)&(1<<i))!=0){Send(i==0?((next&1)!=0?2u:4u):((next&2)!=0?8u:16u),0,0,0);buttons=(byte)((buttons&~(1<<i))|(next&(1<<i)));}}
  public void Release(){if(buttons!=0)SetButtons(0);}
  public void Dispose(){Release();}
 }
 public sealed class WifiReceiver:IDisposable {
  readonly UdpClient udp;readonly WirePacket codec;readonly IXboxTarget xbox;readonly MouseOutput mouse=new MouseOutput();readonly Stopwatch clock=Stopwatch.StartNew();readonly PeerState peer=new PeerState();
  IPEndPoint remote;long feedbackAt;ulong feedbackSequence,metadataSequence;DsuServer dsu;bool disposed;
  public string DeviceName {get;private set;}public string Code {get;private set;}public string MotionStatus {get;private set;}public string Error {get;private set;}public long Reports {get;private set;}
  public int LocalPort{get{return ((IPEndPoint)udp.Client.LocalEndPoint).Port;}}
  public bool Connected{get{return peer.Connected(clock.ElapsedMilliseconds);}}public uint XboxIndex {get{return xbox.Index;}}
  public XboxReport Last{get;private set;}public bool AllowMouse {get{return mouse.Enabled;}set{mouse.Enabled=value;}}
  public WifiReceiver(int port):this(port,new VirtualXbox()){}
  public WifiReceiver(int port,IXboxTarget target):this(port,target,IPAddress.Any){}
  public WifiReceiver(int port,IXboxTarget target,IPAddress address){
   xbox=target;DeviceName="Celular";try{Code=WirePacket.NewCode();codec=new WirePacket(Code);udp=new UdpClient(new IPEndPoint(address,port));udp.Client.IOControl((IOControlCode)unchecked((int)0x9800000c),new byte[]{0,0,0,0},null);udp.Client.Blocking=false;udp.Client.ReceiveBufferSize=65536;VirtualXbox real=target as VirtualXbox;if(real!=null)real.EnableFeedback();MotionStatus="DSU desativado";}
   catch{if(udp!=null)udp.Close();target.Dispose();throw;}
  }
  public void EnableMotion(bool enable){if(dsu!=null){dsu.Dispose();dsu=null;}if(enable){try{dsu=new DsuServer();MotionStatus="Movimento DSU: 127.0.0.1:26760";}catch(SocketException){MotionStatus="Porta DSU 26760 ocupada; movimento nativo indisponível.";}}else MotionStatus="DSU desativado";}
  public static bool Local(IPAddress address){byte[] b=address.GetAddressBytes();return b.Length==4&&(b[0]==127||b[0]==10||b[0]==192&&b[1]==168||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==169&&b[1]==254);}
  public void Tick(){
   if(disposed)return;long now=clock.ElapsedMilliseconds;
   for(int i=0;i<64&&udp.Client.Poll(0,SelectMode.SelectRead);i++){IPEndPoint from=new IPEndPoint(IPAddress.Any,0);byte[] bytes=udp.Receive(ref from);if(bytes.Length>224||!Local(from.Address))continue;WirePacket.Decoded p=codec.Decode(bytes);if(p!=null&&p.Type==4){if(peer.OwnsSession(p,from.ToString(),now)&&p.Sequence>metadataSequence&&p.Payload.Length>1&&p.Payload.Length<=97&&p.Payload[0]==1){try{string name=new System.Text.UTF8Encoding(false,true).GetString(p.Payload,1,p.Payload.Length-1).Trim();bool valid=name.Length>0&&name.Length<=96;foreach(char value in name)if(char.IsControl(value))valid=false;if(valid){DeviceName=name;metadataSequence=p.Sequence;}}catch(System.Text.DecoderFallbackException){}}continue;}byte[] previousSession=peer.Session;if(peer.Accept(p,from.ToString(),now)){if(!WirePacket.Equal(previousSession,p.Session)){DeviceName="Celular";metadataSequence=0;}remote=from;Reports++;if(mouse.Enabled)mouse.Apply(peer.Frame);}}
   Last=peer.Connected(now)?peer.Frame.Xbox():new XboxReport();xbox.Send(Last);if(!peer.Connected(now))mouse.Release();
   if(dsu!=null)dsu.Tick(peer.Connected(now)?peer.Frame:null,now);
   if(remote!=null&&peer.Connected(now)&&now-feedbackAt>=50){feedbackAt=now;byte[] payload=new byte[8];VirtualXbox target=xbox as VirtualXbox;if(target!=null){byte[] rumble=target.Feedback();payload[0]=rumble[0];payload[1]=rumble[1];}payload[2]=78;payload[3]=72;payload[6]=1;payload[4]=(byte)xbox.Index;payload[5]=(byte)(mouse.Enabled?1:0);byte[] response=codec.Encode(2,peer.Session,++feedbackSequence,payload);udp.Send(response,response.Length,remote);}
  }
  public void Dispose(){if(disposed)return;disposed=true;try{mouse.Dispose();}finally{if(dsu!=null)dsu.Dispose();udp.Close();xbox.Dispose();}}
 }
}
