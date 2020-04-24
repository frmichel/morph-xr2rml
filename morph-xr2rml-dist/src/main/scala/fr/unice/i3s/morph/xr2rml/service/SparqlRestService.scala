package fr.unice.i3s.morph.xr2rml.service

import java.io.FileInputStream
import java.io.InputStreamReader

import org.apache.jena.query.QueryFactory
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunner
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunnerFactory
import javax.ws.rs.Consumes
import javax.ws.rs.FormParam
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status

/**
 * REST service implementing the SPARQL query protocol for queries SELECT, DESCRIBE and CONSTRUCT
 * 
 * @author Franck Michel, I3S laboratory
 */
@Path("/sparql")
class SparqlRestService {

    val headerAccept = "Access-Control-Allow-Origin"

    val logger = Logger.getLogger(this.getClass)

    /**
     * Simple test service
     */
    @GET
    @Path("test")
    @Produces(Array("text/plain"))
    def test: Response = {
        return Response.status(Status.OK).header(headerAccept, "*").entity("SPARQL REST service is up and running").build
    }

    /**
     * SPARQL Protocol 1.1: Query via GET has no Request Content Type
     */
    @GET
    def processSparqlQueryGet(@QueryParam("query") query: String,
                              @QueryParam("default-graph-uri") defaultGraphUris: java.util.List[String],
                              @QueryParam("named-graph-uri") namedGraphUris: java.util.List[String],
                              @Context headers: HttpHeaders): Response = {

        processSparqlQuery(query, defaultGraphUris, namedGraphUris, headers)
    }

    /**
     * SPARQL Protocol 1.1:
     * Query via URL-encoded POST has "application/x-www-form-urlencoded" Request Content Type.
     * Query via POST directly has "application/sparql-query" Request Content Type.
     */
    @POST
    @Consumes(Array("application/x-www-form-urlencoded", "application/sparql-query"))
    def processSparqlQueryPost(@FormParam("query") query: String,
                               @FormParam("default-graph-uri") defaultGraphUris: java.util.List[String],
                               @FormParam("named-graph-uri") namedGraphUris: java.util.List[String],
                               @Context headers: HttpHeaders): Response = {

        // @Context servletResponse: HttpServletResponse
        processSparqlQuery(query, defaultGraphUris, namedGraphUris, headers)
    }

    /**
     * Processing of SPARQL queries SELECT, DESCRIBE and CONSTRUCT
     */
    private def processSparqlQuery(query: String,
                                   defaultGraphUris: java.util.List[String],
                                   namedGraphUris: java.util.List[String],
                                   headers: HttpHeaders): Response = {

        val accept =
            if (headers.getRequestHeader(HttpHeaders.ACCEPT) != null)
                headers.getRequestHeader(HttpHeaders.ACCEPT).get(0)
            else
                "application/sparql-results+xml"

        if (logger.isInfoEnabled) {
            logger.info("SPARQL query: " + query)
            logger.info("Default graph: " + defaultGraphUris)
            logger.info("Named graph: " + namedGraphUris)
            logger.info("Content-Type: " + headers.getRequestHeader(HttpHeaders.CONTENT_TYPE))
            logger.info("Accept: " + accept)
        }

        try {
            if (query == null || query.isEmpty)
                return Response.status(Status.BAD_REQUEST).
                    header(headerAccept, "*").
                    header(HttpHeaders.CONTENT_TYPE, "text/plain").
                    entity("No SPARQL query provided.").build

            // Create the runner to execute this query
            val factory = MorphBaseRunnerFactory.createFactory
            val runner: MorphBaseRunner = factory.createRunner

            // Negotiate the content type of the response to the SPARQL query
            val sparqlQuery = QueryFactory.create(query)
            val negContentType = runner.negotiateContentType(accept, sparqlQuery)
            if (!negContentType.isDefined)
                return Response.status(Status.NOT_ACCEPTABLE).
                    header(headerAccept, "*").
                    header(HttpHeaders.CONTENT_TYPE, "text/plain").
                    entity("Requested content type not supported: " + accept).build

            // Execute the query against the database and save results to an output file
            val output = runner.runQuery(sparqlQuery, negContentType.get)
            // Read the response from the output file and direct it to the HTTP response
            if (output.isDefined) {
                val file = new FileInputStream(output.get)
                val isr = new InputStreamReader(file, "UTF-8")
                return Response.status(Status.OK).
                    header(headerAccept, "*").
                    header(HttpHeaders.CONTENT_TYPE, negContentType.get).
                    entity(isr).build
            } else
                return Response.status(Status.INTERNAL_SERVER_ERROR).
                    header(HttpHeaders.CONTENT_TYPE, "text/plain").
                    header(headerAccept, "*").
                    entity("Could not write output result").build

        } catch {
            case e: Exception => {
                val msg = "Error in SPARQL query processing: " + e.getMessage
                logger.error(msg)
                if (logger.isDebugEnabled) logger.debug("Strack trace:\n" + e.getStackTraceString)
                return Response.status(Status.INTERNAL_SERVER_ERROR).
                    header(HttpHeaders.CONTENT_TYPE, "text/plain").
                    header(headerAccept, "*").
                    entity(msg).build
            }
        }
    }
}