package es.upm.fi.dia.oeg.morph.rdb.engine

import java.sql.ResultSet
import java.sql.ResultSetMetaData

import scala.collection.JavaConversions.seqAsJavaList

import org.apache.log4j.Logger

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype
import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.vocabulary.RDF

import Zql.ZConstant
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.DBUtility
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.TemplateUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataTranslator
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.sql.DatatypeMapper
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLConstant
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.base.RDFTerm

class MorphRDBDataTranslator(factory: IMorphFactory) extends MorphBaseDataTranslator(factory) {

    if (!factory.getConnection.isRelationalDB)
        throw new MorphException("Database connection type does not match relational database")

    override val logger = Logger.getLogger(this.getClass().getName())

    /**
     * Query the database and build triples from the result set. For each row of the result set do:
     *
     * (1) create a subject resource and an optional graph resource if the subject map contains a rr:graph/rr:graphMap property,
     *
     * (2) loop on each predicate-object map: create a list of resources for the predicates, a list of resources for the objects,
     * a list of resources from the subject map of a parent object map in case there are referencing object maps,
     * and a list of resources representing target graphs mentioned in the predicate-object map.
     *
     * (3) Combine all subject, graph, predicate and object resources to generate triples.
     */
    override def generateRDFTriples(tm: R2RMLTriplesMap) = {

        val ls = tm.logicalSource;
        val sm = tm.subjectMap;
        val poms = tm.predicateObjectMaps;
        val query = factory.getUnfolder.unfoldTriplesMap(tm)

        // Run the query against the database
        val rows = factory.getDataSourceReader.execute(query, None).asInstanceOf[MorphRDBResultSet].resultSet

        // Make mappings of each column in the result set and its data type and equivalent XML data type
        var mapXMLDatatype: Map[String, String] = Map.empty;
        var mapDBDatatype: Map[String, Integer] = Map.empty;
        var rsmd: ResultSetMetaData = null;
        var columnCount = 0
        try {
            var rsmd = rows.getMetaData();
            val datatypeMapper = new DatatypeMapper();
            columnCount = rsmd.getColumnCount();
            for (i <- 0 until columnCount) {
                val columnName = rsmd.getColumnName(i + 1);
                val columnType = rsmd.getColumnType(i + 1);
                val mappedDatatype = datatypeMapper.getMappedType(columnType);
                mapXMLDatatype += (columnName -> mappedDatatype);
                mapDBDatatype += (columnName -> new Integer(columnType));
                logger.trace("SQL result: column " + columnName + " is mapped to XML type: " + mappedDatatype)
            }
        } catch {
            case e: Exception => {
                val errorMessage = "Unable to detect database columns: " + e.getMessage
                logger.warn(errorMessage);
                throw new MorphException(errorMessage, e);
            }
        }

        // Main loop: iterate and process each row in the SQL result set 
        var i = 0;
        while (rows.next()) { // put current cursor on the new row
            i = i + 1;
            logger.debug("Row " + i + ": " + DBUtility.resultSetCurrentRowToString(rows))
            try {
                // Create the subject resource
                val subjects = this.translateData(sm, rows, ls.alias, mapXMLDatatype);
                if (subjects == null) { throw new Exception("null value in the subject triple") }
                logger.debug("Row " + i + " subjects: " + subjects)

                // Create the list of resources representing subject target graphs
                val subjectGraphs = sm.graphMaps.flatMap(sgmElement => {
                    val subjectGraphValue = this.translateData(sgmElement, rows, ls.alias, mapXMLDatatype)
                    val graphMapTermType = sgmElement.inferTermType;
                    val subjectGraph = graphMapTermType match {
                        case Constants.R2RML_IRI_URI => { subjectGraphValue }
                        case _ => {
                            val errorMessage = "GraphMap's TermType is not valid: " + graphMapTermType;
                            logger.warn(errorMessage);
                            throw new MorphException(errorMessage);
                        }
                    }
                    subjectGraph
                }).toList;
                if (!subjectGraphs.isEmpty)
                    logger.trace("Row " + i + " subject graphs: " + subjectGraphs)

                // Add subject resource to the JENA model with its class (rdf:type) and target graphs
                sm.classURIs.foreach(classURI => {
                    val classRes = factory.getMaterializer.model.createResource(classURI);
                    if (subjectGraphs == null || subjectGraphs.isEmpty) {
                        for (sub <- subjects)
                            factory.getMaterializer.materializeQuad(this.createRDFNode(sub), RDF.`type`, classRes, null);
                    } else {
                        subjectGraphs.foreach(subjectGraph => {
                            for (sub <- subjects)
                                factory.getMaterializer.materializeQuad(this.createRDFNode(sub), RDF.`type`, classRes, this.createRDFNode(subjectGraph));
                        });
                    }
                });

                // Internal loop on each predicate-object map
                poms.foreach(pom => {

                    val alias = if (pom.alias == null) { ls.alias; }
                    else { pom.alias }

                    // ----- Make a list of resources for the predicate maps of this predicate-object map
                    val predicates = pom.predicateMaps.flatMap(predicateMap => {
                        this.translateData(predicateMap, rows, alias, mapXMLDatatype)
                    });
                    logger.debug("Row " + i + " predicates: " + predicates)

                    // ----- Make a list of resources for the object maps of this predicate-object map
                    val objects = pom.objectMaps.flatMap(objectMap => {
                        this.translateData(objectMap, rows, alias, mapXMLDatatype)
                    });
                    logger.debug("Row " + i + " objects: " + objects)

                    // ----- For each RefObjectMap get the IRIs from the subject map of the parent triples map

                    val refObjects = pom.refObjectMaps.flatMap(refObjectMap => {
                        val parentTM = factory.getMappingDocument.getParentTriplesMap(refObjectMap)
                        val parentTabAlias = factory.getUnfolder.asInstanceOf[MorphRDBUnfolder].mapRefObjectMapAlias.getOrElse(refObjectMap, null)

                        val subjectsCandidates = refObjectMap.joinConditions.flatMap(joinCond => {

                            val childMsp = MixedSyntaxPath(joinCond.childRef, Constants.xR2RML_REFFORMULATION_COLUMN)
                            val parentMsp = MixedSyntaxPath(joinCond.parentRef, Constants.xR2RML_REFFORMULATION_COLUMN)

                            if (childMsp.isSimpleColumnExpression && parentMsp.isSimpleColumnExpression) {
                                // Both the child and parent references are pure column references (without other path constructor)
                                // => regular R2RML case: the join is performed by the database
                                val parentSubjects = Some(this.translateData(parentTM.subjectMap, rows, parentTabAlias, mapXMLDatatype))

                                logger.trace("Join parent candidates from regular SQL query: " + joinCond.toString + ", result:" + parentSubjects)
                                parentSubjects

                            } else {
                                // At least the child or parent reference is a mixed syntax path: so the join cannot be performed
                                // by the database in an SQL join query: all columns have been retrieved and we now have to do the join

                                // Evaluate the child value against the child reference
                                val childColFromResultSet = getColumnNameFromResultSet(childMsp.getReferencedColumn.get, ls.alias)
                                val childDbValue = this.getResultSetValue(rows, childColFromResultSet);
                                val childValues: List[Object] = childMsp.evaluate(childDbValue).map(e => e.toString)

                                // Evaluate the parent value against the parent reference
                                val parentColFromResultSet = getColumnNameFromResultSet(parentMsp.getReferencedColumn.get, parentTabAlias)
                                val parentDbValue = this.getResultSetValue(rows, parentColFromResultSet);
                                val parentValues: List[Object] = parentMsp.evaluate(parentDbValue).map(e => e.toString)

                                val parentSubjects =
                                    if (!childValues.intersect(parentValues).isEmpty)
                                        // There is a match between child and parent values, keep the parent IRIs of the current results set row
                                        Some(this.translateData(parentTM.subjectMap, rows, parentTabAlias, mapXMLDatatype))
                                    else
                                        // There was no match: return an empty list so that the final intersection of candidate return nothing
                                        Some(List())

                                logger.trace("Join parent candidates from manual join evaluation: " + joinCond.toString + ", result:" + parentSubjects)
                                parentSubjects
                            }
                        })

                        // There is a logical AND between several join conditions of the same RefObjectMap 
                        // => make the intersection between all subjects generated by all join conditions
                        val finalParentSubjects = GeneralUtility.intersectMultipleTermSets(subjectsCandidates)
                        logger.trace("Join parent subjects after intersection all joinConditions: " + finalParentSubjects)
                        finalParentSubjects
                    })
                    logger.trace("Row " + i + " refObjects: " + refObjects)

                    // Create the list of resources representing target graphs mentioned in the predicate-object map
                    val predicateObjectGraphs = pom.graphMaps.flatMap(pogmElement => {
                        val poGraphValue = this.translateData(pogmElement, rows, null, mapXMLDatatype)
                        poGraphValue
                    }).toList;
                    if (!predicateObjectGraphs.isEmpty)
                        logger.trace("Row " + i + " predicate-object map graphs: " + predicateObjectGraphs)

                    // ----------------------------------------------------------------------------------------------
                    // Finally, combine all the terms to generate triples in the target graphs or default graph
                    // ----------------------------------------------------------------------------------------------
                    val subNodes = subjects.map(this.createRDFNode)
                    val predNodes = predicates.map(this.createRDFNode)
                    val objNodes = objects.map(this.createRDFNode)
                    val refObjNodes = refObjects.map(this.createRDFNode)
                    val graphNodes = (subjectGraphs ++ predicateObjectGraphs).map(this.createRDFNode)

                    factory.getMaterializer.materializeQuads(subNodes, predNodes, objNodes, refObjNodes, graphNodes)
                });

            } catch {
                case e: MorphException => {
                    logger.error("Error while translating data of row " + i + ": " + e.getMessage);
                    e.printStackTrace()
                }
                case e: Exception => {
                    logger.error("Unexpected error while translating data of row " + i + ": " + e.getMessage);
                    e.printStackTrace()
                }
            }
        }

        logger.info(i + " instances retrieved.");
        rows.close();
    }

