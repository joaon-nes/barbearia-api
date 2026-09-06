package com.barbearia.api.repositories;

import com.barbearia.api.models.Barbeiro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Barbeiro b WHERE b.id = :id")
    java.util.Optional<Barbeiro> findByIdComBloqueio(@org.springframework.data.repository.query.Param("id") Long id);

    List<Barbeiro> findByEstabelecimentoIdAndAtivoTrue(Long estabelecimentoId);
}