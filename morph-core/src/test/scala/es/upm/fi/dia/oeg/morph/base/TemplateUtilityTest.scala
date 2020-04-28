package es.upm.fi.dia.oeg.morph.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TemplateUtilityTest {

    @Test def TestGetTemplateGroupsPureJSONPath() {
        println("------------------ TestGetTemplateGroupsPureJSONPath ------------------")
        val jsonPath = """$.*"""
        var tpl = "http://example.org/student/{" + jsonPath + "}"

        val groups = TemplateUtility.getTemplateGroups(tpl)
        println("groups: " + groups)

        assertEquals(jsonPath, groups(0))
    }

    @Test def TestGetTemplateGroups() {
        println("------------------ TestGetTemplateGroups ------------------")
        val xPath = """XPath(\/\/root\/node[1]\(\)\/@id)"""
        val jsonPath = """JSONPath($['store'].book[\(@.length-1\)].title)"""
        val mixedPath = "Column(NAME)/CSV(3)/" + xPath + "/" + jsonPath + "/TSV(name)"
        var tpl = "http://example.org/student/{ID}/{" + mixedPath + "}/{ID2}/{" + mixedPath + "}"

        val groups = TemplateUtility.getTemplateGroups(tpl)
        println("groups: " + groups)

        assertEquals("ID", groups(0))
        assertEquals(mixedPath, groups(1))
        assertEquals("ID2", groups(2))
        assertEquals(mixedPath, groups(3))
    }

    @Test def TestGetTemplateColumns1() {
        println("------------------ TestGetTemplateColumns1 ------------------")
        val tpl = "http://www4.wiwiss.fu-berlin.de/bizer/bsbm/v01/instances/{ar}/{nr}";
        val colRefs = TemplateUtility.getTemplateColumns(tpl);
        println("Found columns " + colRefs + " in template " + tpl)
        assertEquals("ar", colRefs(0))
        assertEquals("nr", colRefs(1))
    }

    @Test def TestGetTemplateColumns2() {
        println("------------------ TestGetTemplateColumns2 ------------------")
        val xPath = """XPath(\/\/root\/node[1]\(\)\/@id)"""
        val jsonPath = """JSONPath($['store'].book[\(@.length-1\)].title)"""
        val mixedPath = "Column(NAME)/CSV(3)/" + xPath + "/" + jsonPath + "/TSV(name)"
        var tpl = "http://example.org/student/{ID}/{" + mixedPath + "}/{ID2}/{" + mixedPath + "}"

        val colRefs = TemplateUtility.getTemplateColumns(tpl)
        println("colRefs: " + colRefs)

        assertEquals("ID", colRefs(0))
        assertEquals("NAME", colRefs(1))
        assertEquals("ID2", colRefs(2))
        assertEquals("NAME", colRefs(3))
    }

    @Test def TestGetTemplateMatching() {
        println("------------------ TestGetTemplateMatching ------------------")

        var groups = TemplateUtility.getTemplateMatching("http://example.org/student/{ID1}/{ID2}/{ID1}", "http://example.org/student/id1/id2/id1")
        println("groups: " + groups)
        assertEquals("id1", groups.get("ID1").get)
        assertEquals("id2", groups.get("ID2").get)

        groups = TemplateUtility.getTemplateMatching("http://example.org/student/{ID1}", "http://foo.com/student/id1")
        println("groups: " + groups)
        assertTrue(groups.isEmpty)

        val xPath = """XPath(\/\/root\/node[1]\(\)\/@id)"""
        val jsonPath = """JSONPath($['store'].book[\(@.length-1\)].title)"""
        val mixedPath = "Column(NAME)/CSV(3)/" + xPath + "/" + jsonPath + "/TSV(name)"
        var tpl = "http://example.org/student/{ID1}/{" + mixedPath + "}/{ID2}"

        groups = TemplateUtility.getTemplateMatching(tpl, "http://example.org/student/id1/mixedPath1/id2")
        println("groups: " + groups)

    }

    @Test def TestCartesianProduct() {
        println("------------------ TestCartesianProduct ------------------")
        val lists: List[List[Object]] = List(List("1", "2", "3"), List("4"), List("5", "6"))
        val combinations = TemplateUtility.cartesianProduct(lists)
        println(combinations)
        assertEquals(6, combinations.length)
        assertEquals(List(
            List("1", "4", "5"),
            List("1", "4", "6"),
            List("2", "4", "5"),
            List("2", "4", "6"),
            List("3", "4", "5"),
            List("3", "4", "6")), combinations)

        val lists2: List[List[Object]] = List(List("1", "2", "3"), List())
        val combinations2 = TemplateUtility.cartesianProduct(lists2)
        println(combinations2)
        assertEquals(3, combinations2.length)
        assertEquals(List(
            List("1", ""),
            List("2", ""),
            List("3", "")), combinations2)
    }

    @Test def TestReplaceTemplateTokens_StraightCase() {
        println("------------------ TestReplaceTemplateTokens_StraightCase ------------------")

        val xPath = """XPath(\/\/root\/node[1]\(\)\/@id)"""
        val jsonPath = """JSONPath($['store'].book[\(@.length-1\)].title)"""
        val mixedPath = "Column(NAME)/CSV(3)/" + xPath + "/" + jsonPath + "/TSV(name)"

        var tpl = "http://example.org/student/{ID}/{" + mixedPath + "}/{ID2}/{" + mixedPath + "}"

        val replacements: List[List[Object]] = List(List("A"), List("12", "34"), List("B", "C"), List("D"))
        val values = TemplateUtility.replaceTemplateGroups(tpl, replacements)
        println("values: " + values)

        assertEquals(4, values.length)
        assertEquals("http://example.org/student/A/12/B/D", values(0))
        assertEquals("http://example.org/student/A/12/C/D", values(1))
        assertEquals("http://example.org/student/A/34/B/D", values(2))
        assertEquals("http://example.org/student/A/34/C/D", values(3))
    }

    @Test def TestReplaceTemplateTokens_LessStraightCases() {
        println("------------------ TestReplaceTemplateTokens_ErrorCases ------------------")

        // One empty replacement
        var tpl = "{A}-{B}-{C}"
        var replacements: List[List[Object]] = List(List("A"), List(), List("D"))
        var values = TemplateUtility.replaceTemplateGroups(tpl, replacements)
        println("values: " + values)
        assertEquals(1, values.length)
        assertEquals("A--D", values(0))

        // More values than template groups
        tpl = "{A}-{B}"
        replacements = List(List("a"), List("b"), List("c"))
        values = TemplateUtility.replaceTemplateGroups(tpl, replacements)
        println("values: " + values)
        assertEquals(1, values.length)
        assertEquals("a-b", values(0))

        // More groups than values => the method return the template with no change
        tpl = "{A}-{B}-{C}"
        replacements = List(List("a"), List("b"))
        values = TemplateUtility.replaceTemplateGroups(tpl, replacements)
        println("values: " + values)
        assertEquals(1, values.length)
        assertEquals(tpl, values(0))
    }

    @Test def TestCompatibleTemplateStrings {

        var tplStr1 = "http://example.org/student/{xx}"
        var tplStr2 = "http://example.org/{yy}/{34}"
        assertFalse(TemplateUtility.compatibleTemplateStrings(tplStr1, tplStr2))

        tplStr1 = "http://example.org/student/{xx}"
        tplStr2 = "http://example.org/student/{yy}/{34}/B{tt}"
        assertFalse(TemplateUtility.compatibleTemplateStrings(tplStr1, tplStr2))

        tplStr1 = "http://example.org/student/{xx}/{12}/B{zz}"
        tplStr2 = "http://example.org/student/{yy}/{34}/B{tt}"
        assertTrue(TemplateUtility.compatibleTemplateStrings(tplStr1, tplStr2))

        val xPath = """XPath(\/\/root\/node[1]\(\)\/@id)"""
        val jsonPath = """JSONPath($['store'].book[\(@.length-1\)].title)"""
        val mixedPath = "Column(NAME)/CSV(3)/" + xPath + "/" + jsonPath + "/TSV(name)"
        var tpl = "http://example.org/student/{ID}/{" + mixedPath + "}/{ID2}/{" + mixedPath + "}"
        assertTrue(TemplateUtility.compatibleTemplateStrings(tpl, tpl))
    }

    @Test def TestSha1 {
        println("------------------ TestSha1 ------------------")

        var tpl = "http://example.org/student/sha1({field1}_{field2})"
        val replacements: List[List[Object]] = List(List("A"), List("B"))
        val values = TemplateUtility.replaceTemplateGroups(tpl, replacements)
        println(values)
        assertEquals(values, List("http://example.org/student/" + org.apache.commons.codec.digest.DigestUtils.sha1Hex("A_B")))
    }
}