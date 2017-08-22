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
    val termMapType: Constants.MorphTermMapType.Value,
    val termType: Option[String],
    val datatype: Option[String],
    val languageTag: Option[String],

    /** The nested term map is mandatory in the case term type is an RDF collection/container */
    val nestedTermMap: Option[xR2RMLNestedTermMap],

    /** Reference formulation from the logical source */
    val refFormulation: String)

        extends IConstantTermMap with IColumnTermMap with ITemplateTermMap with IReferenceTermMap
        with java.io.Serializable {

    def this(rdfNode: RDFNode, refForm: String) = {
        this(R2RMLTermMap.extractTermMapType(rdfNode),
            R2RMLTermMap.extractTermType(rdfNode),
            R2RMLTermMap.extractDatatype(rdfNode),
            R2RMLTermMap.extractLanguageTag(rdfNode),
            R2RMLTermMap.extractNestedTermMap(R2RMLTermMap.extractTermMapType(rdfNode), rdfNode),
            refForm);
        this.parse(rdfNode);
    }

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
     * Return true if the term type's term map is one of RDF list, bag, seq, alt
     */
    def isRdfCollectionTermType: Boolean = {
        R2RMLTermMap.isRdfCollectionTermType(termType)
    }

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
                List(MixedSyntaxPath(this.reference, refFormulation))
            }
            case Constants.MorphTermMapType.TemplateTermMap => {
                // Get the list of template strings
                val tplStrings = TemplateUtility.getTemplateGroups(this.templateString)

                // For each one, parse it as a mixed syntax path
                tplStrings.map(tplString => MixedSyntaxPath(tplString, refFormulation))
            }
            case _ => { throw new Exception("Cannot build a MixedSyntaxPath with a term map of type " + this.termMapType) }
        }
    }

    def isConstantValued: Boolean = {
        this.termMapType == Constants.MorphTermMapType.ConstantTermMap
    }

    def isColumnValued: Boolean = {
        this.termMapType == Constants.MorphTermMapType.ColumnTermMap
    }

    def isReferenceValued: Boolean = {
        this.termMapType == Constants.MorphTermMapType.ReferenceTermMap
    }

    def isTemplateValued: Boolean = {
        this.termMapType == Constants.MorphTermMapType.TemplateTermMap
    }

    def isReferenceOrTemplateValued: Boolean = {
        this.termMapType == Constants.MorphTermMapType.ReferenceTermMap ||
            this.termMapType == Constants.MorphTermMapType.TemplateTermMap
    }

}

object R2RMLTermMap {

    val logger = Logger.getLogger(this.getClass().getName());

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

    /**
     * Look for a xrr:nestedTermMap property and creates an instance of xR2RMLNestedTermMap.
     *
     * LIMITATION: only the term type, datatype and language tag are supported for now.
     * No reference or template nor further nested term map is supported.
     * @todo implement the full support of nested term maps
     */
    def extractNestedTermMap(parentTermMapType: Constants.MorphTermMapType.Value, rdfNode: RDFNode): Option[xR2RMLNestedTermMap] = {
        rdfNode match {
            case resource: Resource => {
                var ntmStmt = resource.getProperty(Constants.xR2RML_NESTEDTM_PROPERTY);
                if ((ntmStmt != null) && ntmStmt.getObject.isResource) {
                    val ntmRes:Resource = ntmStmt.getObject.asResource
                    val termTypeStmt = ntmRes.getProperty(Constants.R2RML_TERMTYPE_PROPERTY)
                    val datatypeStmt = ntmRes.getProperty(Constants.R2RML_DATATYPE_PROPERTY)
                    val langStmt = ntmRes.getProperty(Constants.R2RML_LANGUAGE_PROPERTY)

                    val termType = if (termTypeStmt == null) None else Some(termTypeStmt.getObject().toString())
                    val datatype = if (datatypeStmt == null) None else Some(datatypeStmt.getObject().toString())
                    val language = if (langStmt == null) None else Some(langStmt.getObject().toString())

                   val nestedTermMapType = xR2RMLNestedTermMap.extractNestedTermMapType(ntmRes)

                    Some(new xR2RMLNestedTermMap(parentTermMapType, nestedTermMapType, termType, datatype, language, None))
                } else None
            }
            case _ => { None }
        }
    }

    /**
     * From a, RDF node representing a term map, return a list with the following elements:
     * <ul>
     * <li>type of term map (constant, column, reference, template),</li>
     * <li>term type</li>
     * <li>optional data type</li>
     * <li>optional language tag</li>
     * <li>optional nested term map</li>
     * </ul>
     */
    def extractCoreProperties(rdfNode: RDFNode) = {
        val termMapType = R2RMLTermMap.extractTermMapType(rdfNode);
        val termType = R2RMLTermMap.extractTermType(rdfNode);
        val datatype = R2RMLTermMap.extractDatatype(rdfNode);
        val languageTag = R2RMLTermMap.extractLanguageTag(rdfNode);
        val nestedTM = R2RMLTermMap.extractNestedTermMap(termMapType, rdfNode);

        if (logger.isTraceEnabled()) logger.trace("Extracted term map core properties: termMapType: " + termMapType + ". termType: "
            + termType + ". datatype: " + datatype + ". languageTag: " + languageTag
            + ". nestedTermMap: " + nestedTM)

        val coreProperties = (termMapType, termType, datatype, languageTag, nestedTM)
        coreProperties;
    }

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
                            val sm = new R2RMLSubjectMap(Constants.MorphTermMapType.ConstantTermMap, Some(Constants.R2RML_IRI_URI), Set.empty, Set.empty, refFormulation);
                            sm.parse(stmtObject)
                            Some(sm)
                        }
                        case Constants.MorphPOS.pre => {
                            val pm = new R2RMLPredicateMap(Constants.MorphTermMapType.ConstantTermMap, Some(Constants.R2RML_IRI_URI), refFormulation);
                            pm.parse(stmtObject)
                            Some(pm);
                        }
                        case Constants.MorphPOS.obj => {
                            val om = new R2RMLObjectMap(Constants.MorphTermMapType.ConstantTermMap, Some(Constants.R2RML_LITERAL_URI), None, None, None, refFormulation);
                            om.parse(stmtObject)
                            Some(om)
                        }
                        case Constants.MorphPOS.graph => {
                            val gm = new R2RMLGraphMap(Constants.MorphTermMapType.ConstantTermMap, Some(Constants.R2RML_IRI_URI), None, None, refFormulation);
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

    /**
     * Return true if the term type is one of RDF list, bag, seq, alt
     */
    def isRdfCollectionTermType(termType: Option[String]): Boolean = {
        if (termType.isDefined) {
            val tt = termType.get
            isRdfCollectionTermType(tt)
        } else { false }
    }

    /**
     * Return true if the term type is one of RDF list, bag, seq, alt
     */
    def isRdfCollectionTermType(termType: String): Boolean = {
        (termType == Constants.xR2RML_RDFLIST_URI ||
            termType == Constants.xR2RML_RDFBAG_URI ||
            termType == Constants.xR2RML_RDFSEQ_URI ||
            termType == Constants.xR2RML_RDFALT_URI)
    }
}
