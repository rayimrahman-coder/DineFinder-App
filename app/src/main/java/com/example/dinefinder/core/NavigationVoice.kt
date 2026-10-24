package com.example.dinefinder.core

/** Stable keys prevent every GPS/compass update from repeating the same instruction. */
internal data class NavigationVoiceCue(val key: String, val text: String)

internal fun navigationVoiceCue(progress: RouteProgress?, locationError: String?, destinationName: String?): NavigationVoiceCue {
    if (locationError != null || progress == null) return NavigationVoiceCue("gps", "Waiting for a fresh GPS location. Pause and check your surroundings.")
    if (progress.hasArrived) return NavigationVoiceCue("arrival", "You are near ${destinationName?.takeIf { it.isNotBlank() } ?: "the restaurant"}. Check the entrance around you.")
    if (progress.isOffRoute) return NavigationVoiceCue("off-route", "You are off the walking route. Pause and tap Recalculate for new directions.")
    val instruction = progress.instruction ?: return NavigationVoiceCue("no-instruction", "Follow the walking route. Open Directions for the available steps.")
    return NavigationVoiceCue("instruction:${instruction.point.latitude}:${instruction.point.longitude}:${instruction.text}", instruction.text)
}

/** A cue is committed only after the speech engine accepts it; a failed attempt can be retried. */
internal class NavigationVoiceHistory {
    private var lastKey: String? = null
    fun shouldSpeak(cue: NavigationVoiceCue) = cue.key != lastKey
    fun accepted(cue: NavigationVoiceCue) { lastKey = cue.key }
    fun reset() { lastKey = null }
}
