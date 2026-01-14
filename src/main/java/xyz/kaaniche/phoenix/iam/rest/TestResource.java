package xyz.kaaniche.phoenix.iam.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/test")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestResource {
    
    @GET
    public Response testGet() {
        return Response.ok(Map.of("status", "ok", "message", "test endpoint works")).build();
    }
    
    @POST
    @Path("/string")
    public Response testPost(String body) {
        return Response.ok(Map.of("received", body, "length", body.length())).build();
    }
    
    @POST
    @Path("/dto")
    public Response testDto(xyz.kaaniche.phoenix.iam.rest.dto.RegisterRequest dto) {
        return Response.ok(Map.of("username", dto.getUsername(), "email", dto.getEmail())).build();
    }
}
