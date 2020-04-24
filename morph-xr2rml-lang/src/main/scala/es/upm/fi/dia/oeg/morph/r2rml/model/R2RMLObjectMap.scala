package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions._

import org.apache.log4j.Logger

import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants

class R2RMLObjectMap(
    override val termMapType: Constants.MorphTermMapType.Value,
    override val termType: Option[String],
    override val datatype: Option[String],
    override val languageTag: Option[String],
    override val languageMap: Option[String],
    override val nestedTermMap: Option[xR2RMLNestedTermMap],
    override val refFormulation: String,
    override val listPushDown: List[xR2RMLPushDown])

    extends R2RMLTermMap(termMapType, termType, datatype, languageTag, languageMap, nestedTermMap, refFormulation, listPushDown) {

    var termtype = this.inferTermType
}

object R2RMLObjectMap {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(rdfNode: RDFNode, refFormulation: String): R2RMLObjectMap = {
        val coreProperties = AbstractTermMap.extractCoreProperties(rdfNode, refFormulation);

        val termMapType = coreProperties.getTermMapType
        val termType = coreProperties.getTermType
        val datatype = coreProperties.getDatatype
        val languageTag = coreProperties.getLanguageTag
        val languageMap = coreProperties.getLanguageMap
        val extractedNestedTermMap = coreProperties.getNestedTM
        val listPushDown = coreProperties.getListPushDown

        // A term map with an RDF collection/container term type must have a nested term map.
        // If this is not the case here, define a default nested term type (see xR2RML specification 3.2.1.3):
        // it has term type rr:Literal if the parent term map is column- or reference-valued,
        // it has term type rr:iri if the parent term map is template-valued.
        val nestedTermMap = if (AbstractTermMap.isRdfCollectionTermType(termType) && (!extractedNestedTermMap.isDefined)) {
            val ntmTermType = termMapType match {
                case Constants.MorphTermMapType.ColumnTermMap => Constants.R2RML_LITERAL_URI
                case Constants.MorphTermMapType.ReferenceTermMap => Constants.R2RML_LITERAL_URI
                case Constants.MorphTermMapType.TemplateTermMap => Constants.R2RML_IRI_URI
                case _ => Constants.R2RML_LITERAL_URI
            }

            // The default nested term map has no reference nor template => simple nested term map
            val nestedTermMapType = Constants.MorphTermMapType.SimpleNestedTermMap

            val ntm = new xR2RMLNestedTermMap(termMapType, nestedTermMapType, Some(ntmTermType), None, None, None, None, refFormulation, List.empty)
            if (logger.isDebugEnabled()) logger.debug("Collection/container term type with no nested term map. Defining default nested term map: " + ntm)
            Some(ntm)
        } else
            coreProperties.getNestedTM;

        val om = new R2RMLObjectMap(termMapType, termType, datatype, languageTag, languageMap, nestedTermMap, refFormulation, listPushDown);
        om.parse(rdfNode);
        om;
    }

    /**
     * Create a set of ObjectMaps by checking the rr:objectMap properties of a PredicateObjectMap
     *
     * @param resource A Jena node representing a PredicateObjectMap instance
     * @param refFormulation the current reference formulation given in the configuration file
     * @return a possibly empty set of R2RMLRefObjectMap's
     */
    def extractObjectMaps(resource: Resource, refFormulation: String): Set[R2RMLObjectMap] = {
        logger.trace("Looking for object maps")
        val tms = R2RMLTermMap.extractTermMaps(resource, Constants.MorphPOS.obj, refFormulation);
        val result = tms.map(tm => tm.asInstanceOf[R2RMLObjectMap]);
        if (result.isEmpty)
            logger.trace("No object map found.")
        result;
    }
}