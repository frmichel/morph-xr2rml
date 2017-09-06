package es.upm.fi.dia.oeg.morph.r2rml.model

import com.hp.hpl.jena.rdf.model.{ RDFNode, Resource }
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.{ Constants, TemplateUtility }
import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath
import org.apache.log4j.Logger
import scala.collection.JavaConversions._

abstract class AbstractTermMap(
                                val termMapType: Constants.MorphTermMapType.Value,
                                val termType: Option[String],
                                val datatype: Option[String],
                                val languageTag: Option[String],
                                val nestedTermMap: Option[xR2RMLNestedTermMap],
                                val refFormulation: String,
                                val listPushDown:List[xR2RMLPushDown]
                              )
  extends IConstantTermMap with IColumnTermMap with ITemplateTermMap with IReferenceTermMap with java.io.Serializable {

  val logger = Logger.getLogger(this.getClass().getName());


  /**
    * Decide the type of the term map (constant, column, reference, template) based on its properties,
    * and assign the value of the appropriate trait member: IConstantTermMap.constantValue, IColumnTermMap.columnName etc.
    *
    * If the term map resource has no property (constant, column, reference, template) then it means that this is a
    * constant term map like "[] rr:predicate ex:name".
    *
    * If the node passed in not a resource but a literal, then it means that we have a constant property whose object
    * is a literal and not an IRI or blank node, like in: "[] rr:object 'any string';"
    *
    * @param rdfNode the term map Jena resource
    */
  def parse(rdfNode: RDFNode) = {

    if (rdfNode.isLiteral) {
      // We are in the case of a constant property with a literal object, like "[] rr:object 'NAME'",
      this.constantValue = rdfNode.toString()
    } else {
      val resourceNode = rdfNode.asResource();

      val constantStatement = resourceNode.getProperty(Constants.R2RML_CONSTANT_PROPERTY);
      if (constantStatement != null)
        this.constantValue = constantStatement.getObject().toString();
      else {
        val columnStatement = resourceNode.getProperty(Constants.R2RML_COLUMN_PROPERTY);
        if (columnStatement != null)
          this.columnName = columnStatement.getObject().toString();
        else {
          val templateStatement = resourceNode.getProperty(Constants.R2RML_TEMPLATE_PROPERTY);
          if (templateStatement != null)
            this.templateString = templateStatement.getObject().toString();
          else {
            val refStmt = resourceNode.getProperty(Constants.xR2RML_REFERENCE_PROPERTY);
            if (refStmt != null)
              this.reference = refStmt.getObject().toString();
            else {
              // We are in the case of a constant property, like "[] rr:predicate ex:name",
              this.constantValue = rdfNode.toString()
            }
          }
        }
      }
    }
  }

  def hasNestedTermMap() = { nestedTermMap.isDefined }

  def getReferenceFormulation(): String = this.refFormulation;

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

  /**
    * Determine the characteristics of the RDF terms generated by the term map: term type, datatype and language,
    * + the collection term type if the current term map generates a collection/container
    * (in which case a nested term map should give the details of the generated terms).
    *
    * Note that a term map may have no term type and have a nested term type. In that case,
    * the term type is that of the nested term type.
    */
  def calculateCollecTermType_DataType_LanguageTag_TermType(): (Option[String], Option[String], Option[String], String) = {

    val termMapTermType: String = this.inferTermType;

    if (AbstractTermMap.isRdfCollectionTermType(termMapTermType)) {
      if (this.nestedTermMap.isDefined) {
        // a nested term map MUST be defined in a term map with collection/container term type
        val ntmDataType = this.nestedTermMap.get.datatype;
        val ntmLanguageTag = this.nestedTermMap.get.languageTag;
        val ntmTermType = this.nestedTermMap.get.inferTermType;

        (Some(termMapTermType), ntmDataType, ntmLanguageTag, ntmTermType)
      } else {
        logger.warn("Term map with collection/container term type but no nested term map: " + this)
        (Some(termMapTermType), this.datatype, this.languageTag, Constants.R2RML_LITERAL_URI)
      }

    } else {
      if (this.nestedTermMap.isDefined) {
        // A nested term map may be defined even though the term map type is not collection or container.
        // In that case, the term type of the term map is the term type of the nested term map
        val ntmDataType = this.nestedTermMap.get.datatype;
        val ntmLanguageTag = this.nestedTermMap.get.languageTag;
        val ntmTermType = this.nestedTermMap.get.inferTermType;

        (None, ntmDataType, ntmLanguageTag, ntmTermType)
      } else
        (None, this.datatype, this.languageTag, termMapTermType)
    }
  }

  def isReferenceValued = { this.termMapType == Constants.MorphTermMapType.ReferenceTermMap }

  def isTemplateValued = { this.termMapType == Constants.MorphTermMapType.TemplateTermMap }

  def isSimpleNestedTermMap = { this.termMapType == Constants.MorphTermMapType.SimpleNestedTermMap }

  def isConstantValued: Boolean = { this.termMapType == Constants.MorphTermMapType.ConstantTermMap }

  def isColumnValued: Boolean = { this.termMapType == Constants.MorphTermMapType.ColumnTermMap }

  def isReferenceOrTemplateValued: Boolean = {
    this.termMapType == Constants.MorphTermMapType.ReferenceTermMap ||
      this.termMapType == Constants.MorphTermMapType.TemplateTermMap
  }

  def isRdfCollectionTermType(): Boolean = {
    AbstractTermMap.isRdfCollectionTermType(this.termType)
  }

}

