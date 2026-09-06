package com.barbearia.api.repositories;

import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT a FROM Agendamento a WHERE a.id = :id")
        java.util.Optional<Agendamento> findByIdComBloqueio(@Param("id") Long id);

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT a FROM Agendamento a WHERE a.billingId = :billingId")
        java.util.Optional<Agendamento> findByBillingIdComBloqueio(@Param("billingId") String billingId);

        List<Agendamento> findByClienteId(Long clienteId);

        List<Agendamento> findByEstabelecimentoId(Long estabelecimentoId);

        int countByClienteIdAndStatusIn(Long clienteId, List<StatusAgendamento> statuses);

        int countByClienteIdAndStatus(Long clienteId, StatusAgendamento status);

        @Query("SELECT a FROM Agendamento a WHERE a.estabelecimento.id = :estabelecimentoId AND a.dataHoraInicio >= :inicio AND a.dataHoraInicio <= :fim AND a.status IN ('AGENDADO', 'REAGENDAMENTO_PENDENTE', 'CONCLUIDO')")
        List<Agendamento> buscarAtivosPorEstabelecimentoEDia(
                        @Param("estabelecimentoId") Long estabelecimentoId,
                        @Param("inicio") java.time.LocalDateTime inicio,
                        @Param("fim") java.time.LocalDateTime fim);

        @Query("SELECT a FROM Agendamento a WHERE a.barbeiro.id = :barbeiroId AND a.dataHoraInicio >= :inicio AND a.dataHoraInicio <= :fim AND a.status IN ('AGENDADO', 'REAGENDAMENTO_PENDENTE', 'CONCLUIDO')")
        List<Agendamento> buscarAtivosPorBarbeiroEDia(
                        @Param("barbeiroId") Long barbeiroId,
                        @Param("inicio") java.time.LocalDateTime inicio,
                        @Param("fim") java.time.LocalDateTime fim);

        List<Agendamento> findByDataHoraInicioBetweenAndStatus(LocalDateTime inicio, LocalDateTime fim,
                        StatusAgendamento status);

        long countByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusAgendamento status);
}