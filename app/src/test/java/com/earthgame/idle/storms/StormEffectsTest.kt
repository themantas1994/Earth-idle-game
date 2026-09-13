package com.earthgame.idle.storms

import com.earthgame.idle.domain.engine.STORMS
import com.earthgame.idle.domain.storms.Storm
import com.earthgame.idle.domain.storms.StormType
import com.earthgame.idle.domain.storms.averageIntensityOverWindow
import com.earthgame.idle.domain.storms.averageStormEffectsOverWindow
import com.earthgame.idle.domain.storms.computeStormEffects
import com.earthgame.idle.domain.storms.stormContribution
import com.earthgame.idle.domain.technologies.TechBranch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What storms cost, and the two rules that stop them from becoming a spiral:
 * diminishing returns within a channel, and a hard cap on the total.
 */
class StormEffectsTest {

    private fun storm(
        id: String,
        type: StormType,
        intensity: Double,
        age: Double = 200.0,
        lifetime: Double = 500.0,
    ) = Storm(
        id = id,
        type = type,
        name = id,
        latitudeDeg = 15.0,
        longitudeDeg = -50.0,
        intensity = intensity,
        targetIntensity = intensity,
        peakIntensity = intensity,
        ageSeconds = age,
        lifetimeSeconds = lifetime,
        headingDeg = 315.0,
        speedDegPerSecond = 0.02,
        formedAtStep = 1,
    )

    @Test
    fun `no storms means no penalty at all`() {
        val effects = computeStormEffects(emptyList())
        assertTrue("clear skies must be exactly neutral", effects.isNeutral)
        assertEquals(1.0, effects.globalMultiplier, 0.0)
        assertTrue(
            "and must not even produce a multiplier layer",
            effects.asMultiplierContribution().isEmpty,
        )
    }

    @Test
    fun `a storm still forming costs nothing measurable`() {
        val effects = computeStormEffects(listOf(storm("a", StormType.TROPICAL_STORM, intensity = 0.0)))
        assertEquals(1.0, effects.globalMultiplier, 1e-12)
    }

    @Test
    fun `a storm's penalty scales with its intensity`() {
        val weak = computeStormEffects(listOf(storm("a", StormType.HURRICANE, 0.25))).globalPenalty
        val strong = computeStormEffects(listOf(storm("a", StormType.HURRICANE, 1.0))).globalPenalty

        assertEquals("at full intensity it is the profile's own figure", 0.07, strong, 1e-9)
        assertEquals("and a quarter of that at a quarter intensity", 0.0175, weak, 1e-9)
    }

    @Test
    fun `a storm hits the branches its category names`() {
        val effects = computeStormEffects(listOf(storm("a", StormType.SUPERSTORM, 1.0)))
        assertTrue(
            "a superstorm takes out the grid",
            effects.branchPenalty(TechBranch.ELECTRICITY) > 0.1,
        )
        assertEquals(
            "and leaves alone what it has no business touching",
            0.0,
            effects.branchPenalty(TechBranch.AGRICULTURE),
            1e-12,
        )
    }

    @Test
    fun `overlapping storms stack with diminishing returns`() {
        val one = computeStormEffects(listOf(storm("a", StormType.HURRICANE, 1.0))).globalPenalty
        val two = computeStormEffects(
            listOf(storm("a", StormType.HURRICANE, 1.0), storm("b", StormType.HURRICANE, 1.0)),
        ).globalPenalty

        assertTrue("two storms must be worse than one", two > one)
        assertTrue("but not twice as bad, was $two against ${one * 2}", two < one * 2)
        assertEquals(
            "the second counts for exactly the falloff",
            one * (1.0 + STORMS.STACKING_FALLOFF),
            two,
            1e-9,
        )
    }

