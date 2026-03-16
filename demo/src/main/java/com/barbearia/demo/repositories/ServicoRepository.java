package com.barbearia.demo.repositories;

import com.barbearia.demo.models.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByEstabelecimentoId(Long estabelecimentoId);
}