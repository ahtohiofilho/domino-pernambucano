package com.ahtohiofilho.dominopernambucano.server

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ONLINE_EMAIL_DELIVERY_PROVIDER_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_DELIVERY_PROVIDER"
internal const val ONLINE_EMAIL_SMTP_HOST_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_SMTP_HOST"
internal const val ONLINE_EMAIL_SMTP_PORT_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_SMTP_PORT"
internal const val ONLINE_EMAIL_SMTP_USERNAME_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_SMTP_USERNAME"
internal const val ONLINE_EMAIL_SMTP_PASSWORD_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_SMTP_PASSWORD"
internal const val ONLINE_EMAIL_SMTP_SECURITY_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_SMTP_SECURITY"
internal const val ONLINE_EMAIL_FROM_ADDRESS_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_FROM_ADDRESS"
internal const val ONLINE_EMAIL_FROM_NAME_ENVIRONMENT_VARIABLE =
    "DOMINO_EMAIL_FROM_NAME"

private const val DEFAULT_SMTP_PORT = 587
private const val DEFAULT_FROM_NAME = "Dominó PE"
private const val SMTP_TIMEOUT_MILLIS = 10_000

internal enum class OnlineSmtpConnectionSecurity {
    STARTTLS,
    TLS,
}

internal class OnlineSmtpEmailDeliveryConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val security: OnlineSmtpConnectionSecurity,
    val fromAddress: String,
    val fromName: String,
) {
    init {
        require(
            host.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")),
        ) {
            "O host SMTP configurado é inválido."
        }
        require(port in 1..65_535) {
            "A porta SMTP deve estar entre 1 e 65535."
        }
        require(username.isNotBlank() && username.hasNoHeaderBreak()) {
            "O usuário SMTP é inválido."
        }
        require(password.isNotBlank() && password.hasNoHeaderBreak()) {
            "A senha SMTP é inválida."
        }
        require(
            canonicalizeOnlineEmailAddress(fromAddress) == fromAddress,
        ) {
            "O endereço remetente SMTP é inválido."
        }
        require(fromName.isNotBlank() && fromName.hasNoHeaderBreak()) {
            "O nome remetente SMTP é inválido."
        }
    }

    override fun toString(): String {
        return "OnlineSmtpEmailDeliveryConfig(" +
            "host=$host, port=$port, security=$security, " +
            "fromAddress=$fromAddress, fromName=$fromName, " +
            "credentials=[REDACTED])"
    }
}

internal fun interface OnlineSmtpMessageTransport {
    fun send(
        message: MimeMessage,
        username: String,
        password: String,
    )
}

internal class SmtpOnlineEmailVerificationCodeSender(
    private val config: OnlineSmtpEmailDeliveryConfig,
    private val sessionFactory: (Properties) -> Session = { properties ->
        Session.getInstance(properties)
    },
    private val transport: OnlineSmtpMessageTransport =
        OnlineSmtpMessageTransport { message, username, password ->
            Transport.send(message, username, password)
        },
) : OnlineEmailVerificationCodeSender {
    override suspend fun sendVerificationCode(
        email: String,
        code: String,
    ) {
        val canonicalEmail = canonicalizeOnlineEmailAddress(email)
        require(canonicalEmail == email) {
            "O destinatário deve ser um e-mail canônico válido."
        }
        require(code.length == 6 && code.all(Char::isDigit)) {
            "O código de verificação deve conter seis dígitos."
        }

        val properties = createSmtpProperties(config)
        val session = sessionFactory(properties).also { createdSession ->
            createdSession.debug = false
        }
        val message = MimeMessage(session).apply {
            setFrom(
                InternetAddress(
                    config.fromAddress,
                    config.fromName,
                    StandardCharsets.UTF_8.name(),
                ),
            )
            setRecipient(
                Message.RecipientType.TO,
                InternetAddress(canonicalEmail, true),
            )
            setSubject(
                "Seu código do Dominó PE",
                StandardCharsets.UTF_8.name(),
            )
            setText(
                verificationMessageBody(code),
                StandardCharsets.UTF_8.name(),
            )
            sentDate = Date()
            saveChanges()
        }

        withContext(Dispatchers.IO) {
            transport.send(
                message = message,
                username = config.username,
                password = config.password,
            )
        }
    }
}

