package com.earthgame.idle.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped AdMob identifiers.
 *
 * A unit test resolves resources from the *debug* variant, so it cannot read
 * the production values through `R.string` — and those are exactly the values
 * that must not drift, be overwritten with a test unit, or be reintroduced
 * somewhere else. This test therefore reads the two `ads.xml` files from source,
 * which is also what makes it a meaningful check: it is asserting about what is
 * committed rather than about what the test harness happened to merge.
 *
 * The project directory comes from a system property set in
 * `app/build.gradle.kts`, because a unit test's working directory is not
 * guaranteed.
 */
class ProductionAdConfigTest {

    private val projectDir: File = File(
        requireNotNull(System.getProperty("earth.projectDir")) {
            "earth.projectDir is not set — see testOptions in app/build.gradle.kts"
        },
    )

    private val production = File(projectDir, "src/main/res/values/ads.xml").readText()
    private val debug = File(projectDir, "src/debug/res/values/ads.xml").readText()

    private fun String.stringResource(name: String): String {
        val match = Regex("""<string name="$name"[^>]*>([^<]*)</string>""").find(this)
        return requireNotNull(match) { "No <string name=\"$name\"> in this resource file" }
            .groupValues[1]
    }

    @Test
    fun releaseShipsTheProductionAdMobIdentifiers() {
        assertEquals(
            "The production AdMob app ID changed. It is the account's identity in every " +
                "shipped build and in the Play listing — change it only deliberately.",
            "ca-app-pub-6872627319793193~7208922044",
            production.stringResource("admob_application_id"),
        )
        assertEquals(
            "The production banner unit changed.",
            "ca-app-pub-6872627319793193/5213314092",
            production.stringResource("admob_banner_unit_id"),
        )
    }

    @Test
    fun releaseNeverShipsGooglesSampleIdentifiers() {
        // Google's sample publisher ID. Shipping it means the live account earns
        // nothing and the build is, to AdMob, somebody else's app.
        assertTrue(
            "A Google sample AdMob identifier is present in the release resources.",
            !production.contains(GOOGLE_SAMPLE_PUBLISHER),
        )
    }

    @Test
    fun debugBuildsOverrideBothIdentifiersWithGooglesTestUnits() {
        // AdMob treats a developer's own impressions on their live unit as
        // invalid traffic, and repeat offences suspend the account. The overlay
        // is what makes that impossible rather than merely discouraged.
        assertEquals(
            "ca-app-pub-3940256099942544~3347511713",
            debug.stringResource("admob_application_id"),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/6300978111",
            debug.stringResource("admob_banner_unit_id"),
        )
        assertNotEquals(
            production.stringResource("admob_application_id"),
            debug.stringResource("admob_application_id"),
        )
        assertNotEquals(
            production.stringResource("admob_banner_unit_id"),
            debug.stringResource("admob_banner_unit_id"),
        )
    }

    @Test
    fun noReleaseOverlayCanShadowTheProductionIdentifiers() {
        // Every other test here reads src/main, because that is where the
        // production values live and where the release variant takes them from.
        // A `src/release/res` overlay would silently win over all of it at
        // resource-merge time, and every assertion above would still pass while
        // the shipped APK carried something else entirely. The design is that no
        // such overlay exists — so assert that, rather than assert around it.
        val releaseOverlay = File(projectDir, "src/release/res")
        assertTrue(
            "src/release/res exists. A release resource overlay would override " +
                "src/main/res/values/ads.xml in the shipped build, and nothing else " +
                "in this test suite would notice. Production identifiers belong in " +
                "src/main and nowhere else — see docs/wiki/Advertising.md.",
            !releaseOverlay.exists(),
        )
    }

    @Test
    fun releaseRegistersNoUmpTestDevice() {
        // A test device hashed ID in a release build forces UMP's debug
        // geography for whoever holds that device, which is not a decision a
        // shipped build gets to make.
        assertEquals("", production.stringResource("ump_test_device_hashed_id"))
    }

