package xyz.kaaniche.phoenix.iam.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import xyz.kaaniche.phoenix.iam.service.JwtService;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//@Provider  // Disabled - ResourceServerFilter is used instead
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthenticationFilter implements ContainerRequestFilter {

    @Inject
    private JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        
        // Skip authentication for public endpoints (login, register, OAuth flow, MFA, dev seed)
        if (path.contains("/auth/login") || 
            path.contains("/auth/register") || 
            path.contains("/dev/seed") ||
            path.contains("/authorize") ||
            path.contains("/login/authorization") ||
            path.contains("/oauth/token") ||
            path.contains("/mfa/")) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing or invalid Authorization header")
                    .build()
            );
            return;
        }

        String token = authHeader.substring(7);
        
        try {
            JWTClaimsSet claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            List<String> rolesList = claims.getStringListClaim("roles");
            Set<String> roles = new HashSet<>(rolesList != null ? rolesList : List.of());
            
            requestContext.setSecurityContext(new JwtSecurityContext(username, roles));
            
        } catch (Exception e) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build()
            );
        }
    }

    private static class JwtSecurityContext implements SecurityContext {
        private final String username;
        private final Set<String> roles;

        public JwtSecurityContext(String username, Set<String> roles) {
            this.username = username;
            this.roles = roles;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> username;
        }

        @Override
        public boolean isUserInRole(String role) {
            return roles.contains(role);
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }
    }
}
