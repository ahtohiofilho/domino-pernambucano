package com.ahtohiofilho.dominopernambucano.miniproduction

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

fun main() {
    val running = AtomicBoolean(true)
    Runtime.getRuntime().addShutdownHook(
        Thread(
            {
                running.set(false)
            },
            "miniproduction-population-shutdown",
        ),
    )

    try {
        val config = MiniProductionClientConfig.fromEnvironment()
        val profiles = syntheticRoster.take(config.populationSize)
        val gateway = JavaHttpMiniProductionGateway(config)
        val store = SyntheticIdentityStore(
            stateDirectory = config.stateDirectory,
            baseUrl = config.normalizedBaseUrl,
        )

        store.acquireExclusiveRunLock().use {
            val readinessProbe = AutonomousSyntheticPopulation(
                config = config,
                gateway = gateway,
                profiles = emptyList(),
                credentials = emptyList(),
                running = running,
            )
            readinessProbe.awaitServerReadiness()

            val credentials = SyntheticIdentityProvisioner(
                gateway = gateway,
                store = store,
            ).provision(profiles)

            AutonomousSyntheticPopulation(
                config = config,
                gateway = gateway,
                profiles = profiles,
                credentials = credentials,
                running = running,
            ).run()
        }
    } catch (failure: Throwable) {
        System.err.println(
            "POPULATION_FAILED type=${failure::class.simpleName} " +
                "message=${failure.message.orEmpty().replace('\n', ' ')}",
        )
        exitProcess(1)
    }
}
