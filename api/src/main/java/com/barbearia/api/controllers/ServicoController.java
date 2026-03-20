package com.barbearia.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.barbearia.api.models.Servico;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.repositories.ServicoRepository;
import com.barbearia.api.repositories.UsuarioRepository;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/estabelecimento/{id}")
    public ResponseEntity<List<Servico>> listarPorEstabelecimento(@PathVariable Long id) {
        return ResponseEntity.ok(servicoRepository.findByEstabelecimentoId(id));
    }

    @PostMapping("/estabelecimento/{id}")
    public ResponseEntity<?> adicionarServico(@PathVariable Long id, @RequestBody Servico servico) {
        Usuario usuarioLogado = (Usuario) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(id)) {
            return ResponseEntity.status(403)
                    .body("Acesso negado: Não pode criar serviços para outro estabelecimento.");
        }

        Optional<Usuario> estOpt = usuarioRepository.findById(id);
        if (estOpt.isPresent() && estOpt.get() instanceof com.barbearia.api.models.Estabelecimento) {
            servico.setEstabelecimento((com.barbearia.api.models.Estabelecimento) estOpt.get());
            Servico salvo = servicoRepository.save(servico);
            return ResponseEntity.ok(salvo);
        }
        return ResponseEntity.badRequest().body("Estabelecimento não encontrado ou inválido.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarServico(@PathVariable Long id, @RequestBody Servico dados) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return servicoRepository.findById(id).map(srv -> {
            if (!srv.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403).build();
            }
            srv.setNome(dados.getNome());
            srv.setPreco(dados.getPreco());
            srv.setDuracaoMinutos(dados.getDuracaoMinutos());
            return ResponseEntity.ok(servicoRepository.save(srv));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletarServico(@PathVariable Long id) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return servicoRepository.findById(id).map(srv -> {
            if (!srv.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403).build();
            }
            servicoRepository.delete(srv);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}