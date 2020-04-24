package es.upm.fi.dia.oeg.morph.base.engine

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter

import org.apache.jena.query.Query
import org.apache.jena.query.QueryFactory
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.querytranslator.SparqlUtility

/**
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
class MorphBaseRunner(val factory: IMorphFactory) {

    val logger = Logger.getLogger(this.getClass());

    /**
     * Start process when running in stand-alone application mode (not in SPARQL end-point mode)
     */
    def run = {
        if (factory.getProperties.queryFilePath.isDefined) {
            val sparqlQuery = QueryFactory.read(factory.getProperties.queryFilePath.get)
            this.runQuery(sparqlQuery, null)
        } else
            this.runMaterialization
    }

    /**
     * RDF Triples materialization
     */
    def runMaterialization = {
        logger.info("Running graph materialization...")
        val startTime = System.currentTimeMillis()

        // Run the queries and generate triples
        factory.getDataTranslator.translateData_Materialization(factory.getMappingDocument)

        // Write the result to the output file
        if (factory.getProperties.outputFileMaxTriples > 0)
            factory.getMaterializer.serializeIncremental(factory.getProperties.outputSyntaxRdf)
        else
            factory.getMaterializer.serialize(factory.getProperties.outputSyntaxRdf)

        conclude(startTime)
    }

    /**
     * Run with a SPARQL query, either from the SPARQL endpoint more or in stand-alone running mode
     *
     * @param sparqlQuery hum... the SPARQL query.
     * @param contentType content-type HTTP header from the SPARQL query if any. Can be null,
     * in that case the format used us that of the configuration file
     */
    def runQuery(query: Query, contentType: String): Option[File] = {
        if (logger.isInfoEnabled) {
            logger.info("****************************************************************************************************************")
            logger.info("Running query translation. Content-type: " + contentType)
        }
        val startTime = System.currentTimeMillis()
        var output: Option[File] = None

        // Figure out the output syntax using the query content-type if any,
        // the type of query (SELECT, ASK, DESCRIBE, CONSTRUCT),
        // and the default formats mentioned in the configuration file
        val dfltSyntaxRdf = factory.getProperties.outputSyntaxRdf
        val dfltResultFrmt = factory.getProperties.outputSyntaxResult
        val negCT = SparqlUtility.negotiateContentType(contentType, query, dfltSyntaxRdf, dfltResultFrmt)
        val syntax =
            if (!negCT.isDefined) {
                logger.error("Content-type negotiation failed. Ignoring the query.")
                return None
            } else negCT.get._2

        // Rewrite the SPARQL query and translate it
        val translated = factory.getQueryTranslator.translate(query)
        val rewrittenSparqlQuery = translated._1
        val rewrittenQuery = translated._2

        if (rewrittenSparqlQuery.isDefined) {
            // The abstract query can be ignored, only the SPARQL query will be executed
            output = factory.getQueryProcessor.process(rewrittenSparqlQuery.get, None, syntax)

        } else {

            if (rewrittenQuery.isDefined) {
                if (logger.isInfoEnabled) {
                    logger.info("SPARQL Query = \n" + query);
                    logger.info("------------------ Abstract Query ------------------ = \n" + rewrittenQuery.get.toString);
                    logger.info("------------------ Concrete Query ------------------ = \n" + rewrittenQuery.get.toStringConcrete);
                }
            } else
                logger.warn("Could not translate the SPARQL into a target query.")

            // Execute the query and build the response.
            // If the translation failed because no binding was found (rewrittenQuery is None)
            // then send an empty response, but send a valid SPARQL response anyway
            output = factory.getQueryProcessor.process(query, rewrittenQuery, syntax)
        }

        if (logger.isInfoEnabled)
            logger.info("Query response output file: " + output.getOrElse("None"))
        conclude(startTime)
        output
    }

    private def conclude(startTime: Long) = {
        val endTime = System.currentTimeMillis();
        logger.warn("Overall SPARQL query processing time = " + (endTime - startTime) + "ms.");
        logger.warn("**********************DONE****************************");
    }

    /**
     * Decide the output format and content type of the SPARQL results, based on the type of SPARQL query
     * and the value of the Accept HTTP header.
     * If the Accept is null, the default RDF syntax or result format is returned depending on the query type.
     *
     * @param accept value of the Accept HTTP header. Can be null.
     * @param query the SPARQL query. Cannot be null.
     *
     * @return the content-type value e.g. "application/sparql-results+xml"
     */
    def negotiateContentType(accept: String, query: Query): Option[String] = {

        val dfltSyntaxRdf = factory.getProperties.outputSyntaxRdf
        val dfltSyntaxResult = factory.getProperties.outputSyntaxResult
        val neg = SparqlUtility.negotiateContentType(accept, query, dfltSyntaxRdf, dfltSyntaxResult)
        if (neg.isDefined)
            Some(neg.get._1)
        else
            None
    }
}

object MorphBaseRunner {
    val logger = Logger.getLogger(this.getClass());

    def erasefile(file: String) {
        try {
            var out = new PrintWriter(new BufferedWriter(new FileWriter(file, false)))
            out.print("")
            out.flush()
            out.close()
        } catch {
            case e: IOException => { logger.info("Error with file " + file); }
        }
    }
}