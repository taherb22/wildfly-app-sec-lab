package xyz.kaaniche.phoenix.stego;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * MinIO Client Service for cover image storage
 * For production, use MinIO Java SDK
 */
@ApplicationScoped
public class MinioService {
    private static final Logger LOGGER = Logger.getLogger(MinioService.class.getName());
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    
    public MinioService() {
        Config config = ConfigProvider.getConfig();
        this.endpoint = config.getOptionalValue("minio.endpoint", String.class)
                .orElse("http://localhost:9000");
        this.accessKey = config.getOptionalValue("minio.access.key", String.class)
                .orElse("minioadmin");
        this.secretKey = config.getOptionalValue("minio.secret.key", String.class)
                .orElse("minioadmin");
        this.bucket = config.getOptionalValue("minio.bucket", String.class)
                .orElse("stego-covers");
    }
    
    /**
     * Upload cover image to MinIO
     */
    public String uploadCoverImage(String objectName, byte[] imageData) throws IOException {
        String url = endpoint + "/" + bucket + "/" + objectName;
        
        LOGGER.info("Uploading cover image: " + objectName + " (" + imageData.length + " bytes)");
        
        // For production, use MinIO SDK
        // This is a simplified HTTP PUT implementation
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "image/png");
        
        // Basic auth for demo (production should use proper AWS signature)
        String auth = accessKey + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        
        conn.getOutputStream().write(imageData);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            throw new IOException("MinIO upload failed: " + responseCode);
        }
        
        LOGGER.info("Upload successful: " + url);
        return url;
    }
    
    /**
     * Download cover image from MinIO
     */
    public byte[] downloadCoverImage(String objectName) throws IOException {
        String url = endpoint + "/" + bucket + "/" + objectName;
        
        LOGGER.info("Downloading cover image: " + objectName);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        
        // Basic auth for demo
        String auth = accessKey + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("MinIO download failed: " + responseCode);
        }
        
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        byte[] imageData = baos.toByteArray();
        LOGGER.info("Download successful: " + imageData.length + " bytes");
        
        return imageData;
    }
    
    /**
     * Check if object exists
     */
    public boolean exists(String objectName) {
        try {
            String url = endpoint + "/" + bucket + "/" + objectName;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            
            String auth = accessKey + ":" + secretKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            
            return conn.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }
}
