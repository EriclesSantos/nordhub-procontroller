using System;
using System.Text;
using System.IO;
using System.Security.Cryptography;
using System.Collections.Generic;

namespace Nordhub.ProController {
 public sealed class WirePacket {
  readonly byte[] encryption,authentication;
  public sealed class Decoded {public byte Type;public byte[] Session,Payload;public ulong Sequence;}
  public static string NewCode(){byte[] bytes=new byte[10];using(RandomNumberGenerator random=RandomNumberGenerator.Create())random.GetBytes(bytes);string hex=BitConverter.ToString(bytes).Replace("-","");return hex.Substring(0,4)+"-"+hex.Substring(4,4)+"-"+hex.Substring(8,4)+"-"+hex.Substring(12,4)+"-"+hex.Substring(16,4);}
  public WirePacket(string code){
   string normalized=code.Replace("-","").Replace(" ","").ToUpperInvariant();if(!System.Text.RegularExpressions.Regex.IsMatch(normalized,"\\A[0-9A-F]{20}\\z"))throw new ArgumentException("Código inválido: use cinco grupos de quatro caracteres.");
   byte[] master;using(SHA256 sha=SHA256.Create())master=sha.ComputeHash(Encoding.UTF8.GetBytes("Nordhub local Wi-Fi v1:"+normalized));encryption=Mac(master,Encoding.UTF8.GetBytes("encryption"));authentication=Mac(master,Encoding.UTF8.GetBytes("authentication"));
  }
  static byte[] Mac(byte[] key,byte[] data){using(HMACSHA256 h=new HMACSHA256(key))return h.ComputeHash(data);}
  public byte[] Encode(byte type,byte[] session,ulong sequence,byte[] payload){byte[] iv=new byte[16];using(RandomNumberGenerator random=RandomNumberGenerator.Create())random.GetBytes(iv);return Encode(type,session,sequence,payload,iv);}
  public byte[] Encode(byte type,byte[] session,ulong sequence,byte[] payload,byte[] iv){
   if(type<1||type>4||session.Length!=16||sequence<1||sequence>long.MaxValue||payload.Length>128||iv.Length!=16)throw new ArgumentException("Packet fields");
   byte[] ciphertext;using(Aes aes=Aes.Create()){aes.Key=encryption;aes.IV=iv;aes.Mode=CipherMode.CBC;aes.Padding=PaddingMode.PKCS7;using(ICryptoTransform encrypt=aes.CreateEncryptor())ciphertext=encrypt.TransformFinalBlock(payload,0,payload.Length);}
   byte[] body;using(MemoryStream stream=new MemoryStream())using(BinaryWriter writer=new BinaryWriter(stream)){writer.Write(new byte[]{78,72,87,49,1,type,0,0});writer.Write(sequence);writer.Write(session);writer.Write(iv);writer.Write(ciphertext);body=stream.ToArray();}
   byte[] result=new byte[body.Length+32];Buffer.BlockCopy(body,0,result,0,body.Length);Buffer.BlockCopy(Mac(authentication,body),0,result,body.Length,32);return result;
  }
  public Decoded Decode(byte[] packet){
   if(packet==null||packet.Length<96||packet.Length>224||(packet.Length-80)%16!=0)return null;
   try{byte[] body=new byte[packet.Length-32];Buffer.BlockCopy(packet,0,body,0,body.Length);byte[] expected=Mac(authentication,body);int difference=0;for(int i=0;i<32;i++)difference|=expected[i]^packet[body.Length+i];if(difference!=0)return null;
    using(BinaryReader reader=new BinaryReader(new MemoryStream(body))){if(reader.ReadUInt32()!=0x3157484e||reader.ReadByte()!=1)return null;byte type=reader.ReadByte();if(type<1||type>4||reader.ReadUInt16()!=0)return null;ulong seq=reader.ReadUInt64();if(seq<1||seq>long.MaxValue)return null;byte[] session=reader.ReadBytes(16),iv=reader.ReadBytes(16),ciphertext=reader.ReadBytes(body.Length-48),payload;
     using(Aes aes=Aes.Create()){aes.Key=encryption;aes.IV=iv;aes.Mode=CipherMode.CBC;aes.Padding=PaddingMode.PKCS7;using(ICryptoTransform decrypt=aes.CreateDecryptor())payload=decrypt.TransformFinalBlock(ciphertext,0,ciphertext.Length);}
     return new Decoded{Type=type,Session=session,Sequence=seq,Payload=payload};
    }
   }catch(CryptographicException){return null;}catch(EndOfStreamException){return null;}
  }
  public static bool Equal(byte[] a,byte[] b){if(a==null||b==null||a.Length!=b.Length)return false;int n=0;for(int i=0;i<a.Length;i++)n|=a[i]^b[i];return n==0;}
 }
 public sealed class ControlFrame {
  public ushort Buttons;public byte Hat,LeftTrigger,RightTrigger,MouseButtons,Battery;
  public short LeftX,LeftY,RightX,RightY,MouseX,MouseY,Wheel;public float[] Motion=new float[6];
  public bool MotionAvailable;
  public static ControlFrame Decode(byte[] bytes){
   if(bytes==null||bytes.Length!=64)return null;
   using(BinaryReader r=new BinaryReader(new MemoryStream(bytes))){ControlFrame f=new ControlFrame();f.Buttons=r.ReadUInt16();f.Hat=r.ReadByte();if((f.Buttons&0xc000)!=0||f.Hat>8||r.ReadByte()!=0)return null;
    f.LeftX=r.ReadInt16();f.LeftY=r.ReadInt16();f.RightX=r.ReadInt16();f.RightY=r.ReadInt16();f.LeftTrigger=r.ReadByte();f.RightTrigger=r.ReadByte();f.MouseButtons=r.ReadByte();if(f.MouseButtons>3||r.ReadByte()!=0)return null;
    f.MouseX=r.ReadInt16();f.MouseY=r.ReadInt16();f.Wheel=r.ReadInt16();if(Math.Abs((int)f.MouseX)>300||Math.Abs((int)f.MouseY)>300||Math.Abs((int)f.Wheel)>1200||r.ReadUInt16()!=0)return null;
    for(int i=0;i<6;i++){f.Motion[i]=r.ReadSingle();if(float.IsNaN(f.Motion[i])||float.IsInfinity(f.Motion[i])||Math.Abs(f.Motion[i])>200)return null;}
    f.Battery=r.ReadByte();byte flags=r.ReadByte();if(f.Battery>100||flags>1)return null;f.MotionAvailable=flags==1;for(int i=50;i<64;i++)if(r.ReadByte()!=0)return null;return f;
   }
  }
  public XboxReport Xbox(){
   XboxReport report=new XboxReport();int[] bits={0x1000,0x2000,0x4000,0x8000,0x0100,0x0200,0,0,0x20,0x10,0x400,0,0x40,0x80};for(int i=0;i<14;i++)if((Buttons&(1<<i))!=0)report.Buttons|=(ushort)bits[i];
   int[] hats={1,9,8,10,2,6,4,5,0};report.Buttons|=(ushort)hats[Hat];report.LeftTrigger=LeftTrigger;report.RightTrigger=RightTrigger;report.LeftX=LeftX;report.LeftY=(short)Math.Max(short.MinValue,Math.Min(short.MaxValue,-(int)LeftY));report.RightX=RightX;report.RightY=(short)Math.Max(short.MinValue,Math.Min(short.MaxValue,-(int)RightY));return report;
  }
 }
 public sealed class PeerState {
  byte[] session;string endpoint;ulong sequence;long last;bool assigned;
  public ControlFrame Frame {get;private set;}
  public byte[] Session{get{return session==null?null:(byte[])session.Clone();}}
  public bool Connected(long now){return assigned&&now>=last&&now-last<=250;}
  public bool OwnsSession(WirePacket.Decoded p,string peer,long now){return p!=null&&Connected(now)&&endpoint==peer&&WirePacket.Equal(session,p.Session)&&p.Sequence>sequence;}
  public bool Accept(WirePacket.Decoded p,string peer,long now){
   if(p==null||p.Type!=1)return false;ControlFrame frame=ControlFrame.Decode(p.Payload);if(frame==null)return false;
   if(!assigned||endpoint!=peer||!WirePacket.Equal(session,p.Session)){
    if(assigned&&now-last<5000)return false;endpoint=peer;session=(byte[])p.Session.Clone();sequence=0;assigned=true;
   }
   if(p.Sequence<=sequence)return false;sequence=p.Sequence;last=now;Frame=frame;return true;
  }
 }
}
