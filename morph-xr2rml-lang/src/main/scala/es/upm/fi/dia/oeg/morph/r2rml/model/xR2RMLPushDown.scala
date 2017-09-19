package es.upm.fi.dia.oeg.morph.r2rml.model

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.hp.hpl.jena.rdf.model.{RDFNode, Resource, Statement}
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import org.apache.log4j.Logger

import scala.collection.JavaConversions._
/**
  * Created by freddy on 06/09/2017.
  */
class xR2RMLPushDown(val alias:Option[String]) extends IReferenceTermMap {
  override def toString = s"xR2RMLPushDown(alias=$alias, reference=$reference)"


}

object xR2RMLPushDown {
  private val logger = Logger.getLogger(this.getClass().getName());

  // create an ObjectMapper instance.
  val mapper = new ObjectMapper


  def apply(resource: Resource) : xR2RMLPushDown = {
    val asStmt = resource.getProperty(Constants.xR2RML_AS_PROPERTY)
    val asString = if (asStmt == null) None else Some(asStmt.getObject().toString())
    val pushDownObject = new xR2RMLPushDown(asString);

    val referenceStmt = resource.getProperty(Constants.xR2RML_REFERENCE_PROPERTY)
    val referenceString = if (referenceStmt == null) None else Some(referenceStmt.getObject().toString())
    pushDownObject.reference = referenceString.getOrElse(null);

    pushDownObject
  }

  def extractPushDownTags(resource: Resource) : List[xR2RMLPushDown] = {
    val stmtIteratorPushDown = resource.listProperties(Constants.xR2RML_PUSHDOWN_PROPERTY);
    if (stmtIteratorPushDown != null) {
      val listPushDown = stmtIteratorPushDown.toList
      val result: List[xR2RMLPushDown] = listPushDown.map(pushDown => {
        val resourcePushDown = pushDown.getObject.asResource();
        xR2RMLPushDown(resourcePushDown)
      }).toList
      result
    } else
      Nil
  }

  def extractPushDownTags(rdfNode: RDFNode) : List[xR2RMLPushDown] = {
    rdfNode match {
      case resource: Resource => { xR2RMLPushDown.extractPushDownTags(resource) }
      case _ => Nil
    }
  }

  def generatePushDownFieldsFromJsonNode(listPushDown:List[xR2RMLPushDown], jsonNode:JsonNode) : Map[String, Any] = {
    val result = if(jsonNode.isObject) {
      val objectNode = jsonNode.asInstanceOf[ObjectNode];
      val pushedFields = this.generatePushDownFieldsFromObjectNode(listPushDown, objectNode);
      pushedFields;
    } else if(jsonNode.isArray) {
      throw new MorphException("Pushing down fields/values from a JSON array is not supported!");
    } else {
      throw new MorphException(s"Unsupported JSON type found when pushing down fields/values: $jsonNode")
    }

    result
  }


  def generatePushDownFieldsFromObjectNode(listPushDown:List[xR2RMLPushDown], objectNode:ObjectNode) : Map[String, Any] = {
    val pushedFields:Map[String, Any] = listPushDown.map(pushDown => {
      val pdReference = pushDown.reference;
      //val pdReferenceIDReplaced = this.replaceIDField(pdReference);
      val pdReferenceKey = pdReference.replaceAllLiterally("$.", "")
      //val pdReferenceKeyIDReplaced = this.replaceIDField(pdReference).replaceAllLiterally("$.", "");

      //val pdReferenceValue:Any = jsonDocAsMap.get(pdReferenceKey).get
      val pdReferenceValue = if(pdReferenceKey.equals("_id")) {
        val idValue = objectNode.get(pdReferenceKey);
        val oidValue = idValue.get("$oid");
        if(oidValue == null) {
          idValue
        } else {
          oidValue
        }
      } else {
        objectNode.get(pdReferenceKey)
      }
//      logger.info("pdReferenceValue = " + pdReferenceValue)


      val pdReferenceValueReplaced = if(pdReferenceValue.isTextual) {
        pdReferenceValue.toString.replaceAll("\"", "");
      } else {
        pdReferenceValue
      }

      val pdAlias = pushDown.alias
      val pdAliasValue:String = if(pdAlias.isDefined) {
        pdAlias.get
      } else { pdReferenceKey }
      //logger.info("pdAliasValue = " + pdAliasValue)

      (pdAliasValue -> pdReferenceValueReplaced)
    }).toMap
    pushedFields
  }

  def generatePushDownFieldsFromJsonString(listPushDown:List[xR2RMLPushDown], jsonString:String) : Map[String, Any] = {

    // use the ObjectMapper to read the json string and create a tree
    try {
      val node:JsonNode = mapper.readTree(jsonString)
      this.generatePushDownFieldsFromJsonNode(listPushDown, node);
    } catch {
      case e:Exception => {
        logger.error(s"Error occured when trying to generate push down fields from JSON String $jsonString")
        Map.empty
      }
    }



  }

  def insertPushedDownFieldsIntoJsonString(jsonString:String, pushedFields:Map[String, Any]): JsonNode = {
    val mapper = new ObjectMapper

    if(jsonString == null) {
      null
    } else {
      val jsonNode = mapper.readTree(jsonString)
      this.insertPushedDownFieldsIntoJsonNode(jsonNode, pushedFields);
      jsonNode;
    }
  }

  def insertPushedDownFieldsIntoListJsonString(listJsonString:List[String], pushedFields:Map[String, Any]): List[JsonNode] = {
    if(listJsonString == null) {
      null
    } else {
      listJsonString.map(jsonString => this.insertPushedDownFieldsIntoJsonString(jsonString, pushedFields) )
    }
  }

  def insertPushedDownFieldsIntoJsonNode(jsonNode:JsonNode, pushedFields:Map[String, Any]): Unit = {
    if(jsonNode != null) {
      if(jsonNode.isArray) {
        val arrayNode = jsonNode.asInstanceOf[ArrayNode]
        this.insertPushedDownFieldsIntoArrayNode(arrayNode, pushedFields)
      } else if(jsonNode.isObject) {
        val objectNode = jsonNode.asInstanceOf[ObjectNode];
        this.insertPushedDownFieldsIntoObjectNode(objectNode, pushedFields)
      } else {
        throw new MorphException("Unsupported type of JSON Node : " + jsonNode);
      }
    }
  }

  def insertPushedDownFieldsIntoObjectNode(objectNode:ObjectNode, pushedFields:Map[String, Any]): Unit = {
    if(objectNode != null) {
      for(pushedFieldKey <- pushedFields.keys) {
        val pushedFieldValue = pushedFields(pushedFieldKey);
        objectNode.put(pushedFieldKey, pushedFieldValue.toString.replaceAll("\"", ""));
      }
    }

  }

  def insertPushedDownFieldsIntoArrayNode(arrayNode:ArrayNode, pushedFields:Map[String, Any]): Unit = {
    if(arrayNode != null) {
      val it = arrayNode.iterator();
      while(it.hasNext) {
        val jsonNode = it.next();
        this.insertPushedDownFieldsIntoJsonNode(jsonNode, pushedFields);
      }
    }
  }


}
