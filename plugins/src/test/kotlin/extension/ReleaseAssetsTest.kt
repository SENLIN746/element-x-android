/*
 * Copyright 2026 alljoy
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package extension

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReleaseAssetsTest {
    @Test
    fun `release keeps executable assets and licences but drops maps and diagnostic comments`() {
        val temporary = Files.createTempDirectory("release-assets").toFile()
        try {
            val input = temporary.resolve("input").apply { mkdirs() }
            input.resolve("nested").mkdirs()
            input.resolve("nested/main.js").writeText("const x = 1;\n//# sourceMappingURL=main.js.map\n")
            input.resolve("nested/main.js.map").writeText("{\"sourcesContent\":[\"private diagnostic source\"]}")
            input.resolve("style.css").writeText("body {}/*# sourceMappingURL=style.css.map */")
            input.resolve("LICENSE.txt").writeText("Licence text")
            val wasm = byteArrayOf(0, 97, 115, 109)
            input.resolve("code.wasm").writeBytes(wasm)
            val output = temporary.resolve("output")
            ReleaseAssets.copy(input, output)
            assertFalse(output.resolve("nested/main.js.map").exists())
            assertEquals("const x = 1;\n", output.resolve("nested/main.js").readText())
            assertEquals("body {}", output.resolve("style.css").readText())
            assertEquals("Licence text", output.resolve("LICENSE.txt").readText())
            assertContentEquals(wasm, output.resolve("code.wasm").readBytes())
            output.resolve("stale.map").writeText("stale")
            ReleaseAssets.copy(input, output)
            assertFalse(output.resolve("stale.map").exists())
        } finally {
            temporary.deleteRecursively()
        }
    }
}
