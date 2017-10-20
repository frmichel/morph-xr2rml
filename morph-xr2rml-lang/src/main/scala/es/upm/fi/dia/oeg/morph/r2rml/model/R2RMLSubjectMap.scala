package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions._

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants

class R2RMLSubjectMap(
    termMapType: Constants.MorphTermMapType.Value,
    termType: Option[String],

    /** IRIs of classes defined with the rr:class property */
    val classURIs: Set[String],

    /** IRIs of target graphs defined in the subject map with the rr:graph or rr:graphMap properties */
    val graphMaps: Set[R2RMLGraphMap],

    /** Reference formulation from the logical source */
    refFormulaion: String,

    listPushDown: List[xR2RMLPushDown])

        extends R2RMLTermMap(termMapType, termType, None, None, None, refFormulaion, listPushDown) {

    var termtype = this.inferTermType
}

object R2RMLSubjectMap {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(rdfNode: RDFNode, refFormulation: String): R2RMLSubjectMap = {
        val coreProperties = AbstractTermMap.extractCoreProperties(rdfNode, refFormulation);
        val termMapType = coreProperties._1;
        val termType = coreProperties._2;
        val nestTM = coreProperties._5;
        val listPushDown = coreProperties._6;

        if (nestTM.isDefined)
            logger.error("A nested term map cannot be defined in a subject map. Ignoring.")

        if (AbstractTermMap.isRdfCollectionTermType(termType))
            logger.error("A subject map cannot have a term type: " + termType + ". Ignoring.")

        // List the optional rr:class properties of the subject map
        val classURIs: Set[String] = rdfNode match {
            case resourceNode: Resource => {
                val classStatements = resourceNode.listProperties(Constants.R2RML_CLASS_PROPERTY);
                val classURIsAux: Set[String] =
                    if (classStatements != null) {
                        classStatements.map(classStatement => { classStatement.getObject().toString(); }).toSet;
                    } else {
                        Set.empty;
                    }
                logger.trace("Found rr:class: " + classURIsAux)
                classURIsAux
            }
            case _ => { Set.empty }
        }

        // Find the optional rr:graph of rr:graphMap properties of the subject map
        val graphMaps: Set[R2RMLGraphMap] = rdfNode match {
            case resourceNode: Resource => { R2RMLGraphMap.extractGraphMaps(resourceNode, refFormulation); }
            case _ => { Set.empty }
        }

        val sm = new R2RMLSubjectMap(termMapType, termType, classURIs, graphMaps, refFormulation, listPushDown);

        sm.parse(rdfNode)
        sm
    }

    def extractSubjectMaps(resource: Resource, refFormulation: String): Set[R2RMLSubjectMap] = {
        logger.trace("Looking for subject maps")
        val tms = R2RMLTermMap.extractTermMaps(resource, Constants.MorphPOS.sub, refFormulation);
        val result = tms.map(tm => tm.asInstanceOf[R2RMLSubjectMap]);
        result;
    }
}