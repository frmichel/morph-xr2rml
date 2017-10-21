package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.asScalaIterator

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants

/**
 * Abstract class to represent an xR2RML LogicalSource, parent of xR2RMLTable and xR2RMLQuery.
 *
 * @param logicalTableType either TABLE_NAME or QUERY
 * @param refFormulation Syntax of data elements references (iterator, reference, template). Defaults to xrr:Column
 * @param docIterator Iteration pattern, defaults to None
 * @param uniqueRefs List of xR2RML references that uniquely identifies documents of the database.
 * Used in abstract query optimization (notably self-join elimination).
 * In an RDB, this is typically the primary key but it is possible to get this information using table metadata.
 * In MongoDB, the "_id" field is unique thus reference "$._id" is unique, but there is no way to know whether
 * some other fields are unique.
 *
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
abstract class xR2RMLLogicalSource(
        val logicalTableType: Constants.LogicalTableType.Value,
        val refFormulation: String,
        val docIterator: Option[String],
        val uniqueRefs: Set[String],
        val listPushDown: List[xR2RMLPushDown]) {

    var alias: String = null;

    val logger = Logger.getLogger(this.getClass.getName);

    /** Return the table name or query depending on the type of logical table */
    def getValue: String;

    override def toString: String = {
        val result = this match {
            case _: xR2RMLTable => { "xR2RMLTable"; }
            case _: xR2RMLQuery => { "xR2RMLQuery"; }
            case _ => throw new Exception("Unkown type of logical source or logical table")
        }
        result + ": " + this.getValue + ". ReferenceFormulation: " + this.refFormulation + ". Iterator: " + this.docIterator
    }

    override def hashCode(): Int = {
        this.toString.hashCode
    }
}

object xR2RMLLogicalSource {
    val logger = Logger.getLogger(this.getClass.getName);

    /**
     * Check properties of a logical source or logical table to create the appropriate
     * instance of xR2RMLTable and xR2RMLQuery.
     *
     * @param reource an xrr:LogicalSource or rr:LogicalTable resource
     * @param logResType class URI of a logical source or logical table
     * @param refFormulation the reference formulation
     * @return instance of xR2RMLTable and xR2RMLQuery
     */
    def parse(resource: Resource, logResType: String, refFormulation: String): xR2RMLLogicalSource = {
        val logSrc: xR2RMLLogicalSource =
            {
                val tableNameStmt = resource.getProperty(Constants.R2RML_TABLENAME_PROPERTY)
                val sqlQueryStmt = resource.getProperty(Constants.R2RML_SQLQUERY_PROPERTY)
                val queryStmt = resource.getProperty(Constants.xR2RML_QUERY_PROPERTY)
                val uniqRefStmts = resource.listProperties(Constants.xR2RML_UNIQUEREF_PROPERTY)
                val uniqueRefs: Set[String] =
                    if (uniqRefStmts != null)
                        uniqRefStmts.map(_.getObject.toString).toSet
                    else Set.empty
                val listPushDown = xR2RMLPushDown.extractPushDownTags(resource);

                val source: String =
                    if (tableNameStmt != null)
                        "Table name: " + tableNameStmt.getObject.toString
                    else if (sqlQueryStmt != null)
                        "SQL query: " + sqlQueryStmt.getObject.toString.trim
                    else if (queryStmt != null)
                        "Query: " + queryStmt.getObject.toString.trim
                    else
                        "Undefined property tableName, sqlQuery or query."

                // Check compliance with R2RML
                if (isLogicalTable(logResType)) {
                    if (queryStmt != null) {
                        val msg = "Logical Table cannot have an xrr:query property. " + source;
                        logger.error(msg);
                        throw new Exception(msg);
                    }
                    if (hasIterator(resource)) {
                        val msg = "Logical Table cannot have an rml:iterator property. " + source;
                        logger.error(msg);
                        throw new Exception(msg);
                    }
                }

                if (tableNameStmt != null) {
                    val tableName = tableNameStmt.getObject.toString

                    // Check validity of optional properties iterator and referenceFormulation
                    if (hasIterator(resource))
                        logger.warn("Ignoring iterator [" + readIterator(resource).get + "] with rr:tableName " + tableName)
                    if (!isDefaultReferenceFormulation(refFormulation)) {
                        val msg = "Invalid reference formulation: " + refFormulation + " with rr:tableName " + tableName
                        logger.error(msg);
                        throw new Exception(msg);
                    }

                    new xR2RMLTable(tableName, listPushDown)

                } else if (sqlQueryStmt != null) {

                    // Regular R2RML view
                    val queryStringAux = sqlQueryStmt.getObject.toString.trim;
                    val queryStr =
                        if (queryStringAux.endsWith(";")) {
                            queryStringAux.substring(0, queryStringAux.length - 1); // remove tailing ';'
                        } else {
                            queryStringAux
                        }

                    // Check validity of optional properties iterator and referenceFormulation
                    if (hasIterator(resource))
                        logger.warn("Ignoring iterator \"" + readIterator(resource).get + "\" with rr:sqlQuery \"" + queryStr + "\"")
                    if (!isDefaultReferenceFormulation(refFormulation)) {
                        val msg = "Invalid reference formulation: " + refFormulation + "\". Cannot be used with rr:sqlQuery \"" + queryStr + "\""
                        logger.error(msg);
                        throw new Exception(msg);
                    }

                    new xR2RMLQuery(queryStr, refFormulation, readIterator(resource), uniqueRefs, listPushDown)

                } else if (queryStmt != null) {
                    // xR2RML query
                    val queryStr = queryStmt.getObject.toString.trim;
                    new xR2RMLQuery(queryStr, refFormulation, readIterator(resource), uniqueRefs, listPushDown)

                } else {
                    val errorMessage = "Missing logical source property: rr:tableName, rr:sqlQuery or xrr:query";
                    logger.error(errorMessage);
                    throw new Exception(errorMessage);
                }
            }
        logSrc;
    }

    /**
     * Return true is the resource has an rml:iterator property
     */
    private def hasIterator(resource: Resource): Boolean = {
        (resource.getProperty(Constants.RML_ITERATOR_PROPERTY) != null)
    }

    /**
     *  Read the rml:iterator property, return None if undefined
     */
    private def readIterator(resource: Resource): Option[String] = {
        val iterStmt = resource.getProperty(Constants.RML_ITERATOR_PROPERTY)
        val iter: Option[String] =
            if (iterStmt == null)
                None
            else {
                val iterStr = iterStmt.getObject.toString.trim;
                if (!iterStr.isEmpty)
                    Some(iterStr)
                else None
            }
        iter
    }

    /** Return true is the reference formulation has the default value i.e. xrr:Column */
    private def isDefaultReferenceFormulation(refForm: String): Boolean = {
        Constants.xR2RML_REFFORMULATION_COLUMN.equals(refForm)
    }

    private def isLogicalSource(logResType: String): Boolean = {
        logResType.equals(Constants.xR2RML_LOGICALSOURCE_URI)
    }

    private def isLogicalTable(logResType: String): Boolean = {
        logResType.equals(Constants.R2RML_LOGICALTABLE_URI)
    }
}