    /**
     * This method has been implemented for MongoDB only, in the RDB case the query rewriting
     * is how it was initially developed in Morph-RDB.
     *
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    override def generateRDFTriples(query: AbstractQuery): Unit = {
        throw new MorphException("Unsupported action.")
    }

    /**
     * Apply a term map to the current row of the result set, and generate a list of RDF terms:
     * for each column reference in the term map (column, reference or template), read cell values from the current row,
     * translate them into RDF terms.
     * In the regular R2RML case, the result list should contain only one term.
     *
     * @return a list of RDN node, possibly an empty list in case the db returned null of the evaluation of
     * mixed syntax paths failed.
     */
    private def translateData(termMap: R2RMLTermMap, rs: ResultSet, logicalTableAlias: String, mapXMLDatatype: Map[String, String]): List[RDFTerm] = {

        var datatype = termMap.datatype
        var languageTag = termMap.languageTag

        // Term type of the collection/container to generate, or None if this is not the case 
        var collecTermType: Option[String] = None

        // Term type of the RDF terms to generate from values
        var memberTermType: String = Constants.R2RML_LITERAL_URI

        // In case of a collection/container, a nested term map should give the details of term type, datatype and language or the terms 
        if (termMap.isRdfCollectionTermType()) {
            collecTermType = Some(termMap.inferTermType)
            if (termMap.nestedTermMap.isDefined) { // a nested term type MUST be defined in a term map with collection/container term type
                memberTermType = termMap.nestedTermMap.get.inferTermType
                datatype = termMap.nestedTermMap.get.datatype
                languageTag = termMap.nestedTermMap.get.languageTag
            } else
                logger.warn("Term map with collection/container term type but no nested term map: " + termMap)
        } else {
            collecTermType = None
            memberTermType = termMap.inferTermType
        }

        var result: List[RDFTerm] = termMap.termMapType match {

            // --- Constant-valued term map
            case Constants.MorphTermMapType.ConstantTermMap => {
                MorphBaseDataTranslator.translateSingleValue(termMap.constantValue, collecTermType, memberTermType, datatype, languageTag, encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)
            }

            // --- Column-valued term map
            case Constants.MorphTermMapType.ColumnTermMap => {
                // Match the column name in the term map definition with the column name in the result set  
                val columnTermMapValue = getColumnNameFromResultSet(termMap.columnName, logicalTableAlias)

                // Read the value from the result set and get its XML datatype
                val dbValue = this.getResultSetValue(termMap, rs, columnTermMapValue);
                val datatype =
                    if (termMap.datatype.isDefined) { termMap.datatype }
                    else {
                        val columnNameAux = termMap.columnName.replaceAll("\"", "");
                        mapXMLDatatype.get(columnNameAux)
                    }

                // Generate the RDF terms
                MorphBaseDataTranslator.translateSingleValue(dbValue, collecTermType, memberTermType, datatype, languageTag, encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)
            }

            // --- Reference-valued term map
            case Constants.MorphTermMapType.ReferenceTermMap => {

                // Parse reference as a mixed syntax path and return the column referenced in the first "Column()" path
                val colRef = termMap.getReferencedColumns().get(0)

                // Match the column name in the term map definition with the column name in the result set  
                val colFromResultSet = getColumnNameFromResultSet(colRef, logicalTableAlias)

                // Read the value from the result set
                val dbValue = this.getResultSetValue(termMap, rs, colFromResultSet);

                // Evaluate the value against the mixed syntax path
                val msPath = termMap.getMixedSyntaxPaths()(0)
                val values: List[Object] = msPath.evaluate(dbValue)

                // Generate RDF terms from the values
                val datatype =
                    if (termMap.datatype.isDefined) termMap.datatype
                    else {
                        val dt = mapXMLDatatype.get(colRef.replaceAll("\"", ""))
                        if (dt.isDefined && dt.get == null)
                            None
                        else dt
                    }
                MorphBaseDataTranslator.translateMultipleValues(values, collecTermType, memberTermType, datatype, languageTag, encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)
            }

            // --- Template-valued term map
            case Constants.MorphTermMapType.TemplateTermMap => {

                val colRefs = termMap.getReferencedColumns()
                val msPaths = termMap.getMixedSyntaxPaths()

                // For each group of the template, compute a list of replacement strings:
                // If a db value is null, then an empty replacement list is returned.
                // Thus the list of replacements may contain empty replacements, but it must contain 
                // exactly as many replacements as groups {} in the template string.

                val listReplace = for (i <- 0 to (colRefs.length - 1)) yield {
                    // Match the column name in the term map definition with the column name in the result set  
                    val colFromResultSet = getColumnNameFromResultSet(colRefs(i), logicalTableAlias)

                    // Read the value from the result set
                    val dbValueRaw = this.getResultSetValue(termMap, rs, colFromResultSet)

                    // Evaluate the raw value against the mixed-syntax path.
                    // If the reference is not a mixed-syntax path, then the value is simply returned in a list.
                    // If the db value is null, then return an empty list.
                    val valuesRaw: List[Object] = msPaths(i).evaluate(dbValueRaw)
                    valuesRaw.filter(_ != null).map(v => MorphBaseDataTranslator.encodeResvdCharsIfUri(v, memberTermType, encodeUnsafeCharsInDbValues))
                }
                val lstRep = listReplace.toList
                logger.trace("Template replacements: " + lstRep)

                // Check if all replacements are empty, i.e. NOT(at least one replacement is not empty)
                var isAllEmptyReplacements: Boolean = true
                for (repl <- listReplace) {
                    if (!repl.isEmpty)
                        isAllEmptyReplacements = false
                }

                // Replace {} groups in the template string with corresponding values from the db               
                if (isAllEmptyReplacements) {
                    logger.trace("Template " + termMap.templateString + ": no values read from from the DB.")
                    List()
                } else {
                    // Compute the list of template results by making all possible combinations of the replacement values
                    val tplResults = TemplateUtility.replaceTemplateGroups(termMap.templateString, lstRep)
                    MorphBaseDataTranslator.translateMultipleValues(tplResults, collecTermType, memberTermType, datatype, languageTag, encodeUnsafeCharsInUri, encodeUnsafeCharsInDbValues, literalTrim)
                }
            }

            case _ => { throw new Exception("Invalid term map type " + termMap.termMapType) }
        }
        result
    }

