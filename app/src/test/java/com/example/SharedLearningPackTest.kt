package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class SharedLearningPackTest {
    private fun word(s: String, meaning: String) = WordFormula(s, s, null, null, null, null, null, null, "", meaning, "", "")
    private fun normalized(s: String, meaning: String) = OfflineMorphology.apply(word(s, meaning), "3-CET4-顺序")

    @Test fun `absent base uses shared evidence with bounded multiple layers`() {
        val w = normalized("unhappiness", "n. 不快乐")
        val entry = OfflineMorphology.displayEntry(w)!!
        assertEquals(SharedLearningPack.Kind.MORPHOLOGY, entry.kind)
        assertEquals(listOf("un", "happi", "ness"), entry.blocks)
        assertTrue(w.literalFormula.contains("happy"))
        assertNull(w.rootId)
        assertTrue(OfflineMorphology.componentsFor(listOf(w)).isEmpty())
        assertEquals(listOf("happi", "ness"), OfflineMorphology.displayEntry(normalized("happiness", "n. 幸福"))!!.blocks)
    }

    @Test fun `false roots proper names stale formulas and unknown source never become construction evidence`() {
        listOf("remember", "carpet", "butterfly", "month", "business", "butter", "peer", "letter", "music", "faith", "tower").forEach {
            assertNull(it, SharedLearningPack.construction(word(it, "n. 名词; v. 动词")))
        }
        assertNull(SharedLearningPack.construction(word("Happiness", "n. 幸福")))
        assertNull(SharedLearningPack.construction(word("happiness", "v. 幸福")))
        assertNull(SharedLearningPack.construction(word("happiness", "幸福")))
        val good = normalized("happiness", "n. 幸福")
        assertFalse(OfflineMorphology.canAssemble(good.copy(literalFormula = "fake")))
        assertFalse(OfflineMorphology.canAssemble(good.copy(rootPart = "happi")))
        assertFalse(OfflineMorphology.canAssemble(OfflineMorphology.apply(good, "untrusted")))
    }

    @Test fun `spelling assistance and compound boundaries have distinct provenance`() {
        val remembered = normalized("remember", "v. 记住")
        val entry = OfflineMorphology.displayEntry(remembered)!!
        assertEquals(SharedLearningPack.Kind.SPELLING, entry.kind)
        assertTrue(remembered.literalFormula.contains("CMUdict"))
        assertTrue(remembered.literalFormula.contains("不逐块对应"))
        val compound = OfflineMorphology.displayEntry(normalized("raincoat", "n. 雨衣"))!!
        assertEquals(SharedLearningPack.Kind.COMPOUND, compound.kind)
        assertEquals(listOf("rain", "coat"), compound.blocks)
        assertFalse(OfflineMorphology.canAssemble(normalized("cat", "n. 猫")))
        assertFalse(OfflineMorphology.canAssemble(normalized("sensitive", "adj. 敏感的")))
    }

    @Test fun `every bundled row reconstructs spelling and licenses ship offline`() {
        listOf("morphynet" to 80464, "spelling" to 28300).forEach { (name, count) ->
            var actual = 0
            GZIPInputStream(javaClass.getResourceAsStream("/learning/$name.tsv.gz")!!).bufferedReader().useLines { lines ->
                lines.forEach {
                    val f = it.split('\t')
                    assertEquals(4, f.size)
                    val parts = f[if (name == "morphynet") 2 else 1].split('|')
                    assertTrue(parts.size in 2..6 && parts.all { p -> p.isNotEmpty() })
                    assertEquals(f[0], parts.joinToString(""))
                    actual++
                }
            }
            assertEquals(count, actual)
        }
        assertTrue(SharedLearningPack.resourceText("licenses/CMUdict.txt").contains("Carnegie Mellon"))
        assertTrue(SharedLearningPack.resourceText("licenses/Moby.txt").contains("Grady Ward"))
        assertTrue(SharedLearningPack.resourceText("licenses/CC-BY-SA-3.0.txt").contains("ShareAlike"))
        assertTrue(SharedLearningPack.resourceText("manifest.json").contains(SharedLearningPack.VERSION))
    }

    @Test fun `upgrade and restore preserve scheduling and do not invent component progress`() {
        val old = word("unhappiness", "n. 不快乐").copy(isAssembled = true, ebbinghausStage = 5, lastReviewedAt = 100, nextReviewAt = 9000)
        val data = BackupData("3-CET4-顺序", listOf(old), emptyList(), BackupPreferences("SYSTEM", true, false, false, "system"))
        val next = OfflineMorphology.prepare(data)
        val w = next.words.single()
        assertEquals(old.id, w.id); assertEquals(old.ebbinghausStage, w.ebbinghausStage)
        assertEquals(old.lastReviewedAt, w.lastReviewedAt); assertEquals(old.nextReviewAt, w.nextReviewAt)
        assertEquals(old.isAssembled, w.isAssembled); assertTrue(next.components.isEmpty())
        assertEquals(next, OfflineMorphology.prepare(next))
        val out = java.io.ByteArrayOutputStream()
        BackupFormat.write(next, out)
        assertEquals(next, OfflineMorphology.prepare(BackupFormat.read(out.toByteArray().inputStream()).data))
    }

    @Test fun `real CET books emit auditable category coverage and preserve every progress field`() {
        val sources = File("../../content-samples")
        org.junit.Assume.assumeTrue(File(sources, "cet4-legacy.json").isFile)
        val report = mutableListOf("book\ttotal\tcurated\trules\tmorphology\tcompound\tspelling\twhole")
        listOf("cet4-legacy.json" to "3-CET4-顺序", "cet6-legacy.json" to "4-CET6-顺序").forEach { (name, id) ->
            val result = WordbookImporter.parse(File(sources, name).readText(), id)
            val categories = result.words.associateWith { w ->
                val e = OfflineMorphology.displayEntry(w)
                when { e?.assembly != true -> "whole"; e.ruleDerived -> "rules"; e.kind == null -> "curated"; else -> e.kind.name.lowercase() }
            }
            val counts = categories.values.groupingBy { it }.eachCount()
            val row = listOf(id, result.words.size.toString()) + listOf("curated", "rules", "morphology", "compound", "spelling", "whole").map { counts[it].toString() }
            report += row.joinToString("\t")
            println("LEARNING_PACK " + row.joinToString(" "))
            assertTrue("Shared pack must reduce whole-word fallback", counts.getValue("whole") < if (id.startsWith("3")) 3963 else 3597)
            assertTrue(counts.getValue("morphology") > 100)
            assertTrue(counts.getValue("spelling") > 500)
            File("build/ui-preview").mkdirs()
            File("build/ui-preview/learning-pack-$id.tsv").writeText("word\tcategory\tblocks\tnote\n" + categories.entries.joinToString("\n") { (w, category) ->
                val e = OfflineMorphology.displayEntry(w)
                val blocks = e?.blocks?.takeIf { it.isNotEmpty() } ?: listOfNotNull(w.prefixPart, w.rootPart, w.suffixPart)
                "${w.spell}\t$category\t${blocks.joinToString("|")}\t${w.literalFormula}"
            })
            val learned = result.words.mapIndexed { index, w -> w.copy(isAssembled = index % 2 == 0, ebbinghausStage = index % 9, lastReviewedAt = 100L + index, nextReviewAt = 9000L + index) }
            val prepared = OfflineMorphology.prepare(BackupData(id, learned, result.components, BackupPreferences("SYSTEM", true, false, false, "system")))
            assertEquals(learned.sortedBy { it.id }, prepared.words)
            val out = java.io.ByteArrayOutputStream()
            BackupFormat.write(prepared, out)
            assertEquals(prepared, OfflineMorphology.prepare(BackupFormat.read(out.toByteArray().inputStream()).data))
        }
        File("build/ui-preview/learning-pack-coverage.tsv").writeText(report.joinToString("\n") + "\n")
    }
}
