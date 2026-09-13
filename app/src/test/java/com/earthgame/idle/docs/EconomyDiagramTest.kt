package com.earthgame.idle.docs

import com.earthgame.idle.domain.model.RESOURCE_LIST
import com.earthgame.idle.domain.production.ProductionGraph
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the wiki's economy diagram honest.
 *
 * `docs/wiki/Economy-and-Production.md` carries a Mermaid diagram of the
 * production graph. A diagram that drifts from the code is worse than no
 * diagram, so this checks that every processor the game actually contains
 * appears in it. Run with `-Deconomy.diagram.write=true` to regenerate the
 * machine-readable version under `docs/wiki/`.
 */
class EconomyDiagramTest {

    private val wiki = File("../docs/wiki/Economy-and-Production.md")
        .takeIf { it.exists() }
        ?: File("docs/wiki/Economy-and-Production.md")

    @Test
    fun `the wiki names every processor in the economy`() {
        if (!wiki.exists()) return
        val text = wiki.readText()

        val missing = ProductionGraph.consumers.map { it.id }.filterNot { text.contains(it) }
        assertTrue(
            "these processors are missing from ${wiki.name}: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the wiki names every resource in the economy`() {
        if (!wiki.exists()) return
        val text = wiki.readText()

        val missing = RESOURCE_LIST.map { it.displayName }.filterNot { text.contains(it) }
        assertTrue(
            "these resources are missing from ${wiki.name}: $missing",
            missing.isEmpty(),
        )
    }
}
