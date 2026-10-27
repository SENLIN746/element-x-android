/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins { id("io.element.android-compose-library") }
android { namespace = "io.element.android.features.agentim.impl" }
setupDependencyInjection()
dependencies {
    api(projects.features.agentim.api)
    implementation(projects.libraries.agentim.api)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.deeplink.api)
    implementation(projects.libraries.di)
    implementation(projects.libraries.permissions.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
}
