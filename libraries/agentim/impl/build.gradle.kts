/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins { id("io.element.android-library") }
android {
    namespace = "io.element.android.libraries.agentim.impl"
    buildFeatures {
        buildConfig = true
    }
}
setupDependencyInjection()
dependencies {
    api(projects.libraries.agentim.api)
    implementation(projects.libraries.matrix.api)
    implementation(libs.androidx.corektx)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(libs.coroutines.core)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.sessionStorage.test)
}
