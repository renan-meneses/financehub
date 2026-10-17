import com.financehub.identity.domain.IdentityPolicy;
import java.util.HashSet;

public class IdentityPolicyCheck {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Identity check " + checks + " failed");
    }
    private static void rejects(String password) {
        try { IdentityPolicy.validatePassword(password); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid password");
    }
    public static void main(String[] args) {
        check(IdentityPolicy.normalizeEmail(" Alice@EXAMPLE.COM ").equals("alice@example.com"));
        check(IdentityPolicy.hash("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        rejects(null); rejects("short"); rejects("a".repeat(73)); rejects("€".repeat(25));
        IdentityPolicy.validatePassword("€".repeat(24));
        IdentityPolicy.validatePassword("a".repeat(72));
        check(!IdentityPolicy.validTokenShape(null));
        check(!IdentityPolicy.validTokenShape("a".repeat(42)));
        check(!IdentityPolicy.validTokenShape("a".repeat(42) + "/"));
        var tokens = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            var token = IdentityPolicy.newToken();
            check(IdentityPolicy.validTokenShape(token));
            check(IdentityPolicy.hash(token).length() == 64);
            check(tokens.add(token));
        }
        System.out.println("Passed " + checks + " identity policy assertions");
    }
}
