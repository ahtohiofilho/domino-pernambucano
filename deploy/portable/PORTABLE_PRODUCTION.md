# Gate portatil do servidor

Este gate empacota o servidor Ktor sem escolher provedor de nuvem.

O mesmo `Dockerfile.server` pode ser usado em uma instancia Lightsail, EC2,
ECS/Fargate ou outro host de containers. A infraestrutura precisa preservar o
seguinte contrato:

- uma unica instancia ativa;
- porta interna `8080`;
- `GET /health` retorna `{"status":"ok"}`;
- volume persistente gravavel montado em `/data`;
- estado em `/data/authoritative-state.json`;
- `DOMINO_SERVER_ENVIRONMENT=production`;
- `DOMINO_SESSION_SIGNING_SECRET` com pelo menos 32 bytes;
- encerramento gracioso por `SIGTERM`;
- logs coletados de stdout/stderr.

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

## Proximo gate

Depois do `PASS` local, a mesma imagem sera implantada inicialmente em uma
instancia AWS de baixo custo com disco separado, HTTPS, snapshot e alarme. Esse
passo sera entregue em pacote separado e exigira confirmacao antes de criar
qualquer recurso potencialmente cobrado.
