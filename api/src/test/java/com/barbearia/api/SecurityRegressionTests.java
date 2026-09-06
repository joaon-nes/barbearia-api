package com.barbearia.api;

import com.barbearia.api.dto.*;
import com.barbearia.api.models.*;
import com.barbearia.api.repositories.*;
import com.barbearia.api.services.*;
import com.barbearia.api.controllers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTests {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void cadastroNaoAceitaIdentidadeOuEstadoInterno() throws Exception {
        var dto = mapper.readValue("""
            {"nome":"João","email":"joao@example.com","senha":"12345678","role":"CLIENTE",
             "id":1,"ativo":true,"contaVerificada":true,"notaMedia":5,"tentativasFalhas":null}
            """, CadastroRequest.class);
        var usuario = (Cliente) dto.novoUsuario();
        assertNull(usuario.getId()); assertFalse(usuario.getContaVerificada());
        assertEquals(0.0, usuario.getNotaMedia()); assertEquals(0, usuario.getTentativasFalhas());
        assertThrows(IllegalArgumentException.class, () ->
                new CadastroRequest("A", "a@b.com", "12345678", null, "ADMIN").novoUsuario());
    }

    @Test void cadastroRejeitaSenhaAcimaDe72Bytes() {
        assertThrows(IllegalArgumentException.class, () ->
                new CadastroRequest("A", "a@b.com", "é".repeat(37), null, "CLIENTE").novoUsuario());
    }

    @Test void agendamentoIgnoraPagamentoEAvaliacaoInjetados() throws Exception {
        var dto = mapper.readValue("""
            {"dataHoraInicio":"2030-01-01T10:00:00","estabelecimento":{"id":2,"role":"ESTABELECIMENTO"},
             "servico":{"id":3},"barbeiro":{"id":4},"statusPagamento":"PAGO",
             "notaAvaliacao":5,"billingId":"forged","id":10}
            """, AgendamentoRequest.class);
        var ag = dto.novoAgendamento();
        assertNull(ag.getId()); assertNull(ag.getNotaAvaliacao()); assertNull(ag.getBillingId());
        assertEquals(StatusPagamento.PENDENTE, ag.getStatusPagamento());
    }

    @Test void codigoSemExpiracaoNaoEmiteJwt() {
        var repo = mock(UsuarioRepository.class); var jwt = mock(JwtService.class);
        var user = new Cliente(); user.setCodigo2fa("123456");
        when(repo.findByEmailComBloqueio("a@b.com")).thenReturn(Optional.of(user));
        var service = new AutenticacaoService(repo, mock(EmailService.class), new BCryptPasswordEncoder(), jwt);
        assertEquals(401, service.validar2fa(new Codigo2faRequest("a@b.com", "123456")).getStatusCode().value());
        verifyNoInteractions(jwt);
    }

    @Test void webhookExigeSecretMesmoComAssinaturaCorreta() throws Exception {
        var service = new WebhookSignatureService();
        ReflectionTestUtils.setField(service, "secret", "segredo");
        ReflectionTestUtils.setField(service, "signatureKey", "chave-publica");
        byte[] payload = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("chave-publica".getBytes(), "HmacSHA256"));
        String sig = Base64.getEncoder().encodeToString(mac.doFinal(payload));
        assertTrue(service.validar(payload, sig, "segredo"));
        assertFalse(service.validar(payload, sig, null));
        assertFalse(service.validar(payload, sig, "outro"));
        assertFalse(service.validar("alterado".getBytes(), sig, "segredo"));
        assertFalse(service.validar(payload, "%%%", "segredo"));
    }

    @Test void webhookDuplicadoNaoReabreAgendaCancelada() throws Exception {
        var repo = mock(AgendamentoRepository.class); var service = new WebhookService(repo);
        var ag = new Agendamento(); ag.setStatus(StatusAgendamento.CANCELADO); ag.setBillingAmount(4500);
        when(repo.findByBillingIdComBloqueio("bill_1")).thenReturn(Optional.of(ag));
        var event = mapper.readTree("""
            {"event":"billing.paid","devMode":false,"data":{"id":"bill_1","amount":4500,"status":"PAID"}}
            """);
        service.processar(event); service.processar(event);
        assertEquals(StatusPagamento.PAGO, ag.getStatusPagamento());
        assertEquals(StatusAgendamento.CANCELADO, ag.getStatus()); verify(repo, times(1)).save(ag);
    }

    @Test void webhookRejeitaValorEAmbienteIncorretos() throws Exception {
        var repo = mock(AgendamentoRepository.class); var service = new WebhookService(repo);
        var ag = new Agendamento(); ag.setBillingAmount(4500);
        when(repo.findByBillingIdComBloqueio("bill_1")).thenReturn(Optional.of(ag));
        var event = mapper.readTree("""
            {"event":"billing.paid","devMode":false,"data":{"id":"bill_1","amount":1,"status":"PAID"}}
            """);
        assertThrows(IllegalArgumentException.class, () -> service.processar(event));
        ((com.fasterxml.jackson.databind.node.ObjectNode)event).put("devMode", true);
        assertThrows(IllegalArgumentException.class, () -> service.processar(event));
        verify(repo, never()).save(any());
    }

    @Test void reagendamentoDerivaAutorDoPrincipal() {
        var service = mock(AgendamentoService.class);
        var controller = new AgendamentoController(service, mock(ServicoRepository.class), mock(PagamentoService.class));
        var cliente = new Cliente(); cliente.setId(1L); cliente.setRole(RoleUsuario.CLIENTE);
        var est = new Estabelecimento(); est.setId(2L);
        var ag = new Agendamento(); ag.setCliente(cliente); ag.setEstabelecimento(est);
        when(service.buscarPorId(3L)).thenReturn(Optional.of(ag));
        when(service.proporReagendamento(eq(3L), any(), eq("CLIENTE"))).thenReturn(Optional.of(ag));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(cliente, null, List.of()));
        try {
            controller.proporReagendamento(3L, Map.of("dataHoraProposta", "2030-01-01T10:00:00", "quemSugeriu", "ESTABELECIMENTO"));
            verify(service).proporReagendamento(3L, LocalDateTime.of(2030, 1, 1, 10, 0), "CLIENTE");
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void servicoPublicoNaoSerializaDono() throws Exception {
        var srv = new Servico(); var est = new Estabelecimento(); est.setCpf("12345678901"); srv.setEstabelecimento(est);
        assertFalse(mapper.writeValueAsString(srv).contains("12345678901"));
    }

    @Test void midiaRejeitaEsquemasExecutaveisEInjecaoDeAtributos() {
        var service = new MediaValidationService();
        assertThrows(IllegalArgumentException.class, () -> service.imagem("JaVaScRiPt:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> service.imagem("https://example.com/x' onerror='alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> service.imagem("data:image/svg+xml;base64,PHN2Zz4="));
        assertEquals("https://example.com/foto.png", service.imagem("https://example.com/foto.png"));
    }

    @Test void horariosNaoEntramEmLoopAoCruzarMeiaNoite() {
        var service = new AgendamentoService(mock(AgendamentoRepository.class), mock(UsuarioRepository.class),
                mock(ServicoRepository.class), mock(EmailService.class), mock(BarbeiroRepository.class));
        List<String> slots = new ArrayList<>();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                ReflectionTestUtils.invokeMethod(service, "gerarSlotsNoTurno", java.time.LocalDate.of(2030, 1, 1),
                        "23:00", "23:59", 60, List.of(), slots));
        assertTrue(slots.isEmpty());
    }

    @Test void naoPermiteServicoDeOutroEstabelecimento() {
        var repo = mock(AgendamentoRepository.class); var users = mock(UsuarioRepository.class);
        var servicos = mock(ServicoRepository.class); var barbeiros = mock(BarbeiroRepository.class);
        var service = new AgendamentoService(repo, users, servicos, mock(EmailService.class), barbeiros);
        var cliente = new Cliente(); cliente.setId(1L);
        var est = new Estabelecimento(); est.setId(2L); est.setAtivo(true); est.setVerificadoAdmin(true);
        var outro = new Estabelecimento(); outro.setId(3L);
        var srv = new Servico(); srv.setId(4L); srv.setEstabelecimento(outro);
        var barber = new Barbeiro(); barber.setId(5L); barber.setEstabelecimento(est);
        var ag = new Agendamento(); ag.setCliente(cliente); ag.setEstabelecimento(est);
        ag.setServico(srv); ag.setBarbeiro(barber); ag.setFormaPagamento("PIX");
        ag.setDataHoraInicio(LocalDateTime.now().plusDays(1));
        when(servicos.findById(4L)).thenReturn(Optional.of(srv));
        when(users.findByIdComBloqueio(1L)).thenReturn(Optional.of(cliente));
        when(users.findById(2L)).thenReturn(Optional.of(est));
        when(barbeiros.findByIdComBloqueio(5L)).thenReturn(Optional.of(barber));
        assertThrows(IllegalArgumentException.class, () -> service.criar(ag));
        verify(repo, never()).save(any());
    }
}
