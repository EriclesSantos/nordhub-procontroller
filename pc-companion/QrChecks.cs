using System;
using System.IO;
using System.Drawing;
using System.Drawing.Imaging;
using Nordhub.ProController;
class QrChecks {
 static int checks;
 static void Check(bool value,string message){checks++;if(!value)throw new Exception(message);}
 static int Main(string[] args){try{
  string[] hosts={"192.168.1.20","10.0.0.20"};string code="0011-2233-4455-6677-8899";string payload=QrPairing.Payload(hosts,26761,code);
  Check(payload=="nordhub://pair?v=1&hosts=192.168.1.20,10.0.0.20&port=26761&code=00112233445566778899","QR schema");
  foreach(string bad in new[]{"8.8.8.8","127.0.0.1","192.168.01.2","192.168.1.256","localhost","::1","172.32.1.1","10.0.0.1/path"})Check(!QrPairing.Local(bad),"non-local or ambiguous host");
  foreach(string good in new[]{"10.1.2.3","172.16.0.1","172.31.255.254","169.254.1.2","192.168.0.1"})Check(QrPairing.Local(good),"private literal");
  foreach(int pixels in new[]{180,240,300,420})using(Bitmap image=QrPairing.Image(payload,pixels)){ZXing.Result result=new ZXing.BarcodeReader{AutoRotate=true}.Decode(image);Check(result!=null&&result.Text==payload,"QR roundtrip at "+pixels);image.RotateFlip(RotateFlipType.Rotate90FlipNone);result=new ZXing.BarcodeReader{AutoRotate=true}.Decode(image);Check(result!=null&&result.Text==payload,"rotated QR "+pixels);}
  bool invalid=false;try{QrPairing.Payload(new[]{hosts[0],hosts[0]},26761,code);}catch(ArgumentException){invalid=true;}Check(invalid,"duplicate candidate rejected");invalid=false;try{QrPairing.Payload(hosts,80,code);}catch(ArgumentException){invalid=true;}Check(invalid,"privileged port rejected");
  if(args.Length==1){Directory.CreateDirectory(args[0]);using(Bitmap image=QrPairing.Image(payload,300))image.Save(Path.Combine(args[0],"qr-pairing.png"),ImageFormat.Png);Console.WriteLine("Fixed test QR generated for Java interoperability; no live pairing session.");}
  Console.WriteLine("QR checks: "+checks);return 0;
 }catch(Exception e){Console.Error.WriteLine(e.Message);return 1;}}
}
