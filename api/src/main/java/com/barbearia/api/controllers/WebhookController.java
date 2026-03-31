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
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

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
            if (signature == null) {
                signature = request.getHeader("x-webhook-signature");
            }

            boolean assinaturaValida = isAssinaturaValida(rawPayload, signature, webhookSecret);

            if (!assinaturaValida) {
                System.err.println("[SECURITY WARN] Webhook recebido com assinatura inválida. IP suspeito.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
            String correlationId = java.util.UUID.randomUUID().toString();

            System.err.println("[ERROR] Webhook falhou. Ref: " + correlationId + " | Erro: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Ocorreu um erro interno. Referência: " + correlationId));
        }
    }

    private boolean isAssinaturaValida(byte[] payload, String headerSignature, String secret) {
        if (headerSignature == null || secret == null)
            return false;
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKey);

            byte[] hashCalculado = hmacSha256.doFinal(payload);
            byte[] hashRecebido = Base64.getDecoder().decode(headerSignature);

            if (MessageDigest.isEqual(hashCalculado, hashRecebido)) {
                return true;
            } else {
                System.err.println("[SECURITY] Criptografia calculada não coincidiu com a assinatura do Webhook.");
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}