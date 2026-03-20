package com.barbearia.api.controllers;

import com.barbearia.api.models.Cliente;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.repositories.UsuarioRepository;
import com.barbearia.api.services.EmailService;
import com.barbearia.api.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    @GetMapping
    public ResponseEntity<List<Usuario>> listarTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estabelecimentos/proximos")
    public ResponseEntity<List<Estabelecimento>> buscarProximos(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "15.0") double raioKm) {
        return ResponseEntity.ok(repository.buscarEstabelecimentosProximos(lat, lng, raioKm));
    }

    @PostMapping
    public ResponseEntity<Usuario> criar(@Valid @RequestBody Usuario usuario) {
        usuario.setAtivo(false);
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));

        String codigo = String.format("%06d", secureRandom.nextInt(999999));
        usuario.setCodigo2fa(codigo);
        usuario.setDataExpiracao2fa(java.time.LocalDateTime.now().plusMinutes(15));

        if (usuario instanceof Cliente) {
            ((Cliente) usuario).setContaVerificada(false);
        } else if (usuario instanceof Estabelecimento) {
            ((Estabelecimento) usuario).setPerfilCompleto(false);
        }

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        String email = credenciais.get("email");
        String senha = credenciais.get("senha");

        Optional<Usuario> userOpt = repository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("E-mail não encontrado.");
        }

        Usuario usuario = userOpt.get();

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            return ResponseEntity.status(401).body("Palavra-passe incorreta.");
        }

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            if (usuario.getCodigo2fa() == null) {
                String novoCodigo = String.format("%06d", secureRandom.nextInt(999999));
                usuario.setCodigo2fa(novoCodigo);
                usuario.setDataExpiracao2fa(java.time.LocalDateTime.now().plusMinutes(15));
                repository.save(usuario);
                try {
                    emailService.enviarEmail(usuario.getEmail(), "Código de Ativação",
                            "O seu novo código é: " + novoCodigo);
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.status(202)
                    .body(Map.of("email", usuario.getEmail(), "mensagem", "Aguardando Confirmação"));
        }

        String token = jwtService.gerarToken(usuario);

        if (usuario instanceof Estabelecimento
                && !Boolean.TRUE.equals(((Estabelecimento) usuario).getPerfilCompleto())) {
            return ResponseEntity.status(206).body(Map.of("usuario", usuario, "token", token));
        }

        return ResponseEntity.ok(Map.of("usuario", usuario, "token", token));
    }

    @PostMapping("/validar-2fa")
    public ResponseEntity<?> validar2fa(@RequestBody Map<String, String> dados) {
        String email = dados.get("email");
        String codigo = dados.get("codigo");

        Optional<Usuario> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty())
            return ResponseEntity.status(404).body("Usuário não encontrado.");

        Usuario usuario = userOpt.get();

        if (usuario.getCodigo2fa() != null && usuario.getCodigo2fa().equals(codigo)) {
            if (usuario.getDataExpiracao2fa() != null
                    && java.time.LocalDateTime.now().isAfter(usuario.getDataExpiracao2fa())) {
                return ResponseEntity.status(401).body("Código expirado. Faça login novamente para gerar um novo.");
            }
            usuario.setCodigo2fa(null);
            usuario.setDataExpiracao2fa(null);
            usuario.setAtivo(true);

            repository.save(usuario);
            String token = jwtService.gerarToken(usuario);

            if (usuario instanceof Estabelecimento
                    && !Boolean.TRUE.equals(((Estabelecimento) usuario).getPerfilCompleto())) {
                return ResponseEntity.status(206).body(Map.of("usuario", usuario, "token", token));
            }
            return ResponseEntity.ok(Map.of("usuario", usuario, "token", token));
        }

        return ResponseEntity.status(401).body("Código inválido.");
    }

    @PutMapping("/{id}/completar-perfil")
    public ResponseEntity<?> completarPerfil(@PathVariable Long id, @RequestBody Map<String, Object> dadosCompletos) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuarioLogado = (Usuario) auth.getPrincipal();

        if (!usuarioLogado.getId().equals(id)) {
            return ResponseEntity.status(403).body("Acesso negado: Você não pode alterar dados de outro utilizador.");
        }

        return repository.findById(id).map(u -> {
            if (dadosCompletos.containsKey("telefone")) {
                String novoTelefone = (String) dadosCompletos.get("telefone");

                if (novoTelefone != null && !novoTelefone.equals(u.getTelefone())) {
                    if (repository.existsByTelefone(novoTelefone)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("erro", "Este número de telemóvel já está associado a outra conta."));
                    }
                }
            }

            if (u instanceof Estabelecimento) {
                Estabelecimento est = (Estabelecimento) u;

                if (dadosCompletos.containsKey("nomeBarbearia"))
                    est.setNomeBarbearia((String) dadosCompletos.get("nomeBarbearia"));
                if (dadosCompletos.containsKey("cnpj"))
                    est.setCnpj((String) dadosCompletos.get("cnpj"));
                if (dadosCompletos.containsKey("telefone"))
                    est.setTelefone((String) dadosCompletos.get("telefone"));
                if (dadosCompletos.containsKey("cep"))
                    est.setCep((String) dadosCompletos.get("cep"));
                if (dadosCompletos.containsKey("rua"))
                    est.setRua((String) dadosCompletos.get("rua"));
                if (dadosCompletos.containsKey("numero"))
                    est.setNumero((String) dadosCompletos.get("numero"));
                if (dadosCompletos.containsKey("bairro"))
                    est.setBairro((String) dadosCompletos.get("bairro"));
                if (dadosCompletos.containsKey("cidade"))
                    est.setCidade((String) dadosCompletos.get("cidade"));
                if (dadosCompletos.containsKey("estado"))
                    est.setEstado((String) dadosCompletos.get("estado"));
                if (dadosCompletos.containsKey("horariosFuncionamento"))
                    est.setHorariosFuncionamento((String) dadosCompletos.get("horariosFuncionamento"));

                Object comodidades = dadosCompletos.get("comodidades");
                if (comodidades != null)
                    est.setComodidades(comodidades.toString());

                if (dadosCompletos.containsKey("linkInstagram"))
                    est.setLinkInstagram((String) dadosCompletos.get("linkInstagram"));
                if (dadosCompletos.containsKey("linkFacebook"))
                    est.setLinkFacebook((String) dadosCompletos.get("linkFacebook"));
                if (dadosCompletos.containsKey("linkTiktok"))
                    est.setLinkTiktok((String) dadosCompletos.get("linkTiktok"));

                if (dadosCompletos.containsKey("latitude") && dadosCompletos.get("latitude") != null) {
                    est.setLatitude(Double.parseDouble(dadosCompletos.get("latitude").toString()));
                }
                if (dadosCompletos.containsKey("longitude") && dadosCompletos.get("longitude") != null) {
                    est.setLongitude(Double.parseDouble(dadosCompletos.get("longitude").toString()));
                }

                est.setPerfilCompleto(true);
                return ResponseEntity.ok(repository.save(est));
            }
            return ResponseEntity.badRequest().body("O utilizador não é um Estabelecimento.");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<?> atualizarTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id))
            return ResponseEntity.status(403).build();

        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento) {
                ((Estabelecimento) u).setTags(body.get("tags"));
                return ResponseEntity.ok(repository.save(u));
            }
            return ResponseEntity.badRequest().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/foto")
    public ResponseEntity<?> atualizarFoto(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id))
            return ResponseEntity.status(403).body("Acesso negado.");
        return repository.findById(id).map(u -> {
            u.setFotoPerfil(body.get("fotoPerfil"));
            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/galeria")
    public ResponseEntity<?> atualizarGaleria(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id))
            return ResponseEntity.status(403).body("Acesso negado.");
        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento) {
                ((Estabelecimento) u).setFotosGaleria(body.get("fotosGaleria"));
                return ResponseEntity.ok(repository.save(u));
            }
            return ResponseEntity.badRequest().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}