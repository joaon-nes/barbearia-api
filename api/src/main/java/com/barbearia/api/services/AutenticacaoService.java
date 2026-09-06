package com.barbearia.api.services;
import com.barbearia.api.models.*;
import com.barbearia.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AutenticacaoService {
    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Usuario> criar(com.barbearia.api.dto.CadastroRequest dados) {
        Usuario usuario = dados.novoUsuario();
        usuario.setAtivo(false);
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));

        if (usuario instanceof Cliente) {
            usuario.setRole(com.barbearia.api.models.RoleUsuario.CLIENTE);
            ((Cliente) usuario).setContaVerificada(false);
        } else if (usuario instanceof Estabelecimento) {
            usuario.setRole(com.barbearia.api.models.RoleUsuario.ESTABELECIMENTO);
            ((Estabelecimento) usuario).setPerfilCompleto(false);
            ((Estabelecimento) usuario).setVerificadoAdmin(false);
        } else {
            return ResponseEntity.badRequest().build();
        }

        String codigo = String.valueOf(100000 + secureRandom.nextInt(900000));
        usuario.setCodigo2fa(codigo);
        usuario.setDataExpiracao2fa(java.time.LocalDateTime.now().plusMinutes(15));

        Usuario salvo = repository.save(usuario);

        try {
            emailService.enviarEmail(salvo.getEmail(),
                    "Bem-vindo à Barbearia! Confirme a sua conta",
                    "Olá " + salvo.getNome() + "!\n\nO seu código de ativação é: " + codigo
                            + "\n\nUse este código ao fazer o primeiro login para ativar a sua conta.");
        } catch (Exception e) {
            System.err.println("Aviso: Falha ao enviar e-mail - " + e.getMessage());
        }

        return ResponseEntity.ok(salvo);
    }

    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> login(com.barbearia.api.dto.LoginRequest credenciais) {
        String email = credenciais.email();
        String senha = credenciais.senha();

        Optional<Usuario> userOpt = repository.findByEmailComBloqueio(email);

        if (userOpt.isPresent()) {
            Usuario u = userOpt.get();
            if (u.getBloqueadoAte() != null && java.time.LocalDateTime.now().isBefore(u.getBloqueadoAte())) {
                return ResponseEntity.status(429).body("Conta bloqueada temporariamente. Tente novamente mais tarde.");
            }
        }

        if (userOpt.isEmpty() || !passwordEncoder.matches(senha, userOpt.get().getSenha())) {
            if (userOpt.isPresent()) {
                Usuario u = userOpt.get();
                u.setTentativasFalhas(u.getTentativasFalhas() + 1);
                if (u.getTentativasFalhas() >= 5) {
                    u.setBloqueadoAte(java.time.LocalDateTime.now().plusMinutes(15));
                }
                repository.save(u);
            }
            return ResponseEntity.status(401).body("Credenciais inválidas.");
        }

        Usuario usuario = userOpt.get();

        usuario.setTentativasFalhas(0);
        usuario.setBloqueadoAte(null);
        repository.save(usuario);

        boolean exige2fa = !Boolean.TRUE.equals(usuario.getAtivo()) || usuario instanceof Estabelecimento;

        if (exige2fa) {
            String novoCodigo = String.valueOf(100000 + secureRandom.nextInt(900000));
            usuario.setCodigo2fa(novoCodigo);
            usuario.setDataExpiracao2fa(java.time.LocalDateTime.now().plusMinutes(15));
            repository.save(usuario);
            try {
                emailService.enviarEmail(usuario.getEmail(), "Código de Segurança 2FA",
                        "O seu código é: " + novoCodigo);
            } catch (Exception ignored) {
            }

            return ResponseEntity.status(202)
                    .body(Map.of("email", usuario.getEmail(), "mensagem", "Aguardando Confirmação do 2FA"));
        }

        String token = jwtService.gerarToken(usuario);
        if (usuario instanceof Estabelecimento
                && !Boolean.TRUE.equals(((Estabelecimento) usuario).getPerfilCompleto())) {
            return ResponseEntity.status(206).body(Map.of("usuario", usuario, "token", token));
        }

        return ResponseEntity.ok(Map.of("usuario", usuario, "token", token));
    }

    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> validar2fa(com.barbearia.api.dto.Codigo2faRequest dados) {
        String email = dados.email();
        String codigo = dados.codigo();

        Optional<Usuario> userOpt = repository.findByEmailComBloqueio(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Código ou usuário inválido.");
        }

        Usuario usuario = userOpt.get();

        if (usuario.getBloqueadoAte() != null && java.time.LocalDateTime.now().isBefore(usuario.getBloqueadoAte())) {
            return ResponseEntity.status(429).body("Muitas tentativas. Conta bloqueada temporariamente.");
        }

        if (usuario.getDataExpiracao2fa() == null
                || !java.time.LocalDateTime.now().isBefore(usuario.getDataExpiracao2fa())) {
            return ResponseEntity.status(401).body("Código expirado. Volte e faça login novamente para gerar um novo.");
        }

        if (usuario.getCodigo2fa() != null && usuario.getCodigo2fa().equals(codigo)) {
            usuario.setCodigo2fa(null);
            usuario.setDataExpiracao2fa(null);
            usuario.setTentativasFalhas(0);
            usuario.setBloqueadoAte(null);
            usuario.setAtivo(true);
            repository.save(usuario);

            String token = jwtService.gerarToken(usuario);

            if (usuario instanceof Estabelecimento
                    && !Boolean.TRUE.equals(((Estabelecimento) usuario).getPerfilCompleto())) {
                return ResponseEntity.status(206).body(Map.of("usuario", usuario, "token", token));
            }
            return ResponseEntity.ok(Map.of("usuario", usuario, "token", token));
        }

        usuario.setTentativasFalhas(usuario.getTentativasFalhas() + 1);
        if (usuario.getTentativasFalhas() >= 5) {
            usuario.setBloqueadoAte(java.time.LocalDateTime.now().plusMinutes(15));
            usuario.setCodigo2fa(null);
        }
        repository.save(usuario);

        return ResponseEntity.status(401).body("Código inválido.");
    }

}
