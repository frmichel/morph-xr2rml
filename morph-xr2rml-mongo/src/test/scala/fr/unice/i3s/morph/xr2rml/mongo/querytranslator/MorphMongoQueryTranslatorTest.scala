package fr.unice.i3s.morph.xr2rml.mongo.querytranslator

import scala.collection.JavaConversions.asScalaBuffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.op.OpBGP

import es.upm.fi.dia.oeg.morph.base.GenericConnection
import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataSourceReader
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataTranslator
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunnerFactory
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseUnfolder
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryProcessor
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryTranslator
import es.upm.fi.dia.oeg.morph.base.querytranslator.TpBinding
import es.upm.fi.dia.oeg.morph.base.querytranslator.TpBindings
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLMappingDocument
import fr.unice.i3s.morph.xr2rml.mongo.MongoDBQuery
import fr.unice.i3s.morph.xr2rml.mongo.abstractquery.AbstractQueryAtomicMongo
import fr.unice.i3s.morph.xr2rml.mongo.abstractquery.AbstractQueryInnerJoinRef
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeAnd
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeCompare
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeCondEquals
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeCondExists
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeElemMatch
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeField
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeOr
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryNodeUnion
import fr.unice.i3s.morph.xr2rml.mongo.query.MongoQueryProjectionArraySlice

class MorphFactoryConcret2 extends MorphBaseRunnerFactory {

    var properties: MorphProperties = null
    var mappingDocument: R2RMLMappingDocument = null

    override def createConnection: GenericConnection = null
    override def createUnfolder: MorphBaseUnfolder = null
    override def createDataSourceReader: MorphBaseDataSourceReader = null
    override def createDataTranslator: MorphBaseDataTranslator = null
    override def createQueryTranslator: MorphBaseQueryTranslator = null
    override def createQueryProcessor: MorphBaseQueryProcessor = null
    override def getProperties = properties
    override def getMappingDocument = mappingDocument

    override def postCreateFactory = {}
}

/**
 * @author Franck Michel, I3S laboratory
 *
 */
class MorphMongoQueryTranslatorTest {

    var props = MorphProperties.apply("src/test/resources/query_translator", "morph.properties")
    var mappingDocument = R2RMLMappingDocument(props)

    val factory = new MorphFactoryConcret2
    factory.mappingDocument = mappingDocument
    factory.properties = props
    var queryTranslator = new MorphMongoQueryTranslator(factory)

    val tmMovies = mappingDocument.getClassMappingsByName("Movies")
    val tmDirectors = mappingDocument.getClassMappingsByName("Directors")

    private def cleanString(str: String) = str.trim.replaceAll("\\s", "")

