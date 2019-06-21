package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.setAsJavaSet

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.exception.MorphException

class R2RMLTriplesMap(
        val resource: Resource,
        val logicalSource: xR2RMLLogicalSource,
        val refFormulation: String,
        val subjectMap: R2RMLSubjectMap,
        val predicateObjectMaps: Set[R2RMLPredicateObjectMap]) {

    val name: String = resource.getLocalName

    val logger = Logger.getLogger(this.getClass());

    override def equals(c: Any): Boolean = {
        c.isInstanceOf[R2RMLTriplesMap] && this.name == c.asInstanceOf[R2RMLTriplesMap].name
    }

    override def hashCode(): Int = {
        (this.getClass.toString + this.name).hashCode
    }

    override def toString(): String = { this.name }

    def getConceptName(): String = {
        var result: String = null;

        val classURIs = this.subjectMap.classURIs;
        if (classURIs == null || classURIs.size() == 0) {
            logger.warn("No class URI defined for TriplesMap: " + this);
        } else {
            if (classURIs.size() > 1) {
                logger.warn("Multiple classURIs defined, only one is returned!");
            }
            result = classURIs.iterator().next();
        }

        return result;
    }

    def getPropertyMappings(propertyURI: String): Iterable[R2RMLPredicateObjectMap] = {
        val poms = this.predicateObjectMaps;
        val result = poms.filter(pom => {
            val predicateMapValue = pom.getPredicateMap(0).getOriginalValue();
            predicateMapValue.equals(propertyURI)
        })

        result;
    }

    def isPossibleInstance(uri: String): Boolean = {
        var result = false;

        val subjectMapTermMapType = this.subjectMap.termMapType;
        if (subjectMapTermMapType == Constants.MorphTermMapType.TemplateTermMap) {
            val templateValues = this.subjectMap.getTemplateValues(uri);
            if (templateValues != null && templateValues.size() > 0) {
                result = true;
                for (value <- templateValues.values()) {
                    if (value.contains("/"))
                        result = false;
                }
            }
        } else {
            result = false;
            val errorMessage = "Can't determine whether " + uri + " is a possible instance of " + this.toString();
            logger.warn(errorMessage);
        }

        result;
    }

    def getMappedClassURIs(): Iterable[String] = {
        this.subjectMap.classURIs;
    }

    def getLogicalSource(): xR2RMLLogicalSource = {
        this.logicalSource;
    }

    def getSubjectReferencedColumns(): List[String] = {
        this.subjectMap.getReferencedColumns();
    }
}

object R2RMLTriplesMap {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(tmResource: Resource, refFormulation: String): R2RMLTriplesMap = {
        logger.debug("Parsing triples map: " + tmResource.getLocalName())

        // --- Look for Logical table (rr:logicalTable) or Logical source (xrr:logicalSource) definition
        val logTabStmt = tmResource.getProperty(Constants.R2RML_LOGICALTABLE_PROPERTY)
        val logSrcStmt = tmResource.getProperty(Constants.xR2RML_LOGICALSOURCE_PROPERTY)

        var logSource: xR2RMLLogicalSource = {
            if (logTabStmt != null) {
                val res = logTabStmt.getObject().asInstanceOf[Resource]
                xR2RMLLogicalSource.parse(res, Constants.R2RML_LOGICALTABLE_URI, Constants.xR2RML_REFFORMULATION_COLUMN)

            } else if (logSrcStmt != null) {
                val res = logSrcStmt.getObject().asInstanceOf[Resource]
                xR2RMLLogicalSource.parse(res, Constants.xR2RML_LOGICALSOURCE_URI, refFormulation)

            } else {
                val errorMessage = "Error: missing rr:logicalTable and xrr:logicalSource"
                logger.fatal(errorMessage)
                throw new MorphException(errorMessage)
            }
        }
        logger.trace("Parsed logical table/source. " + logSource.toString)

        // --- Subject map
        val subjectMaps = R2RMLSubjectMap.extractSubjectMaps(tmResource, logSource.refFormulation)
        if (subjectMaps == null) {
            val errorMessage = "Error: missing rr:subjectMap";
            logger.fatal(errorMessage);
            throw new MorphException(errorMessage);
        }
        if (subjectMaps.size > 1) {
            val errorMessage = "Error: multiple rr:subjectMap predicates are not allowed";
            logger.fatal(errorMessage);
            throw new Exception(errorMessage);
        }
        if (!subjectMaps.iterator.hasNext) {
            val errorMessage = "Error: logical source defined without subject map."
            logger.fatal(errorMessage)
            throw new MorphException(errorMessage)
        }
        val subjectMap = subjectMaps.iterator.next;

        // --- Predicate-Object Maps
        logger.trace("Parsing predicate-object maps")
        val pomStmts = tmResource.listProperties(Constants.R2RML_PREDICATEOBJECTMAP_PROPERTY);
        val predicateObjectMaps: Iterable[R2RMLPredicateObjectMap] = if (pomStmts != null) {
            pomStmts.toList().map(pomStmt => {
                val pomObjectRes = pomStmt.getObject().asInstanceOf[Resource]
                val predicateObjectMap = R2RMLPredicateObjectMap(pomObjectRes, logSource.refFormulation)
                predicateObjectMap
            });
        } else {
            Set.empty
        };

        val tm = new R2RMLTriplesMap(tmResource, logSource, refFormulation, subjectMap, predicateObjectMaps.toSet);
        if (logger.isTraceEnabled) logger.trace("Completed parsing triples map: " + tmResource.getLocalName())
        tm;
    }
}

