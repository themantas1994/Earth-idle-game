package com.earthgame.idle.domain.model

import com.earthgame.idle.domain.engine.GameDecimal

/**
 * Immutable per-gas and per-resource containers, backed by flat arrays indexed
 * by enum ordinal.
 *
 * The reference implementation uses `Record<GasId, Decimal>` maps, which is the
 * natural shape in TypeScript. Here it would mean a hash lookup for every gas
 * of every owned generator on every tick — the hot loop of the whole game —
 * plus a fresh map allocation per step. An array of six (or six) slots costs a
 * bounds check, and [Builder] lets one tick mutate a scratch copy and freeze it
 * once instead of rebuilding a map per gas.
 */
class GasAmounts private constructor(private val values: Array<GameDecimal>) {

    operator fun get(id: GasId): GameDecimal = values[id.ordinal]

    fun with(id: GasId, value: GameDecimal): GasAmounts {
        val copy = values.copyOf()
        copy[id.ordinal] = value
        return GasAmounts(copy)
    }

    fun plus(other: GasAmounts): GasAmounts =
        GasAmounts(Array(SIZE) { values[it] + other.values[it] })

    fun minus(other: GasAmounts): GasAmounts =
        GasAmounts(Array(SIZE) { values[it] - other.values[it] })

    fun map(transform: (GasId, GameDecimal) -> GameDecimal): GasAmounts =
        GasAmounts(Array(SIZE) { transform(GasId.entries[it], values[it]) })

    /** Sum across every gas — "total greenhouse gas", used by prestige and the stats screen. */
    fun sum(): GameDecimal {
        var total = GameDecimal.ZERO
        for (value in values) total += value
        return total
    }

    fun isAllZero(): Boolean = values.all { it.isZero() }

    fun toBuilder(): Builder = Builder(values.copyOf())

    fun asMap(): Map<GasId, GameDecimal> = GasId.entries.associateWith { values[it.ordinal] }

    override fun equals(other: Any?): Boolean =
        this === other || (other is GasAmounts && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String =
        GasId.entries.joinToString(prefix = "GasAmounts(", postfix = ")") { "${it.id}=${values[it.ordinal]}" }

    class Builder internal constructor(private val values: Array<GameDecimal>) {
        operator fun get(id: GasId): GameDecimal = values[id.ordinal]
        operator fun set(id: GasId, value: GameDecimal) {
            values[id.ordinal] = value
        }

        fun add(id: GasId, value: GameDecimal) {
            values[id.ordinal] = values[id.ordinal] + value
        }

        fun build(): GasAmounts = GasAmounts(values.copyOf())
    }

    companion object {
        private val SIZE = GasId.entries.size
        val ZERO = GasAmounts(Array(SIZE) { GameDecimal.ZERO })

        fun builder(): Builder = Builder(Array(SIZE) { GameDecimal.ZERO })

        fun of(values: Map<GasId, GameDecimal>): GasAmounts =
            GasAmounts(Array(SIZE) { values[GasId.entries[it]] ?: GameDecimal.ZERO })

        inline fun build(block: (Builder) -> Unit): GasAmounts = builder().also(block).build()
    }
}

/** Per-gas `Double` values — radiative forcing contributions and per-gas multipliers. */
class GasDoubles private constructor(private val values: DoubleArray) {

    operator fun get(id: GasId): Double = values[id.ordinal]

    fun with(id: GasId, value: Double): GasDoubles {
        val copy = values.copyOf()
        copy[id.ordinal] = value
        return GasDoubles(copy)
    }

    fun sum(): Double = values.sum()

    fun toBuilder(): Builder = Builder(values.copyOf())

    fun asMap(): Map<GasId, Double> = GasId.entries.associateWith { values[it.ordinal] }

    override fun equals(other: Any?): Boolean =
        this === other || (other is GasDoubles && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    class Builder internal constructor(private val values: DoubleArray) {
        operator fun get(id: GasId): Double = values[id.ordinal]
        operator fun set(id: GasId, value: Double) {
            values[id.ordinal] = value
        }

        fun multiply(id: GasId, factor: Double) {
            values[id.ordinal] *= factor
        }

        fun build(): GasDoubles = GasDoubles(values.copyOf())
    }

    companion object {
        private val SIZE = GasId.entries.size
        val ZERO = GasDoubles(DoubleArray(SIZE))
        val ONES = GasDoubles(DoubleArray(SIZE) { 1.0 })

        fun builder(initial: Double = 0.0): Builder = Builder(DoubleArray(SIZE) { initial })

        fun of(values: Map<GasId, Double>, default: Double = 0.0): GasDoubles =
            GasDoubles(DoubleArray(SIZE) { values[GasId.entries[it]] ?: default })

        inline fun build(initial: Double = 0.0, block: (Builder) -> Unit): GasDoubles =
            builder(initial).also(block).build()
    }
}

/** Immutable per-resource balances and rates. */
class ResourceAmounts private constructor(private val values: Array<GameDecimal>) {

    operator fun get(id: ResourceId): GameDecimal = values[id.ordinal]

    fun with(id: ResourceId, value: GameDecimal): ResourceAmounts {
        val copy = values.copyOf()
        copy[id.ordinal] = value
        return ResourceAmounts(copy)
    }

    fun plus(other: ResourceAmounts): ResourceAmounts =
        ResourceAmounts(Array(SIZE) { values[it] + other.values[it] })

    fun minus(other: ResourceAmounts): ResourceAmounts =
        ResourceAmounts(Array(SIZE) { values[it] - other.values[it] })

    fun map(transform: (ResourceId, GameDecimal) -> GameDecimal): ResourceAmounts =
        ResourceAmounts(Array(SIZE) { transform(ResourceId.entries[it], values[it]) })

    fun any(predicate: (GameDecimal) -> Boolean): Boolean = values.any(predicate)

    fun toBuilder(): Builder = Builder(values.copyOf())

    fun asMap(): Map<ResourceId, GameDecimal> = ResourceId.entries.associateWith { values[it.ordinal] }

    override fun equals(other: Any?): Boolean =
        this === other || (other is ResourceAmounts && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String =
        ResourceId.entries.joinToString(prefix = "ResourceAmounts(", postfix = ")") { "${it.id}=${values[it.ordinal]}" }

    class Builder internal constructor(private val values: Array<GameDecimal>) {
        operator fun get(id: ResourceId): GameDecimal = values[id.ordinal]
        operator fun set(id: ResourceId, value: GameDecimal) {
            values[id.ordinal] = value
        }

        fun add(id: ResourceId, value: GameDecimal) {
            values[id.ordinal] = values[id.ordinal] + value
        }

        fun build(): ResourceAmounts = ResourceAmounts(values.copyOf())
    }

    companion object {
        private val SIZE = ResourceId.entries.size
        val ZERO = ResourceAmounts(Array(SIZE) { GameDecimal.ZERO })

        fun builder(): Builder = Builder(Array(SIZE) { GameDecimal.ZERO })

        fun of(values: Map<ResourceId, GameDecimal>): ResourceAmounts =
            ResourceAmounts(Array(SIZE) { values[ResourceId.entries[it]] ?: GameDecimal.ZERO })

        inline fun build(block: (Builder) -> Unit): ResourceAmounts = builder().also(block).build()
    }
}
