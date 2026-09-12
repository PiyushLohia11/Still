# Dependency verification
Checked against official release documentation and Google Maven / Maven Central metadata on 2026-09-12. Only stable artifacts are pinned.

| Component | Pin | Official reference |
| --- | --- | --- |
| Android Gradle plugin | 9.3.2 | https://developer.android.com/build/releases/agp-9-3-0-release-notes |
| Gradle | 9.5.0 | AGP 9.3 documented compatible default; newer Gradle releases are not required |
| Kotlin / Compose compiler plugin | 2.4.20 | https://kotlinlang.org/docs/releases.html |
| KSP | 2.3.12 | https://github.com/google/ksp/releases/tag/2.3.12 |
| Compose BOM | 2026.09.00 | https://developer.android.com/develop/ui/compose/bom and Google Maven metadata |
| Activity | 1.13.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Lifecycle | 2.11.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Media3 | 1.11.1 | https://developer.android.com/jetpack/androidx/releases/media3 and Google Maven metadata (patch newer than cached docs header) |
| Room | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore | 1.2.1 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Coroutines | 1.11.0 | https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0 |
| Serialization JSON | 1.11.0 | https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.11.0 |
| OkHttp / MockWebServer | 5.5.0 | Maven Central com.squareup.okhttp3 metadata |
| Coil | 3.6.2 | https://coil-kt.github.io/coil/changelog/ |
| Robolectric | 4.17 | https://github.com/robolectric/robolectric/releases |
| AndroidX Test | Core/Runner/Rules 1.7.0, JUnit 1.3.0, UIAutomator 2.4.0 | https://developer.android.com/jetpack/androidx/releases/test |

Compile SDK 37.1 is necessary for the current Compose and Lifecycle artifacts. Target SDK is 37; minimum runtime is API 26. JDK 21 from Android Studio is used for verification. Media3's playlist preload and shuffle-order APIs require explicit AndroidX opt-in; they are isolated in the service and tested against this pinned version.

The standard Compose BOM aligns Compose components. No alpha/beta/RC runtime artifacts are selected explicitly. The BOM still supplies Material icons via the existing extended icon artifact; only referenced icons are used.
