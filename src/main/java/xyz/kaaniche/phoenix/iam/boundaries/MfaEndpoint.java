package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import xyz.kaaniche.phoenix.iam.controllers.PhoenixIAMRepository;
import xyz.kaaniche.phoenix.iam.entities.Identity;
import xyz.kaaniche.phoenix.iam.security.Argon2Utility;
import xyz.kaaniche.phoenix.iam.security.QrCodeService;
import xyz.kaaniche.phoenix.iam.security.TotpService;

import java.util.Map;

@Path("/mfa")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MfaEndpoint {
    @Inject
    PhoenixIAMRepository phoenixIAMRepository;

    @Inject
    TotpService totpService;

    @Inject
    QrCodeService qrCodeService;

    @POST
    @Path("/enroll")
    public Response enroll(Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        if (username == null || password == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "username and password are required"))
                    .build();
        }
        Identity identity = phoenixIAMRepository.findIdentityByUsername(username);
        if (!Argon2Utility.check(identity.getPassword(), password.toCharArray())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "invalid credentials"))
                    .build();
        }
        if (identity.isTotpEnabled()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "totp already enabled"))
                    .build();
        }
        String secret = totpService.generateSecret();
        identity.setTotpSecret(secret);
        identity.setTotpEnabled(false);
        phoenixIAMRepository.updateIdentity(identity);

        String otpauth = totpService.buildOtpAuthUri(username, secret);
        String qrDataUri;
        try {
            qrDataUri = qrCodeService.toSvgDataUri(otpauth, 200);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "unable to generate qr"))
                    .build();
        }
        return Response.ok(Map.of(
                "secret", secret,
                "otpauth_uri", otpauth,
                "qr_data_uri", qrDataUri
        )).build();
    }

    @POST
    @Path("/verify")
    public Response verify(Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String code = payload.get("code");
        if (username == null || password == null || code == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "username, password and code are required"))
                    .build();
        }
        Identity identity = phoenixIAMRepository.findIdentityByUsername(username);
        if (!Argon2Utility.check(identity.getPassword(), password.toCharArray())) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "invalid credentials"))
                    .build();
        }
        if (identity.getTotpSecret() == null || identity.getTotpSecret().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "totp is not enrolled"))
                    .build();
        }
        if (!totpService.verifyCode(identity.getTotpSecret(), code)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "invalid totp code"))
                    .build();
        }
        identity.setTotpEnabled(true);
        phoenixIAMRepository.updateIdentity(identity);
        return Response.ok(Map.of("status", "totp enabled")).build();
    }
}
