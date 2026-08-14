package com.ahtohiofilho.dominopernambucano.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal const val SYNTHETIC_PROVISIONING_SECRET_ENVIRONMENT_VARIABLE =
    "DOMINO_SYNTHETIC_PROVISIONING_SECRET"

internal class SyntheticProvisioningPolicy private constructor(
    private val expectedSecret: ByteArray?,
) {
    fun authorizes(
        suppliedSecret: String,
    ): Boolean {
        val expected = expectedSecret ?: return false
        val supplied = suppliedSecret.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expected, supplied)
    }

    companion object {
        val Disabled = SyntheticProvisioningPolicy(
            expectedSecret = null,
        )

        fun fromEnvironment(
            readEnvironmentVariable: (String) -> String? = System::getenv,
        ): SyntheticProvisioningPolicy {
            val secret = readEnvironmentVariable(
                SYNTHETIC_PROVISIONING_SECRET_ENVIRONMENT_VARIABLE,
            )
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?: return Disabled

            require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
                "$SYNTHETIC_PROVISIONING_SECRET_ENVIRONMENT_VARIABLE " +
                    "deve possuir pelo menos 32 bytes."
            }

            return SyntheticProvisioningPolicy(
                expectedSecret =
                    secret.toByteArray(StandardCharsets.UTF_8),
            )
        }

        fun fixedForTest(
            secret: String,
        ): SyntheticProvisioningPolicy {
            require(secret.isNotBlank())
            return SyntheticProvisioningPolicy(
                expectedSecret =
                    secret.toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}
