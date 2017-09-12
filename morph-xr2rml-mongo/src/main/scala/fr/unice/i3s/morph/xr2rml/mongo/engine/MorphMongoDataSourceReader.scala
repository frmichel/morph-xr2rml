package fr.unice.i3s.morph.xr2rml.mongo.engine

import java.net.URI

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.log4j.Logger
import org.jongo.Jongo
import org.jongo.MongoCollection
import org.jongo.MongoCursor

import com.mongodb.DB
import com.mongodb.MongoClient
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.GenericConnection
import es.upm.fi.dia.oeg.morph.base.MorphBaseResultSet
import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataSourceReader
import es.upm.fi.dia.oeg.morph.base.path.JSONPath_PathExpression
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import fr.unice.i3s.morph.xr2rml.mongo.JongoResultHandler
import fr.unice.i3s.morph.xr2rml.mongo.MongoDBQuery

import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLPushDown

/**
 * Utility class to handle the execution of MongoDB queries
 *
 * @author Franck Michel, I3S laboratory
 */
class MorphMongoDataSourceReader(factory: IMorphFactory) extends MorphBaseDataSourceReader(factory) {

    /** Cache of already executed queries. The key of the map is the query string itself. */
    private val executedQueries: scala.collection.mutable.Map[String, List[String]] = new scala.collection.mutable.HashMap

    val logger = Logger.getLogger(this.getClass().getName());

    /**
     * Execute a MongoDB query against the database connection.
     *
     * @param query the GenericQuery that encapsulates a target database query
     * @param limit optional maximum number of results to retrieve
     * @return a concrete instance of MorphBaseResultSet. Must NOT return null, may return an empty result.
     */
    override def execute(query: GenericQuery, limit: Option[Long]): MorphBaseResultSet = {
        val mongoQ = query.concreteQuery.asInstanceOf[MongoDBQuery]
        val jongoCnx = factory.getConnection.concreteCnx.asInstanceOf[Jongo]

        val collec: MongoCollection = jongoCnx.getCollection(mongoQ.collection)

        val queryStr =
            if (mongoQ.query.startsWith("{") && mongoQ.query.endsWith("}"))
                mongoQ.query
            else
                "{" + mongoQ.query + "}"

        val results: MongoCursor[String] =
            if (limit.isDefined)
                collec.find(queryStr).limit(limit.get.toInt).map[String](MorphMongoDataSourceReader.jongoHandler)
            else
                collec.find(queryStr).map[String](MorphMongoDataSourceReader.jongoHandler)
        new MorphMongoResultSet(results.toList)
    }

    override def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long]): MorphBaseResultSet = {
      this.executeQueryAndIterator(query, logSrcIterator, limit, Nil)
    }
    
    /**
     * Execute a query against the database and apply an rml:iterator on the results.
     *
     * Results of the query may be saved to a cache (config) to avoid doing the same query several times
     * in case we need it again later (in case of referencing object map).
     * Major drawback: memory consumption, this is not appropriate for very big databases.
     *
     * @param query the GenericQuery that encapsulates a target database query
     * @param logSrcIterator optional xR2RML logical source rml:iterator
     * @param limit optional maximum number of results to retrieve
     * @return a concrete instance of MorphBaseResultSet. Must NOT return null, may return an empty result.
     */
    override def executeQueryAndIterator(query: GenericQuery, logSrcIterator: Option[String], limit: Option[Long]
    , listPushDown:List[xR2RMLPushDown]): MorphBaseResultSet = {

        // A query is simply and uniquely identified by its concrete string value
        logger.info("Executing query: " + query.concreteQuery + " with limit " + limit)
        val queryMapId = MorphMongoDataSourceReader.makeQueryMapId(query, logSrcIterator, limit)
        val start = System.currentTimeMillis
        val queryResult =
            if (executedQueries.contains(queryMapId)) {
                if (logger.isTraceEnabled()) logger.trace("Query retrieved from cache, queryId: " + queryMapId)
                logger.info("Returning query results from cache.")
                executedQueries(queryMapId)
            } else {
                // Execute the query against the database, choose the execution method depending on the db type
                val resultSet = this.execute(query, limit).asInstanceOf[MorphMongoResultSet].resultSet.toList

                // Save the result of this query in case it is asked again later (in a join)
                // @todo USE WITH CARE: this would need to be strongly improved with the use of a real cache library,
                // and memory-consumption-based eviction.
                if (factory.getProperties.cacheQueryResult) {
                    executedQueries += (queryMapId -> resultSet)
                    if (logger.isTraceEnabled()) logger.trace("Adding query to cache: " + query)
                }
                resultSet
            }

        // Apply the iterator to the result set, this creates a new result set
        val queryResultIter =
            if (logSrcIterator.isDefined) {
                val jPath = JSONPath_PathExpression.parseRaw(logSrcIterator.get)
                queryResult.flatMap(result => jPath.evaluate(result).map(value => value.toString))
            } else queryResult

        logger.info("Query returned " + queryResult.size + " result(s), " + queryResultIter.size + " result(s) after applying the iterator, in: " + (System.currentTimeMillis - start) + " ms.");
        new MorphMongoResultSet(queryResultIter)
    }

    override def setTimeout(timeout: Int) {
    }

    override def closeConnection() {
    }
}

object MorphMongoDataSourceReader {

    private val logger = Logger.getLogger(this.getClass().getName());

    private val jongoHandler: JongoResultHandler = new JongoResultHandler

    /**
     * Create a Jongo context from a MongoDB connection
     * @return an instance of GenericConnection with dbtype = Constants.DatabaseType.MongoDB
     */
    def createConnection(props: MorphProperties): GenericConnection = {
        val userName = props.databaseUser;
        val dbName = props.databaseName;
        val userPwd = props.databasePassword;
        val dbUrl = props.databaseURL;

        try {
            val uri = new URI(dbUrl);
            val servAdr = new ServerAddress(uri.getHost(), uri.getPort())

            val dbCnx: DB =
                if (userName.isEmpty() && userPwd.isEmpty()) {
                    new MongoClient(servAdr).getDB(dbName)
                } else {
                    val cred = MongoCredential.createMongoCRCredential(userName, dbName, userPwd.toCharArray())
                    new MongoClient(servAdr, seqAsJavaList(List(cred))).getDB(dbName)
                }

            // Create a generic connection with a Jongo context
            val jongoCtx = new Jongo(dbCnx)
            new GenericConnection(Constants.DatabaseType.MongoDB, jongoCtx);

        } catch {
            case e: java.net.URISyntaxException =>
                throw new Exception("Invalid database URL: " + dbUrl + ". Must be formatted as: mongodb://127.0.0.1:27017", e)
            case e: java.net.UnknownHostException =>
                throw new Exception("Cannot connect to the database: " + e.getMessage(), e)
            case e: com.mongodb.MongoException =>
                throw new Exception("Error when connecting to the database: " + e.getMessage(), e)
        }
    }

    def makeQueryMapId(query: GenericQuery, iter: Option[String], limit: Option[Long]): String = {
        var str = GeneralUtility.cleanString(query.concreteQuery.toString)
        if (limit.isDefined)
            str += ", Limit: " + limit.get
        if (iter.isDefined)
            str += ", Iterator: " + GeneralUtility.cleanString(iter.get)
        str
    }
}