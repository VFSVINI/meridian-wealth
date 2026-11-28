# ADR 041 — Painéis de Risco, Saúde dos dados, Simulador e Mapa sobre dado real

- **Status:** aceito
- **Data:** 2026-11-28
- **Issue:** #101 (item #53 do roadmap)

## Contexto

O protótipo Cosmos desenha quatro painéis "de exemplo": a paisagem de probabilidades do Monte Carlo, a constelação de fontes, a órbita da carteira e o mapa de bolsas. Alguns desenhos pedem dados que o projeto não tem (disponibilidade em 24 horas, tempo de resposta, custos da ordem, retorno de um "perfil"). O ADR 004 proíbe dado simulado.

## Decisão

1. **Onde faltava dado real, o dado passou a existir ou o gráfico não foi desenhado.** A paisagem de probabilidades precisa da distribuição do patrimônio em cada ano: `MonteCarlo.Result` e a API passam a devolver `density` (48 faixas comuns, de 0 ao percentil 99 do patrimônio final, contadas ano a ano sobre as mesmas 5.000 trajetórias; quem passa do topo cai na última faixa). Disponibilidade em 24 horas e tempo de resposta por hora não existem no servidor: a Saúde dos dados mostra o que ele mede (chamadas, sucessos, falhas, barradas, latência média, circuito) e diz que o histórico não existe. Custos da ordem viraram "custos não incluídos".
2. **Nada é sorteado no navegador.** As curvas, o cone, os planetas e as estrelas são desenhados a partir de respostas da API; as quedas de 10%, 20% e 30% do Simulador são aritmética sobre a posição, marcadas como cenário hipotético.
3. **Anéis e cores significam algo medível:** na órbita, o anel é a volatilidade anualizada real do ativo (sem volatilidade, anel pontilhado); no mapa, a cor do país é a variação média dos ativos mostrados e país sem ativo fica tracejado e "não informado"; na constelação, a cor é o estado do circuito.
4. **Premissas de "perfil" não foram inventadas:** o protótipo liga Conservador, Moderado e Arrojado a retorno e volatilidade; sem fonte para esses números, retorno e volatilidade continuam sendo entrada do usuário.
5. **Local das bolsas é fato público** (cidade de cada bolsa); a aba Brasil do mapa continua sendo o mapa dos imóveis dos fundos, já existente.
6. **Movimento sob controle:** órbitas e estrelas giram devagar, pausam com o ponteiro ou o foco e ficam paradas com "movimento reduzido".

## Consequências

- A resposta do Monte Carlo cresce cerca de 41 × 48 inteiros (poucos KB) e continua determinística para a mesma semente.
- Se um dia o servidor guardar histórico de disponibilidade, a Saúde dos dados ganha as séries do protótipo sem mudar o desenho.
