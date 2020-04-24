package es.upm.fi.dia.oeg.morph.base.querytranslator

import java.text.SimpleDateFormat

import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.log4j.Logger

import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.R2RMLMappingUtility
import es.upm.fi.dia.oeg.morph.base.sql.MorphTableMetaData

/**
 * Binding of triples maps to triples patterns.
 * This is the legacy class from Morph-RDB that is still used for SPARQL-to-SQL translation.
 * 
 * For SPARQL-to-MongoDB, see class MorphBaseTriplePatternBinder
 *  
 * @author Freddy Priyatna
 */
class TriplePatternPredicateBounder(tableMetaData: Option[MorphTableMetaData]) {

    val logger = Logger.getLogger(this.getClass());
    val constants = new Constants();

    /**
     * Return a (possibly empty) list of error messages regarding the possibility of the object of
     * triple pattern tp to be matched with one of the object maps of the triples map.
     * If no error message is returned then the triples map can match the triple pattern.
     */
    def expandUnboundedPredicateTriplePattern(tp: Triple, tmRes: Resource): java.util.Map[Resource, java.util.List[String]] = {
        val poms = R2RMLMappingUtility.getPredicateObjectMapResources(tmRes);
        this.checkExpandedTriplePatternList(tp, tmRes, poms);
    }

    private def checkExpandedTriplePatternList(tp: Triple,
                                               tmRes: Resource, pomResources: List[Resource]): java.util.Map[Resource, java.util.List[String]] = {
        val result: Map[Resource, java.util.List[String]] = {
            if (pomResources.isEmpty) {
                Map();
            } else {
                val pomHead = pomResources.head;
                val pomTail = pomResources.tail;
                val resultHead = this.checkExpandedTriplePattern(tp, tmRes, pomHead);
                val mapResultHead = Map(pomHead -> resultHead);
                val mapResultTail = this.checkExpandedTriplePatternList(tp, tmRes, pomTail);
                mapResultHead ++ mapResultTail;
            }
        }
        result
    }

