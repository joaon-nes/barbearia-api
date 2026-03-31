package com.barbearia.api.controllers;

import com.barbearia.api.models.Barbeiro;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.repositories.BarbeiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/barbeiros")
public class BarbeiroController {

    @Autowired
    private BarbeiroRepository barbeiroRepository;

    private Usuario getUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // rota para o cliente e estabelecimento buscarem a equipe
    @GetMapping("/estabelecimento/{estabelecimentoId}")
    public ResponseEntity<List<Barbeiro>> listarPorEstabelecimento(@PathVariable Long estabelecimentoId) {
        return ResponseEntity.ok(barbeiroRepository.findByEstabelecimentoIdAndAtivoTrue(estabelecimentoId));
    }

    // rota para estabelecimento adicionar barbeiro
    @PostMapping("/estabelecimento/{estabelecimentoId}")
    public ResponseEntity<?> criar(@PathVariable Long estabelecimentoId, @RequestBody Barbeiro barbeiro) {
        Usuario usuarioLogado = getUsuarioLogado();

        if (!usuarioLogado.getId().equals(estabelecimentoId) || !(usuarioLogado instanceof Estabelecimento)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("erro", "Acesso negado: Apenas o dono do estabelecimento pode gerenciar a equipe."));
        }
        barbeiro.setId(null);
        barbeiro.setEstabelecimento((Estabelecimento) usuarioLogado);
        return ResponseEntity.ok(barbeiroRepository.save(barbeiro));
    }

    // rota para editar o barbeiro
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody Barbeiro barbeiroAtualizado) {
        Usuario usuarioLogado = getUsuarioLogado();

        return barbeiroRepository.findById(id).map(barbeiro -> {
            if (!barbeiro.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", "Acesso negado."));
            }
            barbeiro.setNome(barbeiroAtualizado.getNome());
            return ResponseEntity.ok(barbeiroRepository.save(barbeiro));
        }).orElse(ResponseEntity.notFound().build());
    }

    // rota para excluir
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        Usuario usuarioLogado = getUsuarioLogado();

        return barbeiroRepository.findById(id).map(barbeiro -> {
            if (!barbeiro.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", "Acesso negado."));
            }
            barbeiro.setAtivo(false);
            barbeiroRepository.save(barbeiro);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}