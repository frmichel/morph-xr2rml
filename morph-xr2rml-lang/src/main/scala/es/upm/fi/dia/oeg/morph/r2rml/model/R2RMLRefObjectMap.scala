package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions._

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants

class R2RMLRefObjectMap(
        val parentTriplesMapResource: Resource,
        val joinConditions: Set[R2RMLJoinCondition],
        val termType: Option[String]) extends java.io.Serializable {

    var rdfNode: RDFNode = null;

    def getRelationName() = this.rdfNode.asResource().getLocalName();
    def getRangeClassMapping() = this.getParentTripleMapName;

    def getParentTripleMapName(): String = {
        this.parentTriplesMapResource.getURI();
    }

    override def toString(): String = {
        this.parentTriplesMapResource.toString() + ": " + joinConditions;
    }

    /**
     * Return true if the term type is a native R2RML term type, i.e. one of IRI, literal or blank node
     */
    def isR2RMLTermType(): Boolean = {
        if (termType.isDefined) {
            val tt = termType.get
            (tt == Constants.R2RML_IRI_URI) || (tt == Constants.R2RML_LITERAL_URI) || (tt == Constants.R2RML_BLANKNODE_URI)
        } else
            true
    }
}

object R2RMLRefObjectMap {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(resource: Resource): R2RMLRefObjectMap = {

        val parentTMStmt = resource.getProperty(Constants.R2RML_PARENTTRIPLESMAP_PROPERTY);
        val parentTM =
            if (parentTMStmt != null) {
                parentTMStmt.getObject().asInstanceOf[Resource];
            } else null

        val joinCondStmts = resource.listProperties(Constants.R2RML_JOINCONDITION_PROPERTY);
        val joinConds: Set[R2RMLJoinCondition] =
            if (joinCondStmts != null) {
                joinCondStmts.map(joinCondStmt => {
                    R2RMLJoinCondition(joinCondStmt.getObject().asInstanceOf[Resource])
                }).toSet
            } else {
                val errorMessage = "No join conditions defined.";
                logger.warn(errorMessage);
                Set.empty;
            }

        new R2RMLRefObjectMap(parentTM, joinConds, extractTermType(resource));
    }

    /**
     * In xR2RML a referencing object map may have a term type. In that case if should
     * only be an RDF collection or container term type.
     */
    private def extractTermType(resource: Resource): Option[String] = {

        val termTypeStmt = resource.getProperty(Constants.R2RML_TERMTYPE_PROPERTY);
        if (termTypeStmt != null) {
            Some(termTypeStmt.getObject().toString());
        } else
            None
    }

    def isRefObjectMap(resource: Resource): Boolean = {
        resource.hasProperty(Constants.R2RML_PARENTTRIPLESMAP_PROPERTY);
    }

    /**
     * Create a set of ReferencingObjectMaps by checking the rr:objectMap properties of a PredicateObjectMap
     *
     * @param resource A Jena node representing a PredicateObjectMap instance
     * @return a possibly empty set of R2RMLRefObjectMap's
     */
    def extractRefObjectMaps(resource: Resource): Set[R2RMLRefObjectMap] = {

        val stmts = resource.listProperties(Constants.R2RML_OBJECTMAP_PROPERTY);
        val refObjMaps = stmts.toList().flatMap(stmt => {
            val stmtObjectRes = stmt.getObject().asInstanceOf[Resource];
            if (R2RMLRefObjectMap.isRefObjectMap(stmtObjectRes)) {
                val rom = R2RMLRefObjectMap(stmtObjectRes);
                rom.rdfNode = stmtObjectRes;
                Some(rom);
            } else {
                None;
            }
        });
        refObjMaps.toSet
    }
}