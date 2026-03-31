package com.barbearia.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.barbearia.api.models.Servico;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.repositories.ServicoRepository;

import java.util.List;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    @Autowired
    private ServicoRepository servicoRepository;

    private Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/estabelecimento/{id}")
    public ResponseEntity<List<Servico>> listarPorEstabelecimento(@PathVariable Long id) {
        return ResponseEntity.ok(servicoRepository.findByEstabelecimentoId(id));
    }

    @PostMapping("/estabelecimento/{id}")
    public ResponseEntity<?> adicionarServico(@PathVariable Long id, @RequestBody Servico servico) {
        Usuario usuarioLogado = getUsuarioLogado();

        if (!usuarioLogado.getId().equals(id) || !(usuarioLogado instanceof Estabelecimento)) {
            return ResponseEntity.status(403).body("Acesso negado.");
        }

        servico.setId(null);

        servico.setEstabelecimento((Estabelecimento) usuarioLogado);
        Servico salvo = servicoRepository.save(servico);
        return ResponseEntity.ok(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarServico(@PathVariable Long id, @RequestBody Servico dados) {
        Usuario usuarioLogado = getUsuarioLogado();

        return servicoRepository.findById(id).map(srv -> {
            if (!(usuarioLogado instanceof Estabelecimento)
                    || !srv.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
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
        Usuario usuarioLogado = getUsuarioLogado();

        return servicoRepository.findById(id).map(srv -> {
            if (!(usuarioLogado instanceof Estabelecimento)
                    || !srv.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403).build();
            }
            servicoRepository.delete(srv);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}