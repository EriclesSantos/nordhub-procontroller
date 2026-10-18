package com.switchphone.wifi;

import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/** Strict local pairing payload. Never opens arbitrary URLs or persists the session code. */
public final class PairingQr {
    public final List<String> hosts;public final int port;public final String code;
    public PairingQr(List<String> hosts,int port,String code){
        if(hosts==null||hosts.isEmpty()||hosts.size()>8||port<1024||port>65535)throw invalid();
        LinkedHashSet<String> unique=new LinkedHashSet<>();for(String host:hosts){if(!local(host))throw invalid();unique.add(host);}
        if(unique.size()!=hosts.size()||code==null)throw invalid();String normalized=code.replace("-","").replace(" ","").toUpperCase(Locale.ROOT);
        if(!normalized.matches("[0-9A-F]{20}"))throw invalid();
        this.hosts=Collections.unmodifiableList(new ArrayList<>(unique));this.port=port;this.code=normalized;
    }
    public static boolean local(String host){
        if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        String[] parts=host.split("\\.");int[] b=new int[4];for(int i=0;i<4;i++){if(parts[i].length()>1&&parts[i].charAt(0)=='0')return false;b[i]=Integer.parseInt(parts[i]);if(b[i]>255)return false;}
        return b[0]==10||b[0]==192&&b[1]==168||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==169&&b[1]==254;
    }
    public static PairingQr parse(String text){
        if(text==null||text.length()>1024)throw invalid();
        try{
            URI uri=new URI(text);if(!"nordhub".equals(uri.getScheme())||!"pair".equals(uri.getRawAuthority())||uri.getFragment()!=null||!"".equals(uri.getPath()))throw invalid();
            String query=uri.getRawQuery();if(query==null)throw invalid();Map<String,String> fields=new HashMap<>();
            for(String part:query.split("&",-1)){String[] pair=part.split("=",-1);if(pair.length!=2)throw invalid();String key=URLDecoder.decode(pair[0],"UTF-8"),value=URLDecoder.decode(pair[1],"UTF-8");if(fields.put(key,value)!=null)throw invalid();}
            if(fields.size()!=4||!"1".equals(fields.get("v"))||!fields.containsKey("hosts")||!fields.containsKey("port")||!fields.containsKey("code")||!fields.get("port").matches("[0-9]{4,5}"))throw invalid();
            return new PairingQr(Arrays.asList(fields.get("hosts").split(",",-1)),Integer.parseInt(fields.get("port")),fields.get("code"));
        }catch(Exception e){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Este QR não é um pareamento Nördhub válido. Gere outro QR no auxiliar do PC.");}
    @Override public String toString(){return "PairingQr[hosts="+hosts+", port="+port+", code=hidden]";}
}
