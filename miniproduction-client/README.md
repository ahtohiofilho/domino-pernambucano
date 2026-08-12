# Cliente externo da mini-produção

Este módulo mantém uma população padrão de 15 contas sintéticas como clientes
HTTP comuns. Nenhuma conta acessa o store do servidor, injeta resultados ou é
registrada como bot interno.

Fluxo canônico de cada identidade:

1. criação de sessão anônima;
2. promoção para conta persistente, preservando `playerId`;
3. atualização de nome público e sigla de mesa;
4. entrada na fila pública ranqueada;
5. leitura do snapshot projetado para o participante;
6. envio de jogadas legais pelo endpoint de ações;
7. nova entrada na fila após a conclusão autoritativa da partida.

O processo aceita apenas endereço loopback e rejeita a porta de produção
`8080`. O diretório de identidades deve ser absoluto e pertencer à mesma
execução isolada do servidor. Tokens nunca são impressos nos logs.

Variáveis:

```text
DOMINO_MINIPRODUCTION_BASE_URL=http://127.0.0.1:18080
DOMINO_MINIPRODUCTION_CLIENT_STATE_DIR=<diretório absoluto isolado>
DOMINO_MINIPRODUCTION_POPULATION_SIZE=15
DOMINO_MINIPRODUCTION_POLL_INTERVAL_MILLIS=750
```

Execução direta, depois de o servidor estar pronto:

```text
gradlew.bat :miniproduction-client:run
```

A janela de 60 minutos e o encerramento coordenado pertencem ao supervisor do
próximo incremento.
