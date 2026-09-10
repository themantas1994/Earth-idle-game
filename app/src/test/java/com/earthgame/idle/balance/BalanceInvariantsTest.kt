package com.earthgame.idle.balance

import com.earthgame.idle.domain.engine.BALANCE
import com.earthgame.idle.domain.engine.OWNERSHIP_BONUS
import com.earthgame.idle.domain.engine.PRESTIGE
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.technologies.GENERATOR_TECHNOLOGIES
import com.earthgame.idle.domain.technologies.generatorBaseCost
import com.earthgame.idle.domain.technologies.generatorCostGrowth
import com.earthgame.idle.domain.technologies.ladderTier
import com.earthgame.idle.domain.technologies.resourceProduction
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The relationships between the tuning constants that the game's pacing rests
 * on, as opposed to their literal values (which `TechnologyParityTest` pins
 * against the reference implementation).
 *
 * These are the assertions the prose in `Constants.kt` and `Ownership.kt` makes
 * about itself. A rebalance is allowed to move any number here — but if it
 * breaks one of these relationships it has broken the game in a way that only
 * shows up hours into a run, which is exactly the kind of regression a test
 * should catch in seconds.
 */
class BalanceInvariantsTest {

    @Test
    fun `climbing a tier gets slower, not faster`() {
        // `r = costGrowth / productionGrowth` is the factor by which the
        // wall-clock time to climb one tier is multiplied each tier. At r <= 1
        // the economy outruns its own prices and a run finishes itself in
        // minutes.
        val r = BALANCE.generatorCostGrowthPerTier / BALANCE.productionGrowthPerTier

        assertTrue("cost growth must outpace production growth (r=$r)", r > 1.0)
    }

    @Test
    fun `emissions grow more slowly than the economy`() {
        // If gas kept pace with resources the planet would always die a few
        // tiers before the tree ran out, making the last technologies
        // unreachable content.
        assertTrue(
            "gas growth ${BALANCE.gasProductionGrowthPerTier} must stay under " +
                "resource growth ${BALANCE.productionGrowthPerTier}",
            BALANCE.gasProductionGrowthPerTier < BALANCE.productionGrowthPerTier,
        )
    }

    @Test
    fun `research paces the tree without blocking it`() {
        assertTrue(
            "research must stay cheaper per tier than the generators it gates",
            BALANCE.researchCostGrowthPerTier < BALANCE.generatorCostGrowthPerTier,
        )
    }

    @Test
    fun `going deep on one generator never beats broadening into new technology`() {
        // Across one ownership span a generator's unit price grows by
        // unitCostGrowth^everyUnits against a single `multiplier` from the
        // bonus. If the bonus won, the tech tree would stop mattering: the
        // optimal play would be to buy one building forever.
        for (tech in GENERATOR_TECHNOLOGIES) {
            val priceGrowthAcrossSpan = generatorCostGrowth(tech.tier).pow(OWNERSHIP_BONUS.everyUnits)

            assertTrue(
                "${tech.id}: ownership bonus ×${OWNERSHIP_BONUS.multiplier} must stay under the " +
                    "×$priceGrowthAcrossSpan the same span costs",
                OWNERSHIP_BONUS.multiplier < priceGrowthAcrossSpan,
            )
        }
    }

    @Test
    fun `the ownership ladder compounds exactly as advertised`() {
        assertTrue(ownershipMultiplier(0) == 1.0)
        assertTrue(ownershipMultiplier(OWNERSHIP_BONUS.everyUnits - 1) == 1.0)
        assertTrue(ownershipMultiplier(OWNERSHIP_BONUS.everyUnits) == OWNERSHIP_BONUS.multiplier)
        assertTrue(
            ownershipMultiplier(10 * OWNERSHIP_BONUS.everyUnits) == OWNERSHIP_BONUS.multiplier.pow(10),
        )
        assertTrue("a negative count cannot earn a bonus", ownershipMultiplier(-50) == 1.0)
    }

    @Test
    fun `the late-tier compression narrows the ladder without inverting it`() {
        val knee = BALANCE.flattenLadderFromTier

        assertTrue("compression must actually compress", BALANCE.lateTierCompression < 1.0)
        assertTrue("but never run backwards", BALANCE.lateTierCompression > 0.0)
        assertTrue("the knee is continuous", ladderTier(knee.toInt()) == knee)

        // Past the knee a tier still has to cost more and produce more than the
        // one below it, or the tree stops progressing.
        for (tier in knee.toInt() until knee.toInt() + 20) {
            assertTrue("tier $tier must cost more than tier ${tier - 1}", generatorBaseCost(tier) > generatorBaseCost(tier - 1))
            assertTrue("tier $tier must out-produce tier ${tier - 1}", resourceProduction(tier) > resourceProduction(tier - 1))
        }
    }

    @Test
    fun `no shipped generator carries a per-technology fudge factor large enough to distort pacing`() {
        // Scaling.kt's contract: keep per-technology `scale` inside roughly
        // 0.3–3 of the curve, or one technology starts pacing the game on its
        // own.
        for (tech in GENERATOR_TECHNOLOGIES) {
            val curve = generatorBaseCost(tech.tier)
            val listed = tech.cost.sumOf { it.baseAmount }
            val ratio = listed / curve

            assertTrue(
                "${tech.id} (tier ${tech.tier}) is priced at ×$ratio of its tier's curve",
                ratio in 0.1..10.0,
            )
        }
    }

    @Test
    fun `the speed term is bounded on both sides`() {
        assertTrue(PRESTIGE.minSpeedMultiplier > 0.0)
        assertTrue(PRESTIGE.maxSpeedMultiplier > PRESTIGE.minSpeedMultiplier)
        assertTrue("a sub-1 exponent is what gives the mass term diminishing returns", PRESTIGE.exponent < 1.0)
    }
}
