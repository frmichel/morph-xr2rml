package fr.unice.i3s.morph.xr2rml.mongo.engine

import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseUnfolder
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLJoinCondition
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLLogicalSource
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLTable
import fr.unice.i3s.morph.xr2rml.mongo.MongoDBQuery

/**
 * @author Franck Michel, I3S laboratory
 *
 */
class MorphMongoUnfolder(factory: IMorphFactory) extends MorphBaseUnfolder(factory) {

    val logger = Logger.getLogger(this.getClass().getName());

    /**
     * Parse the query string provided in the mapping and build an instance of GenericQuery containing
     * a MongoDBQuery for the case of MongoDB, to be extended for other types of db.
     * @return GenericQuery instance corresponding to the query provided in the logical source
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    override def unfoldTriplesMap(triplesMap: R2RMLTriplesMap): GenericQuery = {

        if (logger.isDebugEnabled()) logger.debug("Unfolding triples map " + triplesMap.toString)
        val logicalSrc = triplesMap.logicalSource.asInstanceOf[xR2RMLLogicalSource];

        val logicalSrcQuery: String = logicalSrc match {
            case _: xR2RMLTable => {
                logger.error("Logical source with table name not allowed in the context of a JSON document database: a query is expected")
                null
            }
            case _: xR2RMLQuery => {
                // For some reason, Jena escapes double quotes. The workaround is to remove the slash.
                // replaceAll takes a regular expression, so to match \" we must escape the slash hence the \\"
                logicalSrc.getValue.replaceAll("""\\"""", "\"");
            }
            case _ => { throw new MorphException("Unknown logical table/source type: " + logicalSrc) }
        }

        if (logger.isDebugEnabled())
            logger.debug("Raw query for triples map " + triplesMap.name + ": " + logicalSrcQuery)
        val mongoQuery = MongoDBQuery.parseQueryString(logicalSrcQuery, false)
        logger.info("Cleaned query for triples map " + triplesMap.name + ": " + mongoQuery.toString)
        new GenericQuery(Constants.DatabaseType.MongoDB, mongoQuery, logicalSrc.docIterator)
    }

    override def unfoldJoinConditions(
            joinConditions: Set[R2RMLJoinCondition],
            childTableAlias: String,
            joinQueryAlias: String,
            dbType: String): Object = { null }
}