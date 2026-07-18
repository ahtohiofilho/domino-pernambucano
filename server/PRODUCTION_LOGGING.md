# Logging do servidor em produção

O módulo `:server` inclui um único provider SLF4J no runtime:

```text
org.slf4j:slf4j-simple:2.0.18
```

A versão corresponde ao `slf4j-api` observado na distribuição atual. O
provider escreve os eventos de Ktor e Netty no console do processo, permitindo
que o ambiente de execução faça coleta, retenção e alerta sem gravar logs no
mesmo volume do estado autoritativo.

O servidor não instala logging de corpo, cabeçalhos HTTP ou tokens. Não eleve
Ktor ou Netty para `DEBUG`/`TRACE` em produção sem antes adicionar redação
explícita de credenciais.

## Gate

`ProductionLoggingBackendTest` falha se o runtime voltar a usar
`NOPLoggerFactory` ou se o nível `INFO` estiver desabilitado. O instalador
também confirma que `slf4j-simple-2.0.18.jar` entrou em `installDist`.

Depois da instalação, execute novamente `production-restart-proof.ps1`. Os
dois startups precisam continuar recuperando a partida e os logs não podem
mais conter `No SLF4J providers were found`.
