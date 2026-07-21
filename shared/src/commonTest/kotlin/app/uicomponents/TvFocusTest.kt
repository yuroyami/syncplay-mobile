package app.uicomponents

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvFocusTest {

    @Test
    fun mapsDpadKeysToSpatialFocusDirections() {
        assertEquals(FocusDirection.Up, tvFocusDirection(Key.DirectionUp))
        assertEquals(FocusDirection.Down, tvFocusDirection(Key.DirectionDown))
        assertEquals(FocusDirection.Left, tvFocusDirection(Key.DirectionLeft))
        assertEquals(FocusDirection.Right, tvFocusDirection(Key.DirectionRight))
    }

    @Test
    fun ignoresNonDirectionalKeys() {
        assertNull(tvFocusDirection(Key.Enter))
        assertNull(tvFocusDirection(Key.Back))
    }

    @Test
    fun installsTextFieldNavigationOnlyOnEnabledTvControls() {
        assertTrue(shouldInterceptTvTextFieldNavigation(true, true))
        assertFalse(shouldInterceptTvTextFieldNavigation(true, false))
        assertFalse(shouldInterceptTvTextFieldNavigation(false, true))
    }

    @Test
    fun routesNavigationAfterTheImeIsHiddenOrDismissalStarts() {
        assertTrue(shouldRouteTvTextFieldNavigation(imeVisible = false, imeDismissalPending = false))
        assertTrue(shouldRouteTvTextFieldNavigation(imeVisible = true, imeDismissalPending = true))
        assertFalse(shouldRouteTvTextFieldNavigation(imeVisible = true, imeDismissalPending = false))
    }

    @Test
    fun recognizesTvControlActivationKeys() {
        assertTrue(isTvActivationKey(Key.DirectionCenter))
        assertTrue(isTvActivationKey(Key.Enter))
        assertTrue(isTvActivationKey(Key.NumPadEnter))
        assertFalse(isTvActivationKey(Key.DirectionDown))
    }
}