    private def checkExpandedTriplePattern(tp: Triple, tmRes: Resource,
                                           pomResource: Resource): java.util.List[String] = {
        val tpObject = tp.getObject();
        var result: List[String] = Nil;
        val omResource = R2RMLMappingUtility.getObjectMapResource(pomResource);
        val romResource = R2RMLMappingUtility.getRefObjectMapResource(pomResource);

        if (tpObject.isLiteral()) {
            val objectLiteralValue = tpObject.getLiteral().getValue();

            if (romResource != null) {
                val errorMessage = "triple.object is a literal, but RefObjectMap is specified instead of ObjectMap";
                result = result ::: List(errorMessage);
            }

            if (omResource != null) {
                val omTermMapType = R2RMLMappingUtility.getTermMapType(omResource);
                val omTermType = R2RMLMappingUtility.getTermTypeResource(omResource).getURI();
                if (omTermType.equals(Constants.R2RML_IRI_URI)) {
                    val errorMessage = "triple.object " + tp + " is a literal, but the mapping " + pomResource + " specifies URI.";
                    result = result ::: List(errorMessage);
                }

                omTermMapType match {
                    case Constants.MorphTermMapType.ColumnTermMap => {
                        val rrDatatypeResource = R2RMLMappingUtility.getDatatypeResource(omResource);
                        val omDatatype = {
                            if (rrDatatypeResource == null) {
                                if (this.tableMetaData.isDefined) {
                                    val tableName = R2RMLMappingUtility.getRRLogicalTableTableName(tmRes);
                                    val rrColumnResource = R2RMLMappingUtility.getRRColumnResource(omResource);
                                    val columnName = rrColumnResource.getLiteral().getValue().toString();

                                    val columnMetaData = this.tableMetaData.get.getColumnMetaData(columnName);
                                    if (columnMetaData == null) {
                                        null
                                    } else {
                                        columnMetaData.get.dataType
                                    }
                                } else { null }
                            } else {
                                rrDatatypeResource.asLiteral().getValue().toString();
                            }
                        }

                        if (omDatatype != null) {
                            omDatatype match {
                                case R2RMLMappingUtility.XSDIntegerURI => {
                                    try {
                                        objectLiteralValue.asInstanceOf[Integer];
                                    } catch {
                                        case e: Exception => {
                                            val errorMessage = "triple.object " + tp + " not an integer, but the mapping " + pomResource + " specified mapped column is integer";
                                            result = result ::: List(errorMessage);
                                        }
                                    }
                                }
                                case R2RMLMappingUtility.XSDDoubleURI => {
                                    try {
                                        objectLiteralValue.asInstanceOf[Double];
                                    } catch {
                                        case e: Exception => {
                                            val errorMessage = "triple.object " + tp + " not a double, but the mapping " + pomResource + " specified mapped column is double";
                                            result = result ::: List(errorMessage);
                                        }
                                    }
                                }
                                case R2RMLMappingUtility.XSDDateTimeURI => {
                                    val dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                                    try {
                                        dateFormat.parse(objectLiteralValue.toString());
                                    } catch {
                                        case e: Exception => {
                                            val errorMessage = "triple.object " + tp + " not a datetime, but the mapping " + pomResource + " specified mapped column is datetime";
                                            result = result ::: List(errorMessage);
                                        }
                                    }
                                }
                                case _ => {}
                            }
                        }
                    }
                    case _ => {}
                }
            }
        } else if (tpObject.isURI()) {
            val tpObjectURI = tpObject.getURI();

            if (omResource != null && romResource == null) {
                val omTermType = R2RMLMappingUtility.getTermTypeResource(omResource).getURI();
                omTermType match {
                    case Constants.R2RML_LITERAL_URI => {
                        val errorMessage = "triple.object " + tp + " is an URI, but the mapping " + omResource + " specifies literal";
                        result = result ::: List(errorMessage);
                    }
                    case _ => {}
                }

                val omTermMapType = R2RMLMappingUtility.getTermMapType(omResource);
                omTermMapType match {
                    case Constants.MorphTermMapType.ColumnTermMap => {
                        val rrDatatypeResource = R2RMLMappingUtility.getDatatypeResource(omResource);
                        val omDatatype = {
                            if (rrDatatypeResource == null) {
                                val logicalTableName = R2RMLMappingUtility.getRRLogicalTableTableName(tmRes);
                                val rrColumnResource = R2RMLMappingUtility.getRRColumnResource(omResource);
                                val columnName = rrColumnResource.getLiteral().getValue().toString();

                                if (this.tableMetaData.isDefined) {
                                    val columnMetaData = this.tableMetaData.get.getColumnMetaData(columnName);
                                    if (columnMetaData.isDefined) {
                                        columnMetaData.get.dataType
                                    }
                                }
                            } else {
                                rrDatatypeResource.asLiteral().getValue().toString();
                            }
                        }

                        if (omDatatype != null) {
                            omDatatype match {
                                case R2RMLMappingUtility.XSDIntegerURI
                                    | R2RMLMappingUtility.XSDDoubleURI
                                    | R2RMLMappingUtility.XSDDateTimeURI => {
                                    val tpObjectURI = tpObject.getURI();
                                    val rrColumnResource = R2RMLMappingUtility.getRRColumnResource(omResource);
                                    val columnName = rrColumnResource.getLiteral().getValue().toString();
                                    val errorMessage = "Numeric/Datetime column : " + columnName + " can't be used for URI : " + tpObjectURI;
                                    result = result ::: List(errorMessage);
                                }
                                case _ => {}
                            }
                        }
                    }
                    case Constants.MorphTermMapType.TemplateTermMap => {
                        val templateValues = R2RMLMappingUtility.getTemplateValues(omResource, tpObjectURI);
                        if (templateValues.isEmpty) {
                            val errorMessage = "tp object " + tpObjectURI + " doesn't match the template : " + omResource;
                            result = result ::: List(errorMessage);
                        }
                    }
                    case _ => {}
                }
            } else if (omResource == null && romResource != null) {
                val parentTriplesMapResource = R2RMLMappingUtility.getParentTriplesMapResource(romResource);
                val parentTriplesMapSubjectMapResource = R2RMLMappingUtility.getRRSubjectMapResource(parentTriplesMapResource);
                val templateValues = R2RMLMappingUtility.getTemplateValues(parentTriplesMapSubjectMapResource, tpObjectURI);
                if (templateValues.isEmpty) {
                    val errorMessage = "tp object " + tpObjectURI + " doesn't match the template : " + parentTriplesMapSubjectMapResource;
                    result = result ::: List(errorMessage);
                }
            }
        }
        result
    }
}