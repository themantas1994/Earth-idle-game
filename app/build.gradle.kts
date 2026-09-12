import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing is configured entirely outside the repository — see
 * "Signing a release" in ANDROID.md. Gradle properties take precedence over
 * environment variables so CI can inject either. When nothing is configured
 * the release variant still assembles (unsigned), so a fresh clone is never
 * blocked on secrets.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use(::load)
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey)
        ?: providers.gradleProperty(propertyKey).orNull
        ?: providers.environmentVariable(envKey).orNull

val releaseStoreFile = signingValue("storeFile", "EARTH_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "EARTH_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "EARTH_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "EARTH_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

/**
 * The single source of truth for the shipped version.
 *
 * `earthVersionCode` is Play's identity for a build: it must increase by at
 * least one for every upload, ever, and can never be reused or reduced.
 * `earthVersionName` is what the player sees and follows semantic versioning.
 * The release artifact names are derived from the latter, so a build cannot be
 * filed under a version it does not carry. See docs/wiki/Release-Process.md.
 */
val earthVersionCode = 1
val earthVersionName = "1.0.0"

android {
    namespace = "com.earthgame.idle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.earthgame.idle"
        // 24 is the floor for the Compose/AndroidX stack this app is built on,
        // and covers ~98% of active devices.
        minSdk = 24
        targetSdk = 37
        versionCode = earthVersionCode
        versionName = earthVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // The engine formats numbers with java.time-free APIs, but desugaring
        // keeps the door open for library code that expects them on API 24.
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG is what the consent flow uses to decide whether to
        // force UMP's EEA debug geography, and BuildConfig.VERSION_NAME is what
        // the About screen shows. Which AdMob identifiers a build uses is not
        // decided here — that is a resource overlay, see res/values/ads.xml.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "META-INF/*.version",
            // kotlinx-coroutines ships this for its debug agent to read when one
            // is attached with `DebugProbes.install()`. Nothing here installs
            // one, and a development diagnostic has no business in a production
            // APK — so it is dropped from every variant rather than shipped
            // inert. Purely a packaging exclusion: no code path reads it.
            "DebugProbesKt.bin",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // `ProductionAdConfigTest` reads the release source set's
                // ads.xml directly — a unit test runs against the *debug*
                // resources, so the production identifiers are not otherwise
                // reachable from one, and they are exactly the thing that must
                // not drift unnoticed.
                it.systemProperty("earth.projectDir", projectDir.absolutePath)
            }
        }
    }

    lint {
        // A lint regression must fail the build rather than scroll past in a log.
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
}

