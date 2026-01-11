package xyz.kaaniche.phoenix.iam.security;

import org.apache.commons.codec.binary.Base32;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

@ApplicationScoped
public class TotpService {
    private final Base32 base32 = new Base32();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Config config = ConfigProvider.getConfig();

    public String generateSecret() {
        byte[] secret = new byte[20];
        secureRandom.nextBytes(secret);
        return base32.encodeToString(secret).replace("=", "");
    }

    public String buildOtpAuthUri(String username, String secret) {
        String issuer = config.getOptionalValue("totp.issuer", String.class).orElse("Phoenix IAM");
        int digits = config.getOptionalValue("totp.digits", Integer.class).orElse(6);
        int period = config.getOptionalValue("totp.period.seconds", Integer.class).orElse(30);
        String label = urlEncode(issuer + ":" + username);
        String issuerEncoded = urlEncode(issuer);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuerEncoded
                + "&digits=" + digits + "&period=" + period;
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{6,8}")) {
            return false;
        }
        int digits = config.getOptionalValue("totp.digits", Integer.class).orElse(6);
        int period = config.getOptionalValue("totp.period.seconds", Integer.class).orElse(30);
        int window = config.getOptionalValue("totp.window", Integer.class).orElse(1);

        long now = Instant.now().getEpochSecond();
        for (int i = -window; i <= window; i++) {
            String candidate = generateTotp(secret, (now / period) + i, digits);
            if (constantTimeEquals(candidate, trimmed)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(String secret, long counter, int digits) {
        try {
            byte[] key = base32.decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
