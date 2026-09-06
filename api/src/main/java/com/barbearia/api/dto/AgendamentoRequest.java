package com.barbearia.api.dto;
import com.barbearia.api.models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record AgendamentoRequest(
        @NotNull LocalDateTime dataHoraInicio,
        @NotNull @Valid Referencia estabelecimento,
        @NotNull @Valid Referencia servico,
        @NotNull @Valid Referencia barbeiro,
        @Size(max = 1000) String observacao,
        @Pattern(regexp = "DINHEIRO|PIX|CARD|CARTAO") String formaPagamento) {
    public record Referencia(@NotNull @Positive Long id) {}
    public Agendamento novoAgendamento() {
        var ag = new Agendamento();
        var est = new Estabelecimento(); est.setId(estabelecimento.id());
        var srv = new Servico(); srv.setId(servico.id());
        var profissional = new Barbeiro(); profissional.setId(barbeiro.id());
        ag.setEstabelecimento(est); ag.setServico(srv); ag.setBarbeiro(profissional);
        ag.setDataHoraInicio(dataHoraInicio); ag.setObservacao(observacao);
        ag.setFormaPagamento(formaPagamento == null ? "DINHEIRO" : formaPagamento);
        return ag;
    }
}
