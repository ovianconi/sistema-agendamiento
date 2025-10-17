// src/main/java/com/estetica/ai/LlmFactory.java
package com.estetica.agendamiento.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmFactory {
    @Bean
    public LlmClient llmClient(@Value("${llm.provider:openai}") String p, OpenAiLlmClient openai,
            DeepSeekLlmClient deepseek) {
        return "deepseek".equalsIgnoreCase(p) ? deepseek : openai;
    }
}