    private def getResultSetValue(termMap: R2RMLTermMap, rs: ResultSet, pColumnName: String): Object = {
        try {
            val zConstant = MorphSQLConstant(pColumnName, ZConstant.COLUMNNAME);
            val tableName = zConstant.table;
            val columnName = {
                if (tableName != null) {
                    tableName + "." + zConstant.column
                } else
                    zConstant.column
            }

            val result = if (termMap.datatype == null) {
                rs.getString(columnName);
            } else if (termMap.datatype.equals(XSDDatatype.XSDdateTime.getURI())) {
                rs.getDate(columnName).toString();
            } else {
                rs.getObject(columnName);
            }
            result
        } catch {
            case e: Exception => {
                logger.error("An error occured when reading the SQL result set: " + e.getMessage());
                e.printStackTrace();
                null
            }
        }
    }

    private def getResultSetValue(rs: ResultSet, pColumnName: String): Object = {
        try {
            val zConstant = MorphSQLConstant(pColumnName, ZConstant.COLUMNNAME);
            val tableName = zConstant.table;
            val columnName = {
                if (tableName != null) {
                    tableName + "." + zConstant.column
                } else
                    zConstant.column
            }
            rs.getString(columnName);
        } catch {
            case e: Exception => {
                logger.error("An error occured when reading the SQL result set: " + e.getMessage());
                e.printStackTrace();
                null
            }
        }
    }

    /**
     * Match the column name in the term map definition with the equivalent column name in the result set
     */
    private def getColumnNameFromResultSet(colRef: String, alias: String): String = {
        if (alias != null && !"".equals(alias)) {
            val termMapColSplit = colRef.split("\\.")
            val dbEnclosedCharacter = Constants.getEnclosedCharacter(factory.getProperties.databaseType)
            val columnName = termMapColSplit(termMapColSplit.length - 1).replaceAll("\"", dbEnclosedCharacter); ;
            alias + "_" + columnName;
        } else
            colRef
    }
}
