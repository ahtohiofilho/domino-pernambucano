# Ambiente de mini-produção autônoma

`miniproduction` é um ambiente isolado que preserva as fronteiras de produção
necessárias à futura plataforma viva:

- autenticação exclusivamente por token assinado;
- cabeçalho de identidade de desenvolvimento desabilitado;
- bots internos de desenvolvimento desabilitados;
- estado autoritativo persistente obrigatório em caminho absoluto;
- segredo de sessão estável obrigatório;
- porta isolada padrão `18080`;
- resultados, ciclos e rankings derivados somente de partidas autoritativas.

Variáveis mínimas:

```text
DOMINO_SERVER_ENVIRONMENT=miniproduction
DOMINO_SERVER_PORT=18080
DOMINO_SESSION_SIGNING_SECRET=<segredo com pelo menos 32 bytes>
DOMINO_SERVER_STATE_FILE=<caminho absoluto isolado>
DOMINO_SYNTHETIC_PROVISIONING_SECRET=<segredo com pelo menos 32 bytes>
```

Para validar o login Google real, configure também:

```text
DOMINO_GOOGLE_WEB_CLIENT_ID=<client ID OAuth Web>
```

## Política de publicação

O perfil usa limiar fixo de 12 contas elegíveis nos quatro recortes. Essa
redução é uma política explícita do ambiente, não uma injeção de ranking. As
contas precisam concluir partidas ranqueadas reais; fórmula, elegibilidade,
persistência, projeção pública e UI permanecem canônicas.

As 16 contas externas são persistidas com tipo `SYNTHETIC`. Esse tipo não é
`APPLICATION`: as ações continuam chegando pelo mesmo contrato HTTP das contas
humanas e o servidor não joga em nome delas. A política de ranking, divulgação
visual e premiações será definida em incrementos próprios.

## Limites deste incremento

Este contrato não inicia servidor, população sintética, Android ou janela de
60 minutos. O gerador de clientes externos e o supervisor operacional serão
incrementos separados.
