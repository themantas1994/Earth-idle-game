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

    private companion object {
        const val GOOGLE_SAMPLE_PUBLISHER = "ca-app-pub-3940256099942544"
    }
}
