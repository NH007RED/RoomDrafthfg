package com.nhdeveloper.roomdraft.rendering.room3d

import kotlin.math.PI
import kotlin.math.cos

/**
 * Cinematic fly-around used for the exported 3D video: one full revolution with a slow dolly and a gentle
 * rise and fall. [progress] runs from 0 to 1; the first and last frame show the same view so the clip loops.
 */
object CameraPath {
    private fun ease(t: Double) = t * t * (3.0 - 2.0 * t)

    fun at(progress: Double, roomL: Double, roomW: Double): CameraState {
        val t = progress.coerceIn(0.0, 1.0)
        val base = CameraState.default(roomL, roomW)
        // slight ease at both ends so the motion starts and stops softly
        val spin = 0.85 * t + 0.15 * ease(t)
        val yaw = base.yaw + 2.0 * PI * spin
        val pitch = 0.70 - 0.25 * cos(2.0 * PI * t)
        val dist = base.distance * (0.80 + 0.16 * (0.5 - 0.5 * cos(2.0 * PI * t)))
        return CameraState(yaw = yaw, pitch = pitch, distance = dist, target = base.target)
    }
}
