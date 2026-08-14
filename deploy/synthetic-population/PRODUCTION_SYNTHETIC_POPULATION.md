# População sintética externa de produção

Este incremento prepara, mas não ativa, os 16 participantes sintéticos no
ambiente remoto. A ativação continua sendo uma operação de produção separada,
com imagem versionada, domínio confirmado, segredo provisionado e autorização
explícita.

## Contrato operacional

- o servidor continua autoritativo para fila, partida, regras e ranking;
- cada participante usa os mesmos endpoints HTTP públicos de uma conta comum;
- a única exceção é a promoção e a recuperação de sessão, protegidas pelo
  segredo sintético compartilhado;
- o processo não contém Android, não renderiza anúncios e não emite eventos de
  publicidade;
- as 16 identidades e seus dados históricos sobrevivem a reinícios e upgrades;
- a população roda em imagem separada, sem porta publicada;
- `SIGTERM` produz encerramento coordenado;
- ausência prolongada de interação bem-sucedida encerra o processo com erro,
  permitindo que o orquestrador o reinicie;
- o heartbeat contém somente um timestamp; nunca contém tokens ou segredos.

O segredo configurado no servidor em
`DOMINO_SYNTHETIC_PROVISIONING_SECRET` deve ser idêntico ao segredo montado no
cliente. O exemplo Compose usa Docker secret e entrega apenas o caminho do
arquivo ao processo.

## Construção

Na raiz do repositório:

```text
docker build \
  -f Dockerfile.synthetic-population \
  -t registry.example.invalid/domino-pe/synthetic-population:<commit> \
  .
```

Use `Dockerfile.synthetic-population.dockerignore` como `.dockerignore` no
contexto efetivo da pipeline, ou replique exatamente a lista de inclusão. A
imagem deve ser publicada por digest imutável antes da ativação.

## Estado e reinício

O volume montado em `/state` é parte do contrato de identidade. Não o remova em
deploys ou rollbacks. O arquivo `synthetic-identities.json` contém tokens de
sessão e deve permanecer restrito ao UID `10002`. Ao expirar um token, o cliente
recupera uma sessão nova para o mesmo `accountId` e o mesmo `playerId`; ele não
cria outra identidade.

O `restart: unless-stopped` recupera falhas do processo. Parada administrativa
explícita permanece parada. O healthcheck é uma segunda sinalização; a própria
aplicação termina quando o servidor fica silencioso além de
`DOMINO_SYNTHETIC_MAX_SILENCE_MILLIS`.

## Gate de ativação futuro

Antes de ativar em produção, confirmar em um ciclo próprio:

1. domínio HTTPS real e certificado válido;
2. imagem e digest aprovados;
3. backup e permissões do volume persistente;
4. segredo forte configurado nas duas cargas;
5. telemetria que distingue `SYNTHETIC` de `HUMAN` sem alterar o ranking;
6. parada, reinício e recuperação das mesmas 16 contas;
7. ausência de portas, SDKs e eventos de anúncios na imagem sintética.

Este incremento não decide nem altera política de ranking, disclosure,
premiações visuais ou retirada gradual da população sintética.
