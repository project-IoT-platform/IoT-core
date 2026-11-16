import java.security.SecureRandom;
import java.util.Base64;

public class PepperGenerator {
    public static void main(String[] args) {
        byte[] pepper = new byte[32];
        new SecureRandom().nextBytes(pepper);
        System.out.println(Base64.getEncoder().encodeToString(pepper));
    }
}