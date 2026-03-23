package com.barbearia.api.controllers;

import com.barbearia.api.models.StatusPagamento;
import com.barbearia.api.services.AgendamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/abacatepay")
public class WebhookController {

    @Autowired
    private AgendamentoService agendamentoService;

    @PostMapping
    public ResponseEntity<Void> receberNotificacao(@RequestBody Map<String, Object> payload) {
        System.out.println(">>> Webhook AbacatePay recebido!");

        try {
            String event = (String) payload.get("event");

            if ("billing.paid".equalsIgnoreCase(event)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                Map<String, Object> billing = (Map<String, Object>) data.get("billing");
                List<Map<String, Object>> products = (List<Map<String, Object>>) billing.get("products");

                if (products != null && !products.isEmpty()) {
                    String externalId = (String) products.get(0).get("externalId");

                    if (externalId != null && externalId.startsWith("ag_")) {
                        Long id = Long.parseLong(externalId.split("_")[1]);
                        agendamentoService.atualizarStatusPagamento(id, StatusPagamento.PAGO);
                        System.out.println(">>> SUCESSO! Pagamento atualizado na BD para o Agendamento ID: " + id);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(">>> Erro ao processar webhook: " + e.getMessage());
        }

        // Retorna sempre 200 OK para o AbacatePay saber que recebemos
        return ResponseEntity.ok().build();
    }
}