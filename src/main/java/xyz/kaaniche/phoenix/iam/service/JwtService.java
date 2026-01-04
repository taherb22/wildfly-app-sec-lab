package xyz.kaaniche.phoenix.iam.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class JwtService {
    
    private static final Logger LOGGER = Logger.getLogger(JwtService.class.getName());
    private static final String DEFAULT_SECRET = "phoenix-iam-secret-key-minimum-32-characters-for-hs256-algorithm-default";
    
    @Inject
    private Config config;
    
    private String jwtSecret;
    private int expirationMinutes;

    @PostConstruct
    public void init() {
        jwtSecret = config.getOptionalValue("jwt.secret", String.class)
                .orElse(DEFAULT_SECRET);
        expirationMinutes = config.getOptionalValue("jwt.expiration.minutes", Integer.class)
                .orElse(60);
        
        LOGGER.info("JwtService initialized with secret length: " + jwtSecret.length() + " chars");
    }

    public String generateToken(String username, Set<String> roles) {
        try {
            // Ensure secret is at least 256 bits (32 bytes)
            byte[] secret = ensureSecretLength(jwtSecret);
            JWSSigner signer = new MACSigner(secret);
            
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("roles", roles)
                    .issuer("phoenix-iam")
                    .expirationTime(new Date(System.currentTimeMillis() + expirationMinutes * 60L * 1000L))
                    .issueTime(new Date())
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet);

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            LOGGER.severe("Failed to generate JWT token: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Token generation failed", e);
        }
    }

    public JWTClaimsSet validateToken(String token) throws Exception {
        byte[] secret = ensureSecretLength(jwtSecret);
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(secret);
        
        if (!signedJWT.verify(verifier)) {
            throw new JOSEException("Invalid token signature");
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        if (claims.getExpirationTime().before(new Date())) {
            throw new JOSEException("Token expired");
        }

        return claims;
    }
    
    private byte[] ensureSecretLength(String secret) {
        if (secret == null || secret.isEmpty()) {
            LOGGER.warning("JWT secret is null or empty, using default");
            secret = DEFAULT_SECRET;
        }
        
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // Pad the secret to at least 32 bytes for HS256
            byte[] paddedSecret = new byte[32];
            System.arraycopy(secretBytes, 0, paddedSecret, 0, secretBytes.length);
            for (int i = secretBytes.length; i < 32; i++) {
                paddedSecret[i] = (byte) (i % 256);
            }
            LOGGER.info("Secret padded from " + secretBytes.length + " to 32 bytes");
            return paddedSecret;
        }
        return secretBytes;
    }
}
