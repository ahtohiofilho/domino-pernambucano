# Supervisor da mini-produção autônoma

O supervisor mantém servidor e população sintética ativos por 60 minutos,
independentemente de o aplicativo ser aberto. O ciclo também pode ser encerrado
explicitamente pelo script de parada.

## Iniciar

Na raiz do repositório:

```powershell
& .\miniproduction-runtime\Start-AutonomousMiniproduction.ps1
```

O processo:

1. valida repositório, porta isolada e exclusividade da execução;
2. constrói distribuições JVM do servidor e da população;
3. cria estado autoritativo e identidades em diretório isolado;
4. inicia o servidor no ambiente `miniproduction`;
5. aguarda `/ready` e inicia 15 clientes externos;
6. monitora ambos até 60 minutos ou solicitação de parada;
7. encerra toda a árvore de processos e gera evidência operacional.

O segredo de sessão é gerado em memória para a execução e não entra em logs ou
evidências. O estado canônico completo permanece em `%LOCALAPPDATA%\DominoPE\`
`miniproduction\runs`; ele contém credenciais e não deve ser compartilhado.

Para habilitar o login Google real durante o ciclo, configure antes:

```powershell
$env:DOMINO_GOOGLE_WEB_CLIENT_ID = "<OAuth Web client ID>"
```

## Encerrar explicitamente

```powershell
& .\miniproduction-runtime\Stop-AutonomousMiniproduction.ps1
```

O comando apenas solicita a parada ao supervisor proprietário. O próprio
supervisor encerra a árvore de processos e fecha as evidências.

## Consultar

```powershell
& .\miniproduction-runtime\Get-AutonomousMiniproductionStatus.ps1
```

As evidências operacionais são sempre gravadas em:

```text
[Documentos]\DominoPernambucano-evidence
```

Este incremento não altera a URL do Android nem inicia AVD. Essa integração
pertence ao próximo gate de ensaio humano.
