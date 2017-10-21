package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.sql.MorphDatabaseMetaData
import es.upm.fi.dia.oeg.morph.base.sql.MorphTableMetaData

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
 * @param listPushDown the list of xrr:pushDown annotations as isntances of xR2RMLPushDown
 */
abstract class RDBxR2RMLLogicalSource(
    logicalTableType: Constants.LogicalTableType.Value,
    refFormulation: String,
    docIterator: Option[String],
    uniqueRefs: Set[String],
    listPushDown: List[xR2RMLPushDown])

        extends xR2RMLLogicalSource(logicalTableType, refFormulation, docIterator, uniqueRefs, listPushDown) {

    var tableMetaData: Option[MorphTableMetaData] = None;

    def getLogicalTableSize: Long = {
        if (this.tableMetaData.isDefined) { this.tableMetaData.get.getTableRows; }
        else { -1 }
    }

    def buildMetaData(dbMetaData: Option[MorphDatabaseMetaData]) = {
        val source = this match {
            case _: RDBxR2RMLTable => this.asInstanceOf[RDBxR2RMLTable]
            case _: RDBxR2RMLQuery => this.asInstanceOf[RDBxR2RMLQuery]
        }

        val tableName = this match {
            case xr2rmlTable: RDBxR2RMLTable => {
                val dbType =
                    if (dbMetaData.isDefined) { dbMetaData.get.dbType; }
                    else { Constants.DATABASE_DEFAULT }
                val enclosedChar = Constants.getEnclosedCharacter(dbType);
                val tableNameAux = xr2rmlTable.getValue.replaceAll("\"", enclosedChar);
                tableNameAux
            }
            case xr2rmlQuery: RDBxR2RMLQuery => {
                val queryStringAux = xr2rmlQuery.getValue.trim
                val queryString = {
                    // Remove trailing ';' if any
                    if (queryStringAux.endsWith(";")) { queryStringAux.substring(0, queryStringAux.length - 1) }
                    else { queryStringAux }
                }
                "(" + queryString + ")";
            }
            case _ => {
                val msg = "Unknown logical table/source type: " + this
                logger.error(msg)
                throw new Exception(msg)
            }
        }

        if (dbMetaData.isDefined) {
            val optionTableMetaData = dbMetaData.get.getTableMetaData(tableName);
            val tableMetaData = optionTableMetaData.getOrElse(
                MorphTableMetaData.buildTableMetaData(tableName, dbMetaData.get));
            this.tableMetaData =
                if (tableMetaData != null)
                    Some(tableMetaData)
                else None
        }
    }
}
