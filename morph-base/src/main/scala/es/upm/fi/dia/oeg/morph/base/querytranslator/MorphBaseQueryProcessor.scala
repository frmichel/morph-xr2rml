package es.upm.fi.dia.oeg.morph.base.querytranslator

import java.io.File

import org.apache.jena.query.Query

import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery

/**
 * Abstract class for the engine that shall execute a query and translate results into
 * RDF triples (DESCRIBE, CONTRUCT) or a SPARQL result set (SELECT, ASK)
 * 
 * @author Franck Michel, I3S laboratory
 */
abstract class MorphBaseQueryProcessor(factory: IMorphFactory) {

    /**
     * Execute the query, translate the results from the database into triples
     * or result sets, and serialize the result into an output file
     * 
     * @param sparqlQuery SPARQL query 
     * @param abstractQuery associated AbstractQuery resulting from the translation of sparqlQuery,
     * in which the executable target queries have been computed.
     * If None, then an empty response must be generated.
     * @param syntax the output syntax:  XML or JSON for a SPARQL SELECT or ASK query, and RDF 
     * syntax for a SPARQL DESCRIBE or CONSTRUCT query
     */
    def process(sparqlQuery: Query, abstractQuery: Option[AbstractQuery], syntax: String): Option[File]
}
