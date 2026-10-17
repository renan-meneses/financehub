import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Local Compose initialization only. Production keys must come from a secrets manager. */
public class GenerateDevelopmentKeys {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path privateFile = directory.resolve("development.key");
        Path publicFile = directory.resolve("development.pub");
        if (Files.exists(privateFile) || Files.exists(publicFile)) {
            if (!Files.exists(privateFile) || !Files.exists(publicFile))
                throw new IllegalStateException("Incomplete key pair; restore the missing file before starting");
            return;
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        Files.writeString(privateFile, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardOpenOption.CREATE_NEW);
        Files.writeString(publicFile, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
        System.out.println("Created local development signing key pair");
    }
    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
            + "\n-----END " + type + "-----\n";
    }
}