    @Test
    fun `ten storms cannot zero production`() {
        // The scenario the caps exist for: a maximally hostile sky.
        val everything = (1..10).map { storm("s$it", StormType.SUPERSTORM, 1.0) }
        val effects = computeStormEffects(everything)

        // Diminishing returns alone already hold the total under the cap —
        // ten superstorms come to about a quarter, not to a whole — and the cap
        // is the guarantee behind that rather than a figure anything reaches.
        assertTrue(
            "the global penalty must never exceed the cap, was ${effects.globalPenalty}",
            effects.globalPenalty <= STORMS.MAX_GLOBAL_PENALTY + 1e-9,
        )
        assertTrue(
            "but the worst sky in the game must still be felt",
            effects.globalPenalty > 0.15,
        )
        assertTrue(
            "so production is reduced, never removed",
            effects.globalMultiplier >= 1.0 - STORMS.MAX_GLOBAL_PENALTY,
        )
        for (branch in TechBranch.entries) {
            assertTrue(
                "no branch may exceed its cap: $branch",
                effects.branchPenalty(branch) <= STORMS.MAX_BRANCH_PENALTY + 1e-9,
            )
        }

        // Worst case end to end: the global and branch penalties multiply, and
        // even together they leave a third of production standing.
        val worstBranch = TechBranch.entries.maxOf { effects.branchPenalty(it) }
        val worst = effects.globalMultiplier * (1.0 - worstBranch)
        assertTrue("the worst possible drag still leaves production running, was $worst", worst > 0.3)
    }

    @Test
    fun `the strongest storm is the one that counts most`() {
        // Order must not matter: the stack is sorted before it is discounted.
        val ascending = listOf(
            storm("a", StormType.TROPICAL_STORM, 1.0),
            storm("b", StormType.SUPERSTORM, 1.0),
        )
        val descending = ascending.reversed()
        assertEquals(
            computeStormEffects(ascending).globalPenalty,
            computeStormEffects(descending).globalPenalty,
            1e-12,
        )
    }

    @Test
    fun `a storm reports its own effect for its card`() {
        val hurricane = storm("a", StormType.HURRICANE, 0.5)
        val contribution = stormContribution(hurricane)

        assertEquals("half intensity, half the penalty", 0.035, contribution.globalPenalty, 1e-9)
        assertEquals(
            "and the same for each branch it names",
            0.06,
            contribution.branchPenalties.getValue(TechBranch.ELECTRICITY),
            1e-9,
        )
    }

    // ------------------------------------------------ the offline settlement --

    @Test
    fun `an absence is charged the storm's average, not its instant`() {
        // A storm just past its peak, with most of its wind-down still ahead.
        val fading = storm("a", StormType.HURRICANE, intensity = 0.7, age = 350.0, lifetime = 500.0)
        val instant = computeStormEffects(listOf(fading)).globalPenalty
        val averaged = averageStormEffectsOverWindow(listOf(fading), windowSeconds = 150.0, sustain = 1.0)

        assertTrue(
            "a dying storm must cost less over the window than it does right now",
            averaged.globalPenalty < instant,
        )
        assertTrue("but not nothing", averaged.globalPenalty > 0.0)
    }

    @Test
    fun `a storm is not charged for time after it is gone`() {
        val brief = storm("a", StormType.HURRICANE, intensity = 0.6, age = 490.0, lifetime = 500.0)

        val shortWindow = averageIntensityOverWindow(brief, windowSeconds = 10.0, sustain = 1.0)
        val longWindow = averageIntensityOverWindow(brief, windowSeconds = 3600.0, sustain = 1.0)

        assertTrue(
            "ten seconds of life spread over an hour is nearly nothing, was $longWindow",
            longWindow < shortWindow / 100.0,
        )
    }

    @Test
    fun `an absence with no storms is charged exactly nothing`() {
        val effects = averageStormEffectsOverWindow(emptyList(), windowSeconds = 12 * 3600.0, sustain = 1.0)
        assertTrue(effects.isNeutral)
        assertTrue(
            "which is what keeps the offline path identical to what it always was",
            effects.asMultiplierContribution().isEmpty,
        )
    }

    @Test
    fun `every penalty is a production multiplier, never a loss of progress`() {
        // The design rule the whole system is built around: storms take a slice
        // off the rate while they run, and give it all back when they pass.
        val effects = computeStormEffects((1..6).map { storm("s$it", StormType.SUPERSTORM, 1.0) })
        val contribution = effects.asMultiplierContribution()

        assertTrue("storms only ever scale production", contribution.global!! < 1.0)
        assertTrue("and never touch research directly", contribution.research == null)
        assertTrue("and never touch emissions", contribution.allGas == null)
        assertTrue("and never the per-gas curve", contribution.perGas.isEmpty())
    }
}
