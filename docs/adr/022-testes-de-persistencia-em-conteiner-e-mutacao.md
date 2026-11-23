# ADR 022 — Persistência testada em contêiner, concorrência pela API e teste de mutação

- **Status:** aceito
- **Data:** 2026-11-22
- **Issue:** #53 (item #20 do roadmap)

## Contexto

Os testes de persistência rodam num PostgreSQL embutido (zonky): um binário baixado, com versão própria do servidor e um `commons-lang3` mais novo que o do Spring Boot (dívida 36). Ele permite rodar tudo sem Docker, mas as garantias mais importantes do projeto (livro atômico, `Idempotency-Key`, rotação do refresh token, bloqueio otimista) são justamente as que dependem do comportamento real do PostgreSQL sob concorrência. Além disso, ninguém tinha medido se os testes **quebram quando o código muda**: cobertura de linhas (JaCoCo) mede o que foi executado, não o que foi verificado.

## Decisão

1. **Dois fundos para os mesmos testes.** `PostgresDb` escolhe o PostgreSQL por `-Dmw.test.postgres=`: o padrão segue sendo o binário embutido (roda em qualquer máquina, sem Docker); `container` sobe um PostgreSQL 16 num contêiner do **Testcontainers 1.21.4** (a última da série 1.x; a 1.19 gerenciada pelo Spring Boot 3.3 não negocia a versão da API com Docker recente). A CI roda o `verify` no embutido e, em seguida, os contratos de persistência, a migração V3 e a aplicação inteira com banco **no contêiner**. Localmente, sem Docker, o modo contêiner falha com a mensagem do Testcontainers; só a CI o exercita.
2. **Concorrência e idempotência pela API HTTP**, nos dois modos de persistência (os cenários rodam duas vezes): cadastro simultâneo do mesmo e-mail (exatamente uma conta, as outras 409), refresh simultâneo do mesmo cookie (exatamente uma troca, as outras 401, família revogada, inclusive o token do vencedor), venda total simultânea da mesma posição (exatamente uma venda, as outras erro de cliente, nunca 500, livro com abertura mais uma venda), além dos testes de chave repetida e de compras paralelas que já existiam. Cada teste prova a invariante, não só a ausência de erro.
3. **PIT (teste de mutação) sobre o domínio** (`mvn test-compile org.pitest:pitest-maven:mutationCoverage`, fora do `verify` porque leva alguns minutos, num job próprio da CI com o relatório publicado). A primeira medida foi **71% de mutantes mortos** (força dos testes de 73%), com o motor de timing em 85% de sobreviventes: os testes antigos conferiam faixas, não valores. Resposta:
   - **teste dourado do motor** (`TimingEngineGoldenTest`): onze cenários determinísticos congelam todos os campos da avaliação, inclusive os textos dos fatores; é um teste de caracterização (prova que qualquer mudança aparece, não que a regra está certa; as fórmulas têm testes próprios);
   - testes de frescor, de beta, de volume relativo, de ATR, de quantis, de limites do Monte Carlo e da correlação;
   - dois **defeitos reais** apareceram no caminho: o beta com benchmark praticamente parado (variância de arredondamento) devolvia números gigantes (da ordem de 10¹²) porque só tratava variância exatamente zero (achado ao montar os cenários do teste dourado; o PIT mostrava o `beta` com mutantes vivos), e o motor podia dizer "Realizar lucro" com a explicação "sem gatilhos relevantes" (achado ao cobrir o ramo que o PIT mostrou sem cobertura): agora o fator de reserva só aparece com score neutro, e a soma de leituras moderadas é explicada.
   Depois disso, **85% mortos e força de 87%**; a meta no `pom.xml` é de **80%**, com margem, e a CI falha abaixo dela.
4. **Os mutantes que sobram** ficam documentados como dívida (equivalentes, ou em código de baixo valor): a meta sobe quando alguém os matar, nunca desce.

## Consequências

- A CI fica mais lenta (o passo do contêiner e o job de mutação), em troca de provar as garantias de concorrência num PostgreSQL de produção e de medir a força dos testes.
- O binário embutido continua sendo o caminho de desenvolvimento; a dívida 36 muda de natureza: o Testcontainers existe e a CI o usa, e o embutido fica como conveniência local, não como a única prova.
- O teste dourado trava comportamento: mudar uma regra do motor de propósito exige regravar os valores, o que torna a mudança visível na revisão.

## Alternativas descartadas

- **Trocar o embutido pelo Testcontainers de vez:** exigiria Docker em toda máquina de desenvolvimento (a do projeto não tem).
- **Mutação em todo o código:** a camada web e a infraestrutura dependem de Spring e rede, e o custo e o ruído superam o ganho; o domínio é o código puro e o de maior risco.
- **Meta de 100% de mutação:** gera teste só para matar mutante equivalente.
