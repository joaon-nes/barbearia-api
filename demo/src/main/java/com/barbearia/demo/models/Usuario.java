package com.barbearia.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleUsuario role;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean ativo = false;

    private String codigoVerificacao;

    private String codigo2fa;
}