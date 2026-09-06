package com.barbearia.api.controllers;

import com.barbearia.api.services.WebhookService;
import com.barbearia.api.services.WebhookSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private static final int MAX_PAYLOAD = 65536;
    private final WebhookService service;
    private final WebhookSignatureService signatures;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/abacatepay")
    public ResponseEntity<Void> receberWebhook(HttpServletRequest request) throws IOException {
        byte[] payload = request.getInputStream().readNBytes(MAX_PAYLOAD + 1);
        if (payload.length > MAX_PAYLOAD) return ResponseEntity.status(413).build();
        if (!signatures.validar(payload, request.getHeader("X-Webhook-Signature"),
                request.getParameter("webhookSecret"))) return ResponseEntity.status(403).build();
        try {
            var evento = mapper.readTree(payload);
            if (evento == null || !evento.isObject()) return ResponseEntity.badRequest().build();
            service.processar(evento);
            return ResponseEntity.ok().build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
