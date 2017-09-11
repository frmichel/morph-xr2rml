package es.upm.fi.dia.oeg.morph.r2rml.model

import com.hp.hpl.jena.rdf.model.{RDFNode, Resource, Statement}
import es.upm.fi.dia.oeg.morph.base.Constants
import scala.collection.JavaConversions._
/**
  * Created by freddy on 06/09/2017.
  */
class xR2RMLPushDown(val alias:Option[String]) extends IReferenceTermMap {
  override def toString = s"xR2RMLPushDown(alias=$alias, reference=$reference)"
}

object xR2RMLPushDown {
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
}
