package es.upm.fi.dia.oeg.morph.base.engine

import scala.collection.JavaConverters.seqAsJavaListConverter

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.vocabulary.RDF
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.RDFTerm
import es.upm.fi.dia.oeg.morph.base.RDFTermAlt
import es.upm.fi.dia.oeg.morph.base.RDFTermBag
import es.upm.fi.dia.oeg.morph.base.RDFTermBlankNode
import es.upm.fi.dia.oeg.morph.base.RDFTermIRI
import es.upm.fi.dia.oeg.morph.base.RDFTermList
import es.upm.fi.dia.oeg.morph.base.RDFTermLiteral
import es.upm.fi.dia.oeg.morph.base.RDFTermSeq
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap

/**
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
abstract class MorphBaseDataTranslator(val factory: IMorphFactory) {

    val logger = Logger.getLogger(this.getClass().getName());

    val encodeUnsafeCharsInUri = factory.getProperties.encodeUnsafeCharsInUri

    val encodeUnsafeCharsInDbValues = factory.getProperties.encodeUnsafeCharsInDbValues

    val literalTrim = factory.getProperties.literalTrim

    /**
     * Entry point of the materialization approach:<br>
     * Loop on all triples maps of the mapping graph and generate triples in the data materializer model,
     * based on the triples map: this consists in calculating the query, running it against the database,
     * translating results in RDF terms and making the triples.
     */
    def translateData_Materialization(mappingDocument: R2RMLMappingDocument): Unit = {
        val tms = mappingDocument.triplesMaps
        for (tm <- tms) {
            if (logger.isInfoEnabled) {
                logger.info("===============================================================================");
                logger.info("Starting data materialization of triples map " + tm.name);
            }
            try {
                this.generateRDFTriples(tm)
            } catch {
                case e: Exception => {
                    logger.error("Unexpected error while generatring triples for " + tm + ": " + e.getMessage);
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Materialize triples based on a triples map definition.
     * This is the method where all the database-specific work will be done
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    protected def generateRDFTriples(tm: R2RMLTriplesMap): Unit

    /**
     * Generate triples in the context of the query rewriting: run the queries and apply
     * the triples map bound to the query (GenericQuery.bondTriplesMap) to create RDF triples.
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    def generateRDFTriples(query: AbstractQuery): Unit

    /**
     * Create a literal JENA resource with optional datatype and language tag
     *
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    protected def createLiteralNode(term: RDFTermLiteral): Literal = {
        try {
            if (term.language.isDefined)
                factory.getMaterializer.model.createLiteral(term.value.asInstanceOf[String], term.language.get);
            else if (term.datatype.isDefined)
                factory.getMaterializer.model.createTypedLiteral(term.value, term.datatype.get);
            else
                factory.getMaterializer.model.createLiteral(term.value.asInstanceOf[String]);
        } catch {
            case e: Exception => {
                val msg = "Error translating literal term to RDFNode: " + term.value
                logger.error(msg);
                throw new MorphException(msg, e)
            }
        }
    }

    /**
     *  Create a JENA resource from an RDFTerm
     *
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    protected def createRDFNode(term: RDFTerm): RDFNode = {
        try {
            term match {
                case a: RDFTermLiteral => this.createLiteralNode(a)
                case a: RDFTermIRI => factory.getMaterializer.model.createResource(a.getIRI)
                
                case a: RDFTermBlankNode => factory.getMaterializer.model.createResource(new AnonId(a.value.asInstanceOf[String]))
                
                case a: RDFTermList => {
                    val members = a.getMembers.map(this.createRDFNode).asJava
                    val node = factory.getMaterializer.model.createList(members.iterator)
                    factory.getMaterializer.model.add(node, RDF.`type`, RDF.List)
                    node
                }
                case a: RDFTermBag => {
                    val node = factory.getMaterializer.model.createBag()
                    for (value <- term.asInstanceOf[RDFTermBag].getMembers) node.add(this.createRDFNode(value))
                    node
                }
                case a: RDFTermSeq => {
                    val node = factory.getMaterializer.model.createSeq()
                    for (value <- term.asInstanceOf[RDFTermSeq].getMembers) node.add(this.createRDFNode(value))
                    node
                }
                case a: RDFTermAlt => {
                    val node = factory.getMaterializer.model.createAlt()
                    for (value <- term.asInstanceOf[RDFTermAlt].getMembers) node.add(this.createRDFNode(value))
                    node
                }
                case _ => throw new MorphException("Error: unexpected RDFTerm class: " + term)
            }
        } catch {
            case e: Exception => {
                val msg = "Error translating literal term to RDFNode: " + term.value
                logger.error(msg);
                throw new MorphException(msg, e)
            }
        }
    }
}

object MorphBaseDataTranslator extends java.io.Serializable {

    /**
     * Convert a value (string, integer, boolean, etc) into an RDF term.
     * If collecTermType is not null, the RDF term is enclosed in a term of that type, i.e. a collection or container.
     *
     * The method always returns a List, either empty or with one RDf term.
     *
     * @param dbValue value read from the db, this may be a string, integer, boolean etc.,
     * @param collecTermType term type of the RDF collection/container. May be null.
     * @param memberTermType the term type of RDF terms representing the value
     * @param datatype URI of the data type
     * @param language language tag
     * @param encodeUnsafeCharsInUri
     * @param encodeUnsafeCharsInDbValues
     * @return a list of one RDF node (cannot be empty)
     */
    def translateSingleValue(
        dbValue: Object, collecTermType: Option[String], memberTermType: String, datatype: Option[String], language: Option[String],
        encodeUnsafeCharsInUri: Boolean, encodeUnsafeCharsInDbValues: Boolean, literalTrim: Boolean): List[RDFTerm] = {

        translateMultipleValues(List(dbValue), collecTermType, memberTermType, datatype, language,
            encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)
    }

    /**
     * Convert a list of values (string, integer, boolean, etc) into RDF terms.
     * If collecTermType is not null, RDF terms are enclosed in a term of that type, i.e. a collection or container
     *
     * The method always returns a List, either empty or with one RDF term in the case collecTermType is not null,
     * or with several elements otherwise.
     *
     * @param values list of values these may be strings, integers, booleans etc.,
     * @param collecTermType term type of the RDF collection/container. May be null.
     * @param memberTermType the term type of RDF terms representing values
     * @param datatype URI of the data type
     * @param language language tag
     * @return a list of one RDF node, possibly empty
     */
    def translateMultipleValues(
        values: List[Object],
        collecTermType: Option[String],
        memberTermType: String, datatype: Option[String], languageTag: Option[String],
        encodeUnsafeCharsInUri: Boolean, encodeUnsafeCharsInDbValues: Boolean, literalTrim: Boolean): List[RDFTerm] = {

        if (values.isEmpty) return List()

        // Convert values into RDF nodes
        val valuesAsRdfTerms = translateMultipleValues(
            values, memberTermType, datatype, languageTag,
            encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)

        val result: List[RDFTerm] =
            if (collecTermType.isDefined)
                // Create the collection/container with that list of nodes
                createCollection(collecTermType.get, valuesAsRdfTerms)
            else
                valuesAsRdfTerms
        result
    }

    /**
     * Create a list of RDF terms from a list of values
     *
     * @param values list of values: these may be strings, integers, booleans etc.,
     * @param termType term type of the current term map
     * @param datatype URI of the data type
     * @param language language tag
     * @return a list of RDF terms, possibly empty
     */
    def translateMultipleValues(
        values: List[Object], termType: String, datatype: Option[String], languageTag: Option[String],
        encodeUnsafeCharsInUri: Boolean, encodeUnsafeCharsInDbValues: Boolean, literalTrim: Boolean): List[RDFTerm] = {

        val result: List[RDFTerm] =
            // Create one RDF term for each of the values: the flatMap eliminates None elements, thus the result can be empty
            values.flatMap(value => {
                if (value == null) None // case when the database returned NULL
                else {
                    val node = termType match {
                        case Constants.R2RML_IRI_URI => this.createIRI(value, encodeUnsafeCharsInUri)
                        case Constants.R2RML_LITERAL_URI => this.createLiteral(value, datatype, languageTag, literalTrim)
                        case Constants.R2RML_BLANKNODE_URI => new RDFTermBlankNode(GeneralUtility.encodeUrl(value.toString))
                    }
                    Some(node)
                }
            })
        result
    }

    /**
     *  Create an IRI term after URL-encoding the string
     */
    protected def createIRI(iri: Object, encodeUnsafeCharsInUri: Boolean): RDFTerm = {

        var resultIRI = GeneralUtility.encodeURI(iri.toString, null)
        if (encodeUnsafeCharsInUri)
            resultIRI = GeneralUtility.encodeUrl(resultIRI)
        new RDFTermIRI(resultIRI)
    }

    /**
     * Create a literal term with optional datatype and language tag
     *
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    protected def createLiteral(value: Object, datatype: Option[String], language: Option[String], literalTrim: Boolean): RDFTerm = {
        try {
            val encodedValue =
                if (value == null) "" // case when the database returned NULL
                else {
                    if (literalTrim)
                        GeneralUtility.encodeLiteral(value.toString().trim())
                    else
                        GeneralUtility.encodeLiteral(value.toString())
                }

            val dataT: String = datatype.getOrElse(null)
            val valueConverted =
                if (dataT != null) { // the datatype may be Some(null), so keep this test here
                    if (dataT.equals(XSDDatatype.XSDdateTime.getURI.toString))
                        this.translateDateTime(encodedValue);
                    else if (dataT.equals(XSDDatatype.XSDboolean.getURI.toString))
                        this.translateBoolean(encodedValue);
                    else
                        encodedValue
                } else
                    encodedValue

            new RDFTermLiteral(valueConverted, datatype, language)
        } catch {
            case e: MorphException => {
                val msg = "Error creating literal term: " + value
                throw new MorphException(msg, e)
            }
        }
    }

    /**
     * Convert a list of RDF terms into an RDF term of type collection or container.
     * The result is returned as a list with one element.
     *
     * If the list of nodes is empty, return an empty list, but no empty collection/container is returned.
     *
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    def createCollection(collecTermType: String, values: List[RDFTerm]): List[RDFTerm] = {

        // If values is empty, then do not return a list with one empty RDF list, instead just return an empty list
        if (values.isEmpty) return List()

        val translated: RDFTerm = collecTermType match {
            case Constants.xR2RML_RDFLIST_URI => new RDFTermList(values)
            case Constants.xR2RML_RDFBAG_URI => new RDFTermBag(values)
            case Constants.xR2RML_RDFALT_URI => new RDFTermAlt(values)
            case Constants.xR2RML_RDFSEQ_URI => new RDFTermSeq(values)
            case _ => {
                val msg = "Term type " + collecTermType + " is not an RDF collection/container term type"
                throw new MorphException(msg)
            }
        }
        List(translated)
    }

    private def translateDateTime(value: String): String = {
        value.toString().trim().replaceAll(" ", "T");
    }

    private def translateBoolean(value: String): String = {
        if (value.equalsIgnoreCase("T") || value.equalsIgnoreCase("True") || value.equalsIgnoreCase("1"))
            "true"
        else
            "false"
    }

    /**
     * URL encode database values if the term type is IRI
     */
    def encodeResvdCharsIfUri(value: Object, termType: String, encodeUnsafeCharsInDbValues: Boolean) = {
        if (termType == Constants.R2RML_IRI_URI && value.isInstanceOf[String] && encodeUnsafeCharsInDbValues)
            GeneralUtility.encodeReservedChars(value.asInstanceOf[String])
        else value
    }
}