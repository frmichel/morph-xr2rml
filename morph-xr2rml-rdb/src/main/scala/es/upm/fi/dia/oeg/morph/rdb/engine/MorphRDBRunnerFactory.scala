package es.upm.fi.dia.oeg.morph.rdb.engine

import java.sql.Connection

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.DBUtility
import es.upm.fi.dia.oeg.morph.base.GenericConnection
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataTranslator
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunnerFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryProcessor
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryTranslator
import es.upm.fi.dia.oeg.morph.base.sql.MorphDatabaseMetaData
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBxR2RMLQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBxR2RMLTable
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLTable
import es.upm.fi.dia.oeg.morph.rdb.querytranslator.MorphRDBQueryProcessor
import es.upm.fi.dia.oeg.morph.rdb.querytranslator.MorphRDBQueryTranslator

class MorphRDBRunnerFactory extends MorphBaseRunnerFactory {

    /**
     * Database-specific steps of the creation of a factory
     *
     * Build metadata from the database
     */
    override def postCreateFactory = {

        if (this.connection == null || !this.connection.isRelationalDB)
            throw new MorphException("Connection non initialized or not a relational database connection: " + this.connection)

        logger.info("Building database MetaData")

        val sqlCnx = this.connection.concreteCnx.asInstanceOf[Connection]

        val metaData = MorphDatabaseMetaData(sqlCnx, this.getProperties.databaseName, this.getProperties.databaseType);
        val optMetaData = if (metaData == null) None else Some(metaData)

        // Create new TriplesMaps with a logical source that contains db metadata
        val rdbTMs: Iterable[RDBR2RMLTriplesMap] = this.getMappingDocument.triplesMaps.map { tm =>
            val rdbLS = tm.getLogicalSource match {
                case tab: xR2RMLTable => new RDBxR2RMLTable(tab.tableName, tab.listPushDown)
                case qry: xR2RMLQuery => new RDBxR2RMLQuery(qry.query, qry.refFormulation, qry.docIterator, qry.uniqueRefs, qry.listPushDown)
                case _ => throw new Exception("Unsupported type of Logical Source: " + tm.getLogicalSource)
            }
            rdbLS.buildMetaData(optMetaData)

            val rdbTM = new RDBR2RMLTriplesMap(tm.resource, rdbLS, tm.refFormulation, tm.subjectMap, tm.predicateObjectMaps)
            rdbTM
        }

        val rdbMappingdocument = new RDBR2RMLMappingDocument(rdbTMs, this.getMappingDocument.mappingDocumentPath, this.getMappingDocument.mappingDocumentPrefixMap)
        rdbMappingdocument.dbMetaData = optMetaData
        MorphBaseRunnerFactory.mappingDocument = rdbMappingdocument
    }

    override def createConnection: GenericConnection = {
        val properties = this.getProperties
        val connection = if (properties.noOfDatabase > 0) {
            val databaseUser = properties.databaseUser;
            val databaseName = properties.databaseName;
            val databasePassword = properties.databasePassword;
            val databaseDriver = properties.databaseDriver;
            val databaseURL = properties.databaseURL;
            DBUtility.getLocalConnection(databaseUser, databaseName, databasePassword, databaseDriver, databaseURL, "Runner");
        } else
            null

        new GenericConnection(Constants.DatabaseType.Relational, connection)
    }

    override def createUnfolder: MorphRDBUnfolder = {
        new MorphRDBUnfolder(this)
    }

    override def createDataSourceReader: MorphRDBDataSourceReader = {
        new MorphRDBDataSourceReader(this)
    }

    override def createDataTranslator: MorphBaseDataTranslator = {
        new MorphRDBDataTranslator(this);
    }

    override def createQueryTranslator: MorphBaseQueryTranslator = {
        new MorphRDBQueryTranslator(this)
    }

    override def createQueryProcessor: MorphBaseQueryProcessor = {
        new MorphRDBQueryProcessor(this)
    }
}
