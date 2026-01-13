package com.phoenix.iam;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/csp-report")
public class CspReportResource {

    private static final Logger LOGGER = Logger.getLogger(CspReportResource.class.getName());

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response report(String reportJson) {
        LOGGER.info("CSP Violation Reported: " + reportJson);
        // Can store in DB or monitoring system
        return Response.ok().build();
    }
}
