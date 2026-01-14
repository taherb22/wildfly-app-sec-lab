package xyz.kaaniche.phoenix.stego;

import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Image Quality Assessment Service
 * Calculates PSNR and MSE for steganography validation
 */
@ApplicationScoped
public class ImageQualityService {
    private static final Logger LOGGER = Logger.getLogger(ImageQualityService.class.getName());
    private static final double MIN_PSNR_THRESHOLD = 30.0; // dB
    
    /**
     * Calculate Peak Signal-to-Noise Ratio (PSNR) between two images
     * Higher PSNR = better quality (less distortion)
     * Typical values: 30-50 dB (30 dB = acceptable, 40+ dB = excellent)
     */
    public double calculatePSNR(byte[] originalImage, byte[] modifiedImage) throws IOException {
        BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(originalImage));
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(modifiedImage));
        
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            throw new IllegalArgumentException("Images must have same dimensions");
        }
        
        double mse = calculateMSE(img1, img2);
        
        if (mse == 0) {
            return Double.POSITIVE_INFINITY; // Identical images
        }
        
        double maxPixelValue = 255.0;
        double psnr = 10 * Math.log10((maxPixelValue * maxPixelValue) / mse);
        
        return psnr;
    }
    
    /**
     * Calculate Mean Squared Error (MSE) between two images
     * Lower MSE = more similar images
     */
    public double calculateMSE(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();
        
        double sumSquaredError = 0.0;
        int totalPixels = width * height * 3; // RGB channels
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                
                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;
                
                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;
                
                sumSquaredError += Math.pow(r1 - r2, 2);
                sumSquaredError += Math.pow(g1 - g2, 2);
                sumSquaredError += Math.pow(b1 - b2, 2);
            }
        }
        
        return sumSquaredError / totalPixels;
    }
    
    /**
     * Validate image quality meets threshold
     */
    public boolean validateQuality(byte[] originalImage, byte[] modifiedImage) throws IOException {
        double psnr = calculatePSNR(originalImage, modifiedImage);
        LOGGER.info("Image quality: PSNR = " + String.format("%.2f", psnr) + " dB");
        
        boolean acceptable = psnr >= MIN_PSNR_THRESHOLD;
        
        if (!acceptable) {
            LOGGER.warning("PSNR below minimum threshold: " + psnr + " < " + MIN_PSNR_THRESHOLD);
        }
        
        return acceptable;
    }
    
    /**
     * Get quality assessment report
     */
    public QualityReport assess(byte[] originalImage, byte[] modifiedImage) throws IOException {
        BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(originalImage));
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(modifiedImage));
        
        double mse = calculateMSE(img1, img2);
        double psnr = 10 * Math.log10((255.0 * 255.0) / mse);
        
        return new QualityReport(mse, psnr, psnr >= MIN_PSNR_THRESHOLD);
    }
    
    public static class QualityReport {
        private final double mse;
        private final double psnr;
        private final boolean acceptable;
        
        public QualityReport(double mse, double psnr, boolean acceptable) {
            this.mse = mse;
            this.psnr = psnr;
            this.acceptable = acceptable;
        }
        
        public double getMse() {
            return mse;
        }
        
        public double getPsnr() {
            return psnr;
        }
        
        public boolean isAcceptable() {
            return acceptable;
        }
        
        @Override
        public String toString() {
            return String.format("MSE: %.4f, PSNR: %.2f dB, Acceptable: %s", mse, psnr, acceptable);
        }
    }
}
