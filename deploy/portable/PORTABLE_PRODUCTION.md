# Gate portatil do servidor

Este gate empacota o servidor Ktor sem escolher provedor de nuvem.

O mesmo `Dockerfile.server` pode ser usado em uma instancia Lightsail, EC2,
ECS/Fargate ou outro host de containers. A infraestrutura precisa preservar o
seguinte contrato:

- uma unica instancia ativa;
- porta interna `8080`;
- `GET /health` confirma liveness com `{"status":"ok"}`;
- `GET /ready` retorna HTTP 200 somente enquanto ticker e persistência
  autoritativos estiverem operacionais;
- volume persistente gravavel montado em `/data`;
- estado em `/data/authoritative-state.json`;
- `DOMINO_SERVER_ENVIRONMENT=production`;
- `DOMINO_SESSION_SIGNING_SECRET` com pelo menos 32 bytes;
- encerramento gracioso por `SIGTERM`;
- logs coletados de stdout/stderr.

O servidor também aplica limites de frequência em memória. A emissão de sessão
anônima usa um orçamento global de 60 requisições por minuto. Por identidade
validada, mutações permitem 120, leituras 240 e lotes de traces 30 requisições
por minuto. Rejeições retornam HTTP 429 com `Retry-After`. Essa defesa da
aplicação não substitui o rate limiting do proxy de borda.

Não habilite confiança em `Forwarded` ou `X-Forwarded-*` apenas para obter o IP
do cliente. Esses cabeçalhos só podem participar de decisões de segurança se o
container aceitar conexões exclusivamente de um proxy confiável configurado.

O processo roda sem privilegios, com UID e GID `10001`. Em um host Linux, o
diretorio ou volume montado em `/data` deve permitir escrita para esse usuario.

## Aplicacao do pacote

O instalador do pacote adiciona os arquivos do gate e executa:

```text
gradlew.bat :server:test :server:installDist --no-daemon
```

Ele nao executa Docker e nao cria recursos externos.

## Prova local em container

Pre-requisitos: Docker Desktop em modo de containers Linux, ou Docker Engine,
e acesso de rede para baixar as imagens-base e dependencias no primeiro build.

Na raiz do projeto:

```powershell
& ".\deploy\portable\production-container-restart-proof.ps1" `
    -RepoRoot "C:\Users\ahtoh\AndroidStudioProjects\DominoPernambucano"
```

A prova usa uma porta local livre, sem depender da porta `8080` do host. Ela:

1. constroi a imagem;
2. inicia o primeiro container com um diretorio persistente montado;
3. cria quatro sessoes e inicia uma partida;
4. encerra e remove somente esse container;
5. inicia um segundo container com o mesmo estado e segredo;
6. confirma sala, partida, jogadores e revisao;
7. verifica os logs e gera uma evidencia ZIP;
8. remove o segundo container.

A imagem construida e a evidencia permanecem. O segredo temporario e os tokens
de sessao nao sao gravados na evidencia.

## Limite de escala

Nao execute mais de um container contra o mesmo arquivo. O lock exclusivo deve
continuar falhando fechado. Escala horizontal so pode ser habilitada depois de
substituir `PersistentOnlineServerStore` por um store transacional compartilhado.

## Baseline atual de producao

O ciclo `R8.B.8.C39.D` foi validado em producao em 20/09/2026. O baseline
operacional auditado e:

- imagem `domino-server:approved-c39d-769e760b`;
- image ID `sha256:e6c03cdd7dcbd99091cf15b4111ba5de553d4e382b6c9701b1d5563ea1dd4ba6`;
- revisao `769e760b2b1b695f2c917482249bedbd51a63627`;
- estado autoritativo em schema 13;
- `GET /health` e `GET /ready` validados publicamente;
- ranking corrente usando regra V2;
- historico V1 preservado e consultavel por ciclo versionado.

O deploy preservou o container anterior parado com `restart=no` e um backup
pre-deploy do estado em schema 12. Como a imagem anterior aceita no maximo
schema 12, um rollback para ela exige restaurar primeiro esse backup; nunca
religue a imagem anterior diretamente contra um estado schema 13.

O snapshot de rollback criado no cutover teve SHA-256
`aa78f00a733517aab3c8edd263e71651be6d7c52d33a914aee8833ec99992244`.
A remocao futura desse backup e do container de rollback deve ser tratada como
um gate operacional separado, depois de janela de observacao e autorizacao
explicita.
