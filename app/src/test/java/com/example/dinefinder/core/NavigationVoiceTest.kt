package com.example.dinefinder.core

import org.junit.Assert.*
import org.junit.Test

class NavigationVoiceTest {
    private val start = GeoPoint(5.28, 115.17)
    private val instruction = RouteInstruction(start, "Turn left onto Campus Road", 80.0)
    private val progress = RouteProgress(0.0, 250.0, 0, start, instruction, false, false)
    @Test fun voiceUsesCurrentRouteInstructionWithoutInventingTurnDistance() {
        assertEquals(instruction.text, navigationVoiceCue(progress, null, "Cafe").text)
    }
    @Test fun gpsChangesDoNotRepeatSameInstructionButNewManeuverDoes() {
        val history = NavigationVoiceHistory()
        val cue = navigationVoiceCue(progress, null, "Cafe")
        assertTrue(history.shouldSpeak(cue)); history.accepted(cue)
        assertFalse(history.shouldSpeak(navigationVoiceCue(progress.copy(remainingMeters = 245.0, segmentIndex = 1), null, "Cafe")))
        assertTrue(history.shouldSpeak(navigationVoiceCue(progress.copy(instruction = instruction.copy(point = GeoPoint(5.281, 115.17))), null, "Cafe")))
    }
    @Test fun warningsReplaceUnsafeTurnInstructionAndRecoveryAnnouncesCurrentStep() {
        val history = NavigationVoiceHistory()
        history.accepted(navigationVoiceCue(progress, null, "Cafe"))
        val offRoute = navigationVoiceCue(progress.copy(isOffRoute = true), null, "Cafe")
        assertTrue(offRoute.text.contains("Recalculate"))
        history.accepted(offRoute)
        assertTrue(history.shouldSpeak(navigationVoiceCue(progress, null, "Cafe")))
        val missingGps = navigationVoiceCue(progress, "GPS expired", "Cafe")
        assertEquals("gps", missingGps.key)
        assertFalse(missingGps.text.contains("Turn left"))
    }
    @Test fun arrivalIsAnnouncedOnceAndNullLocationCannotAnnounceArrival() {
        val cue = navigationVoiceCue(progress.copy(hasArrived = true), null, "Cafe UMSKAL")
        assertEquals("arrival", cue.key)
        assertTrue(cue.text.contains("Cafe UMSKAL"))
        val history = NavigationVoiceHistory(); history.accepted(cue)
        assertFalse(history.shouldSpeak(cue))
        assertEquals("gps", navigationVoiceCue(null, null, "Cafe").key)
    }
    @Test fun failedSpeechRemainsRetryableAndResumingCanRepeatCurrentCue() {
        val history = NavigationVoiceHistory()
        val cue = navigationVoiceCue(progress, null, "Cafe")
        assertTrue(history.shouldSpeak(cue)); assertTrue(history.shouldSpeak(cue))
        history.accepted(cue); assertFalse(history.shouldSpeak(cue))
        history.reset(); assertTrue(history.shouldSpeak(cue))
    }
}
