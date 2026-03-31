package com.barbearia.api.repositories;

import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

        Optional<Usuario> findByEmail(String email);

        boolean existsByTelefone(String telefone);

        @Query("SELECT DISTINCT e FROM Estabelecimento e WHERE e.ativo = true AND e.latitude IS NOT NULL AND e.longitude IS NOT NULL")
        List<Estabelecimento> buscarEstabelecimentosAtivos();

        @Query("SELECT e FROM Estabelecimento e WHERE e.ativo = true " +
                        "AND e.latitude IS NOT NULL AND e.longitude IS NOT NULL " +
                        "AND (6371.0 * acos(cos(radians(:lat)) * cos(radians(e.latitude)) * " +
                        "cos(radians(e.longitude) - radians(:lng)) + sin(radians(:lat)) * " +
                        "sin(radians(e.latitude)))) <= :raioKm")
        List<Estabelecimento> buscarEstabelecimentosNoRaio(@Param("lat") Double lat,
                        @Param("lng") Double lng,
                        @Param("raioKm") Double raioKm);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT u FROM Usuario u WHERE u.id = :id")
        Optional<Usuario> findByIdComBloqueio(@Param("id") Long id);
}