# Arquitetura vinculante de identidade multiprovedor

## Estado deste incremento

Este documento é vinculante para o ciclo P1.F.7.F.3.G.2.C.1.

A conta do Dominó PE é a identidade canônica do produto. Identidades
externas são somente meios independentes de localizar, vincular ou recuperar
essa conta.

Provedores reservados a partir do schema 11:

- `GOOGLE`: identidade Google verificada pelo backend;
- `PLAY_GAMES`: identidade de plataforma do Google Play Games verificada pelo
  backend;
- `EMAIL`: identidade de e-mail verificada por fluxo próprio do Dominó PE.

## Regras invariantes

1. Nenhum provedor externo é a própria conta Dominó PE.
2. Uma conta pode possuir identidades de provedores diferentes.
3. Uma conta pode possuir no máximo uma identidade por provedor.
4. Uma identidade externa pode apontar para apenas uma conta canônica.
5. O sistema nunca funde duas contas silenciosamente.
6. Subjects iguais em provedores diferentes pertencem a namespaces distintos.
7. Perfil, histórico, partidas, ranking e configurações pertencem à conta
   Dominó PE, não ao provedor.
8. A perda ou troca de um provedor não pode apagar o progresso da conta.
9. O vínculo adicional exige uma sessão de conta válida.
10. A recuperação por identidade externa somente emite sessão para a conta
    previamente vinculada.

## Contrato específico do Play Games

O aplicativo não deve confiar em um Player ID recebido diretamente do
dispositivo.

O fluxo obrigatório será:

1. o Play Games Services v2 autentica a identidade de plataforma;
2. o Android solicita um código de autorização de servidor de uso único por
   `GamesSignInClient.requestServerSideAccess`;
3. o aplicativo envia somente esse código ao backend;
4. o backend troca o código usando a credencial OAuth Web do servidor;
5. o backend consulta a API do Play Games e obtém o Player ID verificado;
6. somente esse Player ID verificado é persistido como subject do provedor
   `PLAY_GAMES`;
7. o backend vincula ou recupera a conta canônica segundo as regras invariantes.

## Separação entre Play Games e login Google

A identidade Play Games é uma identidade de plataforma gamer. O login Google
atual é um método de autenticação da conta no jogo. Eles podem coexistir na
mesma conta Dominó PE e não devem ser presumidos equivalentes.

## Próximos incrementos obrigatórios

### C.2 — Verificação servidor Play Games

- DTO de código de autorização;
- rota de vínculo e recuperação;
- troca segura do código no backend;
- consulta da API Play Games;
- verificador injetável e testes sem rede;
- segredos somente por ambiente/secret store.

### C.3 — Integração Android Play Games Services v2

- dependência versionada;
- project ID configurável;
- autenticação de plataforma;
- `requestServerSideAccess` com OAuth Web client ID;
- envio do código ao backend;
- UX de vínculo, recuperação e conflitos.

### C.4 — Login não Google por e-mail

- fluxo sem senha tradicional, preferencialmente código de uso único;
- expiração, limitação de tentativas e prevenção de enumeração;
- recuperação e vínculo com a mesma conta canônica.

## Configuração externa necessária antes de C.3

- jogo configurado no Google Play Console;
- credencial Android associada ao package e certificado corretos;
- credencial OAuth Web para o servidor;
- project ID do Play Games;
- ambiente de teste e testadores autorizados.

## Fontes oficiais

- https://developer.android.com/games/pgs/overview
- https://developer.android.com/games/pgs/android/android-signin
- https://developer.android.com/games/pgs/android/server-access
