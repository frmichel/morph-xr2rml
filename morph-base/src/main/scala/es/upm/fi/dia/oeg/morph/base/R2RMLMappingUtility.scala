package es.upm.fi.dia.oeg.morph.base

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.Resource

/**
 * This utility class provides methods that navigate through JENA resources representing the mapping graph.
 */
class R2RMLMappingUtility {
}

object R2RMLMappingUtility {

    val XSDIntegerURI = XSDDatatype.XSDinteger.getURI();
    val XSDDoubleURI = XSDDatatype.XSDdouble.getURI();
    val XSDDateTimeURI = XSDDatatype.XSDdateTime.getURI();
    var datatypesMap: Map[String, String] = Map();
    datatypesMap += ("INT" -> XSDIntegerURI);
    datatypesMap += ("DOUBLE" -> XSDDoubleURI);
    datatypesMap += ("DATETIME" -> XSDDateTimeURI);

    /**
     *  Retrieve the predicate object map resources of a set of triples maps
     */
    def getPredicateObjectMapResources(tmsRes: List[Resource]): List[Resource] = {
        val result = {
            if (tmsRes.isEmpty) {
                Nil;
            } else {
                val resultHead = this.getPredicateObjectMapResources(tmsRes.head);
                val resultTail = this.getPredicateObjectMapResources(tmsRes.tail);
                resultHead ::: resultHead ::: resultTail;
            }
        }
        result;
    }

    /**
     *  Retrieve the predicate-object map resources of a triples map
     */
    def getPredicateObjectMapResources(tmRes: Resource): List[Resource] = {
        val pomStmtsIterator = tmRes.listProperties(Constants.R2RML_PREDICATEOBJECTMAP_PROPERTY);
        val pomStmts = pomStmtsIterator.toList().toList;
        val result = for { pomStmt <- pomStmts }
            yield pomStmt.getObject().asResource();
        result;
    }

    def getRRLogicalTable(tmRes: Resource) = {
        tmRes.getPropertyResourceValue(Constants.R2RML_LOGICALTABLE_PROPERTY);
    }

    def getRRLogicalSource(tmRes: Resource) = {
        tmRes.getPropertyResourceValue(Constants.xR2RML_LOGICALSOURCE_PROPERTY);
    }

    def getRRSubjectMapResource(tmRes: Resource) = {
        tmRes.getPropertyResourceValue(Constants.R2RML_SUBJECTMAP_PROPERTY);
    }

    def getRRLogicalTableTableName(tmRes: Resource) = {
        val rrLogicalTableResource = this.getRRLogicalTable(tmRes);
        val rrTableNameResource = rrLogicalTableResource.getPropertyResourceValue(Constants.R2RML_TABLENAME_PROPERTY);
        val result = {
            if (rrTableNameResource != null)
                rrTableNameResource.asLiteral().getValue().toString();
            else
                null;
        }
        result;
    }

    def getObjectMapResource(predicateObjectMapResource: Resource) = {
        val objectMapResource = predicateObjectMapResource.getPropertyResourceValue(Constants.R2RML_OBJECTMAP_PROPERTY);
        val parentTriplesMap = objectMapResource.getPropertyResourceValue(Constants.R2RML_PARENTTRIPLESMAP_PROPERTY);
        val result: Resource = {
            if (parentTriplesMap == null) {
                objectMapResource
            } else {
                null;
            }
        }
        result;
    }

    def getRefObjectMapResource(predicateObjectMapResource: Resource) = {
        val objectMapResource = predicateObjectMapResource.getPropertyResourceValue(Constants.R2RML_OBJECTMAP_PROPERTY);
        val parentTriplesMap = objectMapResource.getPropertyResourceValue(Constants.R2RML_PARENTTRIPLESMAP_PROPERTY);
        val result: Resource = {
            if (parentTriplesMap != null) {
                objectMapResource
            } else {
                null;
            }
        }

        result;
    }

    def getParentTriplesMapResource(objectMapResource: Resource) = {
        val parentTriplesMapResource = objectMapResource.getPropertyResourceValue(Constants.R2RML_PARENTTRIPLESMAP_PROPERTY);
        parentTriplesMapResource;
    }

    def getParentTriplesMapLogicalTableResource(objectMapResource: Resource) = {
        val parentTriplesMapResource = this.getParentTriplesMapResource(objectMapResource);
        val parentTriplesMapLogicalTableResource = this.getRRLogicalTable(Constants.R2RML_LOGICALTABLE_PROPERTY);
        parentTriplesMapLogicalTableResource
    }

    def getTermTypeResource(termMapResource: Resource) = {
        val termTypeResource = termMapResource.getPropertyResourceValue(Constants.R2RML_TERMTYPE_PROPERTY);
        termTypeResource;
    }

    def getRRColumnResource(termMapResource: Resource) = {
        val rrColumnResource = termMapResource.getProperty(Constants.R2RML_COLUMN_PROPERTY);
        rrColumnResource;
    }

    def getRRTemplateResource(termMapResource: Resource) = {
        val rrTemplateResource = termMapResource.getProperty(Constants.R2RML_TEMPLATE_PROPERTY);
        rrTemplateResource.getObject();
    }

    def getDatatypeResource(termMapResource: Resource) = {
        val datatypeResource = termMapResource.getPropertyResourceValue(Constants.R2RML_DATATYPE_PROPERTY);
        datatypeResource;
    }

    def getTermMapType(tmRes: Resource) = {
        val props = tmRes.listProperties().toList();

        val termMapType = {
            if (tmRes.getProperty(Constants.R2RML_CONSTANT_PROPERTY) != null)
                Constants.MorphTermMapType.ConstantTermMap;
            else if (tmRes.getProperty(Constants.R2RML_COLUMN_PROPERTY) != null)
                Constants.MorphTermMapType.ColumnTermMap;
            else if (tmRes.getProperty(Constants.R2RML_TEMPLATE_PROPERTY) != null)
                Constants.MorphTermMapType.TemplateTermMap;
            else if (tmRes.getProperty(Constants.xR2RML_REFERENCE_PROPERTY) != null)
                Constants.MorphTermMapType.ReferenceTermMap
            else
                Constants.MorphTermMapType.InvalidTermMapType;
        }
        termMapType;
    }

    def getTemplateValues(termMapResource: Resource, uri: String): Map[String, String] = {
        val termMapValueType = this.getTermMapType(termMapResource);

        val result: Map[String, String] = {
            if (termMapValueType == Constants.MorphTermMapType.TemplateTermMap) {
                val templateString = this.getRRTemplateResource(termMapResource).asLiteral().getValue().toString();
                val matchedTemplate = TemplateUtility.getTemplateMatching(templateString, uri);
                matchedTemplate.toMap;
            } else {
                Map();
            }
        }
        result;
    }
}