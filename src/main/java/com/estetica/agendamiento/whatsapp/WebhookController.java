package com.estetica.agendamiento.whatsapp;

import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WebhookController {

    @Autowired
    private NluOrchestrator orchestrator;

    // ===========================================================
    // ✅ VERIFICACIÓN DEL WEBHOOK (cuando Meta valida el callback)
    // ===========================================================
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        final String VERIFY_TOKEN = "appSgacer_verifai";

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(verifyToken)) {
            System.out.println("✅ Webhook verificado correctamente por Meta.");
            return ResponseEntity.ok(challenge);
        } else {
            System.err.println("❌ Falló la verificación del Webhook: token o modo inválido.");
            return ResponseEntity.status(403).body("Verification failed");
        }
    }

    // ===========================================================
    // 📩 RECEPCIÓN DE MENSAJES DESDE WHATSAPP CLOUD API
    // ===========================================================
    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println("📦 Payload recibido desde Meta:");
            System.out.println(payload);

            // 1️⃣ Validar tipo de objeto
            if (!"whatsapp_business_account".equals(payload.get("object"))) {
                System.err.println("⚠️ Payload no corresponde a WhatsApp Business Account");
                return ResponseEntity.ok("ignored");
            }

            // 2️⃣ Obtener el array "entry"
            List<Map<String, Object>> entryList = (List<Map<String, Object>>) payload.get("entry");
            if (entryList == null || entryList.isEmpty()) {
                System.err.println("⚠️ No se encontró entry en el payload");
                return ResponseEntity.ok("no entry");
            }

            // 3️⃣ Obtener el primer entry -> change -> value
            Map<String, Object> entry = entryList.get(0);
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null || changes.isEmpty()) {
                System.err.println("⚠️ No hay cambios en el entry");
                return ResponseEntity.ok("no changes");
            }

            Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
            if (value == null) {
                System.err.println("⚠️ No hay campo 'value' en el cambio");
                return ResponseEntity.ok("no value");
            }

            // 4️⃣ Extraer contacto (cliente) y mensaje
            List<Map<String, Object>> contacts = (List<Map<String, Object>>) value.get("contacts");
            List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");

            if (contacts == null || contacts.isEmpty() || messages == null || messages.isEmpty()) {
                System.err.println("⚠️ No hay mensajes o contactos en el payload");
                return ResponseEntity.ok("no messages");
            }

            Map<String, Object> contact = contacts.get(0);
            Map<String, Object> profile = (Map<String, Object>) contact.get("profile");
            Map<String, Object> message = messages.get(0);
            Map<String, Object> text = (Map<String, Object>) message.get("text");

            // 5️⃣ Crear DTO con los datos extraídos
            String telefono = (String) contact.get("wa_id");
            String nombre = (profile != null) ? (String) profile.get("name") : "Cliente";
            String texto = (text != null) ? (String) text.get("body") : "";

            System.out.printf("📲 Mensaje recibido de %s (%s): %s%n", nombre, telefono, texto);

            WhatsappMessageDTO dto = new WhatsappMessageDTO();
            dto.setTelefono(telefono);
            dto.setTexto(texto);

            // 6️⃣ Pasar al orquestador para que procese y responda
            orchestrator.processIncomingMessage(dto);

            return ResponseEntity.ok("EVENT_RECEIVED");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("EVENT_RECEIVED_WITH_ERROR");
        }
    }
}
