package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import java.io.FileOutputStream
import java.io.PrintWriter

import org.apache.log4j.Logger

import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.rdf.model.Model
import org.apache.jena.sparql.core.describe.DescribeBNodeClosure
import org.apache.jena.sparql.resultset.ResultSetMem

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryProcessor
import java.io.File
import es.upm.fi.dia.oeg.morph.base.GeneralUtility

/**
 * Execute the database query and produce the XML SPARQL result set
 *
 * @author Franck Michel, I3S laboratory
 */
class MorphMongoQueryProcessor(factory: IMorphFactory) extends MorphBaseQueryProcessor(factory) {

    val logger = Logger.getLogger(this.getClass().getName());

    /**
     * Execute the database query, translate the database results into triples,
     * evaluate the SPARQL query on the resulting graph and save the output to a file.
     *
     * @param sparqlQuery SPARQL query
     * @param abstractQuery associated AbstractQuery resulting from the translation of sparqlQuery,
     * in which the executable target queries have been computed.
     * If None, then an empty response must be generated.
     * @param syntax the output syntax:  XML or JSON for a SPARQL SELECT or ASK query, or of the
     * RDF syntaxes for a SPARQL DESCRIBE or CONSTRUCT query
     */
    override def process(sparqlQuery: Query, abstractQuery: Option[AbstractQuery], syntax: String): Option[File] = {

        // Execute the query and translate the results into triples
        if (abstractQuery.isDefined) {
            factory.getDataTranslator.generateRDFTriples(abstractQuery.get)
        }

        // Late SPARQL evaluation: evaluate the SPARQL query on the result graph
        // If abstractQuery is not defined, the model will be empty => no response will come up but no error will occur
        val start = System.currentTimeMillis
        val qexec: QueryExecution = QueryExecutionFactory.create(sparqlQuery, factory.getMaterializer.model)

        // Decide the output file
        var output: Option[File] =
            if (factory.getProperties.serverActive)
                GeneralUtility.createRandomFile("", factory.getProperties.outputFilePath + ".", "")
            else Some(new File(factory.getProperties.outputFilePath))

        if (output.isDefined) {

            if (sparqlQuery.isConstructType) { // --- SPARQL CONSTRUCT

                val resultModel: Model = qexec.execConstruct
                output = factory.getMaterializer.serialize(resultModel, output.get, syntax)
                qexec.close

            } else if (sparqlQuery.isDescribeType) { // --- SPARQL DESCRIBE

                val dh: DescribeBNodeClosure = null
                val resultModel: Model = qexec.execDescribe
                output = factory.getMaterializer.serialize(resultModel, output.get, syntax)
                qexec.close

            } else if (sparqlQuery.isAskType) { // --- SPARQL ASK

                var result: Boolean = qexec.execAsk

                if (syntax == Constants.OUTPUT_FORMAT_RESULT_XML) {
                    val writer = new PrintWriter(output.get, "UTF-8")
                    writer.write(ResultSetFormatter.asXMLString(result))
                    writer.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_JSON) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsJSON(outputStream, result)
                    outputStream.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_CSV) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsCSV(outputStream, result)
                    outputStream.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_TSV) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsTSV(outputStream, result)
                    outputStream.close
                } else {
                    logger.error("Invalid output result syntax: " + factory.getProperties.outputSyntaxResult)
                    output = None
                }

                qexec.close

            } else if (sparqlQuery.isSelectType) { // --- SPARQL SELECT

                var resultSet: ResultSet = qexec.execSelect
                if (factory.getProperties.outputDisplay)
                    // Create an in-memory result set to display it in tabular format as well as save it to a file
                    // Note: the file will be displayed too, but the tabular format is much easier to read
                    resultSet = new ResultSetMem(resultSet)

                if (syntax == Constants.OUTPUT_FORMAT_RESULT_XML) {
                    val writer = new PrintWriter(output.get, "UTF-8")
                    writer.write(ResultSetFormatter.asXMLString(resultSet))
                    writer.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_JSON) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsJSON(outputStream, resultSet)
                    outputStream.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_CSV) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsCSV(outputStream, resultSet)
                    outputStream.close
                } else if (syntax == Constants.OUTPUT_FORMAT_RESULT_TSV) {
                    val outputStream = new FileOutputStream(output.get)
                    ResultSetFormatter.outputAsTSV(outputStream, resultSet)
                    outputStream.close
                } else {
                    logger.error("Invalid output result syntax: " + factory.getProperties.outputSyntaxResult)
                    output = None
                }

                if (output.isDefined && factory.getProperties.outputDisplay) {
                    val rewindable = resultSet.asInstanceOf[ResultSetMem]
                    rewindable.rewind
                    if (rewindable.hasNext) {
                        if (logger.isInfoEnabled) {
                            logger.info("Result set contains " + rewindable.size + " triples.")
                            logger.info("Tabular result set:\n" + ResultSetFormatter.asText(resultSet))
                        }
                    }
                }
                qexec.close
            }
            logger.warn("Time for late SPARQL query evaluation = " + (System.currentTimeMillis - start) + "ms.");
        }

        output
    }
}