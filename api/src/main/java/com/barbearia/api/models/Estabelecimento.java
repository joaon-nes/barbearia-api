package com.barbearia.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "estabelecimentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estabelecimento extends Usuario {

    private String nomeBarbearia;
    private String cnpj;
    private String cep;
    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private String estado;

    @Column(columnDefinition = "TEXT")
    private String horariosFuncionamento;

    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "boolean default false")
    private Boolean perfilCompleto = false;

    private String tags;
    private String comodidades;

    private String linkInstagram;
    private String linkFacebook;
    private String linkTiktok;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fotosGaleria;
}