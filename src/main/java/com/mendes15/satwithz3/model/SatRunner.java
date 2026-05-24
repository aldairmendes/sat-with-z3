package com.mendes15.satwithz3.model;

import com.mendes15.satwithz3.service.FileService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SatRunner implements CommandLineRunner {

    private final FileService fileService;

    public SatRunner(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public void run(String... args) {
        if (args.length < 1) {
            System.err.println("Erro: Informe o número de pessoas. Ex: mvn spring-boot:run -Dspring-boot.run.arguments=\"3\"");
            System.exit(1);
        }

        try {
            int numPeople = Integer.parseInt(args[0]);

            fileService.avaliarDesempenho(numPeople);

            System.exit(0);
        } catch (NumberFormatException e) {
            System.err.println("Erro: O argumento deve ser um número inteiro.");
            System.exit(1);
        }
    }
}