/**
 * The domain layer deliberately has no Compose dependency, so its value types
 * cannot carry `@Immutable`. They are declared stable here instead — without it
 * every composable taking a `GameState`, a `GameDecimal` or a `Technology` is
 * unskippable, and the whole screen recomposes on every 250 ms tick. See
 * `app/compose-stability.conf`.
 */
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))

    // `./gradlew assembleDebug -Pearth.composeReports=true` writes the Compose
    // compiler's own skippability/stability reports to build/compose-reports,
    // which is how the list above was arrived at and how to check it still
    // earns its keep. Off by default: the reports cost build time.
    if (providers.gradleProperty("earth.composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // Nothing declares an @Preview yet; this is the annotation artifact that
    // pairs with the debug-only renderer below, kept so adding the first preview
    // is not also a build-file change.
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Brings androidx.window in transitively; the app has no direct use for it.
    implementation(libs.androidx.compose.material3.window.size)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    // Robolectric runs the Android-dependent tests — DataStore persistence and
    // the Compose UI — on the JVM. There is no hardware-accelerated emulator in
    // CI, and these are exactly the paths that a pure-JVM test cannot reach.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

/**
 * Ships `THIRD_PARTY_NOTICES.txt` inside the app.
 *
 * The file at the repository root is the single copy; it is generated into the
 * asset directory at build time rather than duplicated into `src/main/assets`,
 * because two copies of a legal notice drift and one of them is then wrong.
 * Assets rather than `res/raw` so resource shrinking has no opinion about it.
 */
abstract class BundleNoticesTask : DefaultTask() {

    @get:InputFile
    abstract val notices: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun bundle() {
        val target = outputDirectory.get().asFile
        target.mkdirs()
        notices.get().asFile.copyTo(target.resolve("third_party_notices.txt"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val bundleNotices = tasks.register<BundleNoticesTask>(
            "bundle${variant.name.replaceFirstChar(Char::titlecase)}Notices",
        ) {
            description = "Copies THIRD_PARTY_NOTICES.txt into the ${variant.name} assets."
            notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.txt"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleNotices,
            BundleNoticesTask::outputDirectory,
        )
    }
}

/**
 * Collects the release artifacts under `release/` with the names a release is
 * filed under.
 *
 * Two entry points, because the two distribution channels do not want the same
 * things:
 *
 * | Task | Produces | For |
 * | :-- | :-- | :-- |
 * | `packageReleaseApk` | APK, `mapping.txt`, `SHA256SUMS.txt` | **GitHub Releases** — an APK is the only artifact a player can install |
 * | `packageReleaseArtifacts` | the same, plus the AAB | Play uploads, which will not accept an APK for a new app |
 *
 * A GitHub release needs no bundle, and building one is a second full R8 pass,
 * so `bundleRelease` is deliberately not on the path of the task that cuts one.
 * Neither task is a prerequisite of the other; both are safe to run alone.
 *
 * Alongside the binaries each writes the two things a release is unreadable
 * without: the R8 `mapping.txt` for the exact build (a crash report from a
 * minified APK is noise without it) and a `SHA256SUMS.txt` so whoever downloads
 * an artifact can check they got the one that was built.
 *
 * `release/` is git-ignored: a 20 MB binary in a repository is a mistake that
 * is painful to undo, and the artifacts are reproducible from a tag. Each task
 * only ever copies what the build just produced, and refuses to pretend a
 * missing artifact exists. Nothing either writes is a secret — the keystore is
 * never read here, and CI deletes its decoded copy before this runs.
 */
fun registerReleasePackaging(taskName: String, includeBundle: Boolean) =
    tasks.register(taskName) {
        group = "distribution"
        description = if (includeBundle) {
            "Copies the release APK, AAB, mapping.txt and checksums into release/"
        } else {
            "Copies the release APK, mapping.txt and checksums into release/ for a GitHub release"
        }
        dependsOn(listOfNotNull("assembleRelease", "bundleRelease".takeIf { includeBundle }))

        val apkDir = layout.buildDirectory.dir("outputs/apk/release")
        val bundleDir = layout.buildDirectory.dir("outputs/bundle/release")
        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
        val destination = rootProject.layout.projectDirectory.dir("release")
        val rootDir = rootProject.projectDir
        val version = earthVersionName
        // An artifact nobody can install must not be named as though they can. AGP
        // says so in the APK's filename but not the bundle's, so the signing
        // configuration decides it for both.
        val suffix = if (hasReleaseSigning) "" else "-unsigned"
        val sources = buildList {
            add("apk" to apkDir)
            if (includeBundle) add("aab" to bundleDir)
        }

        doLast {
            val target = destination.asFile.apply { mkdirs() }

            // Clear this version's previous output first. Signing a build that was
            // packaged unsigned earlier leaves both names in the directory, and an
            // `-unsigned` APK sitting next to a signed one is exactly the file that
            // gets attached to a release by mistake. Only this version's artifacts
            // are pruned, so an earlier release archived here survives.
            target.listFiles()
                ?.filter {
                    it.isFile && (
                        it.name.startsWith("EARTH-$version-release") ||
                            it.name == "SHA256SUMS.txt" ||
                            it.name == "SHA256SUMS"
                        )
                }
                .orEmpty()
                .forEach { it.delete() }

            val produced = mutableListOf<File>()
            sources.forEach { (extension, source) ->
                source.get().asFile.listFiles()
                    ?.filter { it.isFile && it.extension == extension }
                    .orEmpty()
                    .forEach { artifact ->
                        val copy = target.resolve("EARTH-$version-release$suffix.$extension")
                        artifact.copyTo(copy, overwrite = true)
                        produced += copy
                        logger.lifecycle("Release artifact: ${copy.relativeTo(rootDir)}")
                    }
            }
            check(produced.isNotEmpty()) {
                "No release artifacts were produced under " +
                    sources.joinToString(" or ") { (_, source) -> source.get().toString() } + "."
            }

            // Only exists when minification ran, which is every release build here —
            // but a build type without R8 must not fail the packaging over it.
            val mapping = mappingFile.get().asFile
            if (mapping.isFile) {
                val copy = target.resolve("EARTH-$version-release-mapping.txt")
                mapping.copyTo(copy, overwrite = true)
                produced += copy
                logger.lifecycle("Release artifact: ${copy.relativeTo(rootDir)}")
            } else {
                logger.lifecycle("No mapping.txt was produced; a crash report from this build cannot be deobfuscated.")
            }

            // Written last so it covers everything above it, in the same format
            // `sha256sum -c` reads.
            val digest = MessageDigest.getInstance("SHA-256")
            val checksums = target.resolve("SHA256SUMS.txt")
            checksums.writeText(
                produced.joinToString("\n", postfix = "\n") { artifact ->
                    digest.reset()
                    val hash = digest.digest(artifact.readBytes())
                        .joinToString("") { byte -> "%02x".format(byte) }
                    "$hash  ${artifact.name}"
                },
            )
            logger.lifecycle("Release artifact: ${checksums.relativeTo(rootDir)}")

            if (!hasReleaseSigning) {
                logger.lifecycle(
                    "These artifacts are UNSIGNED: no keystore was configured. They cannot be " +
                        "installed and must not be attached to a release. " +
                        "See docs/RELEASE-SIGNING.md.",
                )
            }
        }
    }

// The GitHub release artifact, and the Play-plus-GitHub set. See above.
registerReleasePackaging("packageReleaseApk", includeBundle = false)
registerReleasePackaging("packageReleaseArtifacts", includeBundle = true)
