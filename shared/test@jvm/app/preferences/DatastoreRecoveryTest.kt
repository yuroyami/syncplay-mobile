package app.preferences

import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The preference store is read once, blocking, behind the splash screen. If an unreadable file
 * threw there, every launch would fail, with nothing on screen to say why and no way out but a
 * reinstall.
 */
class DatastoreRecoveryTest {

    @AfterTest
    fun restore() = resetPreferencesForTesting()

    @Test
    fun `garbage on disk becomes empty preferences, not a crash`() {
        val file = createTempFile(suffix = ".preferences_pb").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))

        resetPreferencesForTesting()
        datastore = createDataStore { file.absolutePath }

        assertEquals(0, datastoreStateFlow.value.asMap().size)
        assertNotNull(preferencesLoadFailure, "the reset has to be reportable, or nobody is told")
    }
}
