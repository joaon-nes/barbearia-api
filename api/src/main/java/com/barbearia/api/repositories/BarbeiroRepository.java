package com.barbearia.api.repositories;

import com.barbearia.api.models.Barbeiro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, Long> {
    List<Barbeiro> findByEstabelecimentoIdAndAtivoTrue(Long estabelecimentoId);
}