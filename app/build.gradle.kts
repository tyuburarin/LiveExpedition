import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "jp.local.liveexpedition"
    compileSdk = 36
    defaultConfig {
        applicationId = "jp.local.liveexpedition.preview"
        minSdk = 33
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.4-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { isMinifyEnabled = true; isDebuggable = false; signingConfig = signingConfigs.getByName("debug"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val testWork = file(providers.gradleProperty("uiTestWork").getOrElse("${rootProject.buildDir}/ui-test-work"))
            val home = file("$testWork/home")
            val temporary = file("$testWork/tmp")
            it.doFirst { home.mkdirs(); temporary.mkdirs() }
            it.maxHeapSize = "2g"
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("user.home", home.absolutePath)
            it.systemProperty("java.io.tmpdir", temporary.absolutePath)
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
configurations.configureEach { exclude(group="androidx.profileinstaller",module="profileinstaller") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
    implementation("androidx.sqlite:sqlite:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
tasks.register("runtimeInventory") {
    doLast {
        val entries = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}" }.distinct().sorted()
        file("$rootDir/reports/runtime-dependencies.txt").apply { parentFile.mkdirs(); writeText(entries.joinToString("\n") + "\n") }
    }
}
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val audit = tasks.register("verifyReleaseManifest") {
            inputs.file(manifest)
            doLast {
                val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                val document = factory.newDocumentBuilder().parse(manifest.get().asFile)
                val permissions = document.getElementsByTagName("uses-permission")
                val allowed = setOf("android.permission.USE_BIOMETRIC", "android.permission.POST_NOTIFICATIONS", "jp.local.liveexpedition.preview.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                for (i in 0 until permissions.length) {
                    val node = permissions.item(i) as org.w3c.dom.Element
                    check(node.getAttribute("android:name") in allowed) { "Unexpected permission" }
                }
                val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
                check(application.getAttribute("android:debuggable") != "true")
                check(application.getAttribute("android:allowBackup") == "false")
                check(application.getAttribute("android:usesCleartextTraffic") == "false")
                var exposed = 0
                listOf("activity", "activity-alias", "service", "provider", "receiver").forEach { tag ->
                    val nodes = document.getElementsByTagName(tag)
                    for (i in 0 until nodes.length) {
                        val node = nodes.item(i) as org.w3c.dom.Element
                        if (node.getAttribute("android:exported") == "true") {
                            check(tag == "activity" && node.getAttribute("android:name") == "jp.local.liveexpedition.MainActivity")
                            exposed++
                        }
                    }
                }
                check(exposed == 1)
                println("PASS: offline release manifest, permission allowlist, backup policy, exported components")
            }
        }
        tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn(audit) }
    }
}
