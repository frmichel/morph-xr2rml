package es.upm.fi.dia.oeg.morph.base.querytranslator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import org.apache.jena.graph.NodeFactory

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLPredicateMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLSubjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap

/**
 * @author Franck Michel, I3S laboratory
 *
 */
class MorphBaseTriplePatternBindingsTest {

    val triplePatternBinder = new MorphBaseTriplePatternBinder(null)

    @Test
    def test_compatible_VariableTpTerm {
        var variable = NodeFactory.createVariable("x")

        var termMap: R2RMLTermMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, // data type
            None, // language tag
            None, // language map
            None, // xR2RMLNestedTermMap]
            "JSONPath" // Reference formulation
            , Nil //PushDown
        )
        termMap.setConstantValue("val")
        assertTrue(triplePatternBinder.compatible(termMap, variable))

        termMap = new R2RMLSubjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            Set.empty, // class URIs
            Set.empty, // graph URIs
            "JSONPath" // Reference formulation
            , Nil //PushDown
        )
        assertTrue(triplePatternBinder.compatible(termMap, variable))

        termMap = new R2RMLPredicateMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_BLANKNODE_URI), // term type
            "JSONPath" // Reference formulation
            , Nil //PushDown
        )
        assertTrue(triplePatternBinder.compatible(termMap, variable))
    }

    @Test
    def test_compatible_ConstantTermMap_LiteralTpTerm {

        var literal = NodeFactory.createLiteral("3")
        var literal_lang = NodeFactory.createLiteral("3", "fr", false)
        var literal_langde = NodeFactory.createLiteral("3", "de", false)
        var literal_dt = NodeFactory.createLiteral("3", org.apache.jena.datatypes.xsd.XSDDatatype.XSDint)
        var literal_dtl = NodeFactory.createLiteral("3", org.apache.jena.datatypes.xsd.XSDDatatype.XSDlong)

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, // data type
            None, // language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.setConstantValue("3")
        assertTrue(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_lang))
        assertTrue(triplePatternBinder.compatible(termMap, literal_dt))

        termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            Some("http://www.w3.org/2001/XMLSchema#int"), // data type
            None, // language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.setConstantValue("3")
        assertTrue(triplePatternBinder.compatible(termMap, literal_dt))
        assertFalse(triplePatternBinder.compatible(termMap, literal_dtl))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_lang))

        termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, // data type
            Some("fr"), // language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.setConstantValue("3")
        assertTrue(triplePatternBinder.compatible(termMap, literal_lang))
        assertFalse(triplePatternBinder.compatible(termMap, literal_langde))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_dt))
    }

    @Test
    def test_compatible_ConstantTermMap_IriTpTerm {
        var iri = NodeFactory.createURI("http://example.org/%20starring")
        var iri2 = NodeFactory.createURI("http://example.org/starring/star")
        var literal = NodeFactory.createLiteral("http://example.org/starring")
        var bn = NodeFactory.createAnon()

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.setConstantValue("http://example.org/%20starring")
        assertTrue(triplePatternBinder.compatible(termMap, iri))
        assertFalse(triplePatternBinder.compatible(termMap, iri2))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, bn))
    }

    @Test
    def test_compatible_ReferenceTermMap_LiteralTpTerm {
        var literal = NodeFactory.createLiteral("3")
        var literal_lang = NodeFactory.createLiteral("3", "fr", false)
        var literal_langde = NodeFactory.createLiteral("3", "de", false)
        var literal_dt = NodeFactory.createLiteral("3", org.apache.jena.datatypes.xsd.XSDDatatype.XSDstring)
        var iri = NodeFactory.createURI("http://example.org/starring")
        var bn = NodeFactory.createAnon()

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        
        assertTrue(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_lang))
        assertTrue(triplePatternBinder.compatible(termMap, literal_dt))
        assertFalse(triplePatternBinder.compatible(termMap, iri))
        assertFalse(triplePatternBinder.compatible(termMap, bn))

        termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            Some("http://www.w3.org/2001/XMLSchema#string"), // data type
            None, // language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatible(termMap, literal_dt))
        assertTrue(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_lang))

        termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, // data type
            Some("fr"), // language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatible(termMap, literal_lang))
        assertFalse(triplePatternBinder.compatible(termMap, literal_langde))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, literal_dt))
    }

    @Test
    def test_compatible_ReferenceTermMap_IriTpTerm {
        var iri = NodeFactory.createURI("http://example.org/%20starring")
        var literal = NodeFactory.createLiteral("val")
        var bn = NodeFactory.createAnon()

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatible(termMap, iri))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
        assertFalse(triplePatternBinder.compatible(termMap, bn))
    }

    @Test
    def test_compatible_TemplateTermMap_LiteralTpTerm {
        var literal = NodeFactory.createLiteral("http://example.org/starring/foo")
        var literal1 = NodeFactory.createLiteral("http://example.org/starring/foo/faa")
        var literal2 = NodeFactory.createLiteral("http://example.org/starring")
        var literal3 = NodeFactory.createLiteral("dav://example.org/starring/foo")
        var literal_lang = NodeFactory.createLiteral("http://example.org/starring/foo", "fr", false)
        var iri = NodeFactory.createURI("http://example.org/starring/foo")

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.TemplateTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.templateString = "http://example.org/{$.toto.*}/{$.x[5]}"
        assertTrue(triplePatternBinder.compatible(termMap, literal))
        assertTrue(triplePatternBinder.compatible(termMap, literal1))
        assertFalse(triplePatternBinder.compatible(termMap, literal2))
        assertFalse(triplePatternBinder.compatible(termMap, literal3))
        assertFalse(triplePatternBinder.compatible(termMap, literal_lang))
        assertFalse(triplePatternBinder.compatible(termMap, iri))
    }

    @Test
    def test_compatible_TemplateTermMap_IriTpTerm {
        var iri = NodeFactory.createURI("http://example.org/starring/foo")
        var iri1 = NodeFactory.createURI("http://example.org/starring/foo/faa")
        var iri2 = NodeFactory.createURI("http://example.org/starring")
        var iri3 = NodeFactory.createURI("dav://example.org/starring/foo")
        var literal = NodeFactory.createLiteral("http://example.org/starring/foo")

        var termMap = new R2RMLObjectMap(
            Constants.MorphTermMapType.TemplateTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        termMap.templateString = "http://example.org/{$.toto.*}/{$.x[5]}"
        assertTrue(triplePatternBinder.compatible(termMap, iri))
        assertTrue(triplePatternBinder.compatible(termMap, iri1))
        assertFalse(triplePatternBinder.compatible(termMap, iri2))
        assertFalse(triplePatternBinder.compatible(termMap, iri3))
        assertFalse(triplePatternBinder.compatible(termMap, literal))
    }

    @Test
    def test_compatible_TermMaps {
        // Compatible term maps with constant/reference/template

        var termMap1 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ConstantTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap1, termMap1))

        var termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))

        termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.TemplateTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))
    }

    @Test
    def test_compatible_TermMaps2 {
        // Language tags, data types

        var termMap1 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap1, termMap1))

        var termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, Some("fr"), // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap2, termMap2))
        assertFalse(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))

        termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            Some("http://www.w3.org/2001/XMLSchema#string"), None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap2, termMap2))
        assertFalse(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))
    }

    @Test
    def test_compatible_TermMaps3 {
        // Literal, IRI, BlankNode

        var termMap1 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_LITERAL_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown

        // Language tags, data types
        var termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_IRI_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap2, termMap2))
        assertFalse(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))

        termMap2 = new R2RMLObjectMap(
            Constants.MorphTermMapType.ReferenceTermMap, // term map type
            Some(Constants.R2RML_BLANKNODE_URI), // term type
            None, None, // data type, language tag
            None, // language map
            None, "JSONPath", Nil) // xR2RMLNestedTermMap, Reference formulation, PushDown
        assertTrue(triplePatternBinder.compatibleTermMaps(termMap2, termMap2))
        assertFalse(triplePatternBinder.compatibleTermMaps(termMap1, termMap2))
    }
}