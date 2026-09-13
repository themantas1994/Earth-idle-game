package com.earthgame.idle.balance

import com.earthgame.idle.domain.engine.BALANCE
import com.earthgame.idle.domain.engine.OWNERSHIP_BONUS
import com.earthgame.idle.domain.engine.PRESTIGE
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.technologies.BUILDING_TECHNOLOGIES
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
    fun `going deep on one building never beats broadening into new technology`() {
        // Across the *tightest* milestone span a building's unit price grows by
        // unitCostGrowth^baseStep against a single `multiplier` from the bonus.
        // If the bonus won, the tech tree would stop mattering: the optimal play
        // would be to buy one building forever. Later spans are wider, so they
        // only make the inequality safer — the first block is the hard case.
        for (tech in BUILDING_TECHNOLOGIES) {
            val priceGrowthAcrossSpan = generatorCostGrowth(tech.tier).pow(OWNERSHIP_BONUS.baseStep)

            assertTrue(
                "${tech.id}: ownership bonus ×${OWNERSHIP_BONUS.multiplier} must stay under the " +
                    "×$priceGrowthAcrossSpan the same span costs",
                OWNERSHIP_BONUS.multiplier < priceGrowthAcrossSpan,
            )
        }
    }

    @Test
    fun `the milestone ladder only ever spreads out, never contracts`() {
        // The whole point of the progressive ladder is that rewards get rarer
        // as a building deepens. A step that shrank would mean a building
        // suddenly paying out faster the deeper it went.
        var previousStep = 0
        var units = 0
        repeat(60) {
            val next = nextOwnershipMilestone(units)
            val step = next - units
            if (units % OWNERSHIP_BONUS.blockUnits == 0) {
                assertTrue(
                    "milestone spacing shrank from $previousStep to $step at $units owned",
                    step >= previousStep,
                )
                previousStep = step
            }
            units = next
        }
    }

    @Test
    fun `milestones never become so rare that deep buildings stop rewarding`() {
        // Sparser is the point; silent is not. Even a building nobody will ever
        // reach the depth of still owes the player a reward inside one session.
        for (owned in listOf(0, 500, 1_000, 2_000, 5_000)) {
            val step = nextOwnershipMilestone(owned) - owned
            assertTrue(
                "at $owned owned the next milestone is $step units away, which is too far to feel",
                step <= 70,
            )
        }
    }

    @Test
    fun `the ownership ladder compounds exactly as advertised`() {
        assertTrue(ownershipMultiplier(0) == 1.0)
        assertTrue(ownershipMultiplier(OWNERSHIP_BONUS.baseStep - 1) == 1.0)
        assertTrue(ownershipMultiplier(OWNERSHIP_BONUS.baseStep) == OWNERSHIP_BONUS.multiplier)
        assertTrue(
            "the first block still pays exactly the old ladder's rate",
            ownershipMultiplier(10 * OWNERSHIP_BONUS.baseStep) == OWNERSHIP_BONUS.multiplier.pow(10),
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
        for (tech in BUILDING_TECHNOLOGIES) {
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
