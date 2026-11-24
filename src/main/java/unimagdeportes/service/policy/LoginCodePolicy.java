package unimagdeportes.service.policy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LoginCodePolicy {
    private static final int CODE_SPACE = 1_000_000;
    private final SecureRandom random;
    private final SecretKeySpec key;

    public LoginCodePolicy(SecureRandom random, byte[] secret) {
        if (secret.length < 32) {
            throw new IllegalArgumentException("La clave HMAC debe tener al menos 32 bytes");
        }
        this.random = random;
        this.key = new SecretKeySpec(secret.clone(), "HmacSHA256");
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(CODE_SPACE));
    }

    public String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(email.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular el HMAC del código", ex);
        }
    }

    public boolean matches(String email, String code, String expectedHash) {
        return MessageDigest.isEqual(hash(email, code).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
