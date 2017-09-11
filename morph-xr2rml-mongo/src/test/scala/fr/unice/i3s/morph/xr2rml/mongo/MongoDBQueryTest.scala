package fr.unice.i3s.morph.xr2rml.mongo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLQuery

/**
 * @author Franck Michel, I3S laboratory
 *
 */
class MongoDBQueryTest {

    @Test def testMostSpecificQuery_empty() {
        println("------------------------------------------------- testMostSpecificQuery_empty")

        var ls1 = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty, Nil)
        var ls2 = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty, Nil)

        var mostSpec = MongoDBQuery.mostSpecificQuery(ls2, ls1)
        assertTrue(mostSpec.isDefined)
        println(mostSpec.get)
        assertEquals("db.collection.find({})", mostSpec.get.query)

        ls2 = new xR2RMLQuery("db.collection.find({ }  )", "JSONPath", None, Set.empty, Nil)

        mostSpec = MongoDBQuery.mostSpecificQuery(ls1, ls2)
        assertTrue(mostSpec.isDefined)
        println(mostSpec.get)
        assertEquals("db.collection.find({})", mostSpec.get.query)
    }

    @Test def testMostSpecificQuery() {
        println("------------------------------------------------- testMostSpecificQuery")

        var ls1 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        var ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)

        var mostSpec = MongoDBQuery.mostSpecificQuery(ls2, ls1)
        assertTrue(mostSpec.isDefined)
        println(mostSpec.get)
        assertEquals("db.collection.find({query1,query2})", mostSpec.get.query)

        ls1 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        ls2 = new xR2RMLQuery("db.collection.find({query1, query2, query3})", "JSONPath", None, Set.empty, Nil)

        mostSpec = MongoDBQuery.mostSpecificQuery(ls1, ls2)
        assertTrue(mostSpec.isDefined)
        println(mostSpec.get)
        assertEquals("db.collection.find({query1,query2,query3})", mostSpec.get.query)

        mostSpec = MongoDBQuery.mostSpecificQuery(ls2, ls1)
        assertTrue(mostSpec.isDefined)
        println(mostSpec.get)
        assertEquals("db.collection.find({query1,query2,query3})", mostSpec.get.query)
    }

    @Test def testMostSpecificQuery2() {
        println("------------------------------------------------- testMostSpecificQuery2")

        var ls1 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        var ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "XPath", None, Set.empty, Nil)

        var mostSpec = MongoDBQuery.mostSpecificQuery(ls1, ls2)
        assertFalse(mostSpec.isDefined)

        mostSpec = MongoDBQuery.mostSpecificQuery(ls2, ls1)
        assertFalse(mostSpec.isDefined)

        ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "XPath", Some("iter"), Set.empty, Nil)
        mostSpec = MongoDBQuery.mostSpecificQuery(ls2, ls1)
        assertFalse(mostSpec.isDefined)
    }

    @Test def testMostSpecificQuery3 {
        println("------------------------------------------------- testMostSpecificQuery2")

        var ls1 = new xR2RMLQuery("db.taxref3.find( { $where:'this.codeTaxon == this.codeReference' } )", "JSONPath"
            , None, Set.empty, Nil)
        var ls2 = new xR2RMLQuery("db.taxref3.find( {$where: 'this.codeTaxon == this.codeReference', 'spm' : {$ne: ''}, 'spm': {$ne: null} } )", "JSONPath", None, Set.empty, Nil)

        var mostSpec = MongoDBQuery.mostSpecificQuery(ls1, ls2)
        println(mostSpec.get)
        assertTrue(mostSpec.isDefined)
    }

    @Test def testIsLeftMoreSpecific_empty() {
        println("------------------------------------------------- testIsLeftMoreSpecific_empty")

        var left = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty, Nil)
        var right = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty, Nil)
        var mostSpec = MongoDBQuery.isLeftMoreSpecificOrEqual(right, left)
        assertTrue(mostSpec)

        right = new xR2RMLQuery("db.collection.find({})", "JSONPath", None, Set.empty, Nil)
        mostSpec = MongoDBQuery.isLeftMoreSpecificOrEqual(left, right)
        assertTrue(mostSpec)
    }

    @Test def testIsLeftMoreSpecific() {
        println("------------------------------------------------- testIsLeftMoreSpecific")

        var left = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        var right = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)

        assertTrue(MongoDBQuery.isLeftMoreSpecificOrEqual(left, right))

        left = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        right = new xR2RMLQuery("db.collection.find({query1, query2, query3})", "JSONPath", None, Set.empty, Nil)

        assertFalse(MongoDBQuery.isLeftMoreSpecificOrEqual(left, right))
        assertTrue(MongoDBQuery.isLeftMoreSpecificOrEqual(right, left))
    }

    @Test def testIsLeftMoreSpecific2() {
        println("------------------------------------------------- testIsLeftMoreSpecific2")

        var ls1 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty, Nil)
        var ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "XPath", None, Set.empty, Nil)

        assertFalse(MongoDBQuery.isLeftMoreSpecificOrEqual(ls1, ls2))
        assertFalse(MongoDBQuery.isLeftMoreSpecificOrEqual(ls2, ls1))

        ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "XPath", Some("iter"), Set.empty, Nil)
        assertFalse(MongoDBQuery.isLeftMoreSpecificOrEqual(ls1, ls2))
        assertFalse(MongoDBQuery.isLeftMoreSpecificOrEqual(ls2, ls1))
    }

}