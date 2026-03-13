package com.barbearia.demo.controllers;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.services.AgendamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agendamentos")
@CrossOrigin(origins = "*")
public class AgendamentoController {

    @Autowired
    private AgendamentoService service;

    @GetMapping
    public List<Agendamento> listar() {
        return service.listarTodos();
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody Agendamento agendamento) {
        try {
            Agendamento novo = service.criar(agendamento);
            return ResponseEntity.ok(novo);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}