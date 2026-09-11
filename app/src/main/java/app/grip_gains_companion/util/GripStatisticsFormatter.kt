package app.grip_gains_companion.util

data class GripStatisticsText(
    val mean: String,
    val variation: String?,
    val unit: String
)

object GripStatisticsFormatter {
    fun format(meanKg: Double, stdDevKg: Double?, useLbs: Boolean): GripStatisticsText {
        return GripStatisticsText(
            mean = WeightFormatter.format(meanKg, useLbs, includeUnit = false),
            variation = stdDevKg
                ?.takeIf { it > 0.0 }
                ?.let { WeightFormatter.format(it, useLbs, includeUnit = false) },
            unit = if (useLbs) "lbs" else "kg"
        )
    }
}
