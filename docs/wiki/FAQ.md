# Player FAQ

[← Documentation home](Home.md) · [← Repository README](../../README.md)

No programming knowledge needed. For developer questions, start at
[the documentation home](Home.md).

## Getting started

### What am I actually doing?

Building a civilization, and heating the planet it stands on until it cannot hold people any more.
Then doing it again, faster.

Concretely: research technologies, buy generators, watch the atmosphere fill up, collapse, reset,
and come back stronger. The loop takes thirty seconds to learn and several days to finish.

### There is no tap button. How do I get resources?

You already are. Your Natural Fire produces Energy every second whether the app is open or not —
that is what an idle game is. Your job is to **choose what to build next**, not to grind.

Tapping existed in an earlier version and was removed. It made the first ten minutes about wrist
stamina and was worthless for the rest of the run.

### What should I buy first?

Whatever the **Objective** card on Home says. It names the next thing you can afford and the tab it
is sold on — generators live on **Production**, everything else on **Technology**.

### Why can't I afford anything?

Wait. Every Buy button shows a countdown, and because prices in this game never move, that countdown
is a promise: wait that long and the thing is yours at exactly the number on the button.

### It says "Reset available once Earth collapses". When is that?

When habitability reaches zero. It takes a few days of real time on your first Earth, and much less
after that. There is nothing to do to hurry it except produce more.

## Resources and building

### What are the six resources?

**Energy** is the general currency — most things are built with it. **Research** unlocks tree nodes.
**Coal**, **Oil**, **Metals** and **Concrete** are materials that specific branches need. You will not
see one until something you own produces it.

### What is the difference between the Technology and Production tabs?

**Technology** sells one-time things: unlocks that open up new parts of the tree, permanent
multipliers, and the occasional either/or decision. **Production** sells generators — the buildings
that actually produce. You buy those over and over.

### Do prices go up?

**Only from your own purchases of that exact building.** Buying your eleventh coal mine makes the
twelfth coal mine dearer. It does not make anything else dearer, ever. Nothing about how large your
civilization is affects any price.

That is unusual for the genre and deliberate: a price you were quoted is a price you will pay.

### What does "every 10th copy doubles output" mean?

Each generator has its own little progress bar. Every tenth copy you own of it doubles **that
building's** entire output, permanently, for the rest of the run — and it immediately starts
counting toward the next one.

It is worth going deep on a favourite building. It is not worth doing *instead* of unlocking new
ones: across ten copies the price roughly quadruples while the bonus only doubles, so branching out
still wins in the long run.

### What is that ×2 / ×4 / ×32 next to a building?

The ownership bonus it has earned so far. ×32 means you own at least 50 of it.

### I picked Coal Industrialization and Nuclear Industrialization vanished.

That is the one either/or decision in the game, and it is per-Earth. Coal is cheap and filthy (×10
CO₂); nuclear is expensive and clean (×5 Energy). You will get to choose again on your next Earth.

## The planet

### What is "habitability"?

How liveable Earth still is, from 100% down to 0%. It combines five things — temperature, ocean
acidity, sea level, agriculture and biodiversity — by **multiplying** them.

Multiplying matters: if any single one of them hits zero, habitability is zero, even if the other
four look fine. One broken system can end a civilization.

### Why is my habitability dropping when the temperature looks fine?

Check the Atmosphere tab. Sea level accumulates for as long as it is warm at all, ocean pH drifts
with CO₂, and agriculture and biodiversity fall off a cliff past +3 °C and +4 °C. Something other
than the headline number is going.

### What happens when Earth becomes uninhabitable?

The run ends. You get a summary — peak temperature, peak CO₂, how long it lasted, what it produced —
and you bank **Earth Points**. Then you press Reset and start the next Earth with your permanent
upgrades intact.

Nothing is lost. That *is* the progression.

### Is this a real climate simulation?

**No.** The relationships are real ones — CO₂ forcing really is logarithmic, gases really do have
wildly different atmospheric lifetimes, warming really does weaken natural carbon sinks — but the
numbers are tuned for a satisfying multi-day game, not for accuracy. CO₂'s mass-per-ppm is scaled
down by about four; sea level rises at a rate that would be absurd in reality.

If it makes you curious about the real thing, that is the best outcome it can hope for. Do not cite
it.

### Why is there water vapour if I can't produce it?

Because it is a **feedback**, not something you emit. It rises on its own as the other gases warm
the planet, and then adds warming of its own. You cannot build anything that makes it and you are
not scored on it — it is there because a climate model without it would behave wrongly.

## Prestige and resets

### What are Earth Points for?

Eleven permanent upgrades that apply to every future Earth: more production, cheaper technology, a
longer offline cap, and generators and resources you start each new run already holding.

### How is the payout calculated?

Four things: how much greenhouse gas you produced in total, how hard you pushed the atmosphere, how
far up the tech tree you got, and — the big one — **how fast you did it**.

Speed matters most because every run ends in the same place. If the score only looked at the ending,
a stronger civilization would earn *less*, because a stronger one kills the planet sooner and emits
less in total on the way. Speed is what your upgrades actually buy, so speed is what gets scored.

Halving your run time roughly quadruples the payout, up to a ×64 cap.

### Should I reset as soon as I can?

Usually yes. A completed run pays for upgrades that make the next one much faster, and the speed
bonus compounds that. There is no reason to sit on a dead planet.

### Do I lose my achievements or challenge completions?

No. Achievements, challenge completions, Earth Points, upgrades, lifetime statistics, settings and
tutorial progress all survive every reset — forever. Only the run itself starts over.

### Why does my planet die faster every run?

Because you are better at killing it. Your upgrades multiply production, and production is what
heats the atmosphere. That is intended.

## Offline progress

### Does the game run when it is closed?

Yes, completely. Close it, come back later, and everything your civilization produced while you were
away is waiting, with a summary of what changed.

### How much time is banked?

**12 hours** by default — so a night's sleep is fully covered. Two prestige upgrades extend it, up to
**8 days**.

### Does it drain my battery?

No. Nothing runs in the background at all — no service, no scheduled work, no wake locks. The game
notes the time you left and works out the answer in one calculation when you come back. It is
mathematically identical to having watched it happen.

### Do I get purchases while I am away?

No — production, accumulation, and the climate consequences. Never the technologies you would have
bought with it. Spending the pile is the reason to come back.

### Do random events happen offline?

No. An event is a short swing you could have reacted to; handing you a few for a night asleep would
just be noise.

### I was away for a week. Why did I only get 12 hours?

That is the cap. It is per absence, though — two eight-hour absences bank two eight-hour absences.
Buy Extended Endurance and Automated Industry if you check in less often.

### Can I turn offline progress off?

Yes, in Settings. Note that the clock still advances while it is off, so you cannot switch it off,
disappear for a month, and switch it back on to bank the lot.

## Numbers

### What do K, M, B, T, Qa … AA, AB mean?

Magnitudes. K is thousand, M million, B billion, T trillion, then Qa, Qi, Sx, Sp, Oc, No, Dc … up to
Vg at 10⁶³. Past that it switches to two-letter names — AA, AB, AC — which keep going forever.

They are always **two** letters past Vg on purpose, so a huge number can never be mistaken for a
small one with the same letter.

### How big do the numbers get?

Bigger than a computer's ordinary numbers can hold. A finished run passes 10⁴⁰⁰ — a 1 with four
hundred zeroes — and prestige multiplies from there. The game stores them specially so they never
break.

### Can I see plain numbers instead?

Settings → Number Format. Four options: **Compact** (`1.2M`), **Scientific** (`1.20e+6`),
**Engineering** (exponents always multiples of three), and **Full** (`1,200,000`, which switches to
scientific once it gets silly).

## Practical

### Is the game free? Are there in-app purchases?

Free, and none. There is one banner ad at the bottom of the screen and nothing in the game is locked
behind it.

### Does it work without internet?

Entirely. The whole simulation runs on your phone. The only thing that wants a connection is the ad
banner, and the game does not care whether it loads.

### Where is my save? Can I lose it?

On your device, in the app's private storage. The game keeps **two** copies: every save rotates the
previous one into a backup slot in the same atomic write, so a phone killed mid-save still has a
good one.

If the main save is ever unreadable, the game loads the backup and tells you it did. If the file
itself is damaged beyond that, it starts fresh and says so rather than crashing.

Uninstalling deletes it. If Android's Auto Backup is switched on, your save is included and will come
back on a reinstall.

### Can I move my save to another phone?

Only through Android's own backup/transfer, if you have it switched on. There is no export, no cloud
save and no account — nothing is stored anywhere but on your device.

### Does the game collect any data about me?

No analytics, no telemetry, no crash reporting, no account. The only thing that talks to a network is
the ad banner, which uses your advertising ID like every ad does. In the EEA and UK you get a consent
form first, and you can reopen it from Settings.

### It runs slowly / drains battery while open.

Try turning on **Reduced Animations** in Settings. If the problem persists,
[open an issue](https://github.com/themantas1994/Earth-idle-game/issues) with your device model and
Android version — the game is designed to be a few milliseconds of work four times a second, so that
would be a bug worth knowing about.

### Where do I get it?

There is no published release yet. For now you build it yourself — see
[Getting started](../../README.md#getting-started); it is one command.

---

**Next:** [Glossary](Glossary.md) · [Troubleshooting](Troubleshooting.md) · [Repository README](../../README.md)
