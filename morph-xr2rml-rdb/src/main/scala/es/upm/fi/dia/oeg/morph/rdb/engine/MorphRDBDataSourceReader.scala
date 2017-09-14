package es.upm.fi.dia.oeg.morph.rdb.engine

import java.sql.Connection
import es.upm.fi.dia.oeg.morph.base.DBUtility
import es.upm.fi.dia.oeg.morph.base.GenericConnection
import es.upm.fi.dia.oeg.morph.base.MorphBaseResultSet
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataSourceReader
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import es.upm.fi.dia.oeg.morph.base.sql.ISqlQuery
import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLPushDown

/**
 * This class is used in case of the query rewriting access method,
 * to execute queries on the fly.
 * It is not used in the data materialization access method.
 */
class MorphRDBDataSourceReader(factory: IMorphFactory) extends MorphBaseDataSourceReader(factory) {

    var timeout: Int = factory.getProperties.databaseTimeout

    override def execute(query: GenericQuery, limit: Option[Long]): MorphBaseResultSet = {
        val sqlCnx: Connection = factory.getConnection.concreteCnx.asInstanceOf[Connection]
        val rs = DBUtility.execute(sqlCnx, query.concreteQuery.asInstanceOf[ISqlQuery].toString(), this.timeout);
        val resultSet = new MorphRDBResultSet(rs);
        resultSet;
    }

    override def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long]): MorphBaseResultSet = {
        throw new MorphException("Unsupported method.")
    }

    override def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long]
    , listPushDown:List[xR2RMLPushDown]): MorphBaseResultSet = {
        throw new MorphException("Unsupported method.")
    }
        
    override def setTimeout(timeout: Int) {
        this.timeout = timeout
    }

    override def closeConnection() {
        val sqlCnx: Connection = factory.getConnection.concreteCnx.asInstanceOf[Connection]
        DBUtility.closeConnection(sqlCnx, this.getClass().getName());
    }
}