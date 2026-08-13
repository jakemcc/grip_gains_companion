package app.grip_gains_companion.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GripStatisticsFormatterTest {

    @Test
    fun formatsMeanAndVariationAsOneCompactExpression() {
        assertEquals(
            GripStatisticsText(mean = "20.1", variation = "0.4", unit = "kg"),
            GripStatisticsFormatter.format(meanKg = 20.1, stdDevKg = 0.4, useLbs = false)
        )
    }

    @Test
    fun convertsBothValuesToPoundsAndKeepsOneUnitLabel() {
        assertEquals(
            GripStatisticsText(mean = "44.3", variation = "0.9", unit = "lbs"),
            GripStatisticsFormatter.format(meanKg = 20.1, stdDevKg = 0.4, useLbs = true)
        )
    }

    @Test
    fun omitsVariationUntilItIsMeaningful() {
        assertEquals(
            GripStatisticsText(mean = "20.1", variation = null, unit = "kg"),
            GripStatisticsFormatter.format(meanKg = 20.1, stdDevKg = 0.0, useLbs = false)
        )
        assertNull(
            GripStatisticsFormatter.format(meanKg = 20.1, stdDevKg = null, useLbs = false).variation
        )
    }
}
