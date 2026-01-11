package xyz.kaaniche.phoenix.iam.security;

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
    private static final List<String> ALLOWED_ALGORITHMS = List.of("RS256", "ES256");
    
    @Inject
    private JtiStore jtiStore;
    
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
                        ". Only RS256, ES256 permitted");
            }
            
            // Validate signature and extract claims
            return JwtValidator.validate(token, alg);
            
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
               path.startsWith("dev/seed") ||
               path.startsWith("mfa") ||
               path.startsWith("ws/") ||
               path.contains("/login") ||
               path.contains("/register");
    }
}