    @Test
    fun theAdMobAppIdIsDeclaredExactlyOnceInEachVariant() {
        // Two APPLICATION_ID meta-data entries make the merged manifest
        // ambiguous, and the Mobile Ads SDK crashes at startup on a bad one.
        val manifest = File(projectDir, "src/main/AndroidManifest.xml").readText()
        assertEquals(
            1,
            Regex("com\\.google\\.android\\.gms\\.ads\\.APPLICATION_ID").findAll(manifest).count(),
        )
        assertEquals(
            1,
            Regex("""<string name="admob_application_id"""").findAll(production).count(),
        )
        assertEquals(
            1,
            Regex("""<string name="admob_application_id"""").findAll(debug).count(),
        )
    }

    /**
     * Every source set whose contents reach an installable APK.
     *
     * Test sources are deliberately excluded: they are not shipped, and this
     * file itself has to name the very identifiers the two scans below look
     * for.
     */
    private fun shippedSources(vararg extensions: String): List<File> =
        listOf("main", "debug", "release")
            .map { File(projectDir, "src/$it") }
            .filter { it.isDirectory }
            .flatMap { sourceSet ->
                sourceSet.walkTopDown().filter { it.isFile && it.extension in extensions }
            }

    @Test
    fun noShippedSourceFileHardcodesAnAdMobIdentifier() {
        // The overlay only isolates the variants because the identifiers exist
        // in exactly one kind of place. A literal `ca-app-pub-…` in Kotlin — the
        // runtime branch H3 was about — resolves the same in every variant and
        // silently defeats the whole design, while every assertion above still
        // passes. So assert that code never carries one.
        val offenders = shippedSources("kt", "java")
            .filter { it.readText().contains(ADMOB_UNIT_PREFIX) }
            .map { it.relativeTo(projectDir).path }
        assertTrue(
            "AdMob identifiers must live only in res/values/ads.xml, never in code — " +
                "a literal in a source file is variant-blind. Found in: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun googlesSampleIdentifiersAppearOnlyInTheDebugOverlay() {
        // The mirror of the test above: a sample unit that leaks out of
        // src/debug is a release build earning nothing for the live account.
        val debugSourceSet = File(projectDir, "src/debug")
        val offenders = shippedSources("kt", "java", "xml")
            .filterNot { it.startsWith(debugSourceSet) }
            .filter { it.readText().contains(GOOGLE_SAMPLE_PUBLISHER) }
            .map { it.relativeTo(projectDir).path }
        assertTrue(
            "Google's sample AdMob publisher ID belongs in src/debug and nowhere else. " +
                "Found in: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theReleaseBuildTypeMinifiesShrinksAndStaysNonDebuggable() {
        // Every test in this repository runs against the *debug* variant, so
        // nothing else here can observe the release build type at all. These
        // three settings are what make the shipped APK a production artifact
        // rather than a debug one under a different name, and losing any of
        // them is a silent regression: the build still succeeds.
        val buildScript = File(projectDir, "build.gradle.kts").readText()
        val releaseBlock = requireNotNull(
            Regex("""\n        release \{(.*?)\n        \}""", RegexOption.DOT_MATCHES_ALL)
                .find(buildScript),
        ) { "No `release { }` build type in app/build.gradle.kts" }.groupValues[1]

        assertTrue(
            "The release build type must keep R8 enabled: an unminified release APK ships " +
                "the whole unused classpath and every original name.",
            releaseBlock.contains("isMinifyEnabled = true"),
        )
        assertTrue(
            "The release build type must keep resource shrinking enabled.",
            releaseBlock.contains("isShrinkResources = true"),
        )
        assertTrue(
            "The release build type must not be debuggable. A debuggable release APK lets " +
                "anyone attach a debugger to the shipped app.",
            !releaseBlock.contains("isDebuggable = true"),
        )
        assertTrue(
            "The release build type must keep the project's ProGuard rules: without them R8 " +
                "strips the kotlinx.serialization hooks the save format is read through.",
            releaseBlock.contains("proguard-rules.pro"),
        )
    }

    @Test
    fun theDebugVariantInstallsUnderItsOwnApplicationId() {
        // `.debug` is what lets a developer build sit next to the released one
        // on the same device, and it is also why a debug build can never
        // overwrite a player's save or be mistaken for the release APK.
        val buildScript = File(projectDir, "build.gradle.kts").readText()
        assertTrue(
            "The debug build type must keep applicationIdSuffix = \".debug\".",
            buildScript.contains("""applicationIdSuffix = ".debug""""),
        )
    }

    private companion object {
        const val GOOGLE_SAMPLE_PUBLISHER = "ca-app-pub-3940256099942544"

        /** The shared prefix of every AdMob app ID and ad unit ID. */
        const val ADMOB_UNIT_PREFIX = "ca-app-pub-"
    }
}
