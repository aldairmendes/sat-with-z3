package com.mendes15.satwithz3.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class GeminiClient {

    private final ChatClient chatClient;

    public GeminiClient(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("Você é um especialista em lógica formal. " +
                        "Sua tarefa é ajudar a resolver enigmas de Knights and Knaves " +
                        "convertendo-os em proposições para o solver Z3.")
                .build();
    }

    public String enviarPrompt(String prompt) {
        return this.chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}