# DP v37 — plano de rollout controlado

Baseline integrado: `e74aa8569f1c41bb2af09bb56c566289c2efefc7`.

## Gate A — verificacao somente leitura
No PowerShell, com a branch local atualizada:

```powershell
& ".\scripts\Invoke-DPReleaseReadiness.ps1"
```

O preflight verifica worktree/commit, versionCode, imagem, estado, restart total,
mounts dos containers produtivos e /health, /ready. Nao faz deploy, nao altera
volumes, nao imprime secrets. Gera relatorio no diretorio Downloads.
Executar `-SkipRemote` so serve como checagem local e nao aprova a AWS.

## Gate B — candidata Android
Confirmar no Google Play Console o **maior versionCode de todos os tracks**.
A candidata 37 so pode ser usada quando **37 > maior versionCode cadastrado**.
Do contrario, elevar o codigo tambem no script de release neste mesmo PR.

Em Windows, com production.json e assinatura locais provisionados:

```powershell
& ".\scripts\Release-DominoPE.ps1" -VersionCode 37
# Depois do preflight PASS e credenciais locais de assinatura:
& ".\scripts\Release-DominoPE.ps1" -VersionCode 37 -Build
```

A rotina verifica OAuth versus backend, IDs AdMob, certificado, APK/AAB e assinatura;
**nao faz upload** ao Google Play. Homologar primeiro pelo track interno;
promocao para producao e gate separado. No Google Play, rollback nao permite
retomar versionCode menor: exige rollout controlado ou nova versao com codigo maior.

## Gate C — sintéticos AWS
O servidor nao teve alteracoes neste ciclo. Evitar redeploy do `domino-server`.
Antes de atualizar apenas `domino-synthetic-population`, confirmar imagem/digest
anteriores, configuracao real e mounts; criar backup restrito do volume persistente
`/state`; construir imagem do SHA aprovado usando Dockerfile.synthetic-population,
tag imutavel e **apenas uma populacao ativa**. Preservar os 60 accountId/playerId,
secrets e configuracao. Nunca imprimir tokens nos logs de evidencias.

Smoke apos deploy (com autorizacao especifica): processo healthy, 60 contas,
standby 6, acoes aceitas em partidas e ausencia de HTTP 401 em loop. RestartCount
é cumulativo: comparar nova atividade e nao tratar valor historico como falha.
Rollback deve utilizar imagem anterior e **o mesmo volume**, apos validar
compatibilidade; nao apagar identidades ou restaurar backup automaticamente.

## Gates obrigatorios antes de producao
1. CI e revisao do PR da candidata verdes.
2. Preflight de leitura na AWS sem falhas.
3. Confirmacao do maior versionCode na Play.
4. AAB homologado em teste interno.
5. Backup, verificacao de imagem e rollback sintético aprovados.
6. Autorizacoes explicitas e separadas para AWS e Google Play.
