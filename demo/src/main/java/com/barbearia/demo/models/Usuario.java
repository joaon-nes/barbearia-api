package com.barbearia.demo.models;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String email;
    private String senha;
    private String codigoVerificacao;
    private Boolean ativo;
    private String codigo2fa;

    @Enumerated(EnumType.STRING)
    private RoleUsuario role;

    private String nomeBarbearia;
    private String cnpj;
    private String telefone;
    private String cep;
    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private String estado;
    private String comodidades;
    private String linkInstagram;
    private String linkFacebook;
    private String linkTiktok;

    @Column(columnDefinition = "TEXT")
    private String horariosFuncionamento;

    @Column(columnDefinition = "boolean default false")
    private Boolean perfilCompleto = false;

    @Column(columnDefinition = "boolean default false")
    private Boolean contaVerificada = false;

    private String tags;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fotoPerfil;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fotosGaleria;
    
}