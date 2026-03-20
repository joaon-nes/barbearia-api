package com.barbearia.api.repositories;

import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByTelefone(String telefone);

    @Query(value = "SELECT u.id, u.ativo, u.codigo2fa, u.data_expiracao_2fa, u.email, u.foto_perfil, u.nome, u.role, u.senha, u.telefone, "
            +
            "e.bairro, e.cep, e.cidade, e.cnpj, e.comodidades, e.dias_fechados, e.estado, e.fotos_galeria, e.horarios_funcionamento, "
            +
            "e.latitude, e.link_facebook, e.link_instagram, e.link_tiktok, e.longitude, e.nome_barbearia, e.numero, e.perfil_completo, e.rua, e.tags "
            +
            "FROM usuarios u INNER JOIN estabelecimentos e ON u.id = e.id " +
            "WHERE (6371 * acos(cos(radians(:lat)) * cos(radians(e.latitude)) * cos(radians(e.longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(e.latitude)))) <= :raioKm "
            +
            "AND u.ativo = true", nativeQuery = true)
    List<Estabelecimento> buscarEstabelecimentosProximos(
            @org.springframework.data.repository.query.Param("lat") double lat,
            @org.springframework.data.repository.query.Param("lng") double lng,
            @org.springframework.data.repository.query.Param("raioKm") double raioKm);
}