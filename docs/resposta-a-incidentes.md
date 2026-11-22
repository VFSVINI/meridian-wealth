# Resposta a incidentes

Roteiro curto para um incidente de segurança com possível impacto em dados pessoais ou em contas. O MeridianWealth guarda e-mail, nome, perfil de investidor, posições e movimentações; não guarda credenciais de corretoras, dados de cartão nem documentos.

## 1. Detectar e classificar
- Sinais: pico de 401 ou 429 (log de acesso com Correlation-ID e métricas), circuitos abertos sem causa externa, relato pelo canal privado do GitHub ([SECURITY.md](../SECURITY.md)), alerta do Dependabot ou do CodeQL.
- Classifique: **segredo vazado** (chave de API, `MW_JWT_SECRET`, `MW_METRICS_TOKEN`), **conta comprometida**, **vulnerabilidade explorável** ou **vazamento de dados**.

## 2. Conter
| Situação | Ação |
|---|---|
| `MW_JWT_SECRET` vazado | Trocar o valor e reiniciar: todos os access tokens deixam de valer na hora |
| Refresh tokens comprometidos | Em PostgreSQL: `DELETE FROM refresh_tokens;` (todos entram de novo) ou por usuário: `DELETE FROM refresh_tokens WHERE user_id = '<id>';` |
| Chave de provedor vazada | Revogar no painel do provedor, gerar outra e atualizar a variável de ambiente |
| `MW_METRICS_TOKEN` vazado | Trocar o valor (ou esvaziá-lo: as métricas somem, 404) |
| Abuso por um IP | Bloquear no proxy; o rate limit já segura o volume |
| Vulnerabilidade explorável | Desligar a rota afetada por configuração, se possível, e corrigir em PR com teste |

## 3. Erradicar e recuperar
Corrija a causa em PR com teste de regressão (o ritual do projeto), faça o deploy, confirme no app real que o ataque não funciona mais e só então reabra o que foi desligado.

## 4. Notificar (LGPD, art. 48)
Se houve acesso indevido a dados pessoais com risco ou dano relevante aos titulares, comunique **os titulares afetados e a ANPD** em prazo razoável, com: natureza dos dados, titulares envolvidos, medidas técnicas adotadas, riscos e o que foi feito para reverter ou mitigar. Registre a decisão (notificar ou não, e por quê) mesmo quando a conclusão for não notificar.

## 5. Aprender
Registre o incidente (o que, quando, impacto, causa, correção) em [PROJETO.md](PROJETO.md) ou numa Issue, atualize [seguranca-checklist.md](seguranca-checklist.md) e acrescente o teste que teria pego o problema.
