package es.upm.fi.dia.oeg.morph.base.querytranslator

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.query.Query
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpDistinct
import org.apache.jena.sparql.algebra.op.OpFilter
import org.apache.jena.sparql.algebra.op.OpJoin
import org.apache.jena.sparql.algebra.op.OpLeftJoin
import org.apache.jena.sparql.algebra.op.OpOrder
import org.apache.jena.sparql.algebra.op.OpProject
import org.apache.jena.sparql.algebra.op.OpSlice
import org.apache.jena.sparql.algebra.op.OpUnion
import org.apache.jena.sparql.core.BasicPattern
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.Constants

/**
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
object SparqlUtility {

    val logger = Logger.getLogger(this.getClass().getName())

    /**
     * Reorder the list of triples in the order of their subject's hash
     */
    def groupTriplesBySubject(triples: List[Triple]): List[Triple] = {

        if (triples == null || triples.size == 0)
            List.empty
        else if (triples.size == 1)
            triples
        else {
            var mapByHash: Map[Int, List[Triple]] = Map.empty

            for (tp <- triples) {
                val sub = tp.getSubject
                val subHash = sub.hashCode
                if (mapByHash.containsKey(subHash))
                    mapByHash += (subHash -> (mapByHash(subHash) :+ tp))
                else
                    mapByHash += (subHash -> List(tp))
            }

            var triplesReordered: List[Triple] = List.empty
            for (key <- mapByHash.keySet)
                triplesReordered = triplesReordered ::: mapByHash(key)
            triplesReordered
        }
    }

    /**
     * Reorder the triples of a basic graph pattern in the order of their subject
     */
    def groupBGPBySubject(bgp: OpBGP): OpBGP = {
        val triplesReordered = groupTriplesBySubject(bgp.getPattern.getList.toList)
        new OpBGP(BasicPattern.wrap(triplesReordered))
    }

    def getSubjects(triples: List[Triple]): List[Node] = {
        var result: List[Node] = List.empty
        if (triples != null)
            for (triple <- triples)
                result = result :+ triple.getSubject
        result
    }

    def getObjects(triples: List[Triple]): List[Node] = {
        var result: List[Node] = List.empty
        if (triples != null)
            for (triple <- triples)
                result = result :+ triple.getObject
        result
    }

    def isBlankNode(node: Node) = {
        node.isBlank || (node.isVariable && node.getName.startsWith("?"))
    }

    def isNodeInSubjectGraph(node: Node, op: Op): Boolean = {
        val found = op match {
            case tp: Triple => {
                tp.getSubject() == node
            }
            case bgp: OpBGP => {
                this.isNodeInSubjectBGP(node, bgp.getPattern().toList);
            }
            case join: OpJoin => {
                this.isNodeInSubjectGraphs(node, join.getLeft(), join.getRight());
            }
            case leftJoin: OpLeftJoin => {
                this.isNodeInSubjectGraphs(node, leftJoin.getLeft(), leftJoin.getRight());
            }
            case union: OpUnion => {
                this.isNodeInSubjectGraphs(node, union.getLeft(), union.getRight());
            }
            case filter: OpFilter => {
                this.isNodeInSubjectGraph(node, filter.getSubOp());
            }
            case project: OpProject => {
                this.isNodeInSubjectGraph(node, project.getSubOp());
            }
            case slice: OpSlice => {
                this.isNodeInSubjectGraph(node, slice.getSubOp());
            }
            case distinct: OpDistinct => {
                this.isNodeInSubjectGraph(node, distinct.getSubOp());
            }
            case order: OpOrder => {
                this.isNodeInSubjectGraph(node, order.getSubOp());
            }
            case _ => false
        }

        found;
    }

    private def isNodeInSubjectBGP(node: Node, bgpList: List[Triple]): Boolean = {
        val isInHead = node == bgpList.head.getSubject
        var found = isInHead;
        if (!found && !bgpList.tail.isEmpty) {
            found = isNodeInSubjectBGP(node, bgpList.tail);
        }
        found;
    }

    private def isNodeInSubjectGraphs(node: Node, opLeft: Op, opRight: Op): Boolean = {
        val isInLeft = isNodeInSubjectGraph(node, opLeft);
        var found = isInLeft;
        if (!found) {
            found = isNodeInSubjectGraph(node, opRight);
        }
        found;
    }

    /**
     * Decide the output format and content type of the SPARQL results, based on the type of 
     * SPARQL query and the value of the Accept HTTP header.
     *
     * If the Accept is null or empty, the default RDF syntax or result format is returned depending on the query type.
     *
     * Note: the Accept header may contain several values.
     *
     * @param accept value of the Accept HTTP header. Can be null.
     * @param query the SPARQL query. Cannot be null.
     * @param defaultRdfSyntax default RDF syntax, should be read from the configuration file. Must be null.
     * @param defaultResultFrmt default format for SPARQL result sets, should be read from the configuration file. Must be null.
     *
     * @return a couple (negotiated content-type, corresponding format among Constants.OUTPUT_FORMAT*),
     * or None if the requested format cannot be satisfied.<br>
     * Example:
     *   <code>("application/sparql-results+xml", Constants.OUTPUT_FORMAT_RESULT_XML)</code>
     */
    def negotiateContentType(
        accept: String,
        query: Query,
        defaultRdfSyntax: String,
        defaultResultFrmt: String): Option[(String, String)] = {

        val result = if (query.isAskType || query.isSelectType) {

            // --- Formats for a SPARQL result set
            if (accept == null || accept == "") {
                Some((outputFormatToContentType(defaultResultFrmt), defaultResultFrmt))
            } else {
                val accl = accept.toLowerCase
                if (accl contains "application/sparql-results+xml")
                    Some(("application/sparql-results+xml", Constants.OUTPUT_FORMAT_RESULT_XML))
                else if (accl contains "application/sparql-results+json")
                    Some(("application/sparql-results+json", Constants.OUTPUT_FORMAT_RESULT_JSON))
                else if (accl contains "application/sparql-results+csv")
                    Some(("application/sparql-results+csv", Constants.OUTPUT_FORMAT_RESULT_CSV))
                else if (accl contains "application/sparql-results+tsv")
                    Some(("application/sparql-results+tsv", Constants.OUTPUT_FORMAT_RESULT_TSV))
                else if (accl contains "application/xml")
                    Some(("application/xml", Constants.OUTPUT_FORMAT_RESULT_XML))
                else if (accl contains "text/plain")
                    Some(("text/plain", Constants.OUTPUT_FORMAT_RESULT_XML))
                else
                    None
            }

        } else if (query.isConstructType || query.isDescribeType) {

            // --- RDF formats
            if (accept == null || accept == "")
                Some((outputFormatToContentType(defaultRdfSyntax), defaultRdfSyntax))
            else {
                val accl = accept.toLowerCase
                if (accl.contains("text/turtle") || accl.contains("application/turtle") || accl.contains("application/x-turtle"))
                    Some(("text/turtle", Constants.OUTPUT_FORMAT_TURTLE))
                else if (accl contains "text/nt")
                    Some(("text/nt", Constants.OUTPUT_FORMAT_NTRIPLE))
                else if (accl contains "text/n3")
                    Some(("text/n3", Constants.OUTPUT_FORMAT_N3))
                else if (accl contains "application/rdf+xml")
                    Some(("application/rdf+xml", Constants.OUTPUT_FORMAT_RDFXML))
                else if (accl contains "application/xml")
                    Some(("application/xml", Constants.OUTPUT_FORMAT_RDFXML))
                else if (accl contains "application/ld+json")
                    Some(("application/ld+json", Constants.OUTPUT_FORMAT_JSONLD))
                else if (accl contains "application/json")
                    Some(("application/json", Constants.OUTPUT_FORMAT_JSONLD))
                else if (accl contains "text/plain")
                    Some(("text/plain", Constants.OUTPUT_FORMAT_TURTLE))
                else
                    None
            }
        } else None

        if (logger.isInfoEnabled)
            logger.info("Request Accept: " + accept + ". Negotiated: " + result.getOrElse("None"))
        result
    }

    private def outputFormatToContentType(outputFormat: String): String = {
        outputFormat match {
            // --- Formats for a SPARQL result set
            case Constants.OUTPUT_FORMAT_RESULT_XML => "application/sparql-results+xml"
            case Constants.OUTPUT_FORMAT_RESULT_JSON => "application/sparql-results+json"
            case Constants.OUTPUT_FORMAT_RESULT_CSV => "application/sparql-results+csv"
            case Constants.OUTPUT_FORMAT_RESULT_TSV => "application/sparql-results+tsv"

            // --- RDF formats
            case Constants.OUTPUT_FORMAT_TURTLE => "text/turtle"
            case Constants.OUTPUT_FORMAT_RDFXML => "application/rdf+xml"
            case Constants.OUTPUT_FORMAT_RDFXML_ABBREV => "application/rdf+xml"
            case Constants.OUTPUT_FORMAT_NTRIPLE => "text/nt"
            case Constants.OUTPUT_FORMAT_N3 => "text/n3"
            case Constants.OUTPUT_FORMAT_NQUAD => "application/n-quads"

            case _ => ""
        }
    }
}
