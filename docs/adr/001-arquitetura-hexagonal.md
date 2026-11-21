# ADR 001 — Arquitetura hexagonal estrita

- **Status:** aceito (implementação plena e testes ArchUnit no PR #2)
- **Data:** 2026-11-20
- **Issue:** #1

## Contexto

A base herdada já separa `domain`, `application`, `infrastructure` e `web`, mas a separação é por convenção:

- `application` importa Spring (`@Service`, `@Value`, `@Component`) e Jackson (`ObjectMapper` em `MarketService`), então o núcleo de casos de uso não pode ser testado nem reutilizado sem o framework (dívida 1).
- `web` importa tipos de `infrastructure` (`StreamHub`, `ProviderInfo`, `ProviderStats`), acoplando a API REST aos adaptadores concretos (dívida 2).

O roadmap traz carteira, livro de movimentações, idempotência, eventos de domínio e vários provedores de dados. Sem fronteiras verificadas, cada PR aumenta o acoplamento.

## Decisão

Adotar **portas e adaptadores** com regras de dependência verificadas automaticamente:

| Camada | Pode depender de | Não pode depender de |
|---|---|---|
| `domain` | apenas JDK | Spring, Jackson, JDBC, `application`, `infrastructure`, `web` |
| `application` | `domain`, JDK | Spring, Jackson, `infrastructure`, `web` |
| `infrastructure` (adaptadores de saída) | `application`, `domain`, frameworks | `web` |
| `web` (adaptadores de entrada) | `application`, `domain`, Spring Web | `infrastructure` |
| `config` (borda e composição) | tudo | — |

1. **Portas de saída** (`application.port`): interfaces de que o núcleo precisa (`MarketDataPort`, `CacheStore`, `CandleStore`, `SignalStore`, `FundamentalsPort` e, no PR #2, portas de stream e de status dos provedores).
2. **Serviços de aplicação são POJOs**: sem anotações; recebem dependências por construtor e configuração por records imutáveis.
3. **Composição na borda**: classes `@Configuration` num pacote de configuração criam os serviços e ligam as portas aos adaptadores, lendo propriedades com `@ConfigurationProperties`.
4. **Serialização é da borda**: o núcleo não conhece JSON; serializar para cache ou stream é responsabilidade dos adaptadores.
5. **Verificação automática**: testes ArchUnit no `mvn verify` quebram o build quando uma regra é violada.

## Consequências

- **Positivas:** domínio e casos de uso testáveis sem Spring (testes mais rápidos); troca de provedor, cache ou banco sem tocar no núcleo; revisões de PR mais objetivas, porque a regra é executável.
- **Negativas:** mais classes (portas, configuração, mapeadores) e código de ligação explícito.
- **Riscos:** portas anêmicas que só repassam chamadas. Mitigação: uma porta só existe quando há pelo menos um adaptador real e um consumidor no núcleo.

## Alternativas consideradas

- **Manter camadas por convenção:** rejeitado; a base já mostra violações reais sem que nada as detecte.
- **Módulos Maven separados por camada:** isolamento mais forte pelo compilador, mas complica build, Docker e IDE para um time pequeno. Reavaliar se o projeto crescer; o ArchUnit entrega a maior parte do benefício com custo menor.
- **Spring Modulith:** bom para módulos por contexto de negócio, mas não expressa diretamente as regras entre camadas que queremos aqui.
