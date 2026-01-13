package xyz.kaaniche.phoenix.iam.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner; //change macsigner to RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier;//change macverifier to RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class JwtService {

    private static final Logger LOGGER = Logger.getLogger(JwtService.class.getName());

    @Inject
    private Config config;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private int expirationMinutes;

    @PostConstruct
    public void init() {
        try {
            privateKey = loadPrivateKeyFromEnv();
            publicKey = loadPublicKeyFromEnv();
            expirationMinutes = config.getOptionalValue("jwt.expiration.minutes", Integer.class)
                    .orElse(60);

            LOGGER.info("JwtService initialized with RSA keys from env");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JwtService", e);
        }
    }

    private RSAPrivateKey loadPrivateKeyFromEnv() throws Exception {
        String key = System.getenv("JWT_PRIVATE_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("JWT_PRIVATE_KEY is not set");
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKeyFromEnv() throws Exception {
        String key = System.getenv("JWT_PUBLIC_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("JWT_PUBLIC_KEY is not set");
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(spec);
    }

    public String generateToken(String username, Set<String> roles) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("roles", roles)
                    .issuer("phoenix-iam")
                    .issueTime(new Date())
                    .expirationTime(
                            new Date(System.currentTimeMillis() + expirationMinutes * 60L * 1000L)
                    )
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims
            );

            jwt.sign(signer);
            return jwt.serialize();

        } catch (Exception e) {
            throw new RuntimeException("Token generation failed", e);
        }
    }

    public JWTClaimsSet validateToken(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);

        if (!jwt.getHeader().getAlgorithm().equals(JWSAlgorithm.RS256)) {
            throw new JOSEException("Invalid JWT algorithm!!");
        }

        if (!jwt.verify(new RSASSAVerifier(publicKey))) {
            throw new JOSEException("Invalid token signature!!");
        }

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (claims.getExpirationTime().before(new Date())) {
            throw new JOSEException("Token expired!!");
        }

        return claims;
    }
}

    
    //private byte[] ensureSecretLength(String secret) {
    //    if (secret == null || secret.isEmpty()) {
    //        LOGGER.warning("JWT secret is null or empty, using default");
    //        secret = DEFAULT_SECRET;
    //    }
    //    
    //    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    //    if (secretBytes.length < 32) {
    //        // Pad the secret to at least 32 bytes for HS256
    //        byte[] paddedSecret = new byte[32];
    //        System.arraycopy(secretBytes, 0, paddedSecret, 0, secretBytes.length);
    //        for (int i = secretBytes.length; i < 32; i++) {
    //            paddedSecret[i] = (byte) (i % 256);
    //        }
    //        LOGGER.info("Secret padded from " + secretBytes.length + " to 32 bytes");
    //        return paddedSecret;
    //    }
    //    return secretBytes;
    //}   
     //==> No default/padding secret.
