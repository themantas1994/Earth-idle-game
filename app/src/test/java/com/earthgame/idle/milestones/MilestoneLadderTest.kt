package com.earthgame.idle.milestones

import com.earthgame.idle.domain.engine.OWNERSHIP_BONUS
import com.earthgame.idle.domain.engine.milestoneAt
import com.earthgame.idle.domain.engine.milestoneStep
import com.earthgame.idle.domain.engine.nextOwnershipMilestone
import com.earthgame.idle.domain.engine.ownershipLadder
import com.earthgame.idle.domain.engine.ownershipMilestonesCrossed
import com.earthgame.idle.domain.engine.ownershipMilestonesReached
import com.earthgame.idle.domain.engine.ownershipMultiplier
import com.earthgame.idle.domain.engine.ownershipProgress
import com.earthgame.idle.domain.engine.previousOwnershipMilestone
import com.earthgame.idle.domain.engine.unitsToNextOwnershipMilestone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The progressive milestone ladder.
 *
 * The old ladder put a reward on every tenth unit forever. This one starts
 * there and widens: 10 apart over the first hundred units of a building, 11
 * over the second hundred, 12 over the third, and so on — with the rule that a
 * milestone never steps across a hundred, so the round numbers stay round.
 *
 * The exact sequence is what the **NEXT** buy mode aims at and what every card
 * counts down to, so it is pinned here rather than left to the formula.
 */
class MilestoneLadderTest {

    @Test
    fun `the first hundred units keep the old every-ten ladder exactly`() {
        assertEquals(
            (1..10).map { it * 10 },
            ownershipLadder(10).map { it.atUnits },
        )
    }

    @Test
    fun `the second hundred is spaced eleven apart and still lands on two hundred`() {
        val secondBlock = ownershipLadder(20).drop(10).map { it.atUnits }
        assertEquals(
            listOf(111, 122, 133, 144, 155, 166, 177, 188, 199, 200),
            secondBlock,
        )
    }

    @Test
    fun `the third hundred is spaced twelve apart and lands on three hundred`() {
        val thirdBlock = ownershipLadder(29).drop(20).map { it.atUnits }
        assertEquals(
            listOf(212, 224, 236, 248, 260, 272, 284, 296, 300),
            thirdBlock,
        )
    }

    @Test
    fun `the fourth hundred is spaced thirteen apart`() {
        val fourthBlock = ownershipLadder(38).drop(29).map { it.atUnits }
        assertEquals(
            listOf(313, 326, 339, 352, 365, 378, 391, 400),
            fourthBlock.take(8),
        )
    }

    @Test
    fun `the spacing formula is exactly base plus one per hundred owned`() {
        assertEquals(10, milestoneStep(0))
        assertEquals(10, milestoneStep(99))
        assertEquals(11, milestoneStep(100))
        assertEquals(11, milestoneStep(199))
        assertEquals(12, milestoneStep(200))
        assertEquals(19, milestoneStep(900))
        assertEquals(
            OWNERSHIP_BONUS.baseStep + OWNERSHIP_BONUS.stepGrowthPerBlock * 30,
            milestoneStep(3_000),
        )
    }

    @Test
    fun `the worked examples from the design brief all hold`() {
        assertEquals(40, nextOwnershipMilestone(37))
        assertEquals(3, unitsToNextOwnershipMilestone(37))

        assertEquals(100, nextOwnershipMilestone(96))
        assertEquals(4, unitsToNextOwnershipMilestone(96))

        assertEquals(111, nextOwnershipMilestone(101))
        assertEquals(10, unitsToNextOwnershipMilestone(101))

        assertEquals(50, nextOwnershipMilestone(48))
        assertEquals(2, unitsToNextOwnershipMilestone(48))

        assertEquals(111, nextOwnershipMilestone(100))
        assertEquals(11, unitsToNextOwnershipMilestone(100))

        assertEquals(122, nextOwnershipMilestone(111))
        assertEquals(11, unitsToNextOwnershipMilestone(111))

        assertEquals(200, nextOwnershipMilestone(199))
        assertEquals(1, unitsToNextOwnershipMilestone(199))

        assertEquals(212, nextOwnershipMilestone(200))
        assertEquals(12, unitsToNextOwnershipMilestone(200))
    }

    @Test
    fun `the next milestone is always strictly ahead of where you are`() {
        for (owned in 0..2_500) {
            assertTrue(
                "at $owned the next milestone was ${nextOwnershipMilestone(owned)}",
                nextOwnershipMilestone(owned) > owned,
            )
        }
    }

