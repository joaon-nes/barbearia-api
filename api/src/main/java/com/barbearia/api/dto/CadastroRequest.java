package com.barbearia.api.dto;

import com.barbearia.api.models.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

/** Campos permitidos no cadastro público. IDs e estado de segurança são internos. */
public record CadastroRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @Size(max = 20) String telefone,
        @NotBlank @Pattern(regexp = "CLIENTE|ESTABELECIMENTO") String role) {
    public Usuario novoUsuario() {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes UTF-8.");
        }
        Usuario usuario = switch (role) {
            case "CLIENTE" -> new Cliente();
            case "ESTABELECIMENTO" -> new Estabelecimento();
            default -> throw new IllegalArgumentException("Tipo de conta inválido.");
        };
        usuario.setNome(nome.trim());
        usuario.setEmail(email.trim());
        usuario.setSenha(senha);
        usuario.setTelefone(telefone);
        return usuario;
    }
}
