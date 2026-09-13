package com.earthgame.idle.production

import com.earthgame.idle.domain.engine.EffectiveMultipliers
import com.earthgame.idle.domain.engine.GameDecimal
import com.earthgame.idle.domain.engine.computeProductionRates
import com.earthgame.idle.domain.engine.gd
import com.earthgame.idle.domain.model.ResourceId
import com.earthgame.idle.domain.production.ConsumerDemand
import com.earthgame.idle.domain.production.accountFlow
import com.earthgame.idle.domain.production.solveUtilizations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottleneck engine, tested as arithmetic.
 *
 * These build synthetic economies rather than driving the shipped one, because
 * the properties being checked — a consumer runs at the rate of its scarcest
 * input, two consumers share a shortage fairly, nothing goes negative, the
 * answer does not depend on definition order — are properties of the solver,
 * and pinning them to the shipped numbers would mean every rebalance broke
 * them.
 *
 * The shipped economy's own behaviour is covered by `EconomyBalanceTest`.
 */
class ResourceFlowTest {

    private val resourceCount = ResourceId.entries.size

    private fun supply(vararg rates: Pair<ResourceId, Double>): Array<GameDecimal> {
        val array = Array(resourceCount) { GameDecimal.ZERO }
        for ((resource, rate) in rates) array[resource.ordinal] = gd(rate)
        return array
    }

    private fun consumer(
        id: String,
        owned: Int = 1,
        inputs: Map<ResourceId, Double> = emptyMap(),
        outputs: Map<ResourceId, Double> = emptyMap(),
    ): ConsumerDemand {
        val demand = Array(resourceCount) { GameDecimal.ZERO }
        for ((resource, rate) in inputs) demand[resource.ordinal] = gd(rate * owned)
        val rated = Array(resourceCount) { GameDecimal.ZERO }
        for ((resource, rate) in outputs) rated[resource.ordinal] = gd(rate * owned)
        return ConsumerDemand(
            consumerId = id,
            owned = owned,
            demandPerS = demand,
            ratedOutputPerS = rated,
            inputResources = inputs.keys.sortedBy { it.ordinal },
        )
    }

    private fun solve(
        producers: Array<GameDecimal>,
        consumers: List<ConsumerDemand>,
    ) = solveUtilizations(producers, consumers).let { allocation ->
        allocation to accountFlow(producers, consumers, allocation, DoubleArray(consumers.size) { 1.0 })
    }

    // --- The headline behaviour ---------------------------------------------

    @Test
    fun `a fully supplied consumer runs at one hundred per cent`() {
        val producers = supply(ResourceId.OIL to 1_000.0)
        val refineries = listOf(consumer("refinery", owned = 5, inputs = mapOf(ResourceId.OIL to 10.0), outputs = mapOf(ResourceId.FUEL to 8.0)))

        val (allocation, flow) = solve(producers, refineries)

        assertEquals(1.0, allocation.utilization[0], 1e-12)
        assertEquals(40.0, flow.supplyPerS[ResourceId.FUEL].toDouble(), 1e-9)
        assertEquals(50.0, flow.consumptionPerS[ResourceId.OIL].toDouble(), 1e-9)
        assertNull("nothing is limiting it", flow.consumers[0].limitingResource)
    }

    @Test
    fun `a starved consumer runs at the ratio its input allows, and makes exactly that much`() {
        // Five refineries want 50 Oil/s; the economy makes 25 (of which 90% may
        // be claimed). They must run at 45%, not consume 50 and not invent fuel.
        val producers = supply(ResourceId.OIL to 25.0)
        val refineries = listOf(consumer("refinery", owned = 5, inputs = mapOf(ResourceId.OIL to 10.0), outputs = mapOf(ResourceId.FUEL to 8.0)))

        val (allocation, flow) = solve(producers, refineries)

        assertEquals(0.45, allocation.utilization[0], 1e-9)
        assertEquals(22.5, flow.consumptionPerS[ResourceId.OIL].toDouble(), 1e-9)
        assertEquals(40.0 * 0.45, flow.supplyPerS[ResourceId.FUEL].toDouble(), 1e-9)
        assertEquals(ResourceId.OIL, flow.consumers[0].limitingResource)
    }

