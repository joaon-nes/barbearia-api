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
@lombok.RequiredArgsConstructor
public class PagamentoService {

    private final com.barbearia.api.repositories.AgendamentoRepository repository;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

    @Value("${abacatepay.api.key}")
    private String apiKey;

    @Value("${FRONTEND_URL}")
    private String urlFrontend;

    @org.springframework.transaction.annotation.Transactional
    public String gerarLinkDePagamento(Agendamento solicitado) {
        Agendamento agendamento = repository.findByIdComBloqueio(solicitado.getId())
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));
        if (agendamento.getStatus() == com.barbearia.api.models.StatusAgendamento.CANCELADO
                || agendamento.getStatus() == com.barbearia.api.models.StatusAgendamento.CONCLUIDO
                || agendamento.getStatusPagamento() != com.barbearia.api.models.StatusPagamento.PENDENTE) {
            throw new IllegalArgumentException("Agendamento indisponível para pagamento.");
        }
        if (agendamento.getBillingUrl() != null) return agendamento.getBillingUrl();
        try {
            int valorEmCentavos = agendamento.getServico().getPreco().multiply(new BigDecimal("100")).intValueExact();
            if (valorEmCentavos < 100)
                throw new IllegalArgumentException("O pagamento mínimo é R$ 1,00.");

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
            String telefoneCliente = agendamento.getCliente().getTelefone();
            String cpfCliente = agendamento.getCliente().getCpf();
            if (emailCliente == null || telefoneCliente == null || cpfCliente == null) {
                throw new IllegalArgumentException("Complete e-mail, telefone e CPF antes de pagar.");
            }
            telefoneCliente = telefoneCliente.replaceAll("\\D", "");
            cpfCliente = cpfCliente.replaceAll("\\D", "");
            if (!telefoneCliente.matches("[0-9]{10,13}") || !cpfCliente.matches("[0-9]{11}|[0-9]{14}")) {
                throw new IllegalArgumentException("Telefone ou CPF/CNPJ inválido.");
            }

            payload.put("customer", Map.of(
                    "name", agendamento.getCliente().getNome(),
                    "email", emailCliente,
                    "cellphone", telefoneCliente,
                    "taxId", cpfCliente));

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(java.time.Duration.ofSeconds(20))
                    .uri(URI.create("https://api.abacatepay.com/v1/billing/create"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode data = rootNode.path("data");
                String billingId = data.path("id").asText();
                String checkoutUrl = data.path("url").asText();
                URI checkout = URI.create(checkoutUrl);
                if (billingId.isBlank() || !"https".equals(checkout.getScheme()) || checkout.getHost() == null) {
                    throw new IllegalStateException("Resposta de pagamento inválida.");
                }
                agendamento.setBillingId(billingId);
                agendamento.setBillingUrl(checkoutUrl);
                agendamento.setBillingAmount(valorEmCentavos);
                repository.save(agendamento);
                return checkoutUrl;
            } else {
                throw new IllegalStateException("O provedor recusou a criação da cobrança.");
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Solicitação de pagamento interrompida.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar pagamento.", e);
        }
    }
}