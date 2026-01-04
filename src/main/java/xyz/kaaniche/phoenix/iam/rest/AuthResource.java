package xyz.kaaniche.phoenix.iam.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import xyz.kaaniche.phoenix.iam.entity.User;
import xyz.kaaniche.phoenix.iam.service.JwtService;
import xyz.kaaniche.phoenix.iam.service.UserService;

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

    @POST
    @Path("/login")
    public Response login(Map<String, String> credentials) {
        try {
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
}
