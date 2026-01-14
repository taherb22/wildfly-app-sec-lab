package xyz.kaaniche.phoenix.stego;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;
import java.util.logging.Logger;

/**
 * REST API for steganography operations
 */
@Path("/stego")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SteganographyResource {
    private static final Logger LOGGER = Logger.getLogger(SteganographyResource.class.getName());
    
    @Inject
    private SteganographyService stegoService;
    
    @Inject
    private EncryptionService encryptionService;
    
    @Inject
    private ImageQualityService qualityService;
    
    @Inject
    private MinioService minioService;
    
    /**
     * Generate encryption key
     */
    @POST
    @Path("/generate-key")
    public Response generateKey() {
        try {
            javax.crypto.SecretKey key = encryptionService.generateKey();
            String encodedKey = encryptionService.encodeKey(key);
            
            return Response.ok()
                    .entity("{\"key\":\"" + encodedKey + "\"}")
                    .build();
        } catch (Exception e) {
            LOGGER.severe("Key generation failed: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Embed secret data into cover image
     */
    @POST
    @Path("/embed")
    public Response embed(EmbedRequest request) {
        try {
            byte[] coverImage = Base64.getDecoder().decode(request.getCoverImage());
            byte[] secretData = request.getSecretData().getBytes();
            
            byte[] stegoImage = stegoService.embed(coverImage, secretData, request.getEncryptionKey());
            
            // Calculate quality metrics
            ImageQualityService.QualityReport quality = qualityService.assess(coverImage, stegoImage);
            
            String encodedStego = Base64.getEncoder().encodeToString(stegoImage);
            
            return Response.ok()
                    .entity(new EmbedResponse(encodedStego, quality.getPsnr(), quality.getMse()))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.severe("Embedding failed: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    /**
     * Extract secret data from stego image
     */
    @POST
    @Path("/extract")
    public Response extract(ExtractRequest request) {
        try {
            byte[] stegoImage = Base64.getDecoder().decode(request.getStegoImage());
            
            byte[] secretData = stegoService.extract(stegoImage, request.getEncryptionKey());
            String secretText = new String(secretData);
            
            return Response.ok()
                    .entity("{\"secretData\":\"" + secretText + "\"}")
                    .build();
                    
        } catch (Exception e) {
            LOGGER.severe("Extraction failed: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    public static class EmbedRequest {
        private String coverImage; // Base64 encoded
        private String secretData;
        private String encryptionKey;
        
        public String getCoverImage() {
            return coverImage;
        }
        
        public void setCoverImage(String coverImage) {
            this.coverImage = coverImage;
        }
        
        public String getSecretData() {
            return secretData;
        }
        
        public void setSecretData(String secretData) {
            this.secretData = secretData;
        }
        
        public String getEncryptionKey() {
            return encryptionKey;
        }
        
        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }
    
    public static class EmbedResponse {
        private String stegoImage; // Base64 encoded
        private double psnr;
        private double mse;
        
        public EmbedResponse(String stegoImage, double psnr, double mse) {
            this.stegoImage = stegoImage;
            this.psnr = psnr;
            this.mse = mse;
        }
        
        public String getStegoImage() {
            return stegoImage;
        }
        
        public double getPsnr() {
            return psnr;
        }
        
        public double getMse() {
            return mse;
        }
    }
    
    public static class ExtractRequest {
        private String stegoImage; // Base64 encoded
        private String encryptionKey;
        
        public String getStegoImage() {
            return stegoImage;
        }
        
        public void setStegoImage(String stegoImage) {
            this.stegoImage = stegoImage;
        }
        
        public String getEncryptionKey() {
            return encryptionKey;
        }
        
        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }
}
