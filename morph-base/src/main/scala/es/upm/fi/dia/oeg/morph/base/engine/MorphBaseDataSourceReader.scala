package es.upm.fi.dia.oeg.morph.base.engine

import es.upm.fi.dia.oeg.morph.base.GenericConnection
import es.upm.fi.dia.oeg.morph.base.MorphBaseResultSet
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLPushDown

/**
 * A data source reader is used to execute queries against the target database
 *
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
abstract class MorphBaseDataSourceReader(val factory: IMorphFactory) {

    /**
     * Execute a target database query against the connection
     *
     * @param query the GenericQuery that encapsulates a target database query
     * @param limit optional maximum number of results to retrieve
     * @return a concrete instance of MorphBaseResultSet. Must NOT return null, may return an empty result.
     */
    def execute(query: GenericQuery, limit: Option[Long]): MorphBaseResultSet

    /**
     * Execute a target database against the connection and apply an rml:iterator on the results.
     *
     * @param query the GenericQuery that encapsulates a target database query
     * @param logSrcIterator optional xR2RML logical source rml:iterator
     * @param limit optional maximum number of results to retrieve
     * @return a concrete instance of MorphBaseResultSet. Must NOT return null, may return an instance with empty result.
     */
    def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long]): MorphBaseResultSet

    /**
     * Execute a query against the database; apply the optional rml:iterator and xrr:pushDown on the results.
     *
     * @param query the GenericQuery that encapsulates a target database query
     * @param logSrcIterator optional xR2RML logical source rml:iterator
     * @param limit optional maximum number of results to retrieve
     * @param listPushDown optional list of xR2RMLPushDown's from the logical source
     * @return a concrete instance of MorphBaseResultSet. Must NOT return null, may return an instance with empty result.
     */
    def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long], listPushDown: List[xR2RMLPushDown]): MorphBaseResultSet

    def setTimeout(timeout: Int)

    def closeConnection()
}