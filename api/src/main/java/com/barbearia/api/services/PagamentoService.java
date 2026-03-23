package com.barbearia.api.services;

import com.barbearia.api.models.Agendamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class PagamentoService {

    @Value("${abacatepay.api.key}")
    private String apiKey;

    @Value("${app.frontend.url}")
    private String urlFrontend;

    public String gerarLinkDePagamento(Agendamento agendamento) {
        try {
            int valorEmCentavos = agendamento.getServico().getPreco().multiply(new BigDecimal("100")).intValue();
            if (valorEmCentavos < 100)
                valorEmCentavos = 100;

            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("frequency", "ONE_TIME");
            payload.put("methods", List.of("PIX", "CARD"));

            payload.put("returnUrl", urlFrontend);
            payload.put("completionUrl", urlFrontend + "?pagamento=sucesso");

            payload.put("products", List.of(
                    Map.of(
                            "externalId", "ag_" + agendamento.getId(),
                            "name", "Serviço: " + agendamento.getServico().getNome(),
                            "quantity", 1,
                            "price", valorEmCentavos)));

            String emailCliente = agendamento.getCliente().getEmail();
            if (emailCliente == null || !emailCliente.contains("@"))
                emailCliente = "cliente.padrao@barbearia.com";

            String telefoneCliente = agendamento.getCliente().getTelefone();
            if (telefoneCliente == null || telefoneCliente.isBlank()) {
                telefoneCliente = "11999999999";
            } else {
                telefoneCliente = telefoneCliente.replaceAll("[^0-9]", "");
                if (telefoneCliente.length() < 10)
                    telefoneCliente = "11999999999";
            }

            String cpfCliente = "00000000000";

            payload.put("customer", Map.of(
                    "name", agendamento.getCliente().getNome(),
                    "email", emailCliente,
                    "cellphone", telefoneCliente,
                    "taxId", cpfCliente));

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(payload);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.abacatepay.com/v1/billing/create"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = mapper.readTree(response.body());
                return rootNode.path("data").path("url").asText();
            } else {
                throw new RuntimeException("Erro AbacatePay: " + response.body());
            }

        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar pagamento: " + e.getMessage());
        }
    }
}