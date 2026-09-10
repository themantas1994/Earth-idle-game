# GameDecimal

[← Documentation home](Home.md)

`domain/engine/GameDecimal.kt`. The arbitrary-scale number type every gameplay value uses.

## Why `Double` is not enough

An IEEE-754 double overflows to `Infinity` at about **1.8 × 10³⁰⁸**. A finished EARTH run
passes **10⁴⁰⁰** in greenhouse-gas totals, and the [prestige payout](Prestige-System.md)
multiplies from there. A double would silently become `Infinity`, at which point every
comparison, every price and every habitability factor turns to `NaN` and the run is over in a
way no error message explains.

The genre's standard answer, and this one: keep the number in scientific form and let the
*exponent* be the double.

```
value = sign × mantissa × 10^exponent
        sign     ∈ {-1, 0, 1}
        mantissa ∈ [1, 10)     (a Double: ~17 significant digits)
        exponent ∈ ℝ           (a Double: so |exponent| up to ~1.8e308)
```

The representable magnitude is therefore about **10^(1.8 × 10³⁰⁸)** — unreachable. The trade is
that precision stays fixed at a double's ~17 significant digits, which is far more than a game
needs: nobody notices the 18th digit of their methane stockpile.

This is a deliberate, literal port of the reference implementation's `bignum.ts`, itself modelled
on the `break_infinity.js` approach — same branch order, same significance cutoff, same
log-space exponentiation — because the whole economy's numbers are defined by it. See
[Reference parity](Reference-Parity.md).

## Normalization

Every value in circulation has its mantissa in `[1, 10)`. Every arithmetic result goes through
`normalize`, which shifts the difference into the exponent:

```kotlin
val shift = floor(log10(abs(mantissa)))
var m = mantissa / 10.0.pow(shift)
var e = exponent + shift
if (m >= 10.0) { m /= 10.0; e += 1.0 }        // log10 rounding can overshoot
else if (m < 1.0) { m *= 10.0; e -= 1.0 }
```

`GameDecimalEdgeCaseTest.every arithmetic result is normalized` checks the invariant across
every pairing of 18 operands spanning 1e-4000 to 1e4000.

> [!WARNING]
> The primary constructor is public and does **not** normalize:
> `GameDecimal(1, 55.0, 0.0)` is an un-normalized value. Always build through `GameDecimal.of`,
> `gd(...)`, `GameDecimal.fromTriple`, or arithmetic. `fromTriple` exists specifically so a
> hand-edited or older-format save cannot introduce one — it re-normalizes on the way in.

## Zero, sign and NaN

Zero has exactly one representation: `sign = 0, mantissa = 0, exponent = 0`. The constructor
forces `sign = 0` whenever the mantissa is zero, whatever sign was requested, so there is no
negative zero and no `-0 != 0` surprise. `gd(Double.NaN)` is zero too.

```kotlin
gd(0.0) == gd(-0.0) == gd("0e500") == (gd(5.0) - gd(5.0)) == GameDecimal.ZERO   // all true
```

**No operation returns a NaN mantissa.** Overflow and division by zero both produce a *signed
infinity* (`mantissa = exponent = +∞`, sign preserved), which `isFinite()` reports and which
comparisons handle. `1 / 0` is `+∞`; `-1 / 0` is `-∞`.

## Operations

| | |
| :-- | :-- |
| `+ - * /` | Operator overloads, plus `Double`/`Int` variants |
| `pow(exp)`, `sqrt()` | Log-space: `(m·10^e)^p = 10^(p·(log10 m + e))` — which is what keeps `gd("1e100").pow(1000.0)` finite at 1e100000 |
| `log10()`, `ln()` | Return a plain `Double`; `-∞` for zero |
| `cmp`, `eq`, `lt`, `lte`, `gt`, `gte` | A total order across signs and magnitudes |
| `max`, `min`, `clampMin`, `clamp` | |
| `abs()`, `negate()`, `isZero()`, `isFinite()` | |
| `toDouble()`, `toExponential(digits)`, `toTriple()` | |

### Addition drops what it cannot represent

```kotlin
private const val MAX_SIGNIFICANT_DIGITS = 17.0
// ...
if (expDiff > MAX_SIGNIFICANT_DIGITS) return big
```

Beyond ~17 orders of magnitude the smaller term cannot affect a double mantissa at all, so it is
dropped rather than rounded away. This is what makes `gd("1e300") + gd(1.0)` free — and exactly
equal to `gd("1e300")`, which is the correct answer at that precision.

### `toDouble()` saturates

```kotlin
if (exponent > 308) return sign * Double.POSITIVE_INFINITY
```

It never wraps or produces garbage; above the double range it is `±Infinity`, below it is `0.0`.
It is for **UI-only** maths and is documented as best-effort.

## Formatting never mutates

Every formatter takes a `GameDecimal` and returns a `String`. Nothing normalizes in place,
rounds in place, or writes back. `GameDecimalEdgeCaseTest.formatting never mutates the value it
formats` asserts the triple is byte-identical after running every notation at every precision
over every operand — because formatting runs on every frame against live gameplay values, and a
formatter with a side effect would quietly corrupt the economy.

`domain/formatting/NumberFormatting.kt` provides four notations, chosen in Settings:

| Mode | `1.2e6` renders as |
| :-- | :-- |
| `COMPACT` | `1.2M` |
| `SCIENTIFIC` | `1.20e+6` |
| `ENGINEERING` | `1.20e+6` (exponent always a multiple of 3) |
| `FULL` | `1,200,000` (falls back to scientific above 1e100) |

Compact uses short-scale names to `Vg` (1e63), then a two-letter alphabetic fallback: `AA`, `AB`,
`AC`, … The fallback deliberately starts at **two** letters. A one-letter fallback printed 1e69
as `1B` and 1e96 as `1K`, colliding with billions and thousands — magnitudes a single run passes
through on the way there, so the player could not tell 1e9 from 1e69. Every named suffix is a
single letter or mixed case, so no all-caps pair can collide with one.
`FormattingParityTest` asserts no suffix repeats across 0…1e900.

### Two `toFixed` rules, and why both exist

The reference implementation's output was written against ECMAScript, which has *two* different
rounding rules that a naive `String.format` gets wrong in opposite directions:

- `jsToFixed` mirrors `Number.prototype.toFixed`: it rounds the **exact binary value**
  half-away-from-zero, so `(1.005).toFixed(2)` is `"1.00"` because the nearest double to 1.005 is
  slightly below it. Implemented via `BigDecimal`'s exact constructor, which also removes any
  `Locale` dependence.
- `groupWithCommas` mirrors `toLocaleString`: it rounds the **shortest decimal that round-trips**
  to the double. Formatting `1e24` the other way prints `999,999,999,999,999,983,222,784`, which
  is technically the stored value and visibly wrong to a player.

## Serialization

The save stores the exact internal triple:

```json
{ "__decimal": [1, 4.82, 137] }
```

Not a number (which would round-trip through a double and cap the save at 1.8e308, losing a
finished run's totals outright) and not a formatted string (which would lose mantissa
precision). The triple round-trips bit-for-bit. See [Save system](Save-System.md).

`SaveSerialization` guards the edges: an infinite mantissa or exponent is written as
`Double.MAX_VALUE` rather than `null` (which would revive as zero), and on read anything
non-finite decodes to zero rather than poisoning the run.

## Common mistakes

| Don't | Do | Why |
| :-- | :-- | :-- |
| `value.toDouble() * 2` | `value * 2` | A late-game value is `Infinity` as a double. |
| `if (a.toDouble() > b.toDouble())` | `if (a.gt(b))` | Both saturate to `Infinity` and compare equal. |
| `GameDecimal(1, 55.0, 0.0)` | `gd(55.0)` | The constructor does not normalize. |
| Storing a rate as `Double` | Store as `GameDecimal` | Late-game production rates exceed the double range too. |
| `a == b` on values from different code paths | `a.eq(b)` or compare with a tolerance | Different normalization routes can differ by an ulp in the mantissa — `gd(-0.0042)` and `gd("-4.2e-3")` are one such pair. |
| `sum += x.toDouble()` in a loop | Accumulate as `GameDecimal` | The whole point. |

The last two are the ones that actually happen. `GameDecimalEdgeCaseTest` documents the
normalization one directly.

## Testing

| Test | Covers |
| :-- | :-- |
| `GameDecimalParityTest` | Agreement with the reference: 24 operands from 1e-400 to 1e1000, all 576 binary pairs, 104 `pow` cases, the save triple |
| `GameDecimalEdgeCaseTest` | The invariants regardless of the reference: normalization, canonical zero, total ordering, saturation, formatting purity, no NaN anywhere |

---

**Next:** [Simulation](Simulation.md) · [Save system](Save-System.md) · [Reference parity](Reference-Parity.md)
