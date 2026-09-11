package app.grip_gains_companion

import app.grip_gains_companion.config.AppConstants
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun defaultDisengagePercentage_isFortyPercent() {
        assertEquals(0.40, AppConstants.DEFAULT_DISENGAGE_PERCENTAGE, 0.0)
    }
}
