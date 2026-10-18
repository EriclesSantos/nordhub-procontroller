using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using Nordhub.ProController;

class WireChecks {
 static int checks;
 static void Check(bool ok,string message){checks++;if(!ok)throw new Exception(message);}
 sealed class FakeXbox:IXboxTarget {public int Sends,Disposals;public XboxReport Last;public uint Index{get{return 0;}}public void Send(XboxReport r){Sends++;Last=r;}public void Dispose(){Disposals++;}}
 static byte[] Frame(){byte[] bytes=new byte[64];bytes[0]=1;bytes[2]=8;bytes[12]=64;bytes[13]=191;bytes[48]=85;return bytes;}
 static byte[] Session(int seed){byte[] bytes=new byte[16];for(int i=0;i<16;i++)bytes[i]=(byte)(i+seed);return bytes;}
 static WirePacket.Decoded Input(byte[] session,ulong sequence,byte[] bytes){return new WirePacket.Decoded{Type=1,Session=session,Sequence=sequence,Payload=bytes};}
 static void Crypto(){
  WirePacket codec=new WirePacket("00112233445566778899"),wrong=new WirePacket("00112233445566778898");byte[] session=Session(16);
  for(int n=0;n<=128;n++){byte[] payload=new byte[n];new Random(n).NextBytes(payload);byte[] packet=codec.Encode(1,session,(ulong)n+1,payload);WirePacket.Decoded result=codec.Decode(packet);Check(result!=null&&result.Sequence==(ulong)n+1&&WirePacket.Equal(result.Payload,payload)&&WirePacket.Equal(result.Session,session),"roundtrip "+n);Check(wrong.Decode(packet)==null,"wrong code "+n);}
  byte[] good=codec.Encode(1,session,1,Frame());for(int i=0;i<good.Length;i++){byte[] altered=(byte[])good.Clone();altered[i]^=1;Check(codec.Decode(altered)==null,"tamper "+i);}
  for(int n=0;n<good.Length;n++){byte[] shortPacket=new byte[n];Buffer.BlockCopy(good,0,shortPacket,0,n);Check(codec.Decode(shortPacket)==null,"truncated "+n);}
  Check(codec.Decode(new byte[256])==null,"oversized");Check(!WirePacket.Equal(codec.Encode(1,session,1,Frame()),codec.Encode(1,session,1,Frame())),"fresh IV");
  string a=WirePacket.NewCode(),b=WirePacket.NewCode();Check(a.Length==24&&a!=b,"fresh 80-bit code per server");
 }
 static void FramesAndPeers(){
  byte[] data=Frame();ControlFrame frame=ControlFrame.Decode(data);XboxReport report=frame.Xbox();Check(report.Buttons==0x1000&&report.LeftTrigger==64&&report.RightTrigger==191,"gradual triggers with Xbox output");
  for(int i=0;i<14;i++){byte[] bytes=Frame();bytes[0]=0;Buffer.BlockCopy(BitConverter.GetBytes((ushort)(1<<i)),0,bytes,0,2);int[] expected={0x1000,0x2000,0x4000,0x8000,0x100,0x200,0,0,0x20,0x10,0x400,0,0x40,0x80};Check(ControlFrame.Decode(bytes).Xbox().Buttons==expected[i],"logical button "+i);}
  int[] hats={1,9,8,10,2,6,4,5,0};for(int i=0;i<9;i++){byte[] bytes=Frame();bytes[0]=0;bytes[2]=(byte)i;Check(ControlFrame.Decode(bytes).Xbox().Buttons==hats[i],"hat "+i);}
  for(int i=50;i<64;i++){byte[] bytes=Frame();bytes[i]=1;Check(ControlFrame.Decode(bytes)==null,"reserved "+i);}
  foreach(int at in new[]{3,15,22,23}){byte[] bytes=Frame();bytes[at]=1;Check(ControlFrame.Decode(bytes)==null,"reserved "+at);}
  foreach(int at in new[]{24,28,32,36,40,44}){byte[] bytes=Frame();Buffer.BlockCopy(BitConverter.GetBytes(float.NaN),0,bytes,at,4);Check(ControlFrame.Decode(bytes)==null,"nonfinite motion "+at);}
  byte[] bad=Frame();bad[2]=9;Check(ControlFrame.Decode(bad)==null,"hat bound");bad=Frame();bad[1]=128;Check(ControlFrame.Decode(bad)==null,"button bound");bad=Frame();bad[49]=2;Check(ControlFrame.Decode(bad)==null,"motion flags");bad=Frame();bad[48]=101;Check(ControlFrame.Decode(bad)==null,"battery bound");
  bad=Frame();Buffer.BlockCopy(BitConverter.GetBytes(short.MinValue),0,bad,6,2);Check(ControlFrame.Decode(bad).Xbox().LeftY==short.MaxValue,"Y saturation");
  PeerState peer=new PeerState();byte[] s=Session(16),other=Session(17);Check(!peer.Connected(0),"initial disconnected");Check(peer.Accept(Input(s,1,data),"phone:1",100),"first valid peer");Check(peer.Connected(350)&&!peer.Connected(351),"250ms deadman");Check(!peer.Accept(Input(s,1,data),"phone:1",360),"replay rejected");Check(peer.Accept(Input(s,3,data),"phone:1",370),"sequence gap accepted");Check(!peer.Accept(Input(s,2,data),"phone:1",380),"out of order rejected");Check(!peer.Accept(Input(other,1,data),"phone:1",400),"active session protected");Check(!peer.Accept(Input(s,4,data),"other:1",400),"active endpoint protected");bad=Frame();bad[50]=1;Check(!peer.Accept(Input(s,4,bad),"phone:1",400)&&!peer.Connected(621),"invalid frame does not refresh liveness");
  Check(!peer.Accept(Input(other,1,data),"phone:2",5369),"peer replacement waits 5 seconds");Check(peer.Accept(Input(other,1,data),"phone:2",5400),"new session after inactivity");Check(!peer.Accept(new WirePacket.Decoded{Type=2,Session=other,Sequence=2,Payload=data},"phone:2",5410),"feedback never input");
  Check(WifiReceiver.Local(IPAddress.Parse("192.168.1.3"))&&WifiReceiver.Local(IPAddress.Parse("172.31.0.1"))&&WifiReceiver.Local(IPAddress.Loopback),"local IPv4 accepted");Check(!WifiReceiver.Local(IPAddress.Parse("8.8.8.8"))&&!WifiReceiver.Local(IPAddress.Parse("172.32.0.1"))&&!WifiReceiver.Local(IPAddress.IPv6Loopback),"public and IPv6 rejected");
 }
 static void Udp(){
  FakeXbox target=new FakeXbox();using(WifiReceiver receiver=new WifiReceiver(0,target,IPAddress.Loopback))using(UdpClient client=new UdpClient(new IPEndPoint(IPAddress.Loopback,0))){
   Check(!receiver.AllowMouse,"mouse starts disabled");WirePacket codec=new WirePacket(receiver.Code);byte[] s=Session(16),bytes=codec.Encode(1,s,1,Frame());IPEndPoint destination=new IPEndPoint(IPAddress.Loopback,receiver.LocalPort);client.Send(new byte[0],0,destination);client.Send(new byte[240],240,destination);client.Send(new byte[32],32,destination);client.Send(bytes,bytes.Length,destination);Thread.Sleep(20);receiver.Tick();Check(receiver.Reports==1&&receiver.Connected&&target.Last.Buttons==0x1000&&target.Last.LeftTrigger==64,"real loopback datagram reaches fake Xbox: reports="+receiver.Reports+", connected="+receiver.Connected+", buttons="+target.Last.Buttons+", LT="+target.Last.LeftTrigger);
   client.Send(bytes,bytes.Length,destination);Thread.Sleep(20);receiver.Tick();Check(receiver.Reports==1,"UDP replay rejected");Thread.Sleep(20);receiver.Tick();client.Client.ReceiveTimeout=500;IPEndPoint from=new IPEndPoint(IPAddress.Loopback,0);WirePacket.Decoded reply=codec.Decode(client.Receive(ref from));Check(reply!=null&&reply.Type==2&&reply.Payload.Length==8&&WirePacket.Equal(reply.Session,s)&&reply.Payload[2]==78&&reply.Payload[3]==72&&reply.Payload[5]==0&&reply.Payload[6]==1,"authenticated acknowledgement, feature version and mouse permission");
   Thread.Sleep(270);receiver.Tick();Check(!receiver.Connected&&target.Last.Equals(new XboxReport())&&target.Disposals==0,"lost Wi-Fi releases all inputs without removing Xbox");bytes=codec.Encode(1,s,2,Frame());client.Send(bytes,bytes.Length,destination);Thread.Sleep(20);receiver.Tick();Check(receiver.Connected&&receiver.Reports==2,"same session resumes after lost packets");}
  Check(target.Disposals==1,"manual dispose removes target once");
 }
 static void Metadata(){
  FakeXbox target=new FakeXbox();using(WifiReceiver receiver=new WifiReceiver(0,target,IPAddress.Loopback))using(UdpClient phone=new UdpClient(new IPEndPoint(IPAddress.Loopback,0)))using(UdpClient stranger=new UdpClient(new IPEndPoint(IPAddress.Loopback,0))){
   WirePacket codec=new WirePacket(receiver.Code);byte[] s=Session(16);IPEndPoint destination=new IPEndPoint(IPAddress.Loopback,receiver.LocalPort);
   Action<UdpClient,byte,byte[],ulong,byte[]> send=(client,type,session,sequence,payload)=>{byte[] bytes=codec.Encode(type,session,sequence,payload);client.Send(bytes,bytes.Length,destination);Thread.Sleep(10);receiver.Tick();};
   byte[] name=System.Text.Encoding.UTF8.GetBytes("\u0001Xiaomi Teste");
   send(phone,4,s,2,name);Check(receiver.DeviceName=="Celular"&&!receiver.Connected,"metadata cannot establish connection");
   send(phone,1,s,1,Frame());send(phone,4,s,2,name);Check(receiver.DeviceName=="Xiaomi Teste"&&receiver.Reports==1,"authenticated name without input report");
   byte[] other=System.Text.Encoding.UTF8.GetBytes("\u0001Outro");send(stranger,4,s,3,other);Check(receiver.DeviceName=="Xiaomi Teste","metadata endpoint bound");
   send(phone,4,Session(17),3,other);Check(receiver.DeviceName=="Xiaomi Teste","metadata session bound");
   send(phone,4,s,2,other);Check(receiver.DeviceName=="Xiaomi Teste","metadata replay rejected");
   send(phone,4,s,3,new byte[]{1,0xc3,0x28});send(phone,4,s,4,new byte[]{1,65,10,66});send(phone,4,s,5,new byte[98]);Check(receiver.DeviceName=="Xiaomi Teste","malformed names ignored");
   Thread.Sleep(270);send(phone,4,s,6,other);Check(!receiver.Connected&&receiver.DeviceName=="Xiaomi Teste"&&target.Last.Equals(new XboxReport()),"metadata cannot extend input liveness");
   send(phone,1,s,7,Frame());send(phone,4,s,8,other);Check(receiver.Connected&&receiver.DeviceName=="Outro","resumed input allows current metadata");
  }
 }
 static byte[] Request(uint type,byte[] payload){byte[] bytes=new byte[20+payload.Length];Buffer.BlockCopy(new byte[]{68,83,85,67},0,bytes,0,4);Buffer.BlockCopy(BitConverter.GetBytes((ushort)1001),0,bytes,4,2);Buffer.BlockCopy(BitConverter.GetBytes((ushort)(bytes.Length-16)),0,bytes,6,2);Buffer.BlockCopy(BitConverter.GetBytes(type),0,bytes,16,4);Buffer.BlockCopy(payload,0,bytes,20,payload.Length);Buffer.BlockCopy(BitConverter.GetBytes(DsuServer.Crc(bytes)),0,bytes,8,4);return bytes;}
 static void Dsu(){
  ControlFrame frame=ControlFrame.Decode(Frame());frame.MotionAvailable=true;frame.Motion[0]=1;frame.Motion[5]=9.80665f;byte[] bytes=DsuServer.Data(frame,7,123456);
  Check(bytes.Length==100&&BitConverter.ToUInt32(bytes,0)==0x53555344&&BitConverter.ToUInt16(bytes,6)==84,"DSU envelope");Check(BitConverter.ToUInt32(bytes,8)==DsuServer.Crc(bytes),"DSU checksum");Check(bytes[21]==2&&bytes[22]==2&&bytes[31]==1,"DSU active full-gyro device");Check(BitConverter.ToUInt64(bytes,68)==123456&&BitConverter.ToUInt32(bytes,32)==7,"DSU sequence timestamp");Check(Math.Abs(BitConverter.ToSingle(bytes,88)-57.29578)<.0001&&Math.Abs(BitConverter.ToSingle(bytes,84)-1)<.0001,"gyro degrees and acceleration g");Check(bytes[37]==35&&bytes[54]==191&&bytes[55]==64,"DSU south and triggers");
  byte[] request=Request(0x100002,new byte[8]);Check(DsuServer.Valid(request),"valid DSU subscription");request[22]^=1;Check(!DsuServer.Valid(request),"DSU corruption rejected");
  using(DsuServer server=new DsuServer(0))using(UdpClient client=new UdpClient(new IPEndPoint(IPAddress.Loopback,0))){request=Request(0x100002,new byte[8]);client.Send(request,request.Length,new IPEndPoint(IPAddress.Loopback,server.LocalPort));Thread.Sleep(20);server.Tick(frame,100);client.Client.ReceiveTimeout=500;IPEndPoint from=new IPEndPoint(IPAddress.Loopback,0);bytes=client.Receive(ref from);Check(bytes.Length==100&&BitConverter.ToUInt32(bytes,8)==DsuServer.Crc(bytes),"real loopback DSU subscription receives motion");}
 }
 static void Interop(string input,string output){WirePacket codec=new WirePacket("00112233445566778899");byte[] java=File.ReadAllBytes(input),payload=new byte[64],iv=new byte[16];for(int i=0;i<64;i++)payload[i]=(byte)i;for(int i=0;i<16;i++)iv[i]=(byte)i;byte[] encoded=codec.Encode(1,Session(16),0x0102030405060708UL,payload,iv);Check(WirePacket.Equal(encoded,java),"Java and C# exact encrypted vector");WirePacket.Decoded d=codec.Decode(java);Check(d!=null&&d.Sequence==0x0102030405060708UL&&WirePacket.Equal(payload,d.Payload),"Java accepted by C#");File.WriteAllBytes(output,codec.Encode(2,Session(16),0x0102030405060709UL,new byte[]{160,80,78,72,0,0,1,0},iv));
  WirePacket.Decoded packet=codec.Decode(File.ReadAllBytes(input+".frame"));ControlFrame frame=ControlFrame.Decode(packet.Payload);XboxReport r=frame.Xbox();Check(frame.Buttons==0x30c1&&frame.Hat==1&&r.Buttons==0x10c9,"Java buttons and hat reach XInput");Check(r.LeftTrigger==64&&r.RightTrigger==191&&r.LeftX==9830&&r.LeftY==22937&&r.RightX==8192&&r.RightY==16383,"Java native axis and trigger resolution reaches XInput");Check(frame.MouseX==12&&frame.MouseY==-7&&frame.Wheel==120&&frame.Battery==85&&frame.MotionAvailable&&frame.Motion[0]==.5f,"Java pointer, battery and sensors decoded by C#");}
 static int Main(string[] args){try{Crypto();FramesAndPeers();Udp();Metadata();Dsu();if(args.Length==2)Interop(args[0],args[1]);Console.WriteLine("Wi-Fi/DSU checks: "+checks+" (fake Xbox; loopback only; no desktop input injected)");return 0;}catch(Exception ex){Console.Error.WriteLine(ex.Message);return 1;}}
}
