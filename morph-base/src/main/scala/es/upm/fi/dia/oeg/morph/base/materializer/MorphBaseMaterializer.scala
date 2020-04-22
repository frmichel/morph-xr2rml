package es.upm.fi.dia.oeg.morph.base.materializer

import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter

import scala.collection.JavaConversions.mapAsJavaMap

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.tdb.TDBFactory

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import java.io.FileOutputStream

/**
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
class MorphBaseMaterializer(
        val factory: IMorphFactory,
        val model: Model) {

    val logger = Logger.getLogger(this.getClass.getName)

    /** Count of files serialized in case a max number of triples is defined by property split of logical source */
    var counter: Integer = 0

    /**
     * Serialize the default Jena model into the output file defined in the configuration
     *
     * @param syntax the RDF syntax to use
     * @return the output file or None if the operation failed
     */
    def serialize(syntax: String): Option[File] = {
        val output = new File(factory.getProperties.outputFilePath)
        serialize(this.model, output, syntax)
    }

    /**
     * Serialize the default Jena model into the file defined in the configuration
     * with an additional index at the end of the file name,
     * and removes all triples from the model.
     *
     * @param syntax the RDF syntax to use
     * @return the output file or None if the operation failed
     */
    def serializeIncremental(syntax: String): Option[File] = {
        val output = new File(factory.getProperties.outputFilePath + '.' + counter.toString)
        val result = serialize(this.model, output, syntax)
        counter += 1
        this.model.removeAll()
        result
    }

    /**
     * Serialize a Jena model into a given output file
     *
     * @param model the Jena model to serialize
     * @param output the file where to write the RDF serialization
     * @param syntax the RDF syntax to use
     * @return the same file as parameter 'output', or None if any error occurs
     */
    def serialize(model: Model, output: File, syntax: String): Option[File] = {
        try {
            val outputStream = new FileOutputStream(output)

            logger.info("Model size (in triples): " + model.size())
            logger.info("Writing serialization to output " + output)

            val writer = new OutputStreamWriter(outputStream, "UTF-8")
            model.write(writer, syntax, null)
            outputStream.flush
            outputStream.close
            Some(output)
        } catch {
            case e: Exception => {
                logger.error("Cannot serialize model into RDF. Cause: " + e.getMessage)
                None
            }
        }
    }

    def setModelPrefixMap(prefixMap: Map[String, String]) = {
        this.model.setNsPrefixes(prefixMap);
    }

    /**
     * Materialize one RDF triple in the default graph of the current model
     */
    def materializeQuad(subject: RDFNode, predicate: RDFNode, obj: RDFNode, graph: RDFNode) {

        if (graph != null)
            throw new Exception("Named graphs not supported in this version")

        if (subject != null && predicate != null && obj != null) {
            try {
                val pred = this.model.createProperty(predicate.asResource().getURI())

                var tripleAlreadyExists: Boolean = false

                /* Case RDB:
                 * The following is a bit crappy: in a join with mixed syntax path, the join cannot be done in SQL,
                 * as a result we run an SQL cartesian product to retrieve all possibilities and evaluate them afterwards.
                 * This makes lots of lines with the same results, and we generate several times the same triples.
                 * For triples with simple literal of IRI object, that is fine: Jena does not add twice the same triple.
                 * But for RDF lists and containers, Jena has no way to know whether this is the same triple or not.
                 * As a result a list or container is created several times in the model.
                 * So, in those particular cases, list and containers, we have to check whether the same triple is already
                 * in the model. If yes,
                 * - first we do not add a triple.
                 * - but that not enough. The collection/container passed to the method (parameter obj),
                 *   exists in the model since it consists of several triples.
                 *   Therefore, we have to remove this collection/container from the model.
                 */

                // Check if the object is an RDF List and if there would already be the same triple in the model
                if (obj.isResource && GeneralUtility.isRdfList(model, obj.asResource)) {

                    // List all triples concerning the same subject and predicate to see if there would already be the same list
                    val existingObjs = model.listObjectsOfProperty(subject.asResource(), pred)

                    while (!tripleAlreadyExists && existingObjs.hasNext()) {
                        val node = existingObjs.next()
                        if (node.isResource && GeneralUtility.isRdfList(model, node.asResource)) {
                            val same = GeneralUtility.compareRdfList(node.asResource, obj.asResource)
                            // If both lists are the same then we have to remove the new one from the model
                            if (same)
                                GeneralUtility.removeRdfList(model, obj.asResource)
                            tripleAlreadyExists = tripleAlreadyExists || same
                        }
                    }
                }

                // Check if the object is an RDF container and if there would already be the same triple in the model
                if (obj.isResource && GeneralUtility.isRdfContainer(model, obj.asResource)) {
                    // List all triples concerning the subject to see if there would already be the same container
                    val existingObjs = model.listObjectsOfProperty(subject.asResource(), pred)
                    while (!tripleAlreadyExists && existingObjs.hasNext()) {
                        val node = existingObjs.next()
                        if (node.isResource && GeneralUtility.isRdfContainer(model, node.asResource)) {
                            val same = GeneralUtility.compareRdfContainer(node.asResource, obj.asResource)
                            // If both containers are the same, then we have to remove the new one from the model
                            if (same)
                                GeneralUtility.removeRdfContainer(model, obj.asResource)
                            tripleAlreadyExists = tripleAlreadyExists || same
                        }
                    }
                }

                if (!tripleAlreadyExists) {
                    // Create and add the triple into the Jena model
                    val stmt = this.model.createStatement(subject.asResource(), pred, obj)
                    this.model.add(stmt)
                } else {
                    logger.trace("Triple already materialized, ignoring: [" + subject.asResource() + "] [" + pred + "] [" + obj.asResource() + "]")
                }
            } catch {
                case e: Exception => {
                    e.printStackTrace()
                    logger.error("Unable to serialize triple, subject: " + subject);
                }
            }
        } else
            logger.error("Unable to serialize triple, subject: " + subject + ", predicate: " + predicate + ", object: " + obj);
    }

    /**
     * Materialize one RDF triple in in target graphs of the current model
     *
     * @return number of triples generated
     */
    def materializeQuads(
        subjects: List[RDFNode],
        predicates: List[RDFNode],
        objects: List[RDFNode],
        refObjects: List[RDFNode],
        graphs: List[RDFNode]): Integer = {

        var nbTriples = 0
        predicates.foreach(pred => {
            subjects.foreach(sub => {
                objects.foreach(obj => {
                    if (graphs.isEmpty) {
                        this.materializeQuad(sub, pred, obj, null)
                        nbTriples += 1
                        if (logger.isDebugEnabled()) logger.debug("Materialized triple: [" + sub + "] [" + pred + "] [" + obj + "]")
                    } else {
                        graphs.foreach(graph => {
                            this.materializeQuad(sub, pred, obj, graph)
                            nbTriples += 1
                            if (logger.isDebugEnabled()) logger.debug("Materialized triple: graph[" + graph + "], [" + sub + "] [" + pred + "] [" + obj + "]")
                        })
                    }
                })
                refObjects.foreach(obj => {
                    if (obj != null) {
                        if (graphs.isEmpty) {
                            this.materializeQuad(sub, pred, obj, null)
                            nbTriples += 1
                            if (logger.isDebugEnabled()) logger.debug("Materialized triple: [" + sub + "] [" + pred + "] [" + obj + "]")
                        } else {
                            graphs.foreach(graph => {
                                this.materializeQuad(sub, pred, obj, graph)
                                nbTriples += 1
                                if (logger.isDebugEnabled()) logger.debug("Materialized triple: graph[" + graph + "], [" + sub + "] [" + pred + "] [" + obj + "]")
                            })
                        }
                    }
                })
            })
        })
        nbTriples
    }
}

object MorphBaseMaterializer {
    val logger = Logger.getLogger(this.getClass.getName)

    def apply(factory: IMorphFactory, jenaMode: String): MorphBaseMaterializer = {
        if (logger.isDebugEnabled)
            logger.debug("Creating MorphBaseMaterializer. Mode: " + jenaMode + ", format: " +
                factory.getProperties.outputSyntaxRdf + ", output: " + factory.getProperties.outputFilePath)

        val model = MorphBaseMaterializer.createJenaModel(jenaMode)
        new MorphBaseMaterializer(factory, model)
    }

    /**
     * Create a Jena model
     *
     * @param jenaMode "tdb" or "memory". If null, defaults to "memory"
     */
    def createJenaModel(jenaMode: String): Model = {
        val model =
            if (jenaMode == null)
                MorphBaseMaterializer.createJenaMemoryModel
            else {
                if (jenaMode.equalsIgnoreCase(Constants.JENA_MODE_TYPE_TDB))
                    MorphBaseMaterializer.createJenaTDBModel
                else if (jenaMode.equalsIgnoreCase(Constants.JENA_MODE_TYPE_MEMORY))
                    MorphBaseMaterializer.createJenaMemoryModel
                else
                    MorphBaseMaterializer.createJenaMemoryModel
            }
        model
    }

    private def createJenaMemoryModel: Model = { ModelFactory.createDefaultModel; }

    private def createJenaTDBModel: Model = {
        val jenaDatabaseName = System.currentTimeMillis + "";
        val tdbDatabaseFolder = "tdb-database";
        val folder = new File(tdbDatabaseFolder);
        if (!folder.exists)
            folder.mkdir

        val tdbFileBase = tdbDatabaseFolder + "/" + jenaDatabaseName;
        logger.info("TDB filebase = " + tdbFileBase);
        return TDBFactory.createModel(tdbFileBase);
    }

}