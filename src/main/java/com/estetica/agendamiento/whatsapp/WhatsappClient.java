package com.estetica.agendamiento.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class WhatsappClient {
    @Value("${whatsapp.access-token}")
    private String accessToken;

    private final RestTemplate http = new RestTemplate();

    public void sendText(String phoneNumberId, String toWaId, String body) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(accessToken);

        Map<String, Object> payload = Map.of("messaging_product", "whatsapp", "to", toWaId, "type",
                "text", "text", Map.of("body", body));

        http.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, h), String.class);
    }
}
