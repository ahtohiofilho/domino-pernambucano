package com.ahtohiofilho.dominopernambucano.server

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val PUBLIC_SUPPORT_EMAIL =
    "dominopernambucano@gmail.com"

internal fun Route.legalPolicyRoutes() {
    get("/privacy") {
        call.respondText(
            text = privacyPolicyHtml(),
            contentType = ContentType.Text.Html,
        )
    }

    get("/account-deletion") {
        call.respondText(
            text = accountDeletionHtml(),
            contentType = ContentType.Text.Html,
        )
    }
}

private fun privacyPolicyHtml(): String {
    return """
        <!doctype html>
        <html lang="pt-BR">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Política de Privacidade — Dominó PE</title>
        </head>
        <body>
            <main>
                <h1>Política de Privacidade — Dominó PE</h1>
                <p><strong>Última atualização:</strong> 24 de agosto de 2026.</p>
                <p>
                    Esta Política descreve como o aplicativo Dominó PE trata
                    dados para oferecer partidas de dominó, contas, ranking,
                    segurança operacional e publicidade.
                </p>

                <h2>Dados tratados</h2>
                <ul>
                    <li>
                        Dados de conta e autenticação: identificadores técnicos
                        de conta e sessão e, quando escolhido pelo usuário,
                        endereço de e-mail para confirmação de acesso ou
                        identificador técnico da conta Google.
                    </li>
                    <li>
                        Perfil público: nome público e nome curto usado nas
                        partidas e no ranking.
                    </li>
                    <li>
                        Dados de jogo e competição: participação em partidas,
                        resultados, vitórias, métricas competitivas, ciclos e
                        posições de ranking.
                    </li>
                    <li>
                        Dados técnicos de operação e segurança: eventos
                        necessários para diagnosticar falhas, proteger sessões,
                        aplicar limites de uso e manter a integridade das
                        partidas online.
                    </li>
                    <li>
                        Publicidade e consentimento: o app usa Google Mobile Ads
                        e Google User Messaging Platform. O Google pode tratar
                        identificadores de dispositivo, sinais de publicidade e
                        escolhas de consentimento conforme a configuração,
                        a região e as escolhas do usuário.
                    </li>
                </ul>

                <h2>Finalidades</h2>
                <p>
                    Os dados são usados para autenticar e recuperar contas,
                    operar partidas online, manter perfil e ranking, prevenir
                    abuso, diagnosticar problemas, cumprir escolhas de
                    privacidade e exibir publicidade quando permitida.
                </p>

                <h2>Compartilhamento e prestadores</h2>
                <p>
                    O Dominó PE utiliza serviços de terceiros estritamente para
                    viabilizar suas funções, incluindo serviços Google para
                    autenticação, publicidade e consentimento, infraestrutura
                    de hospedagem e rede e serviço de entrega de e-mails para
                    códigos de acesso. Esses prestadores podem tratar dados
                    conforme seus próprios termos e políticas aplicáveis.
                </p>

                <h2>Retenção</h2>
                <p>
                    Dados de conta e perfil são mantidos enquanto a conta
                    permanecer ativa ou enquanto forem necessários para as
                    finalidades descritas. Registros operacionais temporários
                    são limitados por políticas técnicas de retenção. Dados
                    associados a uma conta são excluídos quando uma solicitação
                    válida de exclusão é concluída, salvo quando uma retenção
                    específica for necessária para obrigação legal, prevenção
                    de fraude ou defesa de direitos.
                </p>

                <h2>Exclusão da conta e dos dados</h2>
                <p>
                    Para solicitar a exclusão da conta Dominó PE e dos dados
                    associados, acesse
                    <a href="/account-deletion">Exclusão de conta</a>.
                    A identidade do solicitante poderá ser verificada para
                    impedir a exclusão não autorizada de contas de terceiros.
                </p>

                <h2>Segurança</h2>
                <p>
                    São aplicadas medidas técnicas e organizacionais destinadas
                    a proteger credenciais, sessões e dados persistidos contra
                    acesso, alteração ou divulgação não autorizados.
                </p>

                <h2>Direitos e contato</h2>
                <p>
                    Para dúvidas de privacidade, correção de dados, exercício de
                    direitos ou solicitações relacionadas ao tratamento de
                    dados, entre em contato pelo e-mail
                    <a href="mailto:$PUBLIC_SUPPORT_EMAIL">$PUBLIC_SUPPORT_EMAIL</a>.
                </p>
            </main>
        </body>
        </html>
    """.trimIndent()
}

private fun accountDeletionHtml(): String {
    return """
        <!doctype html>
        <html lang="pt-BR">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Exclusão de conta e dados — Dominó PE</title>
        </head>
        <body>
            <main>
                <h1>Exclusão de conta e dados — Dominó PE</h1>
                <p>
                    Esta página é o recurso público para solicitar a exclusão
                    da sua conta Dominó PE e dos dados associados.
                </p>

                <h2>Como solicitar</h2>
                <ol>
                    <li>
                        Se o app estiver instalado, abra
                        <strong>Configurações → Excluir conta e dados</strong>.
                    </li>
                    <li>
                        Envie a solicitação pelo e-mail de suporte abaixo.
                    </li>
                    <li>
                        Para proteger sua conta, poderemos solicitar confirmação
                        de identidade antes de executar a exclusão.
                    </li>
                </ol>

                <p>
                    <a href="mailto:$PUBLIC_SUPPORT_EMAIL?subject=Exclus%C3%A3o%20de%20conta%20Domin%C3%B3%20PE">
                        Solicitar exclusão por e-mail
                    </a>
                </p>
                <p>
                    Contato:
                    <a href="mailto:$PUBLIC_SUPPORT_EMAIL">$PUBLIC_SUPPORT_EMAIL</a>
                </p>

                <h2>O que a solicitação abrange</h2>
                <p>
                    Após a validação e conclusão da solicitação, a conta,
                    vínculos de autenticação, perfil público e dados
                    identificáveis associados à participação online serão
                    eliminados ou desvinculados conforme a política de
                    exclusão do serviço. Eventual retenção legal estritamente
                    necessária será informada quando aplicável.
                </p>

                <p>
                    Consulte também a
                    <a href="/privacy">Política de Privacidade do Dominó PE</a>.
                </p>
            </main>
        </body>
        </html>
    """.trimIndent()
}