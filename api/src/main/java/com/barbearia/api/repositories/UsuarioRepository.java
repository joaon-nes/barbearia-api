package com.barbearia.api.repositories;

import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

        Optional<Usuario> findByEmail(String email);

        boolean existsByTelefone(String telefone);

        @Query("SELECT DISTINCT e FROM Estabelecimento e WHERE e.ativo = true AND e.latitude IS NOT NULL AND e.longitude IS NOT NULL")
        List<Estabelecimento> buscarEstabelecimentosAtivos();
}