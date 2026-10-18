import com.switchphone.bluetooth.BondFailure;

/** Metadata is optional and may be unknown. It must never imply a definitive culprit. */
public final class BondFailureTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(BondFailure.describe(BondFailure.UNAVAILABLE).contains("não disponibilizado"),"Missing OEM reason is explicitly unavailable");
        check(BondFailure.describe(1).contains("falha de autenticação"),"Android AUTH_FAILED code 1 is distinct from removal");
        check(BondFailure.describe(2).contains("rejeitada"),"AUTH_REJECTED is not labelled successful");
        check(BondFailure.describe(3).contains("localmente"),"Local cancellation is distinct from remote cancellation");
        check(BondFailure.describe(8).contains("remoto"),"Remote auth cancellation is labelled according to system metadata");
        check(BondFailure.describe(6).contains("expirou"),"Auth timeout is distinguished from HID negotiation timeout");
        check(BondFailure.describe(7).contains("tentativas"),"Repeated-attempt limit has its own diagnostic");
        check(BondFailure.describe(9).contains("genérico"),"REMOVED fallback must not be interpreted as definite user removal");
        for(int code:new int[]{0,-1,10,42,Integer.MAX_VALUE}){
            String message=BondFailure.describe(code);
            check(message.startsWith(code+" ")&&message.contains("sem descrição conhecida"),"Unknown raw reason is preserved without invented attribution");
        }
        for(int code=1;code<=9;code++)check(BondFailure.describe(code).startsWith(code+" ("),"Known raw reason remains visible");
        System.out.println("PASS: "+checks+" bond-failure assertions (optional reason, fallback/unknown codes, no invented attribution)");
    }
}
