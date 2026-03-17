package com.barbearia.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void enviarEmail(String destinatario, String assunto, String mensagem) {
        try {
            System.out.println("A preparar para enviar e-mail para: " + destinatario);
            
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(destinatario);
            email.setSubject(assunto);
            email.setText(mensagem);
            
            email.setFrom("scrapertibia@gmail.com"); 

            mailSender.send(email);
            System.out.println("E-mail enviado com sucesso para: " + destinatario);
            
        } catch (Exception e) {
            System.err.println("FALHA CRÍTICA AO ENVIAR E-MAIL PARA " + destinatario);
            System.err.println("Motivo do erro: " + e.getMessage());
            e.printStackTrace(); 
        }
    }
}