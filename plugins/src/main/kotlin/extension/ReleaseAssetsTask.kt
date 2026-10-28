/*
 * Copyright 2026 alljoy
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package extension

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationVariant
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Filters only diagnostic source maps from merged Release assets before APK signing. */
@CacheableTask
abstract class ReleaseAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        ReleaseAssets.copy(inputDirectory.get().asFile, outputDirectory.get().asFile)
    }
}

/** Keeps all runtime assets and licences while removing map files and diagnostic links. */
object ReleaseAssets {
    private val lineComment = Regex("(?m)^[\\t ]*//[#@][\\t ]*sourceMappingURL=[^\\r\\n]*(?:\\r?\\n|$)")
    private val blockComment = Regex("/\\*[#@][\\t ]*sourceMappingURL=[^*]*\\*/")

    fun copy(source: File, destination: File) {
        val sourcePath = source.toPath().toAbsolutePath().normalize()
        val destinationPath = destination.toPath().toAbsolutePath().normalize()
        require(source.isDirectory && !sourcePath.startsWith(destinationPath) && !destinationPath.startsWith(sourcePath))
        destination.deleteRecursively()
        destination.mkdirs()
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            require(!java.nio.file.Files.isSymbolicLink(file.toPath()))
            if (!file.name.endsWith(".map")) {
                val output = File(destination, file.relativeTo(source).path)
                output.parentFile.mkdirs()
                if (file.extension in setOf("js", "mjs", "css")) {
                    output.writeText(file.readText().replace(lineComment, "").replace(blockComment, ""))
                } else {
                    file.copyTo(output)
                }
            }
        }
    }
}

/** Connects the cleaner to the public merged-assets artifact on optimized variants. */
fun ApplicationVariant.prepareReleaseAssets(project: Project) {
    if (buildType == "debug") return
    val taskName = "prepare${name.replaceFirstChar { it.uppercaseChar() }}ReleaseAssets"
    val provider: TaskProvider<ReleaseAssetsTask> = project.tasks.register(taskName, ReleaseAssetsTask::class.java)
    artifacts.use(provider)
        .wiredWithDirectories(ReleaseAssetsTask::inputDirectory, ReleaseAssetsTask::outputDirectory)
        .toTransform(SingleArtifact.ASSETS)
}
