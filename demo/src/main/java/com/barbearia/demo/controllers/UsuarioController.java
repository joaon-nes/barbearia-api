package com.barbearia.demo.controllers;

import com.barbearia.demo.models.RoleUsuario;
import com.barbearia.demo.models.Usuario;
import com.barbearia.demo.repositories.UsuarioRepository;
import com.barbearia.demo.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private EmailService emailService;

    private String gerarCodigo2FA() {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder codigo = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            int index = rnd.nextInt(caracteres.length());
            codigo.append(caracteres.charAt(index));
        }
        return codigo.toString();
    }

    @GetMapping
    public ResponseEntity<List<Usuario>> listarTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrar(@RequestBody Usuario usuario) {
        if (repository.existsByEmail(usuario.getEmail())) {
            return ResponseEntity.badRequest().body("Erro: E-mail já cadastrado!");
        }

        usuario.setAtivo(false);
        String codigo = UUID.randomUUID().toString();
        usuario.setCodigoVerificacao(codigo);

        Usuario salvo = repository.save(usuario);
        emailService.enviarEmailConfirmacao(salvo.getEmail(), salvo.getNome(), codigo);

        return ResponseEntity.ok("Conta criada! Verifique o seu e-mail para a ativar.");
    }

    @GetMapping("/confirmar")
    public String confirmar(@RequestParam String codigo) {
        Optional<Usuario> usuarioOpt = repository.findByCodigoVerificacao(codigo);

        if (usuarioOpt.isPresent()) {
            Usuario u = usuarioOpt.get();
            u.setAtivo(true);
            u.setCodigoVerificacao(null);
            repository.save(u);
            return "<h2>Conta ativada com sucesso!</h2><p>Já pode voltar à aplicação e fazer o Login.</p>";
        }
        return "<h2>Código inválido ou conta já ativada.</h2>";
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        String email = credenciais.get("email");
        String senha = credenciais.get("senha");

        Optional<Usuario> usuarioLogado = repository.findByEmailAndSenha(email, senha);

        if (usuarioLogado.isPresent()) {
            Usuario user = usuarioLogado.get();

            if (!Boolean.TRUE.equals(user.getAtivo())) {
                return ResponseEntity.status(403)
                        .body("Por favor, ative a sua conta no link enviado para o seu e-mail.");
            }

            if (user.getRole() == RoleUsuario.ADMIN || user.getRole() == RoleUsuario.ESTABELECIMENTO) {
                String codigo2fa = gerarCodigo2FA();
                user.setCodigo2fa(codigo2fa);
                repository.save(user);

                emailService.enviarEmail2FA(user.getEmail(), user.getNome(), codigo2fa);

                return ResponseEntity.status(202).body(Map.of("status", "2FA_REQUIRED", "email", user.getEmail()));
            }

            user.setSenha(null);
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(401).body("E-mail ou senha inválidos!");
        }
    }

    @PostMapping("/validar-2fa")
    public ResponseEntity<?> validar2fa(@RequestBody Map<String, String> dados) {
        String email = dados.get("email");
        String codigo = dados.get("codigo");

        Optional<Usuario> userOpt = repository.findByEmail(email);

        if (userOpt.isPresent()) {
            Usuario user = userOpt.get();
            if (codigo.equals(user.getCodigo2fa())) {
                user.setCodigo2fa(null);
                repository.save(user);
                user.setSenha(null);

                if (user.getRole() == RoleUsuario.ESTABELECIMENTO && !Boolean.TRUE.equals(user.getPerfilCompleto())) {
                    return ResponseEntity.status(206).body(user);
                }

                return ResponseEntity.ok(user);
            }
        }
        return ResponseEntity.status(401).body("Código 2FA inválido!");
    }

    @PutMapping("/{id}/completar-perfil")
    public ResponseEntity<?> completarPerfil(@PathVariable Long id, @RequestBody Usuario dadosCompletos) {
        Optional<Usuario> userOpt = repository.findById(id);
        if (userOpt.isPresent()) {
            Usuario u = userOpt.get();
            u.setNomeBarbearia(dadosCompletos.getNomeBarbearia());
            u.setCnpj(dadosCompletos.getCnpj());
            u.setTelefone(dadosCompletos.getTelefone());
            u.setCep(dadosCompletos.getCep());
            u.setRua(dadosCompletos.getRua());
            u.setNumero(dadosCompletos.getNumero());
            u.setBairro(dadosCompletos.getBairro());
            u.setCidade(dadosCompletos.getCidade());
            u.setEstado(dadosCompletos.getEstado());
            u.setHorariosFuncionamento(dadosCompletos.getHorariosFuncionamento());
            u.setTags(dadosCompletos.getTags());
            u.setComodidades(dadosCompletos.getComodidades());
            u.setLinkInstagram(dadosCompletos.getLinkInstagram());
            u.setLinkFacebook(dadosCompletos.getLinkFacebook());
            u.setLinkTiktok(dadosCompletos.getLinkTiktok());

            u.setPerfilCompleto(true);

            return ResponseEntity.ok(repository.save(u));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/foto")
    public ResponseEntity<?> atualizarFoto(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Usuario> userOpt = repository.findById(id);
        if (userOpt.isPresent()) {
            Usuario u = userOpt.get();
            u.setFotoPerfil(body.get("fotoPerfil"));
            return ResponseEntity.ok(repository.save(u));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/galeria")
    public ResponseEntity<?> atualizarGaleria(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Usuario> userOpt = repository.findById(id);
        if (userOpt.isPresent()) {
            Usuario u = userOpt.get();
            u.setFotosGaleria(body.get("fotosGaleria"));
            return ResponseEntity.ok(repository.save(u));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<?> atualizarTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id).map(u -> {
            u.setTags(body.get("tags"));
            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }
}