package com.barbearia.demo.repositories;

import com.barbearia.demo.models.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query("SELECT COUNT(a) > 0 FROM Agendamento a WHERE a.dataHoraInicio = :dataHora AND a.status = 'AGENDADO'")
    boolean existsConflitoDeHorario(@Param("dataHora") LocalDateTime dataHora);
}