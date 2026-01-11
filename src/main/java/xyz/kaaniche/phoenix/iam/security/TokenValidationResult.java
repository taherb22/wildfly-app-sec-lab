package xyz.kaaniche.phoenix.iam.security;

import java.util.List;

public class TokenValidationResult {
    private final boolean valid;
    private final String error;
    private final String subject;
    private final String jti;
    private final String audience;
    private final List<String> roles;
    private final long expiresAt;
    
    private TokenValidationResult(boolean valid, String error, String subject, String jti, 
                                   String audience, List<String> roles, long expiresAt) {
        this.valid = valid;
        this.error = error;
        this.subject = subject;
        this.jti = jti;
        this.audience = audience;
        this.roles = roles;
        this.expiresAt = expiresAt;
    }
    
    public static TokenValidationResult valid(String subject, String jti, String audience, 
                                               List<String> roles, long expiresAt) {
        return new TokenValidationResult(true, null, subject, jti, audience, roles, expiresAt);
    }
    
    public static TokenValidationResult invalid(String error) {
        return new TokenValidationResult(false, error, null, null, null, null, 0);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getError() {
        return error;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public String getJti() {
        return jti;
    }
    
    public String getAudience() {
        return audience;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
}
