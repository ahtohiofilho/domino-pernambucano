# DP v37 — população sintética: preflight, cutover e rollback

**Estado inicial homologado — 14/11/2026:** backup preliminar de 60 identidades confirmado, imagem sintética nova **já staged** na AWS, imagem anterior preservada e CI da candidata aprovado. O servidor `domino-server` **não será atualizado**. Play Store e cliente Android têm autorizações próprias.

## Fontes de verdade imutáveis

- Container em execução: `domino-synthetic-population` (Docker direto, não Compose).
- Imagem anterior: `sha256:d25988e867448a5eda0878bead1acae7794ac3ca82bc6e9691d56026c0dc1827`.
- Imagem candidata staged: `dp-synthetic:sha-acb9c6783ef7`, ID `sha256:17b6ade718176f87aa94979a100907eb423f9c6320c2c13d41b934a698cc5e28`.
- Volume de identidades: `synthetic-participant-state` em `/state`, persistência RW.
- Segredo: bind montado read-only em `/run/secrets/synthetic_provisioning_secret`.
- Backup preliminar root-only: `/var/backups/domino-pe/synthetic/identities-20261113T224716Z-JXrSKd.json`, SHA-256 `07ddaff06ccf595d9aaebf6faa8c364fc37fe315efbe54b9ae3bf3489948b9bf`.
- Contrato: usuário `10002:10002`, Docker `bridge`, sem portas, `read-only`, `init`, `cap-drop ALL`, `no-new-privileges`, `tmpfs /tmp`, log `local`, restart `unless-stopped`, `SIGTERM`, 17 variáveis de ambiente efetivas que **nunca devem ser impressas**.

## Gate F — preflight sem alterações do Docker

O script `scripts/Invoke-DPSyntheticCutover.ps1` sem parâmetro de execução transporta apenas o código de operação para armazenamento protegido (`/var/backups/domino-pe/ops`) e executa **preflight somente de leitura**.

```powershell
& .\scripts\Invoke-DPSyntheticCutover.ps1
```

Checks: imagem/digest antiga e nova, container `running + healthy`, rede, controles de segurança, mounts, env **apenas em memória**, JSON de 60 identidades únicas e mesmos `accountId`/`playerId` do backup preliminar, disponibilidade da API `/health` e `/ready`, inexistência de uma transição antiga.

Com sucesso: `CUTOVER_PREFLIGHT=PASS`, `PRODUCTION_CHANGES=NONE` e `DOCKER_CHANGES=NONE`. Sem `-Execute`, o processo **não invoca `docker stop`, `docker create`, `docker rename`, `docker start` nem mexe no volume**. A cópia do script para o diretório de operações da AWS é a única gravação prevista.

## Gate G — transição após autorização específica

**BLOQUEADO até autorização explícita separada para o cutover dos sintéticos.** Não executar implicitamente por uma mensagem genérica de continuidade.

O controlador exige `-Execute` e a frase exata `AUTORIZO-CUTOVER-SINTETICOS-V37`. Nenhum comando de execução deve ser enviado antes da autorização.

Fluxo preparado no Python `deploy/release/dp_synthetic_cutover.py`:

1. Reexecuta preflight e valida imagem, segredo, volume, API e backup preliminar.
2. Cria **sem iniciar** o novo container sob nome temporário; comprova que suas 17 variáveis de ambiente e opções de segurança reproduzem o container anterior. Há **uma única população em execução**.
3. Dá `docker stop --time 60` no container antigo (SIGTERM); exige encerramento verificado.
4. Copia o estado já parado para um backup consistente root `0600`; valida 60 identidades e SHA-256, sem imprimir tokens.
5. Renomeia o container antigo (sem apagar) para `domino-synthetic-population-rollback-pre-v37`.
6. Renomeia o container candidato já criado para `domino-synthetic-population` e o inicia.
7. Aguarda `healthy`, verifica log `POPULATION_STARTED accounts=60`, standby 6, API `/health` e `/ready`; **não exibe logs brutos**.
8. Em falha, para a nova população **antes** de reativar a antiga, estaciona o novo container como `domino-synthetic-population-failed-v37`, restaura o nome e inicia o anterior. Confirma `healthy` da antiga, se possível.

O procedimento não restaura automaticamente o volume: um backup consistente existe, mas uma restauração precisa de diagnóstico e autorização próprios para evitar apagar renovações legítimas de token. Se o rollback automatizado não for bem-sucedido, o processo sinaliza `ROLLBACK_RESULT=FAILED_MANUAL_INTERVENTION_REQUIRED`; não executa intervenções destrutivas.

**Risco residual:** stop/start dos sintéticos pode interromper partidas correntes; sinais elétricos/SIGKILL/queda da instância não permitem garantia de rollback automático. Manter janela curta de baixa atividade e observação manual do terminal durante a execução.

## Gate H — operação assistida e pós-deploy

Monitorar reinicializações **novas**, autenticação (`401/403`, recuperações por identidade, sem tokens), partidas aceitas, standby 6 e 60 contas. Confirmar que o servidor continua na mesma imagem e que as identidades preservam os mesmos `accountId`/`playerId`.

O cutover AWS, merge do PR e upload Android/Google Play são **três decisões distintas**. Não publicar AAB sem confirmar que `versionCode 37` é maior do que todos os códigos registrados em todas as trilhas do Play Console.