    @Test
    fun `a multi-input consumer runs at the rate of its scarcest input`() {
        // Iron requirement 10/s, Energy requirement 20/s; Iron 100/s available
        // and Energy only 5/s. The mill runs at Energy's ratio, not Iron's.
        val producers = supply(ResourceId.IRON to 100.0, ResourceId.ENERGY to 5.0)
        val mills = listOf(
            consumer(
                "mill",
                inputs = mapOf(ResourceId.IRON to 10.0, ResourceId.ENERGY to 20.0),
                outputs = mapOf(ResourceId.STEEL to 6.0),
            ),
        )

        val (allocation, flow) = solve(producers, mills)

        // 90% of 5 = 4.5 Energy available against a demand of 20.
        assertEquals(4.5 / 20.0, allocation.utilization[0], 1e-9)
        assertEquals(ResourceId.ENERGY, flow.consumers[0].limitingResource)
        // And it eats iron only in proportion to what it actually produced.
        assertEquals(10.0 * (4.5 / 20.0), flow.consumptionPerS[ResourceId.IRON].toDouble(), 1e-9)
    }

    @Test
    fun `a consumer with no supply at all runs at zero rather than going negative`() {
        val producers = supply(ResourceId.ENERGY to 100.0)
        val mills = listOf(consumer("mill", inputs = mapOf(ResourceId.IRON to 10.0), outputs = mapOf(ResourceId.STEEL to 6.0)))

        val (allocation, flow) = solve(producers, mills)

        assertEquals(0.0, allocation.utilization[0], 1e-12)
        assertEquals(0.0, flow.supplyPerS[ResourceId.STEEL].toDouble(), 1e-12)
        assertEquals(0.0, flow.consumptionPerS[ResourceId.IRON].toDouble(), 1e-12)
    }

    @Test
    fun `no resource can ever end a step with a negative net rate`() {
        val producers = supply(ResourceId.OIL to 1.0, ResourceId.ENERGY to 1.0)
        val greedy = listOf(
            consumer("a", owned = 1_000, inputs = mapOf(ResourceId.OIL to 500.0), outputs = mapOf(ResourceId.FUEL to 1.0)),
            consumer("b", owned = 1_000, inputs = mapOf(ResourceId.ENERGY to 500.0), outputs = mapOf(ResourceId.CHEMICALS to 1.0)),
        )

        val (_, flow) = solve(producers, greedy)

        for (resource in ResourceId.entries) {
            assertTrue(
                "${resource.id} has a negative net rate",
                flow.netPerS[resource].gte(GameDecimal.ZERO),
            )
            assertTrue(
                "${resource.id} is consumed faster than it is made",
                flow.consumptionPerS[resource].lte(flow.supplyPerS[resource]),
            )
        }
    }

    // --- Fairness and determinism -------------------------------------------

    @Test
    fun `two consumers competing for one resource split it fairly`() {
        val producers = supply(ResourceId.OIL to 100.0)
        val both = listOf(
            consumer("a", inputs = mapOf(ResourceId.OIL to 100.0), outputs = mapOf(ResourceId.FUEL to 10.0)),
            consumer("b", inputs = mapOf(ResourceId.OIL to 100.0), outputs = mapOf(ResourceId.CHEMICALS to 10.0)),
        )

        val (allocation, flow) = solve(producers, both)

        assertEquals("both get the same share", allocation.utilization[0], allocation.utilization[1], 1e-12)
        assertEquals(0.45, allocation.utilization[0], 1e-9)
        assertEquals(90.0, flow.consumptionPerS[ResourceId.OIL].toDouble(), 1e-9)
    }

