package com.barbearia.api.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class WebhookSignatureService {
    @Value("${abacatepay.webhook.secret}")
    private String secret;
    @Value("${abacatepay.webhook.signature-key}")
    private String signatureKey;

    public boolean validar(byte[] payload, String signature, String suppliedSecret) {
        if (secret == null || secret.isBlank() || signatureKey == null || signatureKey.isBlank()
                || suppliedSecret == null || signature == null) return false;
        if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8))) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signatureKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(payload), Base64.getDecoder().decode(signature));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }
}
