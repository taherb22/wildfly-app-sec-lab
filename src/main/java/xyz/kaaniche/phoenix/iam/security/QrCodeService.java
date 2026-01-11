package xyz.kaaniche.phoenix.iam.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class QrCodeService {
    public String toSvgDataUri(String content, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        String svg = toSvg(matrix);
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    private String toSvg(BitMatrix matrix) {
        StringBuilder sb = new StringBuilder();
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width)
                .append(" ")
                .append(height)
                .append("\" shape-rendering=\"crispEdges\">");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
        sb.append("<path d=\"");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z");
                }
            }
        }
        sb.append("\" fill=\"black\"/></svg>");
        return sb.toString();
    }
}
