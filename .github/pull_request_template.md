Fecha #

## Resumo
<!-- O que muda e por quê, em poucas linhas. -->

## Decisões
<!-- Escolhas técnicas relevantes e trade-offs. Decisões estruturais viram ADR em docs/adr/. -->

## Como testar
```powershell
./mvnw verify
```

## Riscos
<!-- O que pode quebrar, impacto em dados, compatibilidade. -->

## O que ficou sem validar
<!-- Seja explícito: rede, Docker, navegador, provedores reais etc. -->

## Checklist
- [ ] `./mvnw verify` verde (testes, JaCoCo, Checkstyle e Spotless)
- [ ] Nenhum dado simulado no produto; projeções rotuladas como projeção
- [ ] `BigDecimal` em dinheiro, quantidade, preço médio, IR e saldo
- [ ] Nenhuma chave, token ou `.env` no commit
- [ ] `docs/PROJETO.md` atualizado
- [ ] Capturas em `docs/screenshots/` (se alterar a interface)
