package com.mendes15.satwithz3.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mendes15.satwithz3.model.SatSolver;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Service
public class FileService {

    private final GeminiClient client;
    private final SatSolver satSolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileService(GeminiClient client, SatSolver satSolver) {
        this.client = client;
        this.satSolver = satSolver;
    }

    public void avaliarDesempenho(int numPeople) {
        String fileName = "people" + numPeople + "_num100.jsonl";

        try {
            var resource = getClass().getClassLoader().getResource("data/" + fileName);
            if (resource == null) {
                System.err.println("[ERRO] Arquivo não encontrado no resources: data/" + fileName);
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
                System.err.println("Arquivo vazio ou inválido.");
                return;
            }

            String promptFinal = montarPrompt(nodes, numPeople);

            System.out.println("--- Iniciando Processamento ---");
            System.out.println("Arquivo carregado automaticamente: " + fileName);
            System.out.println("Enviando lote de " + nodes.size() + " problemas para o Gemini...");

            String rawResponse = client.enviarPrompt(promptFinal);
            System.out.println("==========================================================");
            System.out.println("Resposta do gemini: " + "\n" + rawResponse);
            System.out.println("==========================================================");

            String[] linhasResposta = rawResponse.split("\\r?\\n");

            validarECompararPorLinha(nodes, linhasResposta, numPeople);

        } catch (Exception e) {
            System.err.println("Erro crítico durante a avaliação: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String montarPrompt(List<JsonNode> nodes, int numPeople) {
        StringBuilder sb = new StringBuilder();
        sb.append("Atue como um resolvedor especialista de lógica 'Knights and Knaves'.\n");
        sb.append(String.format("Para cada problema fornecido abaixo, responda com exatamente %d caracteres separados por vírgula (usando 't' para Verdade/Knight e 'f' para Falso/Knave).\n", numPeople));
        sb.append("ATENÇÃO: Você DEVE gerar exatamente uma linha de resposta para cada problema, na mesma ordem.\n");
        sb.append("Não inclua explicações, não use markdown, não use aspas, responda apenas com as letras e vírgulas.\n");

        String exemplo = "t" + ",t".repeat(numPeople - 1);
        sb.append(String.format("Exemplo de linha de resposta correta: %s\n\n", exemplo));

        for (int i = 0; i < nodes.size(); i++) {
            sb.append(String.format("Problema %d: %s\n", i, nodes.get(i).get("quiz").asText()));
        }

        return sb.toString();
    }

    private void validarECompararPorLinha(List<JsonNode> nodes, String[] linhasResposta, int numPeople) {
        int acertosIndividuais = 0;
        int problemasGanhos = 0;
        int totalProblemas = nodes.size();
        int totalBitsEsperados = totalProblemas * numPeople;

        System.out.println("Validando respostas com Z3 Solver...");

        // Filtra e remove linhas vazias da resposta do LLM que possam vir de quebras extras
        List<String> linhasValidas = java.util.Arrays.stream(linhasResposta)
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        for (int i = 0; i < totalProblemas; i++) {
            // Gabarito gerado pelo Z3
            ArrayNode gabaritoZ3 = satSolver.solve(nodes.get(i).get("statements"));

            if (gabaritoZ3 == null) {
                System.err.println("[AVISO] Z3 não conseguiu resolver o problema " + i);
                continue;
            }

            // Se o LLM parou de responder antes do fim da lista
            if (i >= linhasValidas.size()) {
                System.err.println("[AVISO] O Gemini parou de responder na linha " + i);
                break;
            }

            // Limpa a linha atual do LLM deixando só os 't' e 'f' daquele problema específico
            String sliceLLM = linhasValidas.get(i).toLowerCase().replaceAll("[^tf]", "");

            // Se o LLM errou a quantidade de respostas para essa linha, conta o problema como errado
            if (sliceLLM.length() < numPeople) {
                System.err.println(String.format("[AVISO] Gemini gerou menos respostas do que o esperado (%d) na linha %d: '%s'", numPeople, i, linhasValidas.get(i)));
                continue;
            }

            boolean erroNoProblema = false;

            for (int h = 0; h < numPeople; h++) {
                boolean llmDizT = sliceLLM.charAt(h) == 't';

                JsonNode nodeValue = gabaritoZ3.get(h);
                boolean z3DizT = (nodeValue != null) && nodeValue.asBoolean();

                if (llmDizT == z3DizT) {
                    acertosIndividuais++;
                } else {
                    erroNoProblema = true;
                }
            }

            if (!erroNoProblema) {
                problemasGanhos++;
            }
        }

        imprimirRelatorio(totalProblemas, numPeople, acertosIndividuais, problemasGanhos, totalBitsEsperados);
    }

    private void imprimirRelatorio(int totalProb, int numP, int bitsCorretos, int probGanhos, int esperado) {
        double accPorHabitante = (double) bitsCorretos / esperado * 100;
        double accPorProblema = (double) probGanhos / totalProb * 100;

        System.out.println("\n================ RELATÓRIO DE DESEMPENHO ================");
        System.out.printf("Quantidade de pessoas por problema: %d%n", numP);
        System.out.printf("Problemas 100%% corretos: %d/%d (%.2f%%)%n", probGanhos, totalProb, accPorProblema);
        System.out.printf("Acurácia por habitante (Decisões corretas): %.2f%%%n", accPorHabitante);
        System.out.println("=========================================================\n");
    }
}