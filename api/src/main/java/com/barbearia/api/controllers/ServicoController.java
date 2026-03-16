package com.barbearia.demo.controllers;

import com.barbearia.demo.models.Servico;
import com.barbearia.demo.models.Usuario;
import com.barbearia.demo.repositories.ServicoRepository;
import com.barbearia.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/servicos")
@CrossOrigin(origins = "*")
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
        Optional<Usuario> estOpt = usuarioRepository.findById(id);

        if (estOpt.isPresent()) {
            servico.setEstabelecimento(estOpt.get());
            Servico salvo = servicoRepository.save(servico);
            return ResponseEntity.ok(salvo);
        }
        return ResponseEntity.badRequest().body("Estabelecimento não encontrado.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarServico(@PathVariable Long id, @RequestBody Servico dados) {
        return servicoRepository.findById(id).map(srv -> {
            srv.setNome(dados.getNome());
            srv.setPreco(dados.getPreco());
            srv.setDuracaoMinutos(dados.getDuracaoMinutos());
            return ResponseEntity.ok(servicoRepository.save(srv));
        }).orElse(ResponseEntity.notFound().build());
    }
}