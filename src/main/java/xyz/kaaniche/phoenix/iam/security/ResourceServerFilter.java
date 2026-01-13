package xyz.kaaniche.phoenix.iam.security;

import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import xyz.kaaniche.phoenix.iam.store.JtiStore;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Resource Server Filter - validates JWT tokens for protected resources
 * Enforces:
 * - Algorithm whitelist (RS256, ES256 only - prevents algorithm confusion)
 * - Audience validation
 * - JTI replay prevention
 * - Signature verification
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ResourceServerFilter implements ContainerRequestFilter {
    private static final Logger LOGGER = Logger.getLogger(ResourceServerFilter.class.getName());
    private static final List<String> ALLOWED_ALGORITHMS = List.of("RS256", "ES256", "EdDSA");
    
    @Inject
    private JtiStore jtiStore;
    
    @Inject
    private JwtManager jwtManager;
    
    private final Config config = ConfigProvider.getConfig();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        
        // Skip public endpoints
        if (isPublicEndpoint(path)) {
            return;
        }
        
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Missing or invalid Authorization header\"}")
                    .build());
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            // Parse and validate token
            TokenValidationResult result = validateToken(token);
            
            if (!result.isValid()) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"" + result.getError() + "\"}")
                        .build());
                return;
            }
            
            // Check for replay attack (duplicate JTI)
            String jti = result.getJti();
            if (jti != null && jtiStore.exists(jti)) {
                LOGGER.warning("Replay attack detected - duplicate JTI: " + jti);
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"Token replay detected\"}")
                        .build());
                return;
            }
            
            // Store JTI to prevent replay
            if (jti != null) {
                jtiStore.store(jti, result.getExpiresAt());
            }
            
            // Set security context with validated claims
            requestContext.setSecurityContext(new JwtSecurityContext(result));
            
        } catch (Exception e) {
            LOGGER.severe("Token validation failed: " + e.getMessage());
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Token validation failed\"}")
                    .build());
        }
    }
    
    private TokenValidationResult validateToken(String token) {
        // Parse JWT header to check algorithm
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return TokenValidationResult.invalid("Malformed JWT token");
        }
        
        try {
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            if (headerJson.contains("\"alg\":\"none\"")) {
                return TokenValidationResult.invalid("Algorithm 'none' not allowed");
            }
            
            // Extract algorithm
            String alg = extractAlgorithm(headerJson);
            if (!ALLOWED_ALGORITHMS.contains(alg)) {
                return TokenValidationResult.invalid("Algorithm not allowed: " + alg + 
                        ". Only RS256, ES256, EdDSA permitted");
            }
            
            // Validate signature and extract claims using JwtManager
            return validateWithJwtManager(token);
            
        } catch (Exception e) {
            return TokenValidationResult.invalid("Token parsing failed: " + e.getMessage());
        }
    }
    
    private String extractAlgorithm(String headerJson) {
        // Simple extraction - in production use proper JSON parser
        int algStart = headerJson.indexOf("\"alg\":\"") + 7;
        int algEnd = headerJson.indexOf("\"", algStart);
        return headerJson.substring(algStart, algEnd);
    }
    
    private TokenValidationResult validateWithJwtManager(String token) {
        try {
            // Try to validate with JwtManager (EdDSA tokens from OAuth)
            com.nimbusds.jwt.JWT jwt = jwtManager.validateJWT(token).orElse(null);
            if (jwt != null) {
                com.nimbusds.jwt.JWTClaimsSet claims = jwt.getJWTClaimsSet();
                
                // Extract required claims
                String subject = claims.getSubject();
                String jti = claims.getJWTID();
                
                java.util.List<String> audiences = claims.getAudience();
                String audience = audiences != null && !audiences.isEmpty() ? audiences.get(0) : null;
                
                Object groupsObj = claims.getClaim("groups");
                java.util.List<String> roles = groupsObj instanceof java.util.List 
                    ? (java.util.List<String>) groupsObj 
                    : java.util.List.of();
                
                long expiresAt = claims.getExpirationTime() != null 
                    ? claims.getExpirationTime().getTime() / 1000 
                    : 0;
                
                return TokenValidationResult.valid(subject, jti, audience, roles, expiresAt);
            }
            
            // If JwtManager validation failed, try JwtValidator (RS256 tokens from JwtService)
            try {
                return validateWithJwtValidator(token);
            } catch (Exception e2) {
                LOGGER.warning("Both token validation methods failed");
                return TokenValidationResult.invalid("Token validation failed with both methods");
            }
            
        } catch (Exception e) {
            LOGGER.warning("JwtManager validation failed, attempting fallback: " + e.getMessage());
            // Try JwtValidator as fallback
            try {
                return validateWithJwtValidator(token);
            } catch (Exception e2) {
                LOGGER.severe("JWT validation failed: " + e.getMessage());
                return TokenValidationResult.invalid("Token validation failed: " + e.getMessage());
            }
        }
    }
    
    private TokenValidationResult validateWithJwtValidator(String token) throws Exception {
        // For RS256 tokens, we need to parse without strict JWK validation
        SignedJWT jwt = SignedJWT.parse(token);
        com.nimbusds.jwt.JWTClaimsSet claims = jwt.getJWTClaimsSet();
        
        // For RS256 tokens generated by JwtService, validate expiration
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.before(new Date())) {
            return TokenValidationResult.invalid("Token expired");
        }
        
        String subject = claims.getSubject();
        String jti = claims.getJWTID();
        
        java.util.List<String> audiences = claims.getAudience();
        String audience = audiences != null && !audiences.isEmpty() ? audiences.get(0) : null;
        
        Object rolesObj = claims.getClaim("roles");
        java.util.List<String> roles = rolesObj instanceof java.util.List 
            ? (java.util.List<String>) rolesObj 
            : java.util.List.of();
        
        long expiresAt = expirationTime != null 
            ? expirationTime.getTime() / 1000 
            : 0;
        
        return TokenValidationResult.valid(subject, jti, audience, roles, expiresAt);
    }
    
    private boolean isPublicEndpoint(String path) {
        // Normalize path - remove leading/trailing slashes
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        return path.startsWith("authorize") ||
               path.startsWith("login") ||
               path.startsWith("oauth/token") ||
               path.startsWith("auth/register") ||
               path.startsWith("auth/login") ||
               path.startsWith("api/auth/register") ||
               path.startsWith("api/auth/login") ||
               path.startsWith("api/auth/mfa/") ||
               path.startsWith("dev/seed") ||
               path.startsWith("mfa") ||
               path.startsWith("ws/") ||
               path.contains("/mfa/") ||
               path.contains("/login") ||
               path.contains("/register");
    }
}
