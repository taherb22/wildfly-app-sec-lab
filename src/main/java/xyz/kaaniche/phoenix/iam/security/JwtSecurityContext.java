package xyz.kaaniche.phoenix.iam.security;

import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

public class JwtSecurityContext implements SecurityContext {
    private final TokenValidationResult validationResult;
    
    public JwtSecurityContext(TokenValidationResult validationResult) {
        this.validationResult = validationResult;
    }
    
    @Override
    public Principal getUserPrincipal() {
        return () -> validationResult.getSubject();
    }
    
    @Override
    public boolean isUserInRole(String role) {
        return validationResult.getRoles().contains(role);
    }
    
    @Override
    public boolean isSecure() {
        return true;
    }
    
    @Override
    public String getAuthenticationScheme() {
        return "Bearer";
    }
}
