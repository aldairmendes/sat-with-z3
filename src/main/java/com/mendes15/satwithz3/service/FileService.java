package com.mendes15.satwithz3.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mendes15.satwithz3.model.SatSolver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class FileService {

    private final GeminiClient client;
    private final SatSolver satSolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public static final long MILLIS = 4500;

    public FileService(GeminiClient client, SatSolver satSolver) {
        this.client = client;
        this.satSolver = satSolver;
    }

    public void avaliarDesempenhoComValidacaoLLM(int numPeople) {
        String fileName = "people" + numPeople + "_num100.jsonl";
        try {
            var resource = getClass().getClassLoader().getResource("data/" + fileName);
            if (resource == null) {
                System.err.println("[ERRO] Arquivo de dados nao encontrado.");
                return;
            }
            Path path = Path.of(resource.toURI());

            List<JsonNode> nodes = Files.lines(path)
                    .map(line -> { try { return mapper.readTree(line); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull)
                    .toList();

            if (nodes.isEmpty()) return;

            int totalProblemas = nodes.size();
            int totalBitsEsperados = totalProblemas * numPeople;
            int problemasGanhos = 0;
            int acertosIndividuais = 0;

            for (int i = 0; i < totalProblemas; i++) {
                JsonNode node = nodes.get(i);
                String enigma = node.get("quiz").asText();

                List<Message> historico = new ArrayList<>();

                System.out.printf("[PROBLEMA %d DE 100]\n", i + 1);
                System.out.println("ENUNCIADO: " + enigma);

                // ----------------------------------------------------------------------
                // PASSO 1: 1ª Chamada
                // ----------------------------------------------------------------------
                String inputEnigma = "Enigma: " + enigma;
                historico.add(new UserMessage(inputEnigma));

                String respostaJsonLLM = client.enviarComHistorico(historico);
                historico.add(new AssistantMessage(respostaJsonLLM));

                System.out.println("1a RESPOSTA DA LLM (JSON):\n" + respostaJsonLLM.trim());

                // ----------------------------------------------------------------------
                // PASSO 2: Z3
                // ----------------------------------------------------------------------
                String resultadoZ3;
                boolean isSat = true;
                try {
                    JsonNode statementsConvertido = mapper.readTree(respostaJsonLLM.trim());
                    resultadoZ3 = satSolver.solve(statementsConvertido, numPeople);
                    if (resultadoZ3 == null) {
                        resultadoZ3 = "UNSATISFIABLE (Tradução inconsistente/paradoxo)";
                        isSat = false;
                    }
                } catch (Exception e) {
                    resultadoZ3 = "ERRO_SINTAXE_JSON";
                    isSat = false;
                }

                // ----------------------------------------------------------------------
                // PASSO 3: 2ª Chamada
                // ----------------------------------------------------------------------
                String respostaFinalBrutaLLM;

                if (!isSat) {
                    respostaFinalBrutaLLM = "f (Ignorado automaticamente: Z3 não gerou atribuição válida)";
                    System.out.println("2a RESPOSTA DA LLM (VEREDITO): " + respostaFinalBrutaLLM);
                } else {
                    String promptRetorno = String.format(
                            "O solver Z3 processou sua tradução anterior e encontrou a seguinte valoração de modelo: %s. " +
                                    "Com base no enigma original que te enviei no início e nessa valoração do Z3, decida o veredito final. " +
                                    "Responda APENAS com as letras 't' ou 'f' separadas por vírgula na ordem exata dos habitantes, sem explicações extras.",
                            resultadoZ3
                    );

                    historico.add(new UserMessage(promptRetorno));
                    respostaFinalBrutaLLM = client.enviarComHistorico(historico);
                    System.out.println("2a RESPOSTA DA LLM (VEREDITO): " + respostaFinalBrutaLLM.trim());

                    Thread.sleep(MILLIS);
                }

                // ----------------------------------------------------------------------
                // PASSO 4: Validação com Gabarito Real
                // ----------------------------------------------------------------------
                JsonNode solucaoRealNode = node.get("solution");
                StringBuilder gabaritoRealBuffer = new StringBuilder();
                for (JsonNode bit : solucaoRealNode) {
                    gabaritoRealBuffer.append(bit.asBoolean() ? "t" : "f");
                }
                String gabaritoReal = gabaritoRealBuffer.toString();

                System.out.println("GABARITO REAL: " + gabaritoReal);

                String respostaLimpaLLM = respostaFinalBrutaLLM.toLowerCase().replaceAll("[^tf]", "");


                if (isSat && !respostaLimpaLLM.isEmpty() && respostaLimpaLLM.equals(gabaritoReal)) {
                    System.out.println("STATUS: CORRETO");
                    problemasGanhos++;
                } else {
                    System.out.println("STATUS: INCORRETO");
                }

                // Contabiliza os acertos individuais bit a bit (apenas se houve resposta válida da LLM)
                if (isSat && !respostaLimpaLLM.isEmpty()) {
                    int minLength = Math.min(respostaLimpaLLM.length(), gabaritoReal.length());
                    for (int k = 0; k < minLength; k++) {
                        if (respostaLimpaLLM.charAt(k) == gabaritoReal.charAt(k)) {
                            acertosIndividuais++;
                        }
                    }
                }

                System.out.println("----------------------------------------------------------------------\n");

                Thread.sleep(MILLIS);
            }

            System.out.println("================ RELATORIO DE DESEMPENHO MODO VALIDACAO ================");
            imprimirRelatorio(totalProblemas, numPeople, acertosIndividuais, problemasGanhos, totalBitsEsperados);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void avaliarDesempenho(int numPeople) {
        String fileName = "people" + numPeople + "_num100.jsonl";

        try {
            var resource = getClass().getClassLoader().getResource("data/" + fileName);
            if (resource == null) {
                System.err.println("[ERRO] Arquivo nao encontrado no resources: data/" + fileName);
                return;
            }

            Path path = Path.of(resource.toURI());

            List<JsonNode> nodes = Files.lines(path)
                    .map(line -> {
                        try { return mapper.readTree(line); }
                        catch (Exception e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (nodes.isEmpty()) {
                System.err.println("Arquivo vazio ou invalido.");
                return;
            }

            String promptFinal = montarPrompt(nodes, numPeople);

            System.out.println("Arquivo carregado automaticamente: " + fileName);
            System.out.println("Enviando lote de " + nodes.size() + " problemas para o Gemini...");

            String rawResponse = client.enviarParaValidacaoDireta(promptFinal);
            System.out.println("==========================================================");
            System.out.println("Resposta do gemini:\n" + rawResponse);
            System.out.println("==========================================================");

            String[] linhasResposta = rawResponse.split("\\r?\\n");

            validarECompararPorLinha(nodes, linhasResposta, numPeople);

        } catch (Exception e) {
            System.err.println("Erro critico durante a avaliaçao: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String montarPrompt(List<JsonNode> nodes, int numPeople) {
        StringBuilder promptBuilder = new StringBuilder();


        promptBuilder.append(String.format("Aqui estão os %d enigmas que você deve traduzir:\n\n", numPeople));

        for (int i = 0; i < nodes.toArray().length; i++) {
            JsonNode node = nodes.get(i);
            if (node != null && node.has("quiz")) {
                String enigma = node.get("quiz").asText();

                promptBuilder.append(String.format("[ENIGMA %d]\n%s\n\n", i + 1, enigma));
            }
        }

        return promptBuilder.toString().trim();
    }

    private void validarECompararPorLinha(List<JsonNode> nodes, String[] linhasResposta, int numPeople) {
        int acertosIndividuais = 0;
        int problemasGanhos = 0;
        int totalProblemas = nodes.size();
        int totalBitsEsperados = totalProblemas * numPeople;

        System.out.println("Validando respostas diretas conferindo com o Dataset...");

        List<String> linhasValidas = Arrays.stream(linhasResposta)
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        for (int i = 0; i < totalProblemas; i++) {
            if (i >= linhasValidas.size()) {
                System.err.println("[AVISO] O Gemini parou de responder na linha " + i);
                break;
            }

            String linhaCrua = linhasValidas.get(i);
            String sliceLLM = linhaCrua.toLowerCase().replaceAll("[^tf]", "");

            if (sliceLLM.length() < numPeople) {
                System.err.println(String.format("[AVISO] Gemini gerou menos respostas do que o esperado (%d) na linha %d: '%s'", numPeople, i, linhaCrua));
                continue;
            }

            JsonNode solucaoRealNode = nodes.get(i).get("solution");
            StringBuilder gabaritoRealBuffer = new StringBuilder();
            for (JsonNode bit : solucaoRealNode) {
                gabaritoRealBuffer.append(bit.asBoolean() ? "t" : "f");
            }
            String sliceDataset = gabaritoRealBuffer.toString();

            boolean erroNoProblema = false;

            for (int h = 0; h < numPeople; h++) {
                boolean llmDizT = sliceLLM.charAt(h) == 't';
                boolean datasetDizT = sliceDataset.charAt(h) == 't';

                if (llmDizT == datasetDizT) {
                    acertosIndividuais++;
                } else {
                    erroNoProblema = true;
                }
            }

            if (!erroNoProblema) {
                problemasGanhos++;
            } else {
                String enigma = nodes.get(i).get("quiz").asText();
                System.out.println(String.format("======================================================================"));
                System.out.println(String.format("[ERRO NO PROBLEMA %d]", i + 1));
                System.out.println(String.format("ENUNCIADO: %s", enigma));
                System.out.println(String.format("GABARITO REAL: %s", sliceDataset));
                System.out.println(String.format("RESPOSTA LLM : %s (Original: '%s')", sliceLLM.substring(0, numPeople), linhaCrua));
                System.out.println(String.format("======================================================================\n"));
            }
        }

        System.out.println("================ RELATORIO DE DESEMPENHO MODO NORMAL ================");
        imprimirRelatorio(totalProblemas, numPeople, acertosIndividuais, problemasGanhos, totalBitsEsperados);
    }

    public void executarModoUnitarioPuro(int numPeople) {
        String fileName = "people" + numPeople + "_num100.jsonl";

        try {
            var resource = getClass().getClassLoader().getResource("data/" + fileName);
            if (resource == null) {
                System.err.println("[ERRO] Arquivo de dados não encontrado.");
                return;
            }
            Path path = Path.of(resource.toURI());

            List<JsonNode> nodes = Files.lines(path)
                    .map(line -> { try { return mapper.readTree(line); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull)
                    .toList();

            if (nodes.isEmpty()) return;

            int totalProblemas = nodes.size();
            int problemasGanhos = 0;
            int acertosIndividuais = 0;
            int totalBitsEsperados = totalProblemas * numPeople;

            for (int i = 0; i < totalProblemas; i++) {
                JsonNode node = nodes.get(i);
                String enigma = node.get("quiz").asText();

                System.out.printf("[PROBLEMA %d DE %d]\n", i + 1, totalProblemas);
                System.out.println("ENUNCIADO: " + enigma);

                String linhaCrua = client.enviarParaValidacaoUnitaria(enigma);
                String respostaLimpa = linhaCrua.toLowerCase().replaceAll("[^tf]", "");

                JsonNode solucaoRealNode = node.get("solution");
                StringBuilder gabaritoRealBuffer = new StringBuilder();
                for (JsonNode bit : solucaoRealNode) {
                    gabaritoRealBuffer.append(bit.asBoolean() ? "t" : "f");
                }
                String gabaritoReal = gabaritoRealBuffer.toString();

                System.out.println("RESPOSTA LLM: " + respostaLimpa);
                System.out.println("GABARITO REAL: " + gabaritoReal);

                boolean erroNoProblema = false;
                if (respostaLimpa.length() < numPeople) {
                    System.out.println("STATUS: INCORRETO (Resposta incompleta)");
                    erroNoProblema = true;
                } else {
                    for (int h = 0; h < numPeople; h++) {
                        if (respostaLimpa.charAt(h) == gabaritoReal.charAt(h)) {
                            acertosIndividuais++;
                        } else {
                            erroNoProblema = true;
                        }
                    }

                    if (!erroNoProblema) {
                        System.out.println("STATUS: CORRETO");
                        problemasGanhos++;
                    } else {
                        System.out.println("STATUS: INCORRETO");
                    }
                }

                System.out.println("----------------------------------------------------------------------\n");

                Thread.sleep(MILLIS);
            }

            System.out.println("================ RELATORIO DE DESEMPENHO MODO UNITARIO ================");
            imprimirRelatorio(totalProblemas, numPeople, acertosIndividuais, problemasGanhos, totalBitsEsperados);
        } catch (Exception e) {
            System.err.println("[ERRO NO MODO UNIT-TEST]: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void imprimirRelatorio(int totalProb, int numPeople, int bitsCorretos, int probGanhos, int esperado) {
        double accPorHabitante = (double) bitsCorretos / esperado * 100;
        double accPorProblema = (double) probGanhos / totalProb * 100;

        System.out.printf("Quantidade de pessoas por problema: %d%n", numPeople);
        System.out.printf("Problemas 100%% corretos: %d/%d (%.2f%%)%n", probGanhos, totalProb, accPorProblema);
        System.out.printf("Acurácia por habitante (Decisoes corretas): %.2f%%%n", accPorHabitante);
        System.out.println("=====================================================================\n");
    }
}
