using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Net;
using System.Net.NetworkInformation;
using ZXing;
using ZXing.Common;
using ZXing.QrCode;

namespace Nordhub.ProController {
 public static class QrPairing {
  public static List<string> LocalAddresses(){
   List<string> physical=new List<string>(),other=new List<string>();
   foreach(NetworkInterface net in NetworkInterface.GetAllNetworkInterfaces()){
    if(net.OperationalStatus!=OperationalStatus.Up||net.NetworkInterfaceType==NetworkInterfaceType.Loopback||net.NetworkInterfaceType==NetworkInterfaceType.Tunnel)continue;
    foreach(UnicastIPAddressInformation item in net.GetIPProperties().UnicastAddresses){string address=item.Address.ToString();if(!Local(address))continue;List<string> destination=net.NetworkInterfaceType==NetworkInterfaceType.Wireless80211||net.NetworkInterfaceType==NetworkInterfaceType.Ethernet?physical:other;if(!destination.Contains(address))destination.Add(address);}
   }
   foreach(string address in other)if(!physical.Contains(address))physical.Add(address);if(physical.Count>8)physical.RemoveRange(8,physical.Count-8);return physical;
  }
  public static bool Local(string host){
   if(host==null||!System.Text.RegularExpressions.Regex.IsMatch(host,"\\A[0-9]{1,3}(\\.[0-9]{1,3}){3}\\z"))return false;
   string[] parts=host.Split('.');int[] b=new int[4];for(int i=0;i<4;i++)if(parts[i].Length>1&&parts[i][0]=='0'||!int.TryParse(parts[i],NumberStyles.None,CultureInfo.InvariantCulture,out b[i])||b[i]>255)return false;
   return b[0]==10||b[0]==192&&b[1]==168||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==169&&b[1]==254;
  }
  public static string Payload(IList<string> hosts,int port,string code){
   if(hosts==null||hosts.Count<1||hosts.Count>8||port<1024||port>65535)throw new ArgumentException("Local pairing fields");HashSet<string> unique=new HashSet<string>();foreach(string host in hosts)if(!Local(host)||!unique.Add(host))throw new ArgumentException("Local pairing address");
   new WirePacket(code);string normalized=code.Replace("-","").Replace(" ","").ToUpperInvariant();return "nordhub://pair?v=1&hosts="+string.Join(",",new List<string>(hosts).ToArray())+"&port="+port.ToString(CultureInfo.InvariantCulture)+"&code="+normalized;
  }
  public static Bitmap Image(string payload,int pixels){
   if(pixels<180||pixels>1200)throw new ArgumentException("QR image size");
   Dictionary<EncodeHintType,object> hints=new Dictionary<EncodeHintType,object>();hints[EncodeHintType.MARGIN]=4;hints[EncodeHintType.ERROR_CORRECTION]=ZXing.QrCode.Internal.ErrorCorrectionLevel.M;
   BitMatrix matrix=new QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,1,1,hints);int unit=pixels/matrix.Width;if(unit<2)throw new ArgumentException("QR too dense");int origin=(pixels-matrix.Width*unit)/2;Bitmap image=new Bitmap(pixels,pixels);
   using(Graphics g=Graphics.FromImage(image)){g.Clear(Color.White);for(int y=0;y<matrix.Height;y++)for(int x=0;x<matrix.Width;x++)if(matrix[x,y])g.FillRectangle(Brushes.Black,origin+x*unit,origin+y*unit,unit,unit);}return image;
  }
 }
}
