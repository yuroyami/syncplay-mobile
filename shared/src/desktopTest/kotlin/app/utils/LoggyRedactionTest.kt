package app.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every line that [loggy] prints has passed the redactor first. */
class LoggyRedactionTest {

    private val seen = mutableListOf<String>()

    private val capture = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            seen += message
        }
    }

    @BeforeTest
    fun start() {
        LogRedactor.clearForTesting()
        Logger.setLogWriters(capture)
    }

    @AfterTest
    fun reset() {
        Logger.setLogWriters(platformLogWriter())
        LogRedactor.clearForTesting()
    }

    @Test
    fun aRegisteredNameAndAnAddressNeverReachTheLog() {
        LogRedactor.register(LogRedactor.Kind.User, "Christopher_Lee")
        loggy("Christopher_Lee joined from 10.0.0.7")
        assertEquals(listOf("<user-1> joined from <ip-1>"), seen)
    }
}
