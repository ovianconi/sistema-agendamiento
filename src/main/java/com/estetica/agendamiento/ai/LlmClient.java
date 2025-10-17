// src/main/java/com/estetica/ai/LlmClient.java
package com.estetica.agendamiento.ai;

public interface LlmClient {
    IntentResult extractIntent(String userText, String localeHint);

    // 🧠 Nuevo método: genera texto natural (usado para reinterpretar errores)
    String generateResponse(String prompt, String locale, double temperature);
}
