package xyz.kaaniche.phoenix.stego;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Steganography Service - LSB embedding in spatial domain
 * For production DCT-based embedding, integrate OpenCV
 */
@ApplicationScoped
public class SteganographyService {
    private static final Logger LOGGER = Logger.getLogger(SteganographyService.class.getName());
    private static final int BITS_PER_BYTE = 8;
    
    @Inject
    private EncryptionService encryptionService;
    
    @Inject
    private ImageQualityService qualityService;
    
    /**
     * Embed encrypted data into cover image using LSB
     */
    public byte[] embed(byte[] coverImage, byte[] secretData, String encryptionKey) throws IOException {
        // Encrypt data first
        javax.crypto.SecretKey key = encryptionService.decodeKey(encryptionKey);
        byte[] encrypted = encryptionService.encrypt(secretData, key);
        
        // Load cover image
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(coverImage));
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Check capacity
        int maxBytes = (width * height * 3) / BITS_PER_BYTE; // RGB channels
        int requiredBytes = encrypted.length + 4; // +4 for length prefix
        
        if (requiredBytes > maxBytes) {
            throw new IllegalArgumentException("Secret data too large for cover image. " +
                    "Required: " + requiredBytes + " bytes, available: " + maxBytes + " bytes");
        }
        
        LOGGER.info("Embedding " + encrypted.length + " bytes into " + width + "x" + height + " image");
        
        // Embed length prefix (4 bytes)
        int bitIndex = 0;
        bitIndex = embedInteger(image, encrypted.length, bitIndex);
        
        // Embed encrypted data
        bitIndex = embedBytes(image, encrypted, bitIndex);
        
        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] stegoImage = baos.toByteArray();
        
        // Validate quality
        double psnr = qualityService.calculatePSNR(coverImage, stegoImage);
        LOGGER.info("Embedding complete. PSNR: " + String.format("%.2f", psnr) + " dB");
        
        if (psnr < 30.0) {
            LOGGER.warning("PSNR below recommended threshold (30 dB)");
        }
        
        return stegoImage;
    }
    
    /**
     * Extract encrypted data from stego image using LSB
     */
    public byte[] extract(byte[] stegoImage, String encryptionKey) throws IOException {
        // Load stego image
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(stegoImage));
        
        // Extract length prefix
        int bitIndex = 0;
        int length = extractInteger(image, bitIndex);
        bitIndex += 32;
        
        LOGGER.info("Extracting " + length + " bytes from stego image");
        
        if (length <= 0 || length > 10_000_000) { // Sanity check
            throw new IllegalArgumentException("Invalid embedded data length: " + length);
        }
        
        // Extract encrypted data
        byte[] encrypted = extractBytes(image, length, bitIndex);
        
        // Decrypt data
        javax.crypto.SecretKey key = encryptionService.decodeKey(encryptionKey);
        byte[] decrypted = encryptionService.decrypt(encrypted, key);
        
        LOGGER.info("Extraction complete. Recovered " + decrypted.length + " bytes");
        
        return decrypted;
    }
    
    private int embedInteger(BufferedImage image, int value, int startBit) {
        int bitIndex = startBit;
        for (int i = 31; i >= 0; i--) {
            int bit = (value >> i) & 1;
            bitIndex = embedBit(image, bit, bitIndex);
        }
        return bitIndex;
    }
    
    private int extractInteger(BufferedImage image, int startBit) {
        int value = 0;
        for (int i = 0; i < 32; i++) {
            int bit = extractBit(image, startBit + i);
            value = (value << 1) | bit;
        }
        return value;
    }
    
    private int embedBytes(BufferedImage image, byte[] data, int startBit) {
        int bitIndex = startBit;
        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                int bit = (b >> i) & 1;
                bitIndex = embedBit(image, bit, bitIndex);
            }
        }
        return bitIndex;
    }
    
    private byte[] extractBytes(BufferedImage image, int length, int startBit) {
        byte[] data = new byte[length];
        int bitIndex = startBit;
        
        for (int i = 0; i < length; i++) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                int bit = extractBit(image, bitIndex++);
                b = (b << 1) | bit;
            }
            data[i] = (byte) b;
        }
        
        return data;
    }
    
    private int embedBit(BufferedImage image, int bit, int bitIndex) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int pixelIndex = bitIndex / 3;
        int channel = bitIndex % 3;
        
        int x = pixelIndex % width;
        int y = pixelIndex / width;
        
        int rgb = image.getRGB(x, y);
        int[] channels = new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        
        // Modify LSB
        channels[channel] = (channels[channel] & 0xFE) | bit;
        
        int newRgb = (channels[0] << 16) | (channels[1] << 8) | channels[2];
        image.setRGB(x, y, newRgb);
        
        return bitIndex + 1;
    }
    
    private int extractBit(BufferedImage image, int bitIndex) {
        int width = image.getWidth();
        int pixelIndex = bitIndex / 3;
        int channel = bitIndex % 3;
        
        int x = pixelIndex % width;
        int y = pixelIndex / width;
        
        int rgb = image.getRGB(x, y);
        int[] channels = new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        
        return channels[channel] & 1;
    }
}
