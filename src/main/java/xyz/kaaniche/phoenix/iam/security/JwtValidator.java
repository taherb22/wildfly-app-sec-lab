package xyz.kaaniche.phoenix.iam.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class JwtValidator {
    private static final Logger LOGGER = Logger.getLogger(JwtValidator.class.getName());
    private static final Config config = ConfigProvider.getConfig();
    
    public static TokenValidationResult validate(String token, String algorithm) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Verify signature
            JWSVerifier verifier = getVerifier(algorithm, signedJWT);
            if (!signedJWT.verify(verifier)) {
                return TokenValidationResult.invalid("Invalid signature");
            }
            
            // Validate expiration
            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                return TokenValidationResult.invalid("Token expired");
            }
            
            // Validate audience
            String expectedAudience = config.getOptionalValue("jwt.audience", String.class)
                    .orElse("phoenix-iam");
            List<String> audiences = signedJWT.getJWTClaimsSet().getAudience();
            if (audiences == null || !audiences.contains(expectedAudience)) {
                return TokenValidationResult.invalid("Invalid audience");
            }
            
            // Extract claims
            String subject = signedJWT.getJWTClaimsSet().getSubject();
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            String audience = audiences.isEmpty() ? null : audiences.get(0);
            
            Object groupsObj = signedJWT.getJWTClaimsSet().getClaim("groups");
            List<String> roles = groupsObj instanceof List ? (List<String>) groupsObj : List.of();
            
            long expiresAt = expirationTime.getTime() / 1000;
            
            return TokenValidationResult.valid(subject, jti, audience, roles, expiresAt);
            
        } catch (Exception e) {
            LOGGER.severe("JWT validation error: " + e.getMessage());
            return TokenValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }
    
    private static JWSVerifier getVerifier(String algorithm, SignedJWT jwt) throws Exception {
        String jwkJson = config.getOptionalValue("jwt.key.jwk", String.class).orElse(null);
        
        if (jwkJson == null) {
            throw new IllegalStateException("No JWK configured for validation");
        }
        
        return switch (algorithm) {
            case "RS256" -> {
                RSAKey rsaKey = RSAKey.parse(jwkJson);
                yield new RSASSAVerifier(rsaKey);
            }
            case "ES256" -> {
                ECKey ecKey = ECKey.parse(jwkJson);
                yield new ECDSAVerifier(ecKey);
            }
            case "EdDSA" -> {
                OctetKeyPair okp = OctetKeyPair.parse(jwkJson);
                yield new Ed25519Verifier(okp);
            }
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }
}
