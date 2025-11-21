package com.estetica.agendamiento.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OpenAiAudioClient {

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();

    public String transcribe(byte[] audio, String filename, String language) {
        try {
            String url = "https://api.openai.com/v1/audio/transcriptions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", "whisper-1");
            if (language != null)
                body.add("language", language);

            body.add("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = rest.postForEntity(url, request, Map.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return (String) resp.getBody().get("text");
            }

            System.err.println("⚠️ Error al transcribir audio: " + resp);
        } catch (Exception e) {
            System.err.println("❌ Error de comunicación con Whisper: " + e.getMessage());
        }
        return null;
    }
}
