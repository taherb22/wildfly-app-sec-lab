package xyz.kaaniche.phoenix.iam.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.servlet.http.HttpServletRequest;
import xyz.kaaniche.phoenix.iam.entity.User;
import xyz.kaaniche.phoenix.iam.security.QrCodeService;
import xyz.kaaniche.phoenix.iam.security.RateLimiter;
import xyz.kaaniche.phoenix.iam.security.TotpService;
import xyz.kaaniche.phoenix.iam.service.JwtService;
import xyz.kaaniche.phoenix.iam.service.UserService;
import xyz.kaaniche.phoenix.iam.util.RequestUtil;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOGGER = Logger.getLogger(AuthResource.class.getName());

    @Inject
    private UserService userService;

    @Inject
    private JwtService jwtService;

    @Inject
    private RateLimiter rateLimiter;

    @Inject
    private TotpService totpService;

    @Inject
    private QrCodeService qrCodeService;

    @POST
    @Path("/login")
    public Response login(Map<String, String> credentials, @Context HttpServletRequest request) {
        try {
            RateLimiter.RateLimitResult rateLimit = rateLimiter.check("api-login:" + RequestUtil.clientIp(request));
            if (!rateLimit.allowed()) {
                return Response.status(Status.TOO_MANY_REQUESTS)
                        .entity(Map.of(
                                "error", "too_many_requests",
                                "error_description", "Retry after " + rateLimit.retryAfterSeconds() + " seconds",
                                "retry_after", rateLimit.retryAfterSeconds()
                        ))
                        .header("Retry-After", rateLimit.retryAfterSeconds())
                        .build();
            }
            String username = credentials.get("username");
            String password = credentials.get("password");

            LOGGER.info("Login attempt for user: " + username);

            if (username == null || password == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Username and password required"))
                        .build();
            }

            if (userService.authenticate(username, password)) {
                User user = userService.findByUsername(username).orElseThrow();
                if (user.isTotpEnabled()) {
                    String totp = credentials.get("totp");
                    if (totp == null || !totpService.verifyCode(user.getTotpSecret(), totp)) {
                        return Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("error", "mfa_required", "error_description", "valid totp code required"))
                                .build();
                    }
                }
                userService.updateLastLogin(user.getId());
                
                LOGGER.info("User authenticated, generating token for: " + username);
                
                String token = jwtService.generateToken(username, user.getRoles());
                
                LOGGER.info("Token generated successfully for: " + username);
                
                return Response.ok(Map.of(
                    "token", token,
                    "username", username,
                    "roles", user.getRoles()
                )).build();
            }

            LOGGER.warning("Authentication failed for user: " + username);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid credentials"))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during login", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Token generation failed", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/register")
    public Response register(Map<String, String> userData) {
        try {
            String username = userData.get("username");
            String email = userData.get("email");
            String password = userData.get("password");

            if (username == null || email == null || password == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Username, email and password required"))
                        .build();
            }

            if (userService.findByUsername(username).isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "Username already exists"))
                        .build();
            }

            User user = userService.createUser(username, email, password, "USER");
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of("id", user.getId(), "username", user.getUsername()))
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during registration", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Registration failed", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/mfa/enroll")
    public Response enrollTotp(Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        if (username == null || password == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password required"))
                    .build();
        }
        User user = userService.findByUsername(username).orElse(null);
        if (user == null || !userService.authenticate(username, password)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid credentials"))
                    .build();
        }
        if (user.isTotpEnabled()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "TOTP already enabled"))
                    .build();
        }
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        userService.updateUser(user);
        String otpauth = totpService.buildOtpAuthUri(username, secret);
        String qrDataUri;
        try {
            qrDataUri = qrCodeService.toSvgDataUri(otpauth, 200);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "QR generation failed"))
                    .build();
        }
        return Response.ok(Map.of(
                "secret", secret,
                "otpauth_uri", otpauth,
                "qr_data_uri", qrDataUri
        )).build();
    }

    @POST
    @Path("/mfa/verify")
    public Response verifyTotp(Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String code = payload.get("code");
        if (username == null || password == null || code == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username, password and code required"))
                    .build();
        }
        User user = userService.findByUsername(username).orElse(null);
        if (user == null || !userService.authenticate(username, password)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid credentials"))
                    .build();
        }
        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "TOTP not enrolled"))
                    .build();
        }
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid TOTP code"))
                    .build();
        }
        user.setTotpEnabled(true);
        userService.updateUser(user);
        return Response.ok(Map.of("status", "TOTP enabled")).build();
    }
}