    @Test def test_mongoAbstractQuerytoConcrete_Field() {
        println("------ test_mongoAbstractQuerytoConcrete_Field")

        val field = new MongoQueryNodeField("b", List(new MongoQueryNodeCondEquals("bbb")))
        val field2 = new MongoQueryNodeField("b.0.b", List(new MongoQueryNodeCondEquals("b0b")))

        println("---------------------------------")
        var fromPart = new MongoDBQuery("collection", "tititutu")
        var result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, field)
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("tititutu"))
        assertTrue(cleanString(result(0).query).contains("'b':{$eq:'bbb'}"))
    }

    @Test def test_mongoAbstractQuerytoConcrete() {
        println("------ test_mongoAbstractQuerytoConcrete")
        var fromPart = new MongoDBQuery("collection", "tititutu")

        val compare = new MongoQueryNodeField("a", new MongoQueryNodeCompare(MongoQueryNodeCompare.Operator.GT, "10"))
        val field2 = new MongoQueryNodeField("b.0.b", List(new MongoQueryNodeCondEquals("b0b")))
        val exists1 = new MongoQueryNodeField("c", new MongoQueryNodeCondExists)
        val exists2 = new MongoQueryNodeField("d", new MongoQueryNodeCondExists)
        val or = new MongoQueryNodeOr(List(exists1, exists2))
        val and2 = new MongoQueryNodeAnd(List(compare, field2))
        val union = new MongoQueryNodeUnion(List(compare, or))

        println("---------------------------------")
        // Remove top-level AND
        var result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, and2)
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("tititutu"))
        assertTrue(cleanString(result(0).query).contains("'a':{$gt:10}"))
        assertTrue(cleanString(result(0).query).contains("'b.0.b':{$eq:'b0b'}"))

        println("---------------------------------")
        result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(compare, field2)))
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("tititutu"))
        assertTrue(cleanString(result(0).query).contains("'a':{$gt:10}"))
        assertTrue(cleanString(result(0).query).contains("'b.0.b':{$eq:'b0b'}"))

        println("---------------------------------")
        // Remove top-level AND from AND(OR, AND)
        result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(or, and2)))
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("tititutu"))
        assertTrue(cleanString(result(0).query).contains("'a':{$gt:10}"))
        assertTrue(cleanString(result(0).query).contains("'b.0.b':{$eq:'b0b'}"))
        assertTrue(cleanString(result(0).query).contains("$or:[{'c':{$exists:true}},{'d':{$exists:true}}]"))

        println("---------------------------------")
        // Top-level UNION
        result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, union)
        println(result)
        assertTrue(result.size == 2)
        assertTrue(cleanString(result(0).query).contains("tititutu"))
        assertTrue(cleanString(result(0).query).contains("'a':{$gt:10}"))
        assertTrue(cleanString(result(1).query).contains("tititutu"))
        assertTrue(cleanString(result(1).query).contains("$or:[{'c':{$exists:true}},{'d':{$exists:true}}]"))
    }

    @Test def test_mongoAbstractQuerytoConcrete_FusionQueries() {
        println("------ test_mongoAbstractQuerytoConcrete_FusionQueries")
        var fromPart = new MongoDBQuery("collection", "")

        val compare = new MongoQueryNodeField("a.b", new MongoQueryNodeCompare(MongoQueryNodeCompare.Operator.GT, "10"))
        val exists = new MongoQueryNodeField("a.b", new MongoQueryNodeCondExists)
        val field = new MongoQueryNodeField("c", List(new MongoQueryNodeCondEquals("ccc")))

        println("---------------------------------")
        var result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(compare, field, exists)))
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("'a.b':{$gt:10,$exists:true}"))
        assertTrue(cleanString(result(0).query).contains("'c':{$eq:'ccc'}"))

        println("---------------------------------")
        result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(compare, field, exists)))
        println(result)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("'a.b':{$gt:10,$exists:true}"))
        assertTrue(cleanString(result(0).query).contains("'c':{$eq:'ccc'}"))

    }

    @Test def test_mongoAbstractQuerytoConcrete_FusionQueries_ElemMatch() {
        println("------ test_mongoAbstractQuerytoConcrete_FusionQueries_ElemMatch")
        var fromPart = new MongoDBQuery("collection", "")

        val elemmatch = new MongoQueryNodeField(
            "a.b",
            List(new MongoQueryNodeElemMatch(new MongoQueryNodeCompare(MongoQueryNodeCompare.Operator.GT, "10"))),
            List(new MongoQueryProjectionArraySlice("a.b", "10")))
        val elemmatch2 = new MongoQueryNodeField(
            "a.b", new MongoQueryNodeElemMatch(new MongoQueryNodeCompare(MongoQueryNodeCompare.Operator.LT, "20")))
        val arraysize = new MongoQueryNodeField(
            "a.b", new MongoQueryNodeCompare(MongoQueryNodeCompare.Operator.SIZE, "99"))

        // 2 arrays with one slice
        var result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(elemmatch, elemmatch2)))
        println(result(0).toString)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("{'a.b':{$elemMatch:{$gt:10,$lt:20}}}"))
        assertTrue(cleanString(result(0).projection).contains("{'a.b':{$slice:10}}"))

        println("---------------------------------")
        // 2 arrays with one slice and one size
        result = queryTranslator.mongoAbstractQuerytoConcrete(fromPart, Set.empty, new MongoQueryNodeAnd(List(elemmatch, elemmatch2, arraysize)))
        println(result(0).toString)
        assertTrue(result.size == 1)
        assertTrue(cleanString(result(0).query).contains("{'a.b':{$size:99,$elemMatch:{$gt:10,$lt:20}}}"))
        assertTrue(cleanString(result(0).projection).contains("{'a.b':{$slice:10}}"))
    }

    @Test def test_transTPm_NonNormalizedTM() {
        println("------ test_transTPm_NonNormalizedTM")

        // Triple pattern: ?x ex:starring "T. Leung"
        val s = NodeFactory.createVariable("x")
        val p = NodeFactory.createURI("http://example.org/starring")
        val o = NodeFactory.createLiteral("T. Leung")
        val tp = Triple.create(s, p, o)

        try {
            var tm = mappingDocument.getClassMappingsByName("TM_NoPOM")
            var res = queryTranslator.transTPm(TpBinding(tp, tm), None)
            fail()
        } catch { case e: Exception => {} }

        try {
            var tm = mappingDocument.getClassMappingsByName("TM_MultiplePOM")
            var res = queryTranslator.transTPm(TpBinding(tp, tm), None)
            fail()
        } catch { case e: Exception => {} }
    }

    @Test def test_transTPm_noparentTM() {
        println("------ test_transTPm_noparentTM")

        // Triple pattern: ?x ex:starring "T. Leung"
        val s = NodeFactory.createVariable("x")
        val p = NodeFactory.createURI("http://example.org/starring")
        val o = NodeFactory.createLiteral("T. Leung")
        val tp = Triple.create(s, p, o)

        val Q = queryTranslator.transTPm(TpBinding(tp, tmMovies), None)
        assertTrue(Q.isInstanceOf[AbstractQueryAtomicMongo])
        val q = Q.asInstanceOf[AbstractQueryAtomicMongo]
        assertEquals(q.from.getValue, "db.movies.find({decade:{$exists:true}})")
        assertTrue(q.project.head.references.contains("$.code"))
        assertEquals("?x", q.project.head.as.get)
    }

    @Test def test_transTPm_parentTM() {
        println("------ test_transTPm_parentTM")

        // Triple pattern: ?x ex:directed <http://example.org/movie/Manh>
        val s = NodeFactory.createVariable("x")
        val p = NodeFactory.createURI("http://example.org/directed")
        val o = NodeFactory.createURI("http://example.org/movie/Manh")
        val tp = Triple.create(s, p, o)

        val Q = queryTranslator.transTPm(TpBinding(tp, tmDirectors), None)
        assertTrue(Q.isInstanceOf[AbstractQueryInnerJoinRef])
        val q = Q.asInstanceOf[AbstractQueryInnerJoinRef]
        assertEquals("$.directed.*", q.childRef)
        assertEquals("$.dirname", q.parentRef)
    }

    @Test def test_excludeTriplesAboutCollecOrContainer0() {
        println("------ test_excludeTriplesAboutCollecOrContainer0")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX ex: <http://example.org/>
                    SELECT * WHERE { ?x a ?y . }"""

        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)
        val op2 = queryTranslator.excludeTriplesAboutCollecOrContainer(op)
        println(op2)
        assertTrue(op2.isDefined)
    }

    @Test def test_excludeTriplesAboutCollecOrContainer() {
        println("------ test_excludeTriplesAboutCollecOrContainer")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX ex: <http://example.org/>

                    SELECT *
                    WHERE {
                        ?x ex:directed ?y .
                        ?y ex:starringBag ?z .
                        ?z a rdf:Bag .
                        ?z rdf:_1 ?actor1 .
                    }"""

        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)
        val op2 = queryTranslator.excludeTriplesAboutCollecOrContainer(op).get
        println(op2)
        assertTrue(op2.isInstanceOf[OpBGP])
        val triples = op2.asInstanceOf[OpBGP].getPattern.getList.toList.map(_.toString)
        println(triples)

        var tp = Triple.create(NodeFactory.createVariable("x"),
            NodeFactory.createURI("http://example.org/directed"),
            NodeFactory.createVariable("y"))
        assertTrue(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("y"),
            NodeFactory.createURI("http://example.org/starringBag"),
            NodeFactory.createVariable("z"))
        assertTrue(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("z"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"),
            NodeFactory.createVariable("actor1"))
        assertFalse(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("z"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#Bag"))
        assertFalse(triples.contains(tp.toString))
    }

    @Test def test_excludeTriplesAboutCollecOrContainer2() {
        println("------ test_excludeTriplesAboutCollecOrContainer2")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX ex: <http://example.org/>

                    SELECT *
                    WHERE {
                        ?x ex:directed ?y .
                        ?y ex:starringList ?z .
                        ?z a rdf:List .
                        ?z rdf:first ?actor1 .
                    }"""

        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)
        val op2 = queryTranslator.excludeTriplesAboutCollecOrContainer(op).get
        println(op2)
        assertTrue(op2.isInstanceOf[OpBGP])
        val triples = op2.asInstanceOf[OpBGP].getPattern.getList.toList.map(_.toString)
        println(triples)

        var tp = Triple.create(NodeFactory.createVariable("x"),
            NodeFactory.createURI("http://example.org/directed"),
            NodeFactory.createVariable("y"))
        assertTrue(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("y"),
            NodeFactory.createURI("http://example.org/starringList"),
            NodeFactory.createVariable("z"))
        assertTrue(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("z"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"),
            NodeFactory.createVariable("actor1"))
        assertFalse(triples.contains(tp.toString))

        tp = Triple.create(NodeFactory.createVariable("z"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#List"))
        assertFalse(triples.contains(tp.toString))
    }

    @Test def test_excludeTriplesAboutCollecOrContainer_NoExclusion() {
        println("------ test_excludeTriplesAboutCollecOrContainer_NoExclusion")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX ex: <http://example.org/>
                    SELECT DISTINCT ?x WHERE { ?x a ?y . } GROUP BY ?x HAVING (?x > 10)"""
        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)

        val op2 = queryTranslator.excludeTriplesAboutCollecOrContainer(op)
        println(op2)
        assertEquals(op, op2.get)

    }

    @Test def test_excludeTriplesAboutCollecOrContainer_NoExclusion2() {
        println("------ test_excludeTriplesAboutCollecOrContainer_NoExclusion2")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX ex: <http://example.org/>
                    SELECT DISTINCT * WHERE { ?x a ?y . } ORDER BY ?x DESC(?y) LIMIT 1000"""
        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)

        val op2 = queryTranslator.excludeTriplesAboutCollecOrContainer(op)
        println(op2)
        assertEquals(op, op2.get)
    }
    @Test def test_allVarsProjectedAsConstantTermMaps() {
        println("------ test_allVarsProjectedAsConstantTermMaps")

        val q = """ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX : <http://example.org/>
                    SELECT distinct ?p WHERE { VALUES ?p { :abc :def } } LIMIT 1000"""
        val query = QueryFactory.create(q)
        val op = Algebra.compile(query)
        println(op)
    }

}