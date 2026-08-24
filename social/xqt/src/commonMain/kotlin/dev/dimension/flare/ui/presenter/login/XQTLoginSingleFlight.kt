package dev.dimension.flare.ui.presenter.login

import kotlinx.coroutines.sync.Mutex

internal class XQTLoginSingleFlight {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
