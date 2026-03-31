package com.barbearia.api.controllers;

import com.barbearia.api.models.Cliente;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.repositories.AgendamentoRepository;
import com.barbearia.api.repositories.UsuarioRepository;
import com.barbearia.api.services.EmailService;
import com.barbearia.api.services.JwtService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!usuarioLogado.getId().equals(id) && !usuarioLogado.getRole().name().equals("ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado."));
        }

        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estabelecimentos/proximos")
    public ResponseEntity<?> buscarEstabelecimentosProximos(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10.0") Double raioKm) {

        if (raioKm > 50.0) {
            raioKm = 50.0;
        }
        try {
            List<Estabelecimento> ativosNoRaio = repository.buscarEstabelecimentosNoRaio(lat, lng, raioKm);
            List<Map<String, Object>> resultados = new ArrayList<>();

            java.util.Set<Long> idsProcessados = new java.util.HashSet<>();

            for (Estabelecimento e : ativosNoRaio) {
                if (!idsProcessados.add(e.getId())) {
                    continue;
                }

                if (e.getFotoPerfil() == null || e.getFotoPerfil().trim().isEmpty()) {
                    continue;
                }

                double distancia = calcularDistancia(lat, lng, e.getLatitude(), e.getLongitude());

                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("nomeBarbearia", e.getNomeBarbearia() != null ? e.getNomeBarbearia() : e.getNome());
                map.put("fotoPerfil", e.getFotoPerfil());
                map.put("rua", e.getRua());
                map.put("numero", e.getNumero());
                map.put("bairro", e.getBairro());
                map.put("cidade", e.getCidade());
                map.put("distancia", distancia);

                map.put("notaMedia", e.getNotaMedia() != null ? e.getNotaMedia() : 0.0);
                map.put("totalAvaliacoes", e.getTotalAvaliacoes() != null ? e.getTotalAvaliacoes() : 0);

                map.put("telefone", e.getTelefone());
                map.put("linkInstagram", e.getLinkInstagram());
                map.put("linkFacebook", e.getLinkFacebook());
                map.put("linkTiktok", e.getLinkTiktok());
                map.put("comodidades", e.getComodidades());
                map.put("horariosFuncionamento", e.getHorariosFuncionamento());
                map.put("diasFechados", e.getDiasFechados());
                map.put("fotosGaleria", e.getFotosGaleria());
                map.put("latitude", e.getLatitude());
                map.put("longitude", e.getLongitude());

                try {
                    long concluidos = agendamentoRepository.countByEstabelecimentoIdAndStatus(e.getId(),
                            StatusAgendamento.CONCLUIDO);
                    map.put("totalAtendimentos", concluidos);
                } catch (Exception ex) {
                    map.put("totalAtendimentos", 0);
                }

                resultados.add(map);
            }

            resultados.sort((m1, m2) -> Double.compare((Double) m1.get("distancia"), (Double) m2.get("distancia")));

            return ResponseEntity.ok(resultados);

        } catch (Exception e) {
            log.error("Falha ao buscar estabelecimentos próximos", e);
            throw new RuntimeException("Erro interno ao buscar estabelecimentos", e);
        }
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @PostMapping
    public ResponseEntity<Usuario> criar(@Valid @RequestBody Usuario usuario) {
        usuario.setAtivo(false);
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));

        if (usuario instanceof Cliente) {
            usuario.setRole(com.barbearia.api.models.RoleUsuario.CLIENTE);
            ((Cliente) usuario).setContaVerificada(false);
        } else if (usuario instanceof Estabelecimento) {
            usuario.setRole(com.barbearia.api.models.RoleUsuario.ESTABELECIMENTO);
            ((Estabelecimento) usuario).setPerfilCompleto(false);
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciais) {
        String email = credenciais.get("email");
        String senha = credenciais.get("senha");

        Optional<Usuario> userOpt = repository.findByEmail(email);

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

    @PostMapping("/validar-2fa")
    public ResponseEntity<?> validar2fa(@RequestBody Map<String, String> dados) {
        String email = dados.get("email");
        String codigo = dados.get("codigo");

        Optional<Usuario> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Código ou usuário inválido.");
        }

        Usuario usuario = userOpt.get();

        if (usuario.getBloqueadoAte() != null && java.time.LocalDateTime.now().isBefore(usuario.getBloqueadoAte())) {
            return ResponseEntity.status(429).body("Muitas tentativas. Conta bloqueada temporariamente.");
        }

        if (usuario.getDataExpiracao2fa() != null
                && java.time.LocalDateTime.now().isAfter(usuario.getDataExpiracao2fa())) {
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

    @PutMapping("/{id}/completar-perfil")
    public ResponseEntity<?> completarPerfil(@PathVariable Long id, @RequestBody Map<String, Object> dadosCompletos) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuarioLogado = (Usuario) auth.getPrincipal();

        if (!usuarioLogado.getId().equals(id)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erro", "Acesso negado: Você não pode alterar dados de outro utilizador."));
        }

        return repository.findById(id).map(u -> {
            if (dadosCompletos.containsKey("telefone")) {
                String novoTelefone = (String) dadosCompletos.get("telefone");
                if (novoTelefone != null && !novoTelefone.equals(u.getTelefone())
                        && repository.existsByTelefone(novoTelefone)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("erro", "Este número de telemóvel já está associado a outra conta."));
                }
                u.setTelefone(novoTelefone);
            }

            if (dadosCompletos.containsKey("nome") && dadosCompletos.get("nome") != null) {
                u.setNome(dadosCompletos.get("nome").toString().replaceAll("<[^>]*>", ""));
            }

            if (dadosCompletos.containsKey("cpf")) {
                String cpf = ((String) dadosCompletos.get("cpf")).replaceAll("\\D", "");
                u.setCpf(cpf);
            }

            if (u instanceof Estabelecimento) {
                Estabelecimento est = (Estabelecimento) u;

                if (dadosCompletos.containsKey("nomeBarbearia") && dadosCompletos.get("nomeBarbearia") != null)
                    est.setNomeBarbearia(dadosCompletos.get("nomeBarbearia").toString().replaceAll("<[^>]*>", ""));

                if (dadosCompletos.containsKey("cnpj"))
                    est.setCnpj((String) dadosCompletos.get("cnpj"));

                if (dadosCompletos.containsKey("cep"))
                    est.setCep((String) dadosCompletos.get("cep"));

                if (dadosCompletos.containsKey("rua") && dadosCompletos.get("rua") != null)
                    est.setRua(dadosCompletos.get("rua").toString().replaceAll("<[^>]*>", ""));

                if (dadosCompletos.containsKey("numero"))
                    est.setNumero((String) dadosCompletos.get("numero"));

                if (dadosCompletos.containsKey("bairro") && dadosCompletos.get("bairro") != null)
                    est.setBairro(dadosCompletos.get("bairro").toString().replaceAll("<[^>]*>", ""));

                if (dadosCompletos.containsKey("cidade") && dadosCompletos.get("cidade") != null)
                    est.setCidade(dadosCompletos.get("cidade").toString().replaceAll("<[^>]*>", ""));

                if (dadosCompletos.containsKey("estado") && dadosCompletos.get("estado") != null)
                    est.setEstado(dadosCompletos.get("estado").toString().replaceAll("<[^>]*>", ""));

                if (dadosCompletos.containsKey("horariosFuncionamento"))
                    est.setHorariosFuncionamento((String) dadosCompletos.get("horariosFuncionamento"));

                if (dadosCompletos.containsKey("comodidades") && dadosCompletos.get("comodidades") != null) {
                    String comSegura = dadosCompletos.get("comodidades").toString().replaceAll("<[^>]*>", "");
                    est.setComodidades(comSegura);
                }

                if (dadosCompletos.containsKey("latitude") && dadosCompletos.get("latitude") != null)
                    est.setLatitude(Double.parseDouble(dadosCompletos.get("latitude").toString()));

                if (dadosCompletos.containsKey("longitude") && dadosCompletos.get("longitude") != null)
                    est.setLongitude(Double.parseDouble(dadosCompletos.get("longitude").toString()));

                est.setPerfilCompleto(true);
            } else if (u instanceof Cliente) {
                if (u.getTelefone() != null && u.getCpf() != null) {
                    ((Cliente) u).setContaVerificada(true);
                }
            }

            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<?> atualizarTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id))
            return ResponseEntity.status(403).build();

        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento && body.get("tags") != null) {
                String tagsSeguras = body.get("tags").replaceAll("<[^>]*>", "");
                ((Estabelecimento) u).setTags(tagsSeguras);
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
            if (body.get("fotoPerfil") != null) {
                String fotoSegura = body.get("fotoPerfil").replaceAll("<[^>]*>", "").replace("javascript:", "");
                u.setFotoPerfil(fotoSegura);
            }
            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/galeria")
    public ResponseEntity<?> atualizarGaleria(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id))
            return ResponseEntity.status(403).body("Acesso negado.");

        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento && body.get("fotosGaleria") != null) {
                String galeriaSegura = body.get("fotosGaleria").replaceAll("<[^>]*>", "").replace("javascript:", "");
                ((Estabelecimento) u).setFotosGaleria(galeriaSegura);
                return ResponseEntity.ok(repository.save(u));
            }
            return ResponseEntity.badRequest().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}