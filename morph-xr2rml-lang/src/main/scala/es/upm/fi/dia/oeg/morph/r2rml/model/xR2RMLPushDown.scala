package es.upm.fi.dia.oeg.morph.r2rml.model

import com.hp.hpl.jena.rdf.model.{RDFNode, Resource}
import es.upm.fi.dia.oeg.morph.base.Constants

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
}
