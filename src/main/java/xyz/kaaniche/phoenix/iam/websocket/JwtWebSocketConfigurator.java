package xyz.kaaniche.phoenix.iam.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import xyz.kaaniche.phoenix.iam.security.JwtValidator;
import xyz.kaaniche.phoenix.iam.security.TokenValidationResult;

import java.util.List;
import java.util.logging.Logger;

/**
 * WebSocket configurator that validates JWT tokens during handshake
 */
public class JwtWebSocketConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger LOGGER = Logger.getLogger(JwtWebSocketConfigurator.class.getName());
    
    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        // Extract token from query parameter or header
        String token = extractToken(request);
        
        if (token == null) {
            LOGGER.warning("WebSocket handshake rejected: no token provided");
            return;
        }
        
        // Validate token
        TokenValidationResult result = JwtValidator.validate(token, "EdDSA");
        
        if (!result.isValid()) {
            LOGGER.warning("WebSocket handshake rejected: " + result.getError());
            return;
        }
        
        // Store user info in session
        config.getUserProperties().put("userId", result.getSubject());
        config.getUserProperties().put("roles", result.getRoles());
        
        LOGGER.info("WebSocket handshake accepted for user: " + result.getSubject());
    }
    
    private String extractToken(HandshakeRequest request) {
        // Try Authorization header first
        List<String> authHeaders = request.getHeaders().get("authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        
        // Try query parameter
        List<String> tokenParams = request.getParameterMap().get("token");
        if (tokenParams != null && !tokenParams.isEmpty()) {
            return tokenParams.get(0);
        }
        
        return null;
    }
}