    @Test
    fun `the milestone count agrees with walking the ladder`() {
        // Two independent implementations of the same sequence: the arithmetic
        // block count, and stepping the ladder one rung at a time.
        var rung = 0
        var expected = 0
        for (owned in 0..1_500) {
            while (rung < owned) {
                val next = nextOwnershipMilestone(rung)
                if (next > owned) break
                rung = next
                expected++
            }
            assertEquals("milestones reached at $owned", expected, ownershipMilestonesReached(owned))
        }
    }

    @Test
    fun `every hundred is itself a milestone`() {
        for (hundred in 1..30) {
            val units = hundred * 100
            assertEquals("$units should be a milestone", units, previousOwnershipMilestone(units))
        }
    }

    @Test
    fun `the multiplier compounds once per milestone`() {
        assertEquals(1.0, ownershipMultiplier(0), 0.0)
        assertEquals(1.0, ownershipMultiplier(9), 0.0)
        assertEquals(2.0, ownershipMultiplier(10), 0.0)
        assertEquals(4.0, ownershipMultiplier(20), 0.0)
        assertEquals(2.0.pow(10), ownershipMultiplier(100), 0.0)
        // 19 rungs by 199: ten in the first block, nine in the second.
        assertEquals(2.0.pow(19), ownershipMultiplier(199), 0.0)
        assertEquals(2.0.pow(20), ownershipMultiplier(200), 0.0)
    }

    @Test
    fun `progress runs from the milestone just passed to the one ahead`() {
        assertEquals(0.0, ownershipProgress(0), 1e-12)
        assertEquals(0.5, ownershipProgress(5), 1e-12)
        assertEquals(0.0, ownershipProgress(10), 1e-12)
        assertEquals(0.0, ownershipProgress(100), 1e-12)
        // 105 sits five units into an eleven-unit span from 100 to 111.
        assertEquals(5.0 / 11.0, ownershipProgress(105), 1e-12)
        for (owned in 0..1_000) {
            val progress = ownershipProgress(owned)
            assertTrue("progress out of range at $owned: $progress", progress in 0.0..1.0)
        }
    }

    @Test
    fun `crossings report every milestone a bulk purchase passes, in order`() {
        assertEquals(emptyList<Int>(), ownershipMilestonesCrossed(0, 5))
        assertEquals(listOf(10), ownershipMilestonesCrossed(0, 10))
        assertEquals(listOf(10, 20), ownershipMilestonesCrossed(5, 25))
        assertEquals(listOf(20, 30, 40), ownershipMilestonesCrossed(12, 47))
        assertEquals(emptyList<Int>(), ownershipMilestonesCrossed(10, 10))
        assertEquals((1..10).map { it * 10 }, ownershipMilestonesCrossed(0, 100))
        assertEquals(listOf(111, 122), ownershipMilestonesCrossed(100, 125))
        assertEquals(listOf(200, 212), ownershipMilestonesCrossed(199, 215))
    }

    @Test
    fun `crossings and the reached count never disagree`() {
        for (before in listOf(0, 3, 10, 47, 99, 100, 150, 199, 200, 305, 999)) {
            for (bought in listOf(1, 7, 40, 250)) {
                val after = before + bought
                assertEquals(
                    "crossings from $before to $after",
                    ownershipMilestonesReached(after) - ownershipMilestonesReached(before),
                    ownershipMilestonesCrossed(before, after).size,
                )
            }
        }
    }

    @Test
    fun `milestones are data, so a rung can be looked up by index`() {
        assertEquals(10, milestoneAt(1)!!.atUnits)
        assertEquals(100, milestoneAt(10)!!.atUnits)
        assertEquals(111, milestoneAt(11)!!.atUnits)
        assertEquals(200, milestoneAt(20)!!.atUnits)
        assertEquals(OWNERSHIP_BONUS.multiplier, milestoneAt(1)!!.outputMultiplier, 0.0)
        assertNull("there is no zeroth rung", milestoneAt(0))
    }

    @Test
    fun `nonsense ownership counts are handled rather than crashing`() {
        assertEquals(0, ownershipMilestonesReached(-1_000))
        assertEquals(1.0, ownershipMultiplier(-1_000), 0.0)
        assertEquals(0.0, ownershipProgress(-1_000), 0.0)
        assertEquals(10, nextOwnershipMilestone(-1_000))
        assertTrue("a colossal count must still terminate", ownershipMilestonesReached(Int.MAX_VALUE) > 0)
    }
}
