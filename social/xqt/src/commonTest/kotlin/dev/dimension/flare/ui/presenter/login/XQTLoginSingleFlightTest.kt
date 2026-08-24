package dev.dimension.flare.ui.presenter.login

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XQTLoginSingleFlightTest {
    @Test
    fun duplicateRunsProduceOneResult() =
        runTest {
            val singleFlight = XQTLoginSingleFlight()
            var executions = 0

            val results =
                (0 until 10)
                    .map {
                        async {
                            singleFlight.run {
                                executions += 1
                                delay(10)
                                "success"
                            }
                        }
                    }.awaitAll()

            assertEquals(1, executions)
            assertEquals(1, results.count { it == "success" })
            assertEquals(9, results.count { it == null })
        }

    @Test
    fun guardIsReleasedAfterFailure() =
        runTest {
            val singleFlight = XQTLoginSingleFlight()

            assertFailsWith<IllegalStateException> {
                singleFlight.run {
                    error("validation failed")
                }
            }

            assertEquals("retry", singleFlight.run { "retry" })
        }
}
