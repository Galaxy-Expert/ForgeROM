package com.forgerom.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SourceParsersTest {

    @Test
    fun parseYaml_readsTopLevelKeysOnly() {
        val yaml = "name: 'Galaxy S20 FE'\nvendor: Samsung\n  nested: ignored\n# comment\nmodels: [SM-G780F, SM-G780G]\n"
        val map = SourceParsers.parseYaml(yaml)
        assertEquals("Galaxy S20 FE", map["name"])
        assertEquals("Samsung", map["vendor"])
        assertEquals("[SM-G780F, SM-G780G]", map["models"])
        assertFalse(map.containsKey("nested"))
    }

    @Test
    fun parseList_handlesQuotesAndNull() {
        assertEquals(listOf("SM-G780F", "SM-G780G/DS"), SourceParsers.parseList("[SM-G780F, 'SM-G780G/DS']"))
        assertTrue(SourceParsers.parseList(null).isEmpty())
    }

    @Test
    fun normalizeModel_removesDualSimSuffixAndSpaces() {
        assertEquals("SM-G780G", SourceParsers.normalizeModel("sm-g780g/DS "))
    }

    @Test
    fun lineageToAndroid_mapsKnownVersions() {
        assertEquals("15", SourceParsers.lineageToAndroid("22.2"))
        assertEquals("13", SourceParsers.lineageToAndroid("20"))
        assertEquals("10", SourceParsers.lineageToAndroid("17.1"))
        assertNull(SourceParsers.lineageToAndroid("99"))
    }

    private val crdroidHtml = """
        <h5>r8q</h5><img src="https://crdroid.net/img/devices/r8q-small.webp"><a href="https://crdroid.net/r8q/10">crDroid 10</a><p>Last build: 2024-07-28</p>
        <h5>r8s</h5><a href="https://crdroid.net/r8s/11">x</a><a href="https://crdroid.net/r8s/12">y</a><p>Last build: <b>2025-10-04</b></p>
        <a href="https://crdroid.net/x1s/12">x1s</a><p>Last build: 2026-06-08</p>
        <a href="https://crdroid.net/Pong/12">Pong</a><p>Last build: 2026-09-20</p>
    """.trimIndent()

    @Test
    fun parseCrDroid_findsLatestVersionAndDate() {
        val r8q = SourceParsers.parseCrDroid(crdroidHtml, "r8q")
        assertNotNull(r8q)
        assertEquals(10, r8q!!.latest)
        assertEquals("2024-07-28", r8q.lastBuild)

        val r8s = SourceParsers.parseCrDroid(crdroidHtml, "r8s")
        assertEquals(12, r8s!!.latest)
        assertEquals("2025-10-04", r8s.lastBuild)
    }

    @Test
    fun parseCrDroid_doesNotMatchSimilarCodenames() {
        assertNull(SourceParsers.parseCrDroid(crdroidHtml, "x1"))
        assertNull(SourceParsers.parseCrDroid(crdroidHtml, "r8"))
        assertEquals("x1s", SourceParsers.parseCrDroid(crdroidHtml, "x1s")!!.code)
    }

    @Test
    fun parseCrDroid_isCaseInsensitiveButKeepsSiteSpelling() {
        assertEquals("Pong", SourceParsers.parseCrDroid(crdroidHtml, "pong")!!.code)
    }

    @Test
    fun isStale_usesOneYearByDefault() {
        val today = LocalDate.of(2026, 10, 23)
        assertTrue(SourceParsers.isStale("2024-07-28", today))
        assertFalse(SourceParsers.isStale("2026-06-08", today))
        assertFalse(SourceParsers.isStale(null, today))
        assertFalse(SourceParsers.isStale("not a date", today))
    }

    @Test
    fun parseLineageBuilds_picksNewestZipAndIgnoresBadEntries() {
        val sha = "a".repeat(64)
        val json = """
            {"response":[
              {"datetime":100,"filename":"lineage-21.0-a.zip","id":"$sha","size":1000,"url":"https://mirrorbits.lineageos.org/full/x/a.zip","version":"21.0"},
              {"datetime":200,"filename":"lineage-22.2-b.zip","id":"short","size":0,"url":"https://mirrorbits.lineageos.org/full/x/b.zip","version":"22.2"},
              {"datetime":300,"filename":"recovery.img","id":"","size":5,"url":"https://mirrorbits.lineageos.org/full/x/recovery.img","version":"22.2"},
              {"datetime":400,"filename":"lineage-22.2-c.zip","id":"","size":5,"url":"http://insecure.example/c.zip","version":"22.2"}
            ]}
        """.trimIndent()
        val best = SourceParsers.parseLineageBuilds(json)
        assertNotNull(best)
        assertEquals("lineage-22.2-b.zip", best!!.fileName)
        assertEquals("22.2", best.version)
        assertNull(best.sha256)
        assertNull(best.sizeBytes)
    }

    @Test
    fun parseLineageBuilds_returnsNullWithoutBuilds() {
        assertNull(SourceParsers.parseLineageBuilds("""{"response":[]}"""))
        assertNull(SourceParsers.parseLineageBuilds("""{"other":1}"""))
    }

    @Test
    fun isNewer_ignoresPrefixAndSuffix() {
        assertTrue(SourceParsers.isNewer("v0.5.0-beta", "0.4.0-alpha"))
        assertTrue(SourceParsers.isNewer("0.10.0", "0.9.5"))
        assertFalse(SourceParsers.isNewer("0.4.0", "0.4.0-alpha"))
        assertFalse(SourceParsers.isNewer("v0.4.0-alpha", "0.5.0-beta"))
    }
}