    @Test
    fun `a consumer limited by another input releases the share it was not going to use`() {
        // "a" wants 100 Oil and 100 Energy but only 10 Energy exists, so it can
        // only run at a tenth — and the 90 Oil it is not going to touch has to
        // reach "b" rather than being reserved against a demand that cannot
        // happen.
        val producers = supply(ResourceId.OIL to 200.0 / 0.9, ResourceId.ENERGY to 10.0 / 0.9)
        val consumers = listOf(
            consumer("a", inputs = mapOf(ResourceId.OIL to 100.0, ResourceId.ENERGY to 100.0), outputs = mapOf(ResourceId.FUEL to 1.0)),
            consumer("b", inputs = mapOf(ResourceId.OIL to 150.0), outputs = mapOf(ResourceId.CHEMICALS to 1.0)),
        )

        val (allocation, _) = solve(producers, consumers)

        assertEquals("a is pinned by Energy", 0.1, allocation.utilization[0], 1e-9)
        assertEquals("b gets the oil a released", 1.0, allocation.utilization[1], 1e-9)
    }

    @Test
    fun `the answer does not depend on the order the consumers are defined in`() {
        val producers = supply(ResourceId.OIL to 37.0, ResourceId.ENERGY to 91.0, ResourceId.IRON to 12.0)
        val a = consumer("a", owned = 3, inputs = mapOf(ResourceId.OIL to 4.0, ResourceId.ENERGY to 9.0), outputs = mapOf(ResourceId.FUEL to 2.0))
        val b = consumer("b", owned = 7, inputs = mapOf(ResourceId.IRON to 2.0, ResourceId.ENERGY to 3.0), outputs = mapOf(ResourceId.STEEL to 1.0))
        val c = consumer("c", owned = 2, inputs = mapOf(ResourceId.OIL to 11.0), outputs = mapOf(ResourceId.CHEMICALS to 5.0))

        val forwards = solveUtilizations(producers, listOf(a, b, c)).utilization
        val backwards = solveUtilizations(producers, listOf(c, b, a)).utilization

        assertEquals(forwards[0], backwards[2], 1e-12)
        assertEquals(forwards[1], backwards[1], 1e-12)
        assertEquals(forwards[2], backwards[0], 1e-12)
    }

    @Test
    fun `a chain settles so a starved first stage starves the second`() {
        // Oil -> refinery -> Fuel -> power station -> Energy, with only enough
        // oil for half the refineries. The power stations must fall with them
        // rather than running on fuel that was never made.
        val producers = supply(ResourceId.OIL to 50.0 / 0.9)
        val chain = listOf(
            consumer("refinery", owned = 10, inputs = mapOf(ResourceId.OIL to 10.0), outputs = mapOf(ResourceId.FUEL to 10.0)),
            consumer("power", owned = 10, inputs = mapOf(ResourceId.FUEL to 5.0), outputs = mapOf(ResourceId.ENERGY to 20.0)),
        )

        val (allocation, flow) = solve(producers, chain)

        assertEquals("refineries run at half", 0.5, allocation.utilization[0], 1e-9)
        // 50 Fuel/s made, 90% claimable = 45, against a demand of 50.
        assertEquals("power stations follow", 0.9, allocation.utilization[1], 1e-9)
        assertTrue(flow.netPerS[ResourceId.ENERGY].gt(GameDecimal.ZERO))
    }

