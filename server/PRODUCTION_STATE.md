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

Para habilitar as rotas de vinculação e recuperação Google, configure também
ao menos um client ID OAuth do tipo Web.

Configuração estável de um único client:

```text
DOMINO_GOOGLE_WEB_CLIENT_ID=<client ID OAuth do tipo Web>
```

Durante uma migração controlada de client OAuth, o servidor pode aceitar uma
lista adicional, separada por vírgulas:

```text
DOMINO_GOOGLE_WEB_CLIENT_ID=<client ID atual>
DOMINO_GOOGLE_WEB_CLIENT_IDS=<novo client ID>[,<outro client ID>...]
```

As duas variáveis são combinadas, valores vazios são ignorados e IDs repetidos
são eliminados. Isso permite manter o client anterior aceito enquanto Android e
Google Play migram para o novo audience. Depois que todos os clientes ativos
estiverem usando o novo client ID, remova o client antigo da configuração e
volte preferencialmente à variável singular.

Sem nenhuma dessas variáveis, o servidor continua compatível com os fluxos
existentes, mas as duas rotas Google falham fechadas com HTTP 503. O ID token é
validado quanto a assinatura, audiência, emissor e expiração. Somente o `sub`
estável é persistido; e-mail e demais dados do perfil não integram a chave da
conta.

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

## Liveness e readiness

`GET /health` confirma somente que o processo HTTP está respondendo. O endpoint
`GET /ready` retorna HTTP 200 enquanto o ticker autoritativo está ativo e o
store consegue persistir. Durante o encerramento, depois de uma falha do ticker
ou enquanto a persistência estiver indisponível, `/ready` retorna HTTP 503 sem
expor caminhos ou mensagens internas.

Uma escrita persistente bem-sucedida permite que o sinal do store se recupere
de uma falha transitória. Uma falha do ticker exige reinício do processo, pois
o avanço autoritativo não deve ser anunciado como saudável depois que sua
coroutine terminou.
