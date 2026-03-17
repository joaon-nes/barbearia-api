package com.barbearia.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.api.models.Servico;

import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByEstabelecimentoId(Long estabelecimentoId);
}