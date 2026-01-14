package xyz.kaaniche.phoenix.iam.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Protected Resource - requires valid JWT token
 * Used to test ResourceServerFilter and JWT validation
 */
@Path("/protected-resource")
@Produces(MediaType.APPLICATION_JSON)
public class ProtectedResource {
    private static final Logger LOGGER = Logger.getLogger(ProtectedResource.class.getName());
    
    @GET
    public Response getProtectedData(@Context SecurityContext securityContext) {
        String username = securityContext.getUserPrincipal() != null 
                ? securityContext.getUserPrincipal().getName() 
                : "anonymous";
        
        LOGGER.info("Protected resource accessed by: " + username);
        
        return Response.ok()
                .entity(Map.of(
                        "message", "This is protected data",
                        "user", username,
                        "timestamp", System.currentTimeMillis()
                ))
                .build();
    }
    
    @GET
    @Path("/admin-only")
    public Response getAdminData(@Context SecurityContext securityContext) {
        if (!securityContext.isUserInRole("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Admin role required"))
                    .build();
        }
        
        String username = securityContext.getUserPrincipal().getName();
        LOGGER.info("Admin resource accessed by: " + username);
        
        return Response.ok()
                .entity(Map.of(
                        "message", "This is admin-only data",
                        "user", username,
                        "timestamp", System.currentTimeMillis()
                ))
                .build();
    }
}
