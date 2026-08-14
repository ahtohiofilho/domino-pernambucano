# Cliente externo da população sintética

Este módulo mantém uma população padrão de 16 contas sintéticas como clientes
HTTP comuns. Nenhuma conta acessa o store do servidor, injeta resultados ou é
registrada como bot interno. A promoção autenticada grava o tipo canônico
`SYNTHETIC`, distinto de `HUMAN` e de `APPLICATION`.

Fluxo canônico de cada identidade:

1. criação de sessão anônima;
2. promoção para conta persistente, preservando `playerId`;
3. atualização de nome público e sigla de mesa;
4. entrada na fila pública ranqueada;
5. leitura do snapshot projetado para o participante;
6. envio de jogadas legais pelo endpoint de ações;
7. nova entrada na fila após a conclusão autoritativa da partida;
8. recuperação de sessão para o mesmo `accountId` quando o token expira.

Tokens nunca são impressos nos logs. O diretório de identidades deve ser
absoluto, persistente e exclusivo de uma instância da população.

## Alvos explícitos

`LOCAL_MINIPRODUCTION` é o padrão compatível com o supervisor existente. Ele
aceita somente HTTP loopback, porta explícita diferente de `8080` e também lê
as variáveis legadas `DOMINO_MINIPRODUCTION_*`.

`REMOTE_PRODUCTION` deve ser selecionado explicitamente. Ele exige HTTPS,
rejeita loopback, caminhos adicionais, credenciais na URL e portas diferentes
da HTTPS padrão. Selecionar esse alvo não realiza deploy nem ativa qualquer
carga.

Variáveis do runtime remoto:

```text
DOMINO_SYNTHETIC_RUNTIME_TARGET=REMOTE_PRODUCTION
DOMINO_SYNTHETIC_BASE_URL=https://<domínio-real>
DOMINO_SYNTHETIC_CLIENT_STATE_DIR=<diretório absoluto persistente>
DOMINO_SYNTHETIC_POPULATION_SIZE=16
DOMINO_SYNTHETIC_POLL_INTERVAL_MILLIS=750
DOMINO_SYNTHETIC_HEARTBEAT_FILE=<arquivo absoluto>
DOMINO_SYNTHETIC_MAX_SILENCE_MILLIS=180000
DOMINO_SYNTHETIC_PROVISIONING_SECRET_FILE=<arquivo absoluto restrito>
```

O segredo também pode ser entregue diretamente por
`DOMINO_SYNTHETIC_PROVISIONING_SECRET`, mas as duas origens não podem coexistir.

Execução direta, depois de o servidor estar pronto:

```text
gradlew.bat :miniproduction-client:run
```

O supervisor local continua responsável pela janela de 60 minutos. O runtime
remoto é contínuo, encerra de forma coordenada por `SIGTERM` e falha se ficar
sem interação bem-sucedida com o servidor além da tolerância configurada.