internal fun createDefaultOnlineEmailVerificationService(
    serverEnvironment: OnlineServerEnvironment,
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineEmailVerificationService? {
    val config = resolveOnlineSmtpEmailDeliveryConfig(
        serverEnvironment = serverEnvironment,
        readEnvironmentVariable = readEnvironmentVariable,
    ) ?: return null

    return OnlineEmailVerificationService(
        sender = SmtpOnlineEmailVerificationCodeSender(
            config = config,
        ),
    )
}

internal fun resolveOnlineSmtpEmailDeliveryConfig(
    serverEnvironment: OnlineServerEnvironment,
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineSmtpEmailDeliveryConfig? {
    val provider = readEnvironmentVariable(
        ONLINE_EMAIL_DELIVERY_PROVIDER_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
        ?: return null

    if (provider == "disabled") {
        return null
    }
    require(provider == "smtp") {
        "$ONLINE_EMAIL_DELIVERY_PROVIDER_ENVIRONMENT_VARIABLE possui valor inválido."
    }
    require(serverEnvironment.allowsConfiguredEmailIdentityRoutes) {
        "O ambiente atual não permite ativar autenticação por e-mail."
    }

    val host = requiredTrimmedEnvironmentValue(
        ONLINE_EMAIL_SMTP_HOST_ENVIRONMENT_VARIABLE,
        readEnvironmentVariable,
    )
    val rawPort = readEnvironmentVariable(
        ONLINE_EMAIL_SMTP_PORT_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val port = rawPort?.toIntOrNull() ?: when {
        rawPort != null -> throw IllegalArgumentException(
            "$ONLINE_EMAIL_SMTP_PORT_ENVIRONMENT_VARIABLE possui valor inválido.",
        )

        else -> DEFAULT_SMTP_PORT
    }
    val username = requiredTrimmedEnvironmentValue(
        ONLINE_EMAIL_SMTP_USERNAME_ENVIRONMENT_VARIABLE,
        readEnvironmentVariable,
    )
    val password = readEnvironmentVariable(
        ONLINE_EMAIL_SMTP_PASSWORD_ENVIRONMENT_VARIABLE,
    )?.takeIf(String::isNotBlank) ?: throw IllegalStateException(
        "$ONLINE_EMAIL_SMTP_PASSWORD_ENVIRONMENT_VARIABLE deve ser configurada.",
    )
    val security = when (
        readEnvironmentVariable(
            ONLINE_EMAIL_SMTP_SECURITY_ENVIRONMENT_VARIABLE,
        )
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
            ?: "starttls"
    ) {
        "starttls" -> OnlineSmtpConnectionSecurity.STARTTLS
        "tls" -> OnlineSmtpConnectionSecurity.TLS
        else -> throw IllegalArgumentException(
            "$ONLINE_EMAIL_SMTP_SECURITY_ENVIRONMENT_VARIABLE possui valor inválido.",
        )
    }
    val fromAddress = canonicalizeOnlineEmailAddress(
        requiredTrimmedEnvironmentValue(
            ONLINE_EMAIL_FROM_ADDRESS_ENVIRONMENT_VARIABLE,
            readEnvironmentVariable,
        ),
    ) ?: throw IllegalArgumentException(
        "$ONLINE_EMAIL_FROM_ADDRESS_ENVIRONMENT_VARIABLE possui valor inválido.",
    )
    val fromName = readEnvironmentVariable(
        ONLINE_EMAIL_FROM_NAME_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: DEFAULT_FROM_NAME

    return OnlineSmtpEmailDeliveryConfig(
        host = host,
        port = port,
        username = username,
        password = password,
        security = security,
        fromAddress = fromAddress,
        fromName = fromName,
    )
}

private fun createSmtpProperties(
    config: OnlineSmtpEmailDeliveryConfig,
): Properties {
    return Properties().apply {
        setProperty("mail.smtp.host", config.host)
        setProperty("mail.smtp.port", config.port.toString())
        setProperty("mail.smtp.auth", "true")
        setProperty("mail.smtp.ssl.checkserveridentity", "true")
        setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2")
        setProperty(
            "mail.smtp.connectiontimeout",
            SMTP_TIMEOUT_MILLIS.toString(),
        )
        setProperty("mail.smtp.timeout", SMTP_TIMEOUT_MILLIS.toString())
        setProperty("mail.smtp.writetimeout", SMTP_TIMEOUT_MILLIS.toString())

        when (config.security) {
            OnlineSmtpConnectionSecurity.STARTTLS -> {
                setProperty("mail.smtp.starttls.enable", "true")
                setProperty("mail.smtp.starttls.required", "true")
                setProperty("mail.smtp.ssl.enable", "false")
            }

            OnlineSmtpConnectionSecurity.TLS -> {
                setProperty("mail.smtp.starttls.enable", "false")
                setProperty("mail.smtp.starttls.required", "false")
                setProperty("mail.smtp.ssl.enable", "true")
            }
        }
    }
}

private fun verificationMessageBody(
    code: String,
): String {
    return "Seu código de verificação do Dominó PE é: $code\n\n" +
        "Ele expira em 10 minutos. Se você não solicitou este código, " +
        "ignore esta mensagem."
}

private fun requiredTrimmedEnvironmentValue(
    variableName: String,
    readEnvironmentVariable: (String) -> String?,
): String {
    return readEnvironmentVariable(variableName)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException(
            "$variableName deve ser configurada.",
        )
}

private fun String.hasNoHeaderBreak(): Boolean {
    return none { character -> character == '\r' || character == '\n' }
}
