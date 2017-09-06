package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.asScalaBuffer
import org.apache.log4j.Logger
import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.rdf.model.Resource
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.TemplateUtility
import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath
import es.upm.fi.dia.oeg.morph.base.exception.MorphException

abstract class R2RMLTermMap(
		override val termMapType: Constants.MorphTermMapType.Value,
		override val termType: Option[String],
		override val datatype: Option[String],
		override val languageTag: Option[String],

		/** The nested term map is mandatory in the case term type is an RDF collection/container */
		override val nestedTermMap: Option[xR2RMLNestedTermMap],

		/** Reference formulation from the logical source */
		override val refFormulation: String,
		
		override val listPushDown:List[xR2RMLPushDown]
)

extends AbstractTermMap(termMapType, termType, datatype, languageTag, nestedTermMap, refFormulation, listPushDown) {

	/**
	 * Return the list of references (strings) referenced by the term map.
	 * Nil in case of a constant-valued term-map, one string for a column-valued term map or reference-valued term map,
	 * and a list of several strings for a template-valued term map.
	 *
	 * Each reference is returned as it appears in the mapping, may it be a mixed syntax path or not.
	 *
	 * @return list of strings representing references. Cannot return null.
	 */
	def getReferences(): List[String] = {
			val result = this.termMapType match {
			case Constants.MorphTermMapType.ConstantTermMap => { List(this.constantValue) }
			case Constants.MorphTermMapType.ColumnTermMap => { List(this.columnName) }
			case Constants.MorphTermMapType.ReferenceTermMap => { List(this.reference) }
			case Constants.MorphTermMapType.TemplateTermMap => {
				// Get the list of template strings
				TemplateUtility.getTemplateGroups(this.templateString)
			}
			case _ => { throw new Exception("Invalid term map type") }
			}
			result
	}

	/**
	 * Return the list of column names (strings) referenced by the term map.
	 * Nil in case of a constant-valued term-map, a list of one string for a column-valued term map or reference-valued term map,
	 * and a list of several strings for a template-valued term map.
	 *
	 * @return list of strings representing column names. Cannot return null
	 */
	def getReferencedColumns(): List[String] = {
			val result = this.termMapType match {
			case Constants.MorphTermMapType.ConstantTermMap => { Nil }
			case Constants.MorphTermMapType.ColumnTermMap => { List(this.columnName) }
			case Constants.MorphTermMapType.ReferenceTermMap => {
				// Parse reference as a mixed syntax path and return the column referenced in the first path "Column()"
				List(MixedSyntaxPath(this.reference, refFormulation).getReferencedColumn.get)
			}
			case Constants.MorphTermMapType.TemplateTermMap => {
				// Get the list of template strings
				val tplStrings = TemplateUtility.getTemplateColumns(this.templateString)

						// For each one, parse it as a mixed syntax path and return the column referenced in the first path "Column()"
						tplStrings.map(tplString => MixedSyntaxPath(tplString, refFormulation).getReferencedColumn.get)
			}
			case _ => { throw new Exception("Invalid term map type") }
			}
			result
	}

	def getOriginalValue(): String = {
			val result = this.termMapType match {
			case Constants.MorphTermMapType.ConstantTermMap => { this.constantValue; }
			case Constants.MorphTermMapType.ColumnTermMap => { this.columnName; }
			case Constants.MorphTermMapType.TemplateTermMap => { this.templateString; }
			case Constants.MorphTermMapType.ReferenceTermMap => { this.reference; }
			case _ => { null; }
			}
			result
	}

	override def toString(): String = {
			var result = this.termMapType match {
			case Constants.MorphTermMapType.ConstantTermMap => { "rr:constant"; }
			case Constants.MorphTermMapType.ColumnTermMap => { "rr:column"; }
			case Constants.MorphTermMapType.TemplateTermMap => { "rr:template"; }
			case Constants.MorphTermMapType.ReferenceTermMap => { "xrr:reference"; }
			case _ => "";
			}
			result = "[" + result + ": " + this.getOriginalValue() + "]";
			return result;
	}

}

object R2RMLTermMap {

	val logger = Logger.getLogger(this.getClass().getName());

	/**
	 * Find term maps of the given triples map by term position (subject, object, predicate) by looking for
	 * constant (e.g.g rr:subject) and non-constant (e.g. rr:subjectMap) properties
	 *
	 * @param resource instance of R2RMLTriplesMap
	 * @param termPos what to extract: subject, predicate, object or graph
	 * @param refFormulation the reference formulation of the current triples map's logical source
	 */
	def extractTermMaps(resource: Resource, termPos: Constants.MorphPOS.Value, refFormulation: String): Set[R2RMLTermMap] = {

			// Extract term map introduced with non-constant properties rr:subjectMap, rr:predicateMap, rr:objectMap and rr:graphMap
			val nonConstProperties = termPos match {
			case Constants.MorphPOS.sub => Constants.R2RML_SUBJECTMAP_PROPERTY
			case Constants.MorphPOS.pre => Constants.R2RML_PREDICATEMAP_PROPERTY
			case Constants.MorphPOS.obj => Constants.R2RML_OBJECTMAP_PROPERTY
			case Constants.MorphPOS.graph => Constants.R2RML_GRAPHMAP_PROPERTY
			}
			var stmts = resource.listProperties(nonConstProperties);
			val maps1 =
					if (stmts != null) {
						stmts.toList().flatMap(mapStatement => {
							val stmtObject = mapStatement.getObject();
							termPos match {
							case Constants.MorphPOS.sub => Some(R2RMLSubjectMap(stmtObject, refFormulation))
							case Constants.MorphPOS.pre => Some(R2RMLPredicateMap(stmtObject, refFormulation))
							case Constants.MorphPOS.obj => {
								val stmtObjectResource = stmtObject.asInstanceOf[Resource]
										if (!R2RMLRefObjectMap.isRefObjectMap(stmtObjectResource)) {
											Some(R2RMLObjectMap(stmtObject, refFormulation))
										} else None
							}
							case Constants.MorphPOS.graph => {
								val gm = R2RMLGraphMap(stmtObject, refFormulation);
								if (Constants.R2RML_DEFAULT_GRAPH_URI.equals(gm.getOriginalValue)) {
									None
								} else { Some(gm) }
							}
							case _ => { None }
							}
						})
					} else { Nil }

			// Extract term map introduced with constant properties rr:subject, rr:predicate, rr:object and rr:graph
			val constProperties = termPos match {
			case Constants.MorphPOS.sub => Constants.R2RML_SUBJECT_PROPERTY
			case Constants.MorphPOS.pre => Constants.R2RML_PREDICATE_PROPERTY
			case Constants.MorphPOS.obj => Constants.R2RML_OBJECT_PROPERTY
			case Constants.MorphPOS.graph => Constants.R2RML_GRAPH_PROPERTY
			}
			stmts = resource.listProperties(constProperties)
					val maps2 =
					if (stmts != null) {
						stmts.toList().flatMap(mapStatement => {
							val stmtObject = mapStatement.getObject()
									termPos match {
									case Constants.MorphPOS.sub => {
										val sm = new R2RMLSubjectMap(Constants.MorphTermMapType.ConstantTermMap
										    , Some(Constants.R2RML_IRI_URI), Set.empty, Set.empty, refFormulation, Nil);
										sm.parse(stmtObject)
										Some(sm)
									}
									case Constants.MorphPOS.pre => {
										val pm = new R2RMLPredicateMap(Constants.MorphTermMapType.ConstantTermMap
										    , Some(Constants.R2RML_IRI_URI), refFormulation, Nil);
										pm.parse(stmtObject)
										Some(pm);
									}
									case Constants.MorphPOS.obj => {
										val om = new R2RMLObjectMap(Constants.MorphTermMapType.ConstantTermMap
										    , Some(Constants.R2RML_LITERAL_URI), None, None, None, refFormulation, Nil);
										om.parse(stmtObject)
										Some(om)
									}
									case Constants.MorphPOS.graph => {
										val gm = new R2RMLGraphMap(Constants.MorphTermMapType.ConstantTermMap
										    , Some(Constants.R2RML_IRI_URI), None, None, refFormulation, Nil);
										gm.parse(stmtObject)
										if (Constants.R2RML_DEFAULT_GRAPH_URI.equals(gm.getOriginalValue)) {
											None
										} else { Some(gm) }
									}
									case _ => { throw new Exception("Invalid triple term position: " + termPos) }
							}
						})
					} else { Nil }

			val maps = maps1 ++ maps2
					val mapsSet = maps.toSet
					if (logger.isTraceEnabled()) logger.trace("Extracted term maps: " + mapsSet)
					mapsSet
	}
}
