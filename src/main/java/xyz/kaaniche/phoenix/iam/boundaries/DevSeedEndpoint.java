package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import xyz.kaaniche.phoenix.iam.controllers.Role;
import xyz.kaaniche.phoenix.iam.entities.Grant;
import xyz.kaaniche.phoenix.iam.entities.GrantPK;
import xyz.kaaniche.phoenix.iam.entities.Identity;
import xyz.kaaniche.phoenix.iam.entities.Tenant;
import xyz.kaaniche.phoenix.iam.security.Argon2Utility;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dev-only endpoint to seed a tenant, identity, and grant for OAuth testing.
 * Not for production use.
 */
@Path("/dev/seed")
@PermitAll
public class DevSeedEndpoint {

    @PersistenceContext
    private EntityManager em;

    @POST
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    public Response seed() {
        // Create Tenant
        Tenant tenant = new Tenant();
        tenant.setName("TENANT_TEST");
        tenant.setSecret("secret");
        tenant.setRedirectUri("http://localhost/callback");
        tenant.setAllowedRoles(Long.MAX_VALUE);
        tenant.setRequiredScopes("openid profile");
        tenant.setSupportedGrantTypes("authorization_code");
        em.persist(tenant);

        // Create Identity
        Identity identity = new Identity();
        identity.setUsername("john");
        identity.setPassword(Argon2Utility.hash("SecurePass123!".toCharArray()));
        // Grant two custom roles (mapped via 'roles' config): first two bits
        identity.setRoles(Role.R_P00.getValue() | Role.R_P01.getValue());
        identity.setProvidedScopes("openid profile email");
        identity.setTotpEnabled(false);
        em.persist(identity);

        // Create Grant linking tenant and identity
        Grant grant = new Grant();
        GrantPK pk = new GrantPK();
        pk.setTenantId(tenant.getId());
        pk.setIdentityId(identity.getId());
        grant.setId(pk);
        grant.setTenant(tenant);
        grant.setIdentity(identity);
        grant.setApprovedScopes("openid profile");
        grant.setIssuanceDateTime(LocalDateTime.now());
        em.persist(grant);

        return Response.ok(Map.of(
                "tenant", tenant.getName(),
                "redirect_uri", tenant.getRedirectUri(),
                "username", identity.getUsername(),
                "password", "SecurePass123!",
                "approved_scopes", grant.getApprovedScopes()
        )).build();
    }
}
