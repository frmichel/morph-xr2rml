package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.log4j.Logger

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
/**
 * @author Freddy on 06/09/2017
 * @author Franck Michel, additions 20/10/2017
 *
 * A xrr:pushDown element can appear either within the logical source along with an rml:iterator,
 * or within a term map that has a nested term map.
 * It pushed a field of the root document or in fact at the current iteration level), into
 * documents of the sub-iteration level, may this new iteration be entailed by the rml:iterator
 * or by a nested term map.
 *
 * An xR2RMLPushDown has an xr:reference property whose object is an data element reference,
 * and an xrr:as (alias) property whose object is the name of the created element.
 * If the xrr:as is omitted, then the field is pushed down with the same name.
 */
class xR2RMLPushDown(val alias: Option[String]) extends IReferenceTermMap {

    override def toString = s"xR2RMLPushDown(alias=$alias, reference=$reference)"
}

object xR2RMLPushDown {
    private val logger = Logger.getLogger(this.getClass().getName());

    val mapper = new ObjectMapper

    /**
     * Constructor. The xrr:as is optional but xrr:reference is mandatory.
     * @throws MorphException in case there is no xrr:reference in the PushDown.
     */
    def apply(resource: Resource): xR2RMLPushDown = {
        val asStmt = resource.getProperty(Constants.xR2RML_AS_PROPERTY)
        val asString =
            if (asStmt == null) {
                if (logger.isDebugEnabled()) logger.debug("PushDown element without any xrr:as property: " + resource)
                None
            } else Some(asStmt.getObject().toString())

        val pushDownObject = new xR2RMLPushDown(asString);

        val referenceStmt = resource.getProperty(Constants.xR2RML_REFERENCE_PROPERTY)
        val referenceString = if (referenceStmt == null)
            throw new MorphException("Invalid mapping definition: the object of an xrr:pushDown property must have an xrr:reference property: " + resource.toString)
        else referenceStmt.getObject().toString()
        pushDownObject.reference = referenceString

        pushDownObject
    }

    /**
     * Parse xrr:pushDown properties and return a list of xR2RMLPushDown instances.
     * @return Empty list if there is no xrr:pushDown property
     */
    def extractPushDownTags(rdfNode: RDFNode): List[xR2RMLPushDown] = {
        rdfNode match {
            case resource: Resource => {
                val stmtIterator = resource.listProperties(Constants.xR2RML_PUSHDOWN_PROPERTY);
                if (stmtIterator != null) {
                    val listPushDown = stmtIterator.toList
                    val result = listPushDown.map(
                        pushDown => xR2RMLPushDown(pushDown.getObject.asResource())
                    )
                    result.toList
                } else
                    List.empty
            }
            case _ => List.empty
        }
    }

    /**
     * Apply a set of xR2RMLPushDown instances against a JSON document and return a map of pairs (alias name, alias value).
     * One pair is generated for each xR2RMLPushDown instance.
     *
     * @example An xR2RMLPushDown instance representing this:<br>
     * <code>[] xrr:pushDown [ xrr:reference "$.field1"; xrr:as "field2" ]</code><br>
     * will return a map with one pair ("field2", "value of $.field1 in the JSON document").
     *
     * @return Empty map if the list of PushDowns is empty but cannot return null. Empty map in case any error occurs.
     */
    def generatePushDownFieldsFromJsonString(listPushDown: List[xR2RMLPushDown], jsonString: String): Map[String, Any] = {

        if (listPushDown.isEmpty) Map.empty
        else {
            try {
                // Use the ObjectMapper to read the JSON string and create a tree
                val node: JsonNode = mapper.readTree(jsonString)

                if (node.isObject) {
                    this.generatePushDownFieldsFromObjectNode(listPushDown, node.asInstanceOf[ObjectNode])
                } else if (node.isArray)
                    throw new MorphException(s"Pushing down fields/values from a JSON array is not supported. Document: $node");
                else
                    throw new MorphException(s"Unsupported JSON node type found when pushing down fields/values: $node")
            } catch {
                case e: Exception => {
                    logger.error(s"Error occured when trying to generate push down fields from JSON String: $jsonString")
                    logger.error(e.getMessage)
                    e.printStackTrace()
                    Map.empty
                }
            }
        }
    }

    /**
     * Apply a set of xR2RMLPushDown instances against a JSON node and return a map of pairs (alias name, alias value).
     * One pair is generated for each xR2RMLPushDown instance.
     *
     * @example An xR2RMLPushDown instance representing this:<br>
     * <code>[] xrr:pushDown [ xrr:reference "$.field1"; xrr:as "field2" ]</code><br>
     * will return a map with one pair ("field2", "value of $.field1 in the JSON node").
     *
     * @return Empty map if the list of PushDowns is empty but cannot return null. Empty map in case any error occurs.
     */
    private def generatePushDownFieldsFromObjectNode(listPushDown: List[xR2RMLPushDown], objectNode: ObjectNode): Map[String, Any] = {

        val pushedFields: Map[String, Any] = listPushDown.map(pushDown => {

            val pdReferenceKey = pushDown.reference.replaceAllLiterally("$.", "")

            // Evaluate the reference against the document
            val idValue = objectNode.get(pdReferenceKey)
            val pdReferenceValue =
              if (idValue  != null)  {
                // If the field is an ObjectId (such as "_id") then return the inner $oid
                val oidValue = idValue.get("$oid")
                if (oidValue != null) oidValue else idValue
              } else null

            val pdReferenceValueReplaced =
              if (pdReferenceValue  != null) {
                if (pdReferenceValue.isTextual)
                   pdReferenceValue.toString.replaceAll("\"", "");
                else pdReferenceValue
              } else null

            // Use the xrr:as property if provided, otherwise use the name of the field whose value is pushed
            val pdAlias = pushDown.alias.getOrElse(pdReferenceKey)

            (pdAlias -> pdReferenceValueReplaced)
        }).filter(x => x._2 != null).toMap
        if (logger.isDebugEnabled()) logger.debug("Map of fields pushed down: " + pushedFields)
        pushedFields
    }

    /**
     * Insert a list of fields into a JSON document passed as a string
     * @return the new JSON document as a JsonNode
     */
    def insertPushedDownFieldsIntoJsonString(jsonString: String, pushedFields: Map[String, Any]): JsonNode = {
        val mapper = new ObjectMapper
        val jsonNode = mapper.readTree(jsonString)
        if (jsonNode == null)
            logger.error("xrr:pushDown ignored because JSON string cannot be converted to a JSON document: " + jsonString)
        else
            this.insertPushedDownFieldsIntoJsonNode(jsonNode, pushedFields)
        jsonNode
    }

    private def insertPushedDownFieldsIntoJsonNode(jsonNode: JsonNode, pushedFields: Map[String, Any]): Unit = {
        if (jsonNode.isArray)
            this.insertPushedDownFieldsIntoArrayNode(jsonNode.asInstanceOf[ArrayNode], pushedFields)
        else if (jsonNode.isObject)
            this.insertPushedDownFieldsIntoObjectNode(jsonNode.asInstanceOf[ObjectNode], pushedFields)
        else
            throw new MorphException("Unexpected type of JSON Node : " + jsonNode)
    }

    private def insertPushedDownFieldsIntoObjectNode(objectNode: ObjectNode, pushedFields: Map[String, Any]): Unit = {
        for ((pushedFieldKey, pushedFieldValue) <- pushedFields)
            objectNode.put(pushedFieldKey, pushedFieldValue.toString.replaceAll("\"", ""))
    }

    private def insertPushedDownFieldsIntoArrayNode(arrayNode: ArrayNode, pushedFields: Map[String, Any]): Unit = {
        val it = arrayNode.iterator();
        while (it.hasNext) {
            this.insertPushedDownFieldsIntoJsonNode(it.next(), pushedFields);
        }
    }
}
