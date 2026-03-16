package com.barbearia.demo.controllers;

import com.barbearia.demo.models.Estabelecimento;
import com.barbearia.demo.models.Usuario;
import com.barbearia.demo.repositories.UsuarioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository repository;

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

    @PostMapping
    public ResponseEntity<Usuario> criar(@Valid @RequestBody Usuario usuario) {
        return ResponseEntity.ok(repository.save(usuario));
    }

    @PutMapping("/{id}/completar-perfil")
    public ResponseEntity<?> completarPerfil(@PathVariable Long id, @RequestBody Estabelecimento dadosCompletos) {
        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento) {
                Estabelecimento est = (Estabelecimento) u;
                est.setNomeBarbearia(dadosCompletos.getNomeBarbearia());
                est.setCnpj(dadosCompletos.getCnpj());
                est.setTelefone(dadosCompletos.getTelefone());
                est.setCep(dadosCompletos.getCep());
                est.setRua(dadosCompletos.getRua());
                est.setNumero(dadosCompletos.getNumero());
                est.setBairro(dadosCompletos.getBairro());
                est.setCidade(dadosCompletos.getCidade());
                est.setEstado(dadosCompletos.getEstado());
                est.setHorariosFuncionamento(dadosCompletos.getHorariosFuncionamento());
                est.setTags(dadosCompletos.getTags());
                est.setComodidades(dadosCompletos.getComodidades());
                est.setLinkInstagram(dadosCompletos.getLinkInstagram());
                est.setLinkFacebook(dadosCompletos.getLinkFacebook());
                est.setLinkTiktok(dadosCompletos.getLinkTiktok());
                est.setPerfilCompleto(true);
                return ResponseEntity.ok(repository.save(est));
            }
            return ResponseEntity.badRequest().body("O utilizador não é um Estabelecimento.");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<?> atualizarTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
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
        return repository.findById(id).map(u -> {
            u.setFotoPerfil(body.get("fotoPerfil"));
            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/galeria")
    public ResponseEntity<?> atualizarGaleria(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id).map(u -> {
            if (u instanceof Estabelecimento) {
                ((Estabelecimento) u).setFotosGaleria(body.get("fotosGaleria"));
                return ResponseEntity.ok(repository.save(u));
            }
            return ResponseEntity.badRequest().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}