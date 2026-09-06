package com.barbearia.api.services;

import com.barbearia.api.models.*;
import com.barbearia.api.repositories.AgendamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private final AgendamentoRepository repository;
    @Value("${abacatepay.dev-mode:false}")
    private boolean devMode;

    @Transactional
    public void processar(JsonNode evento) {
        if (!"billing.paid".equals(evento.path("event").asText())) return;
        if (!evento.path("devMode").isBoolean() || evento.path("devMode").asBoolean() != devMode) {
            throw new IllegalArgumentException("Ambiente de pagamento incompatível.");
        }
        JsonNode data = evento.path("data");
        JsonNode billing = data.has("billing") ? data.path("billing") : data;
        String id = billing.path("id").asText();
        if (id.isBlank() || !"PAID".equals(billing.path("status").asText())) {
            throw new IllegalArgumentException("Cobrança inválida.");
        }
        var ag = repository.findByBillingIdComBloqueio(id)
                .orElseThrow(() -> new IllegalArgumentException("Cobrança desconhecida."));
        JsonNode amount = billing.path("amount");
        if (!amount.isIntegralNumber() || !amount.canConvertToInt()
                || ag.getBillingAmount() == null || amount.intValue() != ag.getBillingAmount()) {
            throw new IllegalArgumentException("Valor de pagamento incompatível.");
        }
        if (ag.getStatusPagamento() == StatusPagamento.PAGO) return;
        // Registrar o recebimento sem reabrir agenda cancelada, concluída ou em negociação.
        ag.setStatusPagamento(StatusPagamento.PAGO);
        repository.save(ag);
    }
}
