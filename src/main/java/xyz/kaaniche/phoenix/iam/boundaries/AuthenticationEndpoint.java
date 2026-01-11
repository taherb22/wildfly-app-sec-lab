package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import com.google.common.html.HtmlEscapers;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import xyz.kaaniche.phoenix.iam.controllers.PhoenixIAMRepository;
import xyz.kaaniche.phoenix.iam.entities.Grant;
import xyz.kaaniche.phoenix.iam.entities.Identity;
import xyz.kaaniche.phoenix.iam.entities.Tenant;
import xyz.kaaniche.phoenix.iam.security.Argon2Utility;
import xyz.kaaniche.phoenix.iam.security.AuthorizationCode;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;



@Path("/")
@RequestScoped
public class AuthenticationEndpoint {
    public static final String CHALLENGE_RESPONSE_COOKIE_ID = "signInId";
    public static final String STATE_COOKIE_ID = "oauthState";
    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Za-z0-9\\-\\._~]{16,512}$");
    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");
    @Inject
    private Logger logger;

    @Inject
    PhoenixIAMRepository phoenixIAMRepository;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/authorize")
    public Response authorize(@Context UriInfo uriInfo) {
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        //1. Check tenant
        String clientId = params.getFirst("client_id");
        if (clientId == null || clientId.isEmpty()) {
            return informUserAboutError("you should provide client_id");
        }
        Tenant tenant = phoenixIAMRepository.findTenantByName(clientId);
        if (tenant == null) {
            return informUserAboutError("Invalid cred");
        }
        //2. Client Authorized Grant Type
        if (tenant.getSupportedGrantTypes() != null && !tenant.getSupportedGrantTypes().contains("authorization_code")) {
            return informUserAboutError("Authorization Grant type, authorization_code, is not allowed for this tenant :" + clientId);
        }
        //3. redirectUri
        String redirectUri = params.getFirst("redirect_uri");
        if (tenant.getRedirectUri() != null && !tenant.getRedirectUri().isEmpty()) {
            if (redirectUri != null && !redirectUri.isEmpty() && !tenant.getRedirectUri().equals(redirectUri)) {
                //sould be in the client.redirectUri
                return informUserAboutError("redirect_uri is pre-registred and should match");
            }
            redirectUri = tenant.getRedirectUri();
        } else {
            if (redirectUri == null || redirectUri.isEmpty()) {
                return informUserAboutError("redirect_uri is not pre-registred and should be provided");
            }
        }

        //4. response_type
        String responseType = params.getFirst("response_type");
        if (!"code".equals(responseType) && !"token".equals(responseType)) {
            String error = "invalid_grant :" + responseType + ", response_type params should be code or token:";
            return informUserAboutError(error);
        }

        String state = params.getFirst("state");
        if (!isValidState(state)) {
            return informUserAboutError("invalid_request : state is required and must be 16-512 characters from [A-Z a-z 0-9 - . _ ~]");
        }

        //5. check scope
        String requestedScope = params.getFirst("scope");
        if (requestedScope == null || requestedScope.isEmpty()) {
            requestedScope = tenant.getRequiredScopes();
        }

        if ("code".equals(responseType)) {
            //6. code_challenge_method must be S256
            String codeChallengeMethod = params.getFirst("code_challenge_method");
            if (codeChallengeMethod == null || !codeChallengeMethod.equals("S256")) {
                String error = "invalid_grant :" + codeChallengeMethod + ", code_challenge_method must be 'S256'";
                return informUserAboutError(error);
            }
            String codeChallenge = params.getFirst("code_challenge");
            if (!isValidCodeChallenge(codeChallenge)) {
                return informUserAboutError("invalid_request : code_challenge must be base64url (43-128 chars)");
            }
        }
        StreamingOutput stream = output -> {
            try (InputStream is = Objects.requireNonNull(getClass().getResource("/login.html")).openStream()){
                output.write(is.readAllBytes());
            }
        };
        return Response.ok(stream).location(uriInfo.getBaseUri().resolve("/login/authorization"))
                .cookie(new NewCookie.Builder(CHALLENGE_RESPONSE_COOKIE_ID)
                .httpOnly(true).secure(true).sameSite(NewCookie.SameSite.STRICT).value(tenant.getName()+"#"+requestedScope+"$"+redirectUri).build(),
                        new NewCookie.Builder(STATE_COOKIE_ID)
                .httpOnly(true).secure(true).sameSite(NewCookie.SameSite.STRICT).value(state).maxAge(300).build()).build();
    }

    @POST
    @Path("/login/authorization")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response login(@CookieParam(CHALLENGE_RESPONSE_COOKIE_ID) Cookie cookie,
                          @CookieParam(STATE_COOKIE_ID) Cookie stateCookie,
                          @FormParam("username")String username,
                          @FormParam("password")String password,
                          @Context UriInfo uriInfo) throws Exception {
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isEmpty()) {
            return informUserAboutError("invalid_request : missing sign-in context");
        }
        Identity identity = phoenixIAMRepository.findIdentityByUsername(username);
        if (identity == null || !Argon2Utility.check(identity.getPassword(), password.toCharArray())) {//check if the identity is Null to prevent server error (prevent NPE)
        logger.info("Failure when authenticating identity:" + username);
         return informUserAboutError("User doesn't approved the request."); 
           }           
           logger.info("Authenticated identity:"+username);
            MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
            String responseType = params.getFirst("response_type");
            String state = params.getFirst("state");
            Response stateError = validateState(state, stateCookie);
            if (stateError != null) {
                return stateError;
            }
            String codeChallenge = params.getFirst("code_challenge");
            if ("code".equals(responseType) && !isValidCodeChallenge(codeChallenge)) {
                return informUserAboutError("invalid_request : code_challenge must be base64url (43-128 chars)");
            }
            Optional<Grant> grant = phoenixIAMRepository.findGrant(cookie.getValue().split("#")[0],identity.getId());
            if(grant.isPresent()){
                String redirectURI = buildActualRedirectURI(
                        cookie.getValue().split("\\$")[1],responseType,
                        cookie.getValue().split("#")[0],
                        username,
                        checkUserScopes(grant.get().getApprovedScopes(),cookie.getValue().split("#")[1].split("\\$")[0])
                        ,codeChallenge,state
                );
                return Response.seeOther(UriBuilder.fromUri(redirectURI).build())
                        .cookie(expireStateCookie())
                        .build();
            }else{
                StreamingOutput stream = output -> {
                    try (InputStream is = Objects.requireNonNull(getClass().getResource("/consent.html")).openStream()){
                        output.write(is.readAllBytes());
                    }
                };
                return Response.ok(stream).build();
            }
        //else {
        //    logger.info("Failure when authenticating identity:"+username);
        //    URI location = UriBuilder.fromUri(cookie.getValue().split("\\$")[1])
        //            .queryParam("error", "User doesn't approved the request.")
        //            .queryParam("error_description", "User doesn't approved the request.")
        //            .build();
        //    return Response.seeOther(location).build();
        
    }

    @PATCH
    @Path("/login/authorization")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response grantConsent(@CookieParam(CHALLENGE_RESPONSE_COOKIE_ID) Cookie cookie,
                                 @CookieParam(STATE_COOKIE_ID) Cookie stateCookie,
                                 @FormParam("approved_scope") String scope,
                                 @FormParam("approval_status") String approvalStatus,
                                 @FormParam("username") String username,
                                 @Context UriInfo uriInfo){
        if ("NO".equals(approvalStatus)) {
            URI location = UriBuilder.fromUri(cookie.getValue().split("\\$")[1])
                    .queryParam("error", "User doesn't approved the request.")
                    .queryParam("error_description", "User doesn't approved the request.")
                    .build();
            return Response.seeOther(location).build();
        }
        String state = uriInfo.getQueryParameters().getFirst("state");
        Response stateError = validateState(state, stateCookie);
        if (stateError != null) {
            return stateError;
        }
        String responseType = uriInfo.getQueryParameters().getFirst("response_type");
        String codeChallenge = uriInfo.getQueryParameters().getFirst("code_challenge");
        if ("code".equals(responseType) && !isValidCodeChallenge(codeChallenge)) {
            return informUserAboutError("invalid_request : code_challenge must be base64url (43-128 chars)");
        }
        //==> YES
        List<String> approvedScopes = Arrays.stream(scope.split(" ")).toList();
        if (approvedScopes.isEmpty()) {
            URI location = UriBuilder.fromUri(cookie.getValue().split("\\$")[1])
                    .queryParam("error", "User doesn't approved the request.")
                    .queryParam("error_description", "User doesn't approved the request.")
                    .build();
            return Response.seeOther(location).build();
        }
        try {
            return Response.seeOther(UriBuilder.fromUri(buildActualRedirectURI(
                    cookie.getValue().split("\\$")[1],responseType,
                    cookie.getValue().split("#")[0],username, String.join(" ", approvedScopes), codeChallenge,state
            )).build())
                    .cookie(expireStateCookie())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildActualRedirectURI(String redirectUri,String responseType,String clientId,String userId,String approvedScopes,String codeChallenge,String state) throws Exception {
        StringBuilder sb = new StringBuilder(redirectUri);
        if ("code".equals(responseType)) {
            AuthorizationCode authorizationCode = new AuthorizationCode(clientId,userId,
                    approvedScopes, Instant.now().plus(2, ChronoUnit.MINUTES).getEpochSecond(),redirectUri);
            sb.append("?code=").append(URLEncoder.encode(authorizationCode.getCode(codeChallenge), StandardCharsets.UTF_8));
        } else {
            //Implicit: responseType=token : Not Supported
            return null;
        }
        if (state != null) {
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String checkUserScopes(String userScopes, String requestedScope) {
        Set<String> allowedScopes = new LinkedHashSet<>();
        Set<String> rScopes = new HashSet<>(Arrays.asList(requestedScope.split(" ")));
        Set<String> uScopes = new HashSet<>(Arrays.asList(userScopes.split(" ")));
        for (String scope : uScopes) {
            if (rScopes.contains(scope)) allowedScopes.add(scope);
        }
        return String.join( " ", allowedScopes);
    }

    private Response informUserAboutError(String error) {
    String safe = HtmlEscapers.htmlEscaper().escape(error); //  prevent XSS attacks by escaping user-provided content
    return Response.status(Response.Status.BAD_REQUEST)
        .entity("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"/><title>Error</title></head>
            <body><aside class="container"><p>%s</p></aside></body>
            </html>
            """.formatted(safe)).build();
}

    private Response validateState(String state, Cookie stateCookie) {
        if (!isValidState(state)) {
            return informUserAboutError("invalid_request : state is required and must be 16-512 characters from [A-Z a-z 0-9 - . _ ~]");
        }
        if (stateCookie == null || stateCookie.getValue() == null || !state.equals(stateCookie.getValue())) {
            return informUserAboutError("invalid_request : state mismatch");
        }
        return null;
    }

    private boolean isValidState(String state) {
        return state != null && STATE_PATTERN.matcher(state).matches();
    }

    private boolean isValidCodeChallenge(String codeChallenge) {
        return codeChallenge != null && CODE_CHALLENGE_PATTERN.matcher(codeChallenge).matches();
    }

    private NewCookie expireStateCookie() {
        return new NewCookie.Builder(STATE_COOKIE_ID)
                .value("")
                .path("/")
                .maxAge(0)
                .build();
    }
}