object AbstractTermMap {
  val logger = Logger.getLogger(this.getClass().getName());

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
    if (termTypeOption.isDefined) {
      AbstractTermMap.isRdfCollectionTermType(termTypeOption.get)
    } else {
      false;
    }
  }

  /**
    * Deduce the type of the term map (constant, column, reference, template) based on its properties
    * @param rdfNode the term map node
    * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException in case the term map type cannot be decided
    */
  def extractTermMapType(rdfNode: RDFNode) = {
    rdfNode match {
      case resource: Resource => {
        if (resource.getProperty(Constants.R2RML_CONSTANT_PROPERTY) != null) Constants.MorphTermMapType.ConstantTermMap;
        else if (resource.getProperty(Constants.R2RML_COLUMN_PROPERTY) != null) Constants.MorphTermMapType.ColumnTermMap;
        else if (resource.getProperty(Constants.R2RML_TEMPLATE_PROPERTY) != null) Constants.MorphTermMapType.TemplateTermMap;
        else if (resource.getProperty(Constants.xR2RML_REFERENCE_PROPERTY) != null) Constants.MorphTermMapType.ReferenceTermMap;
        else {
          val errorMessage = "Invalid term map " + resource.getLocalName() + ". Should have one of rr:constant, rr:column, rr:template or xrr:reference.";
          logger.error(errorMessage);
          throw new MorphException(errorMessage);
        }
      }
      case _ => {
        Constants.MorphTermMapType.ConstantTermMap;
      }
    }
  }

  /**
    * Extract the value of the rr:termpType property, returns None is no property found
    */
  def extractTermType(rdfNode: RDFNode) = {
    rdfNode match {
      case resource: Resource => {
        val termTypeStatement = resource.getProperty(Constants.R2RML_TERMTYPE_PROPERTY);
        if (termTypeStatement != null) {
          Some(termTypeStatement.getObject().toString());
        } else
          None
      }
      case _ => None
    }
  }

  def extractDatatype(rdfNode: RDFNode) = {
    rdfNode match {
      case resource: Resource => {
        val datatypeStatement = resource.getProperty(Constants.R2RML_DATATYPE_PROPERTY);
        if (datatypeStatement != null) {
          Some(datatypeStatement.getObject().toString());
        } else
          None
      }
      case _ => None
    }
  }

  def extractLanguageTag(rdfNode: RDFNode) = {
    rdfNode match {
      case resource: Resource => {
        val languageStatement = resource.getProperty(Constants.R2RML_LANGUAGE_PROPERTY);
        if (languageStatement != null) {
          Some(languageStatement.getObject().toString());
        } else
          None
      }
      case _ => None
    }
  }

  def extractPushDownTags(rdfNode: RDFNode) : List[xR2RMLPushDown] = {
    rdfNode match {
      case resource: Resource => {
        val stmtIteratorPushDown = resource.listProperties(Constants.xR2RML_PUSHDOWN_PROPERTY);
        if (stmtIteratorPushDown != null) {
          val listPushDown = stmtIteratorPushDown.toList
          val result:List[xR2RMLPushDown] = listPushDown.map(pushDown => {
            val resourcePushDown = pushDown.getObject.asResource();
            xR2RMLPushDown(resourcePushDown)
          }).toList
          result
        } else
          Nil
      }
      case _ => Nil
    }




  }

  /**
    * From an RDF node representing a term map, return a list with the following elements:
    * <ul>
    * <li>type of term map (constant, column, reference, template),</li>
    * <li>term type</li>
    * <li>optional data type</li>
    * <li>optional language tag</li>
    * <li>optional nested term map</li>
    * </ul>
    */
  def extractCoreProperties(rdfNode: RDFNode, refFormulation: String) = {
    val termMapType = AbstractTermMap.extractTermMapType(rdfNode);
    val termType = AbstractTermMap.extractTermType(rdfNode);
    val datatype = AbstractTermMap.extractDatatype(rdfNode);
    val languageTag = AbstractTermMap.extractLanguageTag(rdfNode);
    val nestedTM = xR2RMLNestedTermMap.extractNestedTermMap(termMapType, rdfNode, refFormulation);
    val listPushDown = AbstractTermMap.extractPushDownTags(rdfNode);

    if (logger.isTraceEnabled()) logger.trace("Extracted term map core properties: termMapType: " + termMapType + ". termType: "
      + termType + ". datatype: " + datatype + ". languageTag: " + languageTag
      + ". nestedTermMap: " + nestedTM
      + ". listPushDown: " + listPushDown
    )

    val coreProperties = (termMapType, termType, datatype, languageTag, nestedTM, listPushDown)
    coreProperties;
  }
}