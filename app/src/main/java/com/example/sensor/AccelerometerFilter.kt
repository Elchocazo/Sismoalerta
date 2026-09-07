package com.example.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Filter and peak magnitude estimator for accelerometer readings to distinguish
 * seismic P/S waves from daily smartphone drops / walking motion.
 */
class AccelerometerFilter {
    private var alpha = 0.8f // Low pass filter factor for gravity removal
    private val gravity = FloatArray(3) { 0f }
    private val linearAcceleration = FloatArray(3) { 0f }

    private val windowSize = 25
    private val accelerationHistory = ArrayDeque<Float>()

    private var baselinePga = 0.05f

    /**
     * Processes raw x, y, z accelerometer values (m/s^2).
     * Returns a [SeismicDetectionResult].
     */
    fun processSample(x: Float, y: Float, z: Float): SeismicDetectionResult {
        // Isolate gravity with low-pass filter
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        // Remove gravity to get dynamic linear acceleration
        linearAcceleration[0] = x - gravity[0]
        linearAcceleration[1] = y - gravity[1]
        linearAcceleration[2] = z - gravity[2]

        // Dynamic magnitude calculation |a| = sqrt(ax^2 + ay^2 + az^2)
        val currentMagnitude = sqrt(
            (linearAcceleration[0] * linearAcceleration[0] +
             linearAcceleration[1] * linearAcceleration[1] +
             linearAcceleration[2] * linearAcceleration[2]).toDouble()
        ).toFloat()

        // Maintain moving history window
        accelerationHistory.addLast(currentMagnitude)
        if (accelerationHistory.size > windowSize) {
            accelerationHistory.removeFirst()
        }

        val mean = accelerationHistory.average().toFloat()
        var varianceSum = 0.0
        for (valItem in accelerationHistory) {
            val diff = valItem - mean
            varianceSum += diff * diff
        }
        val variance = if (accelerationHistory.isNotEmpty()) (varianceSum / accelerationHistory.size).toFloat() else 0f

        // Seismic P/S waves exhibit sustained multi-directional vibration with continuous variance,
        // unlike sudden impact drops (single sharp peak with zero sustained variance).
        val estimatedPgaG = currentMagnitude / 9.81f // Convert to g

        val isImpactDrop = currentMagnitude > 18.0f && variance < 1.2f
        val isSustainedSeismicWave = currentMagnitude in 2.2f..15.0f && variance > 0.8f

        val estimatedMagnitudeRichter = when {
            estimatedPgaG > 0.35f -> 6.8
            estimatedPgaG > 0.20f -> 5.8
            estimatedPgaG > 0.08f -> 4.5
            estimatedPgaG > 0.03f -> 3.5
            else -> 2.0
        }

        return SeismicDetectionResult(
            rawMagnitude = currentMagnitude,
            estimatedPgaG = estimatedPgaG,
            variance = variance,
            isSeismicTrigger = isSustainedSeismicWave,
            isDropOrFalsePositive = isImpactDrop,
            estimatedRichter = estimatedMagnitudeRichter
        )
    }
}

data class SeismicDetectionResult(
    val rawMagnitude: Float,
    val estimatedPgaG: Float,
    val variance: Float,
    val isSeismicTrigger: Boolean,
    val isDropOrFalsePositive: Boolean,
    val estimatedRichter: Double
)
