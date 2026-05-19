package com.innov8.loyaltypos.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TotpService {
    private TotpService() {}

    public static class VerifyResult {
        public boolean valid;
        public String error;
        public VerifyResult(boolean valid) { this.valid = valid; }
        public VerifyResult(boolean valid, String error) { this.valid = valid; this.error = error; }
    }

    public static class SetupResult {
        public String secret;
        public String qrDataUrl;
    }

    public static String getSecret() {
        String s = SettingsService.getString("totp_secret", "");
        return s == null || s.isEmpty() ? null : s;
    }

    public static boolean isConfigured() { return getSecret() != null; }

    public static VerifyResult verify(String token) {
        String secret = getSecret();
        if (secret == null) return new VerifyResult(false, "no_secret");
        try {
            TimeProvider tp = new SystemTimeProvider();
            CodeGenerator cg = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
            DefaultCodeVerifier verifier = new DefaultCodeVerifier(cg, tp);
            verifier.setTimePeriod(30);
            verifier.setAllowedTimePeriodDiscrepancy(1);
            return new VerifyResult(verifier.isValidCode(secret, token == null ? "" : token.trim()));
        } catch (Exception e) {
            return new VerifyResult(false, "verify_failed");
        }
    }

    public static SetupResult setup() {
        SecretGenerator sg = new DefaultSecretGenerator();
        String secret = sg.generate();
        String biz = SettingsService.getString("business_name", "Loyalty POS");
        String label = URLEncoder.encode("Admin", StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode(biz, StandardCharsets.UTF_8);
        String uri = "otpauth://totp/" + issuer + ":" + label + "?secret=" + secret + "&issuer=" + issuer;

        String dataUrl;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(uri, BarcodeFormat.QR_CODE, 256, 256);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("QR generation failed: " + e.getMessage(), e);
        }

        SettingsService.set("totp_secret", secret);
        SetupResult r = new SetupResult();
        r.secret = secret;
        r.qrDataUrl = dataUrl;
        return r;
    }

    public static void reset() {
        SettingsService.set("totp_secret", "");
    }
}