    @Test
    fun `a loop cannot amplify supply beyond what the buildings are rated for`() {
        // A deliberately circular economy: Energy makes Metals, Metals make
        // Energy, each at a large gain. Utilization is capped at 1, so total
        // supply is still bounded by what the player actually bought — the
        // guarantee that makes production loops safe.
        val producers = supply(ResourceId.ENERGY to 10.0)
        val loop = listOf(
            consumer("mill", inputs = mapOf(ResourceId.ENERGY to 1.0), outputs = mapOf(ResourceId.STEEL to 1_000.0)),
            consumer("plant", inputs = mapOf(ResourceId.STEEL to 1.0), outputs = mapOf(ResourceId.ENERGY to 1_000.0)),
        )

        val (allocation, flow) = solve(producers, loop)

        for (utilization in allocation.utilization) {
            assertTrue("utilization must never exceed 1, got $utilization", utilization <= 1.0 + 1e-12)
        }
        val ratedCeiling = 10.0 + 1_000.0
        assertTrue(
            "energy supply ${flow.supplyPerS[ResourceId.ENERGY]} exceeded the rated ceiling $ratedCeiling",
            flow.supplyPerS[ResourceId.ENERGY].lte(gd(ratedCeiling)),
        )
    }

    @Test
    fun `processors never claim the last tenth of a resource`() {
        // The anti-softlock rule: whatever the demand, a resource always
        // accumulates, so the player can always buy their way out of a
        // bottleneck.
        val producers = supply(ResourceId.OIL to 100.0)
        val ravenous = listOf(consumer("refinery", owned = 10_000, inputs = mapOf(ResourceId.OIL to 100.0), outputs = mapOf(ResourceId.FUEL to 1.0)))

        val (_, flow) = solve(producers, ravenous)

        assertEquals(10.0, flow.netPerS[ResourceId.OIL].toDouble(), 1e-9)
    }

    @Test
    fun `an economy with no consumers is exactly its producers`() {
        val producers = supply(ResourceId.OIL to 17.0, ResourceId.ENERGY to 4.0)

        val (_, flow) = solve(producers, emptyList())

        assertEquals(17.0, flow.netPerS[ResourceId.OIL].toDouble(), 1e-12)
        assertEquals(4.0, flow.netPerS[ResourceId.ENERGY].toDouble(), 1e-12)
        assertTrue(flow.consumers.isEmpty())
    }

    @Test
    fun `utilization survives numbers far beyond a Double`() {
        val producers = supply(ResourceId.OIL to 1e300)
        val huge = listOf(consumer("refinery", owned = 1, inputs = mapOf(ResourceId.OIL to 1e301), outputs = mapOf(ResourceId.FUEL to 1e299)))

        val (allocation, flow) = solve(producers, huge)

        assertEquals(0.09, allocation.utilization[0], 1e-9)
        assertTrue(flow.netPerS[ResourceId.OIL].gt(GameDecimal.ZERO))
    }

    // --- Through the real engine --------------------------------------------

    @Test
    fun `the engine reports a processor's bottleneck through the production rates`() {
        val owned = mapOf(
            "natural_fire" to 50,
            "oil_drilling" to 2,
            "refinery_complex" to 40,
        )

        val rates = computeProductionRates(owned, EffectiveMultipliers.IDENTITY)
        val refinery = rates.consumerFlow("refinery_complex")

        assertTrue("the refinery should be reported", refinery != null)
        assertTrue("40 refineries on 2 derricks must be starved", refinery!!.utilization < 1.0)
        assertEquals(ResourceId.OIL, refinery.limitingResource)
        assertTrue("but it should still be producing something", rates.resourcePerS[ResourceId.FUEL].gt(GameDecimal.ZERO))
    }

    @Test
    fun `emissions fall with utilization rather than being charged in full`() {
        val starved = computeProductionRates(
            mapOf("natural_fire" to 10, "oil_drilling" to 1, "refinery_complex" to 50),
            EffectiveMultipliers.IDENTITY,
        )
        val fed = computeProductionRates(
            mapOf("natural_fire" to 10, "oil_drilling" to 500, "refinery_complex" to 50),
            EffectiveMultipliers.IDENTITY,
        )

        assertTrue(
            "a starved refinery must emit less than a supplied one",
            starved.gasGrossKgPerS[com.earthgame.idle.domain.model.GasId.CO2]
                .lt(fed.gasGrossKgPerS[com.earthgame.idle.domain.model.GasId.CO2]),
        )
    }
}
