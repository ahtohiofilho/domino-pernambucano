package com.ahtohiofilho.dominopernambucano.server

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSmtpEmailVerificationCodeSenderTest {
    @Test
    fun missing_or_disabled_provider_keeps_delivery_off() {
        assertNull(
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = { null },
            ),
        )
        assertNull(
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = { variableName ->
                    if (
                        variableName ==
                        ONLINE_EMAIL_DELIVERY_PROVIDER_ENVIRONMENT_VARIABLE
                    ) {
                        "disabled"
                    } else {
                        null
                    }
                },
            ),
        )
    }

    @Test
    fun smtp_configuration_is_strict_and_secret_is_redacted() {
        val values = validEnvironment()
        val config = requireNotNull(
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = values::get,
            ),
        )

        assertEquals("smtp.example.com", config.host)
        assertEquals(587, config.port)
        assertEquals(
            OnlineSmtpConnectionSecurity.STARTTLS,
            config.security,
        )
        assertEquals("contato@dominope.com.br", config.fromAddress)
        assertFalse(config.toString().contains("super-secret"))
        assertTrue(config.toString().contains("[REDACTED]"))

        values.remove(ONLINE_EMAIL_SMTP_PASSWORD_ENVIRONMENT_VARIABLE)
        assertThrows(IllegalStateException::class.java) {
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.PRODUCTION,
                readEnvironmentVariable = values::get,
            )
        }
    }

    @Test
    fun configured_smtp_is_rejected_in_development() {
        val values = validEnvironment()

        assertThrows(IllegalArgumentException::class.java) {
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.DEVELOPMENT,
                readEnvironmentVariable = values::get,
            )
        }
    }

    @Test
    fun sender_composes_six_digit_message_and_requires_tls() = runBlocking {
        val config = requireNotNull(
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.HOMOLOGATION,
                readEnvironmentVariable = validEnvironment()::get,
            ),
        )
        var capturedProperties: Properties? = null
        var capturedMessage: MimeMessage? = null
        var capturedUsername: String? = null
        var capturedPassword: String? = null
        val sender = SmtpOnlineEmailVerificationCodeSender(
            config = config,
            sessionFactory = { properties ->
                capturedProperties = Properties().apply {
                    putAll(properties)
                }
                Session.getInstance(properties)
            },
            transport = OnlineSmtpMessageTransport {
                    message,
                    username,
                    password,
                ->
                capturedMessage = message
                capturedUsername = username
                capturedPassword = password
            },
        )

        sender.sendVerificationCode(
            email = "player@example.com",
            code = "012345",
        )

        val properties = requireNotNull(capturedProperties)
        assertEquals("true", properties.getProperty("mail.smtp.auth"))
        assertEquals(
            "true",
            properties.getProperty("mail.smtp.starttls.enable"),
        )
        assertEquals(
            "true",
            properties.getProperty("mail.smtp.starttls.required"),
        )
        assertEquals(
            "true",
            properties.getProperty("mail.smtp.ssl.checkserveridentity"),
        )
        val message = requireNotNull(capturedMessage)
        assertEquals("Seu código do Dominó PE", message.subject)
        assertTrue(message.content.toString().contains("012345"))
        assertEquals("player@example.com", message.allRecipients.single().toString())
        assertEquals("smtp-user", capturedUsername)
        assertEquals("super-secret", capturedPassword)
    }

    @Test
    fun sender_rejects_noncanonical_recipient_and_malformed_code() {
        val config = requireNotNull(
            resolveOnlineSmtpEmailDeliveryConfig(
                serverEnvironment = OnlineServerEnvironment.TEST,
                readEnvironmentVariable = validEnvironment()::get,
            ),
        )
        val sender = SmtpOnlineEmailVerificationCodeSender(
            config = config,
            transport = OnlineSmtpMessageTransport { _, _, _ ->
                throw AssertionError("Transport should not run")
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                sender.sendVerificationCode(
                    email = " Player@Example.com ",
                    code = "123456",
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                sender.sendVerificationCode(
                    email = "player@example.com",
                    code = "12345x",
                )
            }
        }
    }

    private fun validEnvironment(): MutableMap<String, String> {
        return mutableMapOf(
            ONLINE_EMAIL_DELIVERY_PROVIDER_ENVIRONMENT_VARIABLE to "smtp",
            ONLINE_EMAIL_SMTP_HOST_ENVIRONMENT_VARIABLE to
                "smtp.example.com",
            ONLINE_EMAIL_SMTP_PORT_ENVIRONMENT_VARIABLE to "587",
            ONLINE_EMAIL_SMTP_USERNAME_ENVIRONMENT_VARIABLE to "smtp-user",
            ONLINE_EMAIL_SMTP_PASSWORD_ENVIRONMENT_VARIABLE to "super-secret",
            ONLINE_EMAIL_SMTP_SECURITY_ENVIRONMENT_VARIABLE to "starttls",
            ONLINE_EMAIL_FROM_ADDRESS_ENVIRONMENT_VARIABLE to
                "contato@dominope.com.br",
            ONLINE_EMAIL_FROM_NAME_ENVIRONMENT_VARIABLE to "Dominó PE",
        )
    }
}
