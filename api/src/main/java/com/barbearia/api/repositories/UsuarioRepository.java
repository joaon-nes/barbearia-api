package com.barbearia.api.repositories;

import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    @Query(value = "SELECT * FROM usuario WHERE dtype = 'Estabelecimento' AND latitude IS NOT NULL AND ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat)) / 1000 <= :raioKm ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat))", nativeQuery = true)
    List<Estabelecimento> buscarEstabelecimentosProximos(@Param("lat") double lat, @Param("lng") double lng,
            @Param("raioKm") double raioKm);
}