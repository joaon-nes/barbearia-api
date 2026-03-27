package com.barbearia.api.controllers;

import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.models.StatusPagamento;
import com.barbearia.api.repositories.AgendamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final AgendamentoRepository agendamentoRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${abacatepay.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/abacatepay")
    public ResponseEntity<?> receberWebhook(HttpServletRequest request) {

        try {
            byte[] rawPayload = request.getInputStream().readAllBytes();

            String signature = request.getHeader("X-Webhook-Signature");
            if (signature == null)
                signature = request.getHeader("x-webhook-signature");

            String headerSecret = request.getHeader("X-Webhook-Secret");
            if (headerSecret == null)
                headerSecret = request.getHeader("x-webhook-secret");

            boolean mathValida = isAssinaturaValida(rawPayload, signature, webhookSecret);
            boolean segredoValido = webhookSecret.equals(headerSecret);

            if (!mathValida && !segredoValido) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acesso negado");
            }

            JsonNode rootNode = mapper.readTree(rawPayload);
            JsonNode billingNode = rootNode.path("data").path("billing");
            String statusTransacao = billingNode.path("status").asText();

            String externalId = null;
            JsonNode products = billingNode.path("products");
            if (products != null && products.isArray() && products.size() > 0) {
                externalId = products.get(0).path("externalId").asText();
            }

            if (externalId != null && externalId.startsWith("ag_")) {
                Long agendamentoId = Long.parseLong(externalId.replace("ag_", ""));

                if ("PAID".equalsIgnoreCase(statusTransacao)) {
                    agendamentoRepository.findById(agendamentoId).ifPresent(ag -> {
                        ag.setStatusPagamento(StatusPagamento.PAGO);
                        ag.setStatus(StatusAgendamento.AGENDADO);
                        agendamentoRepository.save(ag);
                    });
                } else if ("CANCELLED".equalsIgnoreCase(statusTransacao) || "EXPIRED".equalsIgnoreCase(statusTransacao)
                        || "FAILED".equalsIgnoreCase(statusTransacao)) {
                    agendamentoRepository.findById(agendamentoId).ifPresent(ag -> {
                        ag.setStatusPagamento(StatusPagamento.CANCELADO);
                        ag.setStatus(StatusAgendamento.CANCELADO);
                        agendamentoRepository.save(ag);
                    });
                } else {
                    System.out.println("Status: " + statusTransacao + ". Aguardando...");
                }
            } else {
                System.out.println("ID do Agendamento não encontrado.");
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("Erro interno: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isAssinaturaValida(byte[] payload, String headerSignature, String secret) {
        if (headerSignature == null || secret == null)
            return false;
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKey);

            byte[] hash = hmacSha256.doFinal(payload);
            String calculada = Base64.getEncoder().encodeToString(hash);

            if (calculada.equals(headerSignature)) {
                return true;
            } else {
                System.out.println("-> Criptografia calculada não coincidiu.");
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}