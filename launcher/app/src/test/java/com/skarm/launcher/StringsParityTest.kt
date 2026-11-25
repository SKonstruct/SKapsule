package com.skarm.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The translated string files (`res/values-<language>/strings.xml`) must stay in step with the English one, so a
 * machine translation cannot break a screen: the same keys, and the same format placeholders (`%1$s`, `%d`) in every
 * string. A string that is not translated yet is simply a copy of the English text.
 */
class StringsParityTest {
    private val res = File("src/main/res")

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = e.textContent
        }
        return out
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[sdf]").findAll(text).map { it.value.replace(Regex("^%\\d+\\$"), "%") }.sorted().toList()

    private fun localeFiles(): List<File> =
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
            .sortedBy { it.parentFile.name }

    @Test
    fun everyLanguageHasTheEnglishKeys() {
        val english = strings(File(res, "values/strings.xml"))
        val files = localeFiles()
        assertTrue("no translated string files found", files.isNotEmpty())
        for (file in files) {
            val other = strings(file)
            val missing = english.keys - other.keys
            val extra = other.keys - english.keys
            assertTrue("${file.parentFile.name} lacks: ${missing.take(5)}", missing.isEmpty())
            assertTrue("${file.parentFile.name} has unknown keys: ${extra.take(5)}", extra.isEmpty())
        }
    }

    @Test
    fun translationsKeepTheFormatPlaceholders() {
        val english = strings(File(res, "values/strings.xml"))
        for (file in localeFiles()) {
            for ((name, text) in strings(file)) {
                val wanted = placeholders(english[name] ?: continue)
                assertEquals("${file.parentFile.name}: placeholders of $name", wanted, placeholders(text))
            }
        }
    }

    @Test
    fun theLocaleConfigListsTheLanguagesThatHaveStrings() {
        val config = File(res, "xml/locales_config.xml").readText()
        val tags = Regex("android:name=\"([A-Za-z-]+)\"").findAll(config).map { it.groupValues[1] }.toSet()
        for (file in localeFiles()) {
            // values-pt-rBR is pt-BR, values-zh-rCN is zh-CN.
            val tag = file.parentFile.name.removePrefix("values-").replace("-r", "-")
            assertTrue("locales_config.xml lacks $tag", tag in tags)
        }
    }
}
