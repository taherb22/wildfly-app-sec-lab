package xyz.kaaniche.phoenix.iam.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebSocket endpoint for real-time security events
 * Requires valid JWT token for connection
 */
@ServerEndpoint(
    value = "/ws/security-events",
    configurator = JwtWebSocketConfigurator.class
)
@ApplicationScoped
public class SecurityEventsEndpoint {
    private static final Logger LOGGER = Logger.getLogger(SecurityEventsEndpoint.class.getName());
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        // Validate token (done by configurator)
        String userId = (String) config.getUserProperties().get("userId");
        if (userId == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Unauthorized"));
            } catch (IOException e) {
                LOGGER.severe("Error closing unauthorized session: " + e.getMessage());
            }
            return;
        }
        
        sessions.add(session);
        LOGGER.info("WebSocket opened for user: " + userId + ", total sessions: " + sessions.size());
        
        // Send welcome message
        sendToSession(session, "{\"type\":\"connected\",\"message\":\"Security events stream active\"}");
    }
    
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessions.remove(session);
        LOGGER.info("WebSocket closed: " + reason.getReasonPhrase() + ", remaining: " + sessions.size());
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        LOGGER.severe("WebSocket error: " + error.getMessage());
        sessions.remove(session);
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        LOGGER.info("Received message: " + message);
        // Echo for testing
        sendToSession(session, "{\"type\":\"echo\",\"message\":\"" + message + "\"}");
    }
    
    /**
     * Broadcast security event to all connected clients
     */
    public static void broadcast(String event) {
        LOGGER.info("Broadcasting to " + sessions.size() + " sessions: " + event);
        sessions.forEach(session -> sendToSession(session, event));
    }
    
    private static void sendToSession(Session session, String message) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                LOGGER.severe("Error sending message: " + e.getMessage());
            }
        }
    }
}
