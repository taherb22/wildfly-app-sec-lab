package xyz.kaaniche.phoenix.iam.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import xyz.kaaniche.phoenix.iam.abac.AbacEvaluator;
import xyz.kaaniche.phoenix.iam.abac.AbacPolicy;
import xyz.kaaniche.phoenix.iam.abac.PolicyStore;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ABAC REST API - policy management and evaluation
 */
@Path("/abac")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AbacResource {
    private static final Logger LOGGER = Logger.getLogger(AbacResource.class.getName());
    
    @Inject
    private AbacEvaluator evaluator;
    
    @Inject
    private PolicyStore policyStore;
    
    /**
     * Evaluate ABAC policy for given context
     */
    @POST
    @Path("/evaluate")
    public Response evaluate(Map<String, Object> context, @Context SecurityContext securityContext) {
        try {
            LOGGER.info("Evaluating ABAC policy for context: " + context);
            
            AbacEvaluator.Decision decision = evaluator.evaluate(context);
            
            return Response.ok()
                    .entity(Map.of(
                            "permitted", decision.isPermitted(),
                            "effect", decision.getEffect(),
                            "reason", decision.getReason() != null ? decision.getReason() : "N/A"
                    ))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.severe("ABAC evaluation failed: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get all policies
     */
    @GET
    @Path("/policies")
    public Response getPolicies(@Context SecurityContext securityContext) {
        // Only admins can view policies
        if (securityContext.getUserPrincipal() == null || 
            !securityContext.isUserInRole("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Admin role required"))
                    .build();
        }
        
        List<AbacPolicy> policies = policyStore.getAllPolicies();
        return Response.ok(policies).build();
    }
    
    /**
     * Add new policy
     */
    @POST
    @Path("/policies")
    public Response addPolicy(AbacPolicy policy, @Context SecurityContext securityContext) {
        // Only admins can add policies
        if (securityContext.getUserPrincipal() == null || 
            !securityContext.isUserInRole("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Admin role required"))
                    .build();
        }
        
        try {
            policyStore.addPolicy(policy);
            LOGGER.info("Policy added: " + policy.getName());
            
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of("message", "Policy added successfully", "id", policy.getId()))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.severe("Failed to add policy: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Delete policy
     */
    @DELETE
    @Path("/policies/{policyId}")
    public Response deletePolicy(@PathParam("policyId") String policyId, 
                                  @Context SecurityContext securityContext) {
        // Only admins can delete policies
        if (securityContext.getUserPrincipal() == null || 
            !securityContext.isUserInRole("ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Admin role required"))
                    .build();
        }
        
        policyStore.removePolicy(policyId);
        LOGGER.info("Policy deleted: " + policyId);
        
        return Response.ok()
                .entity(Map.of("message", "Policy deleted successfully"))
                .build();
    }
}
