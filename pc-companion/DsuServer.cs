using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Collections.Generic;

namespace Nordhub.ProController {
 /** Original encoder from published DSU packet facts; loopback only, one virtual slot. */
 public sealed class DsuServer:IDisposable {
  readonly UdpClient udp;
  readonly Dictionary<string,Subscriber> clients=new Dictionary<string,Subscriber>();uint packet;
  sealed class Subscriber {public IPEndPoint Endpoint;public long At;}
  static readonly byte[] VirtualId={2,78,72,80,0,1};
  public DsuServer():this(26760){}
  public DsuServer(int port){udp=new UdpClient(new IPEndPoint(IPAddress.Loopback,port));udp.Client.IOControl((IOControlCode)unchecked((int)0x9800000c),new byte[]{0,0,0,0},null);udp.Client.Blocking=false;}
  public int LocalPort{get{return ((IPEndPoint)udp.Client.LocalEndPoint).Port;}}
  public static uint Crc(byte[] bytes){uint crc=0xffffffff;for(int i=0;i<bytes.Length;i++){byte value=i>=8&&i<12?(byte)0:bytes[i];crc^=value;for(int bit=0;bit<8;bit++)crc=(crc>>1)^((crc&1)!=0?0xedb88320u:0);}return ~crc;}
  public static bool Valid(byte[] bytes){return bytes!=null&&bytes.Length>=20&&bytes.Length<=64&&BitConverter.ToUInt32(bytes,0)==0x43555344&&BitConverter.ToUInt16(bytes,4)==1001&&BitConverter.ToUInt16(bytes,6)==bytes.Length-16&&BitConverter.ToUInt32(bytes,8)==Crc(bytes);}
  static byte[] Wrap(uint type,byte[] payload){byte[] bytes=new byte[20+payload.Length];using(BinaryWriter writer=new BinaryWriter(new MemoryStream(bytes))){writer.Write(new byte[]{68,83,85,83});writer.Write((ushort)1001);writer.Write((ushort)(bytes.Length-16));writer.Write(0u);writer.Write(0x4e485031u);writer.Write(type);writer.Write(payload);}Buffer.BlockCopy(BitConverter.GetBytes(Crc(bytes)),0,bytes,8,4);return bytes;}
  static byte[] Info(ControlFrame frame,byte slot,int length){byte[] b=new byte[length];b[0]=slot;if(slot==0&&frame!=null){b[1]=2;b[2]=(byte)(frame.MotionAvailable?2:1);b[3]=0;Buffer.BlockCopy(VirtualId,0,b,4,6);b[10]=(byte)(frame.Battery>=95?5:frame.Battery>=70?4:frame.Battery>=35?3:frame.Battery>=10?2:1);}return b;}
  public static byte[] Data(ControlFrame frame,uint packet,long micros){
   byte[] payload=Info(frame,0,80);if(frame==null)return Wrap(0x100002,payload);payload[11]=1;Buffer.BlockCopy(BitConverter.GetBytes(packet),0,payload,12,4);XboxReport r=frame.Xbox();
   int[] src1={2,1,3,0,4,7,6,5};for(int i=0;i<8;i++)if((r.Buttons&(1<<src1[i]))!=0)payload[16]|=(byte)(1<<(7-i));
   int[] xbits={0x8000,0x2000,0x1000,0x4000,0x200,0x100};for(int i=0;i<6;i++)if((r.Buttons&xbits[i])!=0)payload[17]|=(byte)(1<<(7-i));if(r.RightTrigger>0)payload[17]|=2;if(r.LeftTrigger>0)payload[17]|=1;
   payload[18]=(byte)((frame.Buttons&(1<<10))!=0?1:0);payload[20]=Stick(r.LeftX);payload[21]=Stick(r.LeftY);payload[22]=Stick(r.RightX);payload[23]=Stick(r.RightY);
   int[] dpad={4,2,8,1};for(int i=0;i<4;i++)payload[24+i]=(byte)((r.Buttons&dpad[i])!=0?255:0);for(int i=0;i<6;i++)payload[28+i]=(byte)((r.Buttons&xbits[i])!=0?255:0);payload[34]=r.RightTrigger;payload[35]=r.LeftTrigger;
   Buffer.BlockCopy(BitConverter.GetBytes((ulong)Math.Max(0,micros)),0,payload,48,8);
   for(int i=0;i<3;i++){Buffer.BlockCopy(BitConverter.GetBytes(frame.Motion[i+3]/9.80665f),0,payload,56+i*4,4);Buffer.BlockCopy(BitConverter.GetBytes(frame.Motion[i]*57.2957795f),0,payload,68+i*4,4);}return Wrap(0x100002,payload);
  }
  static byte Stick(short axis){return (byte)Math.Max(0,Math.Min(255,Math.Round((axis+32768)/257.0)));}
  public void Tick(ControlFrame frame,long now){
   for(int i=0;i<16&&udp.Client.Poll(0,SelectMode.SelectRead);i++){IPEndPoint remote=new IPEndPoint(IPAddress.Loopback,0);byte[] request=udp.Receive(ref remote);if(!IPAddress.IsLoopback(remote.Address)||!Valid(request))continue;uint type=BitConverter.ToUInt32(request,16);
    if(type==0x100000){byte[] response=Wrap(type,BitConverter.GetBytes((ushort)1001));udp.Send(response,response.Length,remote);}
    else if(type==0x100001&&request.Length>=25){int count=BitConverter.ToInt32(request,20);if(count<1||count>4||request.Length!=24+count)continue;for(int j=0;j<count;j++){byte slot=request[24+j];if(slot>3)continue;byte[] response=Wrap(type,Info(frame,slot,12));udp.Send(response,response.Length,remote);}}
    else if(type==0x100002&&request.Length==28){byte flags=request[20];if(flags>3||(flags&1)!=0&&request[21]!=0)continue;if((flags&2)!=0){bool match=true;for(int n=0;n<6;n++)match&=request[22+n]==VirtualId[n];if(!match)continue;}string key=remote.ToString();if(clients.Count<8||clients.ContainsKey(key))clients[key]=new Subscriber{Endpoint=remote,At=now};}
   }
   foreach(string key in new List<string>(clients.Keys)){Subscriber client=clients[key];if(now-client.At>5000){clients.Remove(key);continue;}if(frame!=null){byte[] response=Data(frame,++packet,now*1000);udp.Send(response,response.Length,client.Endpoint);}}
  }
  public void Dispose(){udp.Close();clients.Clear();}
 }
}
