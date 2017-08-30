package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.{Constants, TemplateUtility}
import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath
import org.apache.log4j.Logger

abstract class AbstractTermMap(
                                val termMapType: Constants.MorphTermMapType.Value,
                                val termType: Option[String],
                                val datatype: Option[String],
                                val languageTag: Option[String],
                                val nestedTermMap: Option[xR2RMLNestedTermMap]
                              ) {
  val logger = Logger.getLogger(this.getClass().getName());

  def getReferenceFormulation() : String;

  /**
    *  Parse the mixed syntax path values read from properties xrr:reference or rr:template
    *  and create corresponding MixedSyntaxPath instances.
    *  A non mixed-syntax path is returned as a mixed syntax path with only one PathExpression,
    *  the type of that PathExpression depends on the logical source reference formulation.
    *
    *  @return a list of MixedSyntaxPath instances. The list contains one element in case
    *  of a reference-valued term map, zero or more in case of a template-valued term map.
    *  Cannot return null but may return an empty list.
    */
   def getMixedSyntaxPaths(): List[MixedSyntaxPath] = {

    this.termMapType match {
      case Constants.MorphTermMapType.ReferenceTermMap => {
        List(MixedSyntaxPath(this.getReference(), this.getReferenceFormulation()))
      }
      case Constants.MorphTermMapType.TemplateTermMap => {
        // Get the list of template strings
        val tplStrings = TemplateUtility.getTemplateGroups(this.getTemplateString())

        // For each one, parse it as a mixed syntax path
        tplStrings.map(tplString => MixedSyntaxPath(tplString, this.getReferenceFormulation()))
      }
      case _ => { throw new Exception("Cannot build a MixedSyntaxPath with a term map of type " + this.termMapType) }
    }
  }

	def getReference() : String;

	def getTemplateString(): String;

  def getConstantValue(): String;

  def hasRDFCollectionTermType(): Boolean = {
    AbstractTermMap.isRdfCollectionTermType(this.inferTermType)
  }

  /**
    * Return the term type mentioned by property rr:termType or the default term type otherwise
    */
  def inferTermType: String = {
    this.termType.getOrElse(this.getDefaultTermType)
  }

  def getDefaultTermType: String = {
    val result = this match {
      case _: R2RMLObjectMap => {
        if (termMapType == Constants.MorphTermMapType.ColumnTermMap
          || termMapType == Constants.MorphTermMapType.ReferenceTermMap
          || languageTag.isDefined
          || datatype.isDefined) {
          Constants.R2RML_LITERAL_URI;
        } else
          Constants.R2RML_IRI_URI;
      }

      case _: R2RMLPredicateMap => {
        if (termType.isDefined && !termType.get.equals(Constants.R2RML_IRI_URI)) {
          throw new Exception("Illegal termtype in predicateMap: " + termType.get);
        } else
          Constants.R2RML_IRI_URI;
      }

      case _: R2RMLSubjectMap => { Constants.R2RML_IRI_URI; }
      case _ => { Constants.R2RML_IRI_URI; }
    }
    result;
  }



  // In case of a collection/container, a nested term map should give the details of term type
  //, datatype and language or the terms
  def calculateCollectionTermnTypeAndDataTypeAndLanguageTagAndMemberTermType() :
  (Option[String], Option[String], Option[String], String) = {
    val termMapTermType:String = this.inferTermType;

    if (this.hasRDFCollectionTermType) {
      if (this.nestedTermMap.isDefined) {
        // a nested term type MUST be defined in a term map with collection/container term type
        //memberTermType = termMap.nestedTermMap.get.inferTermType
        val ntmDataType = this.nestedTermMap.get.datatype;
        val ntmLanguageTag = this.nestedTermMap.get.languageTag;
        val ntmTermType = this.nestedTermMap.get.inferTermType;

        (Some(termMapTermType), ntmDataType, ntmLanguageTag, ntmTermType)
      } else {
        logger.warn("Term map with collection/container term type but no nested term map: " + this)
        (Some(termMapTermType), this.datatype, this.languageTag, Constants.R2RML_LITERAL_URI)
      }

    } else {
      (None, this.datatype, this.languageTag, termMapTermType)
    }
  }

  /**
    * Return true if the nested term map has a xrr:reference property
    */
  def isReferenceValued = { this.termMapType == Constants.MorphTermMapType.ReferenceTermMap }

  /**
    * Return true if the nested term map has a rr:template property
    */
  def isTemplateValued = { this.termMapType == Constants.MorphTermMapType.TemplateTermMap }

  def isConstantValued: Boolean = {
    this.termMapType == Constants.MorphTermMapType.ConstantTermMap
  }

  def isColumnValued: Boolean = {
    this.termMapType == Constants.MorphTermMapType.ColumnTermMap
  }



  def isReferenceOrTemplateValued: Boolean = {
    this.termMapType == Constants.MorphTermMapType.ReferenceTermMap ||
      this.termMapType == Constants.MorphTermMapType.TemplateTermMap
  }

  def isRdfCollectionTermType(): Boolean = {
    AbstractTermMap.isRdfCollectionTermType(this.termType)
  }

}

object AbstractTermMap {
  /**
    * Return true if the term type is one of RDF list, bag, seq, alt
    */
  def isRdfCollectionTermType(termType: String): Boolean = {
    (termType == Constants.xR2RML_RDFLIST_URI ||
      termType == Constants.xR2RML_RDFBAG_URI ||
      termType == Constants.xR2RML_RDFSEQ_URI ||
      termType == Constants.xR2RML_RDFALT_URI)
  }

  /**
    * Return true if the term type is one of RDF list, bag, seq, alt
    */
  def isRdfCollectionTermType(termTypeOption: Option[String]): Boolean = {
    if(termTypeOption.isDefined) {
      AbstractTermMap.isRdfCollectionTermType(termTypeOption.get)
    } else {
      false;
    }
  }
}