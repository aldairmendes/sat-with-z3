package com.mendes15.satwithz3.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiClient {

    private final ChatClient.Builder chatClientBuilder;
    private final GoogleGenAiChatOptions defaultOptions;

    private static final String PROMPT_MODO_VALIDACAO =
            "Você é um especialista em lógica formal. Sua tarefa é ajudar a resolver enigmas de Knights and Knaves convertendo-os em proposições para o solver Z3.\n\n" +
                    "Atue estritamente como um compilador sintático rígido. Sua tarefa é trazer o enigma Knights & Knaves fornecido em um array JSON válido para o solver Z3.\n\n" +
                    "CONVENÇÃO DE IDENTIFICAÇÃO (MUITO IMPORTANTE):\n" +
                    "- Mapeie cada habitante para um índice INTEIRO com base na ordem em que aparecem no enunciado (começando do 0).\n" +
                    "  Exemplo: Se o texto diz 'You meet 2 inhabitants: Zoey, and Oliver', então Zoey = 0 e Oliver = 1.\n\n" +
                    "OPERADORES PERMITIDOS (USE APENAS ESTES):\n" +
                    "1. Identidade (O habitante diz a verdade): [\"telling-truth\", indice_inteiro]\n" +
                    "2. Negação: \"not\"\n" +
                    "3. Conjunção: \"and\"\n" +
                    "4. Disjunção: \"or\"\n" +
                    "5. Implicação (Se... então): \"implies\"\n" +
                    "6. Equivalência/Bi-implicação (se e somente se): \"==\"\n\n" +
                    "RESTRIÇÕES SINTÁTICAS CRUCIAIS:\n" +
                    "- O segundo elemento de \"telling-truth\" DEVE ser um número inteiro puro, SEM ASPAS. Exemplo correto: [\"telling-truth\", 0]\n" +
                    "- O único símbolo permitido nesta gramática é o operador de igualdade \"==\".\n" +
                    "- É ESTRITAMENTE PROIBIDO o uso do símbolo \"=>\" (substitua sempre por \"implies\").\n" +
                    "- É ESTRITAMENTE PROIBIDO o uso do termo \"iff\" (substitua sempre por \"==\").\n" +
                    "- NUNCA invente operadores como \"is-knight\", \"is-knave\", \"knight\", \"knave\", \"truth\" ou \"false\".\n\n" +
                    "ESTRUTURA DA RESPOSTA:\n" +
                    "- Envolva cada fala do habitante de índice X em uma equivalência com sua própria honestidade:\n" +
                    "  [\"==\", [\"telling-truth\", X], [fala_traduzida]]\n" +
                    "- Responda APENAS o array JSON em linha única. PROIBIDO usar markdown ou blocos ```json.";

    private static final String PROMPT_MODO_NORMAL =
            "Você é um resolvedor de enigmas Knights and Knaves experiente.\n" +
                    "Sua tarefa é analisar a lista de enigmas fornecida e responder com a valoração correta de cada habitante.\n" +
                    "Regras obrigatórias de resposta:\n" +
                    "- Analise cada enigma linha por linha correspondente ao índice fornecido.\n" +
                    "- Para cada enigma, responda APENAS com as letras 't' (para cavaleiro/verdade) ou 'f' (para cafajeste/falso) sem espaços, sem vírgulas.\n" +
                    "- Cada linha da sua resposta deve conter o veredito exato de um problema.\n" +
                    "- Exemplo: se o problema 1 tem 2 habitantes e ambos dizem a verdade, sua primeira linha deve ser apenas: tt\n" +
                    "- Não inclua markdown, não inclua explicações. Apenas os vereditos lineares.";

    public GeminiClient(ChatClient.Builder builder) {
        this.chatClientBuilder = builder;
        this.defaultOptions = GoogleGenAiChatOptions.builder()
                .model("gemini-3.1-flash-lite")
                .temperature(0.0)
                .build();
    }

    public String enviarComHistorico(List<Message> historico) {
        return this.chatClientBuilder.build().prompt()
                .system(PROMPT_MODO_VALIDACAO)
                .options(this.defaultOptions)
                .messages(historico)
                .call()
                .content();
    }

    public String enviarParaValidacaoDireta(String lotePerguntas) {
        return this.chatClientBuilder.build().prompt()
                .system(PROMPT_MODO_NORMAL)
                .options(this.defaultOptions)
                .user(lotePerguntas)
                .call()
                .content();
    }

    public String enviarParaValidacaoUnitaria(String enigma) {
        String inputEnigma = "Enigma: " + enigma;

        return this.chatClientBuilder.build().prompt()
                .system(PROMPT_MODO_NORMAL)
                .options(this.defaultOptions)
                .user(inputEnigma)
                .call()
                .content();
    }
}