# Knights and Knaves: LLM vs Z3 SAT Solver 🧩🤖

Este projeto é um pipeline de avaliação automatizado projetado para medir a capacidade de raciocínio lógico de Grandes Modelos de Linguagem (LLMs). Ele processa lotes de 100 problemas do clássico enigma lógico **Knights and Knaves** (Variando de 2 a 8 habitantes por problema), extraídos de arquivos estruturados em formato `.jsonl`.

O sistema envia os enigmas para o **Gemini**, enquanto resolve as mesmas restrições lógicas em tempo real usando o provador de teoremas **Z3 SAT Solver**. A resposta matemática do Z3 é usada como o gabarito absoluto para gerar um relatório detalhado de acurácia do modelo de IA.

---

## 🛠️ Tecnologias e Versões

* **Linguagem:** Java 21 (LTS)
* **Framework:** Spring Boot v3.5.14 (Modo CLI / CommandLineRunner)
* **Orquestrador de Dependências:** Maven
* **Engine SAT Solver:** Microsoft Z3 Theorem Prover v4.16.0
* **Modelo de LLM:** Gemini 3.1 Flash Lite *(Configurado via API)*

---

## 🧩 Arquitetura do Fluxo de Avaliação

1. **Leitura Dinâmica:** O sistema lê arquivos como `people2_num100.jsonl` contendo 100 problemas estruturados.
2. **Batch Prompting:** Monta um lote com os textos dos quizes (modo normal) ou envia os problemas individualmente (modos unitário e validação) e instrui o Gemini a responder em formato de linhas restritas (ex: `t,f`).
3. **Resolução Simbólica:** O `SatSolver` analisa a árvore lógica abstrata (`statements`) contida no JSONL e instancia variáveis booleanas sequenciais ($H_0, H_1, \dots, H_n$) no núcleo C++ do Z3 para deduzir a única resposta matematicamente válida.
4. **Validação Cruzada:** O sistema compara o caractere de cada linha da IA com o booleano gerado pelo Z3, evitando deslocamentos globais de strings.

---

## 💾 Como Baixar e Configurar o Z3 Solver (v4.16.0)

Como o Z3 possui um motor nativo (escrito em C++), o Java precisa acessar os binários do sistema para que as ferramentas funcionem corretamente. Siga o passo a passo abaixo:

### Passo 1: Baixar os Binários Oficiais
1. Acesse o repositório oficial do Z3 no GitHub: [Z3 Releases](https://github.com/Z3Prover/z3/releases).
2. Busque pela versão **`z3-4.16.0`**.
3. Baixe o pacote compactado (`.zip` ou `.tar.gz`) correspondente ao seu sistema operacional e arquitetura (ex: `z3-4.16.0-x64-win.zip` para Windows ou `z3-4.16.0-x64-ubuntu` para Linux).

### Passo 2: Configurar Variáveis de Ambiente
Após extrair o arquivo baixado em um local definitivo do seu computador (ex: `C:\z3\`), você deve apontar o caminho da pasta `bin` para o sistema:

* **No Windows:**
    1. Pesquise por "Editar as variáveis de ambiente do sistema".
    2. Clique em **Variáveis de Ambiente**.
    3. Em *Variáveis do Sistema*, selecione **Path** e clique em *Editar*.
    4. Adicione o caminho completo até a pasta bin (Ex: `C:\z3\bin`).
    5. Salve e reinicie seu terminal ou IDE.

* **No Linux / macOS:**
  Adicione a seguinte linha ao seu arquivo de configuração (`.bashrc` ou `.zshrc`):
    ```bash
    export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/caminho/para/z3-4.16.0/bin
    export PATH=$PATH:/caminho/para/z3-4.16.0/bin
    ```

---

## 🚀 Como Iniciar a Aplicação

### Pré-requisitos
* Certifique-se de que sua API Key do Gemini está configurada adequadamente nas propriedades do Spring (`application.yaml`) ou execute o seguinte comando:
* no Linux / macOS / Git Bash:
  `echo "GEMINI_API_KEY=<sua_chave_aqui>" >> .env`
* no Windows:
  `Add-Content -Path .env -Value "GEMINI_API_KEY=<sua_chave_aqui>"`
* Os arquivos `.jsonl` devem estar localizados no diretório: `src/main/resources/data/`.

### Opção 1: Executando com o Maven (Modo Normal)
O modo mais simples onde a llm recebe todos os problemas de uma vez em um único prompt.

```bash
# Substitua o número "2" pela quantidade de pessoas desejada (2 a 8)
mvn spring-boot:run -Dspring-boot.run.arguments="2"
```
### Opção 2: Executando com o Maven (Modo Unitário)
Nesse modo, os problemas são enviados para a llm um de cada vez.

```bash
# Substitua o número "2" pela quantidade de pessoas desejada (2 a 8)
mvn spring-boot:run -Dspring-boot.run.arguments="2 -unit"
```
### Opção 3: Executando com o Maven (Modo Validação)
Nesse modo, os problemas são enviados para a llm um de cada vez e ela responde com uma expressão que o solver Z3 entende, então, o Z3 devolve a valoração que resolve a expressão e essa resposta é devolvida com histórico para a llm, a llm então terá que decidir se vai responder com o que o solver mandou, ou não.

```bash
# Substitua o número "2" pela quantidade de pessoas desejada (2 a 8)
mvn spring-boot:run -Dspring-boot.run.arguments="2 -validation"
```