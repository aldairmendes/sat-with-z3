package com.mendes15.satwithz3;

import com.mendes15.satwithz3.service.FileService;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SatWithZ3ApplicationTests {

    @Autowired
    private FileService fileService;

    @BeforeAll
    static void loadEnv() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            String apiKey = dotenv.get("GEMINI_API_KEY");

            if (apiKey != null && !apiKey.isEmpty()) {
                System.setProperty("spring.ai.google.genai.api-key", apiKey);
            } else {
                System.err.println("[AVISO] GEMINI_API_KEY não encontrada");
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao carregar o arquivo .env nos testes: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Executa o pipeline completo de tradução da LLM e validação de tautologia via Z3")
    void rodarPipelineTraducaoLLM() {
        String numPeopleProp = System.getProperty("numPeople");
        int numPeople = Integer.parseInt(numPeopleProp);

        fileService.avaliarDesempenhoComValidacaoLLM(numPeople);
    }
}