package com.barbearia.demo.repositories;

import com.barbearia.demo.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByEmail(String email);
    Optional<Usuario> findByEmailAndSenha(String email, String senha);
    
    Optional<Usuario> findByCodigoVerificacao(String codigoVerificacao);
    Optional<Usuario> findByEmail(String email);
}