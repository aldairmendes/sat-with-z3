package com.mendes15.satwithz3;

import com.mendes15.satwithz3.service.FileService;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SatWithZ3Application {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(SatWithZ3Application.class, args);
    }

    @Bean
    public CommandLineRunner clr(FileService fileService) {
        return args -> {
            if (args.length < 1) {
                System.out.println("\n[ERRO] Uso correto: mvn spring-boot:run -Dspring-boot.run.arguments=\"<numero_de_pessoas> [modo]\"");
                System.out.println("Exemplo Modo Lote:       mvn spring-boot:run -Dspring-boot.run.arguments=\"2\"");
                System.out.println("Exemplo Modo Validação:  mvn spring-boot:run -Dspring-boot.run.arguments=\"2 -validation\"");
                System.out.println("Exemplo Modo Unitário:   mvn spring-boot:run -Dspring-boot.run.arguments=\"2 -unit\"\n");
                return;
            }

            try {
                int numPeople = Integer.parseInt(args[0]);

                String flag = (args.length > 1) ? args[1].toLowerCase() : "";

                switch (flag) {
                    case "-validation" -> {
                        System.out.println("[MODO] Iniciando modo validação: LLM -> Z3 -> LLM (Pessoas: " + numPeople + ")");
                        fileService.avaliarDesempenhoComValidacaoLLM(numPeople);
                    }
                    case "-unit" -> {
                        System.out.println("[MODO] Iniciando modo execução unitária: LLM Solo (Pessoas: " + numPeople + ")");
                        fileService.executarModoUnitarioPuro(numPeople);
                    }
                    default -> {
                        System.out.println("[MODO] Iniciando modo normal em lote (Pessoas: " + numPeople + ")");
                        fileService.avaliarDesempenho(numPeople);
                    }
                }

                System.exit(0);
            } catch (NumberFormatException e) {
                System.err.println("[ERRO] O argumento '" + args[0] + "' não é um número válido.");
            } catch (Exception e) {
                System.err.println("[ERRO] Falha na execução: " + e.getMessage());
            }
        };
    }
}