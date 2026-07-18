# Estado autoritativo em produção

O servidor usa estado volátil somente em `development` e `test`, quando nenhum
arquivo persistente é configurado. O ambiente `production` falha durante o
startup se o armazenamento durável não estiver explicitamente habilitado.

Variáveis obrigatórias para o servidor produtivo:

```text
DOMINO_SERVER_ENVIRONMENT=production
DOMINO_SESSION_SIGNING_SECRET=<segredo com pelo menos 32 bytes>
DOMINO_SERVER_STATE_FILE=<caminho absoluto em volume persistente>
```

Exemplo Linux:

```text
DOMINO_SERVER_STATE_FILE=/var/lib/domino/authoritative-state.json
```

O arquivo é substituído atomicamente e protegido por lock exclusivo. Portanto,
esta implementação opera em uma única instância do servidor. O arquivo e seu
diretório precisam estar em volume persistente, com backup e espaço monitorado.

Para escalar horizontalmente, mantenha `OnlineServerStore` como contrato e
substitua `PersistentOnlineServerStore` por uma implementação transacional em
banco compartilhado, com controle otimista pela revisão da partida.

Política padrão de contenção:

- até 1.024 salas retidas;
- até 32.768 resultados de ação para idempotência;
- salas aguardando jogadores expiram após 6 horas;
- salas finalizadas ou fechadas expiram após 24 horas;
- o estado serializado não pode exceder 64 MiB.

O servidor nunca substitui silenciosamente um arquivo ilegível. Corrupção,
arquivo vazio, lock concorrente ou falha de persistência interrompem a operação
para evitar divergência entre o estado confirmado ao cliente e o estado
recuperável depois de um reinício.
