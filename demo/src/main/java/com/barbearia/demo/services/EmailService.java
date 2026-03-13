package com.barbearia.demo.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarEmailConfirmacao(String para, String nome, String codigo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(para);
        mensagem.setSubject("Confirme a sua conta - Barbearia");
        mensagem.setText("Olá " + nome + ",\n\nObrigado por se registar!\n\n"
                + "Clique no link abaixo para ativar a sua conta:\n"
                + "http://localhost:8080/api/usuarios/confirmar?codigo=" + codigo);
        mailSender.send(mensagem);
    }

    public void enviarEmail2FA(String para, String nome, String codigo2fa) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(para);
        mensagem.setSubject("Código de Acesso 2FA - Barbearia");
        mensagem.setText(
                "Olá " + nome + ",\n\nO seu código de verificação para aceder ao sistema é: " + codigo2fa + "\n\n"
                        + "Este código é válido para este login.");
        mailSender.send(mensagem);
    }
}