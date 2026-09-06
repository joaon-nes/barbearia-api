package com.barbearia.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Base64;

@Service
public class MediaValidationService {
    private static final int MAX_IMAGE_LENGTH = 2_800_000;
    private final ObjectMapper mapper = new ObjectMapper();

    public String imagem(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() > MAX_IMAGE_LENGTH || value.chars().anyMatch(c -> c < 32 || c == 34 || c == 39 || c == 60 || c == 62)) {
            throw new IllegalArgumentException("Imagem inválida ou maior que 2 MB.");
        }
        if (value.startsWith("data:image/")) {
            if (!value.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+")) {
                throw new IllegalArgumentException("Formato de imagem não permitido.");
            }
            try { Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Imagem Base64 inválida."); }
        } else {
            URI uri;
            try { uri = URI.create(value); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("URL de imagem inválida."); }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Use uma imagem PNG/JPEG/WebP ou uma URL HTTPS.");
            }
        }
        return value;
    }

    public String galeria(String value) {
        if (value == null || value.isBlank()) return "[]";
        if (value.length() > 14_000_000) throw new IllegalArgumentException("Galeria muito grande.");
        try {
            var items = mapper.readTree(value);
            if (!items.isArray() || items.size() > 5) throw new IllegalArgumentException("Use até cinco imagens.");
            for (var item : items) {
                if (!item.isTextual()) throw new IllegalArgumentException("Imagem inválida.");
                imagem(item.asText());
            }
            return mapper.writeValueAsString(items);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Galeria deve ser uma lista JSON de imagens.");
        }
    }
}
