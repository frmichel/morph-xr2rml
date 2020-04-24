package fr.unice.i3s.morph.xr2rml.mongo.abstractquery

import org.apache.log4j.Logger
import org.apache.jena.graph.Triple
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.MorphBaseResultRdfTerms
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataSourceReader
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseDataTranslator
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryTranslator
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import fr.unice.i3s.morph.xr2rml.mongo.engine.MorphMongoDataTranslator
import fr.unice.i3s.morph.xr2rml.mongo.engine.MorphMongoResultSet
import es.upm.fi.dia.oeg.morph.base.querytranslator.TpBindings
import fr.unice.i3s.morph.xr2rml.mongo.engine.MorphMongoDataSourceReader
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLLogicalSource
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryOptimizer
import es.upm.fi.dia.oeg.morph.base.query.AbstractQueryProjection
import es.upm.fi.dia.oeg.morph.base.querytranslator.TpBindings

/**
 * Representation of the INNER JOIN abstract query between two atomic abstract queries,
 * generated from the relation between a child and a parent triples map.
 *
 * @param tpBindings a set of couples (triple pattern, List[triples map]) for which we create this atomic query.
 * May contain several bindings after query optimization e.g. self-join elimination i.e. 2 merged atomic queries
 * @param child the query representing the child triples map
 * @param childRef the xR2RML child reference of the join condition: rr:joinCondition [ ... rr:child ... ]
 * @param parent the query representing the parent triples map
 * @param parentRef the xR2RML parent reference of the join condition: rr:joinCondition [ ... rr:parent ... ]
 *
 * @author Franck Michel, I3S laboratory
 */
class AbstractQueryInnerJoinRef(

    tpBindings: TpBindings,
    val child: AbstractQueryAtomicMongo,
    val childRef: String,
    val parent: AbstractQueryAtomicMongo,
    val parentRef: String,
    lim: Option[Long])

        extends AbstractQuery(tpBindings, lim) {

    val logger = Logger.getLogger(this.getClass().getName());

    override def equals(a: Any): Boolean = {
        a.isInstanceOf[AbstractQueryInnerJoinRef] && {
            val q = a.asInstanceOf[AbstractQueryInnerJoinRef]
            this.child == q.child && this.childRef == q.childRef && this.parent == q.parent && this.parentRef == q.parentRef
        }
    }

    override def toString = {
        val bdgs = if (tpBindings.nonEmpty) tpBindings.toString + "\n" else ""
        bdgs +
            child.toString + " AS child\n" +
            "INNER JOIN\n" +
            parent.toString + " AS parent\n" +
            "ON child/" + childRef + " = parent/" + parentRef + limitStr
    }

    override def toStringConcrete: String = {
        child.toStringConcrete + " AS child\n" +
            "INNER JOIN\n" +
            parent.toStringConcrete + " AS parent\n" +
            "ON child/" + childRef + " = parent/" + parentRef + limitStr
    }

    /**
     * Translate all atomic abstract queries of this abstract query into concrete queries.
     * @param translator the query translator
     */
    override def translateAbstactQueriesToConcrete(translator: MorphBaseQueryTranslator): Unit = {
        child.translateAbstactQueriesToConcrete(translator)
        parent.translateAbstactQueriesToConcrete(translator)
    }

    /**
     * Check if atomic abstract queries within this query have a target query properly initialized
     * i.e. targetQuery is not empty
     */
    override def isTargetQuerySet: Boolean = {
        child.isTargetQuerySet && parent.isTargetQuerySet
    }

    /**
     * Return the list of SPARQL variables projected in this abstract query
     */
    override def getVariables: Set[String] = {
        (child.getVariables ++ parent.getVariables)
    }

    /**
     * Get the xR2RML projection of variable ?x in this query.
     *
     * @param varName the variable name
     * @return a set of projections in which the 'as' field is defined and equals 'varName'
     */
    override def getProjectionsForVariable(varName: String): Set[AbstractQueryProjection] = {
        child.getProjectionsForVariable(varName) ++ parent.getProjectionsForVariable(varName)
    }

    /**
     * Try to propagate conditions from one to the other or to merge the child and parent queries
     */
    override def optimizeQuery(optimizer: MorphBaseQueryOptimizer): AbstractQuery = {

        var childQ = child
        var parentQ = parent

        if (logger.isDebugEnabled)
            logger.debug("\n------------------ Optimizing query ------------------\n" + this)

        var newThis: AbstractQuery =
            if (optimizer.propagateConditionFromJoin) {
                // ----- Try to narrow down joined atomic queries by propagating conditions from one to the other -----
                // In the case of an inner join for a referencing triples map, this may happen only if the same 
                // variable is used several times in the triple pattern e.g. ?x ex:pred ?x
                childQ = childQ.propagateConditionFromJoinedQuery(parentQ)
                if (logger.isDebugEnabled && childQ != child)
                    logger.debug("Propagated condition of parent into child")

                parentQ = parentQ.propagateConditionFromJoinedQuery(childQ)
                if (logger.isDebugEnabled && parentQ != parent)
                    logger.debug("Propagated condition of child into parent")

                new AbstractQueryInnerJoinRef(this.tpBindings, childQ, this.childRef, parentQ, this.parentRef, limit)
            } else
                this

        if (optimizer.selfJoinElimination) {
            // ----- Try to eliminate a Self-Join by merging the 2 atomic queries -----
            val opt = childQ.mergeForInnerJoinRef(this.childRef, parentQ, this.parentRef)
            newThis = opt.getOrElse(newThis)
        }

        if (logger.isDebugEnabled) {
            if (newThis != this)
                logger.debug("\n------------------ Query optimized into ------------------\n" + newThis)
        }
        newThis
    }

    /**
     * Execute the query and produce the RDF terms for each of the result documents
     * by applying the triples map bound to this query.
     *
     * Each of the child and parent queries is executed. If their "targetQuery" contains
     * several queries their result is UNIONed.
     *
     * @param dataSourceReader the data source reader to query the database
     * @param dataTrans the data translator to create RDF terms
     * @return a list of MorphBaseResultRdfTerms instances, one for each result document
     * May return an empty result but NOT null.
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if the triples map bound to the query has no referencing object map
     */
    override def generateRdfTerms(
        dataSourceReader: MorphBaseDataSourceReader,
        dataTrans: MorphBaseDataTranslator): List[MorphBaseResultRdfTerms] = {

        val start = System.currentTimeMillis
        val dataTranslator = dataTrans.asInstanceOf[MorphMongoDataTranslator]

        if (logger.isInfoEnabled) {
            logger.info("===============================================================================");
            logger.info("Generating RDF triples from the inner join Ref query:\n" + this.toStringConcrete)
        }

        // Cache queries in case we have several bindings for this query
        var executedQueries = Map[String, List[String]]()

        val result = tpBindings.getNonEmptyBindings.toList.flatMap(tpb => {

            val tp = tpb.triple
            val boundTMs = tpb.boundTMs.toList
            val perBindingResult = boundTMs.flatMap(tm => {

                val subjectAsVariable = if (tp.getSubject.isVariable) Some(tp.getSubject.toString) else None
                val predicateAsVariable = if (tp.getPredicate.isVariable) Some(tp.getPredicate.toString) else None
                val objectAsVariable = if (tp.getObject.isVariable) Some(tp.getObject.toString) else None

                val sm = tm.subjectMap;
                val pom = tm.predicateObjectMaps.head
                val iter: Option[String] = tm.logicalSource.docIterator
                if (logger.isInfoEnabled)
                    logger.info("Generating RDF triples from inner join Ref query under triples map " + tm.toString + ": \n" + this.toStringConcrete);

                // Execute the child queries
                val childResSets = child.targetQuery.map(query => {
                    val queryMapId = MorphMongoDataSourceReader.makeQueryMapId(query, iter, None)
                    if (executedQueries.contains(queryMapId)) {
                        logger.info("Returning query results from cache.")
                        executedQueries(queryMapId)
                    } else {
                        val resultSet = dataSourceReader.executeQueryAndIterator(query, iter, None).asInstanceOf[MorphMongoResultSet].resultSet
                        executedQueries += (queryMapId -> resultSet.toList)
                        resultSet
                    }
                })
                // Make a UNION (flatten) of all the child results
                val childResultSet = childResSets.flatten
                logger.info("Query returned " + childResultSet.size + " results.")

                // Execute the parent queries (in the parent triples map), apply the iterator, and make a UNION (flatMap) of the results
                val parentResultSet = {
                    if (!pom.refObjectMaps.isEmpty) {
                        val rom = pom.refObjectMaps.head
                        val parentTM = dataSourceReader.factory.getMappingDocument.getParentTriplesMap(rom)

                        // Execute the parent queries 
                        val parentResSets = parent.targetQuery.map(query => {
                            val queryMapId = MorphMongoDataSourceReader.makeQueryMapId(query, parentTM.logicalSource.docIterator, None)
                            if (executedQueries.contains(queryMapId)) {
                                logger.info("Returning query results from cache.")
                                executedQueries(queryMapId)
                            } else {
                                val resultSet = dataSourceReader.executeQueryAndIterator(query, parentTM.logicalSource.docIterator, None).asInstanceOf[MorphMongoResultSet].resultSet
                                executedQueries += (queryMapId -> resultSet.toList)
                                resultSet
                            }
                        })
                        // Make a UNION (flatten) of all the parent results
                        val parentResultSet = parentResSets.flatten
                        logger.info("Query returned " + parentResultSet.size + " results.")
                        parentResultSet
                    } else
                        throw new MorphException("Error: inner join ref query bound to a triples map that has no RefObjectMap.")
                }

                // Main loop: iterate and process each result document of the result set
                var nbTriples = 0
                var i = 0;
                val triples = for (document <- childResultSet if (!limit.isDefined || (limit.isDefined && nbTriples < limit.get))) yield {
                    try {
                        i = i + 1;
                        if (logger.isDebugEnabled()) logger.debug("Generating RDF terms for child document " + i + "/" + childResultSet.size + ": " + document)

                        //---- Create the subject resource
                        val subjects = dataTranslator.translateData(sm, document)
                        if (subjects == null) { throw new Exception("null value in the subject triple") }
                        if (logger.isDebugEnabled()) logger.debug("Document " + i + " subjects: " + subjects)

                        //---- Create the list of resources representing subject target graphs
                        val subjectGraphs = sm.graphMaps.flatMap(sgmElement => {
                            dataTranslator.translateData(sgmElement, document)
                        })
                        if (logger.isTraceEnabled()) logger.trace("Document " + i + " subject graphs: " + subjectGraphs)

                        // ----- Make a list of resources for the predicate map of the predicate-object map
                        val predicates = dataTranslator.translateData(pom.predicateMaps.head, document)
                        if (logger.isTraceEnabled()) logger.trace("Document " + i + " predicates: " + predicates)

                        // ------ Make a list of resources for the referencing object map:
                        // Generate the IRIs from the parent query using the subject map of the parent triples map, 
                        // and join the child and parent references in the join condition
                        val refObjects = {
                            if (!pom.refObjectMaps.isEmpty) {

                                // ---- Compute a list of subject IRIs for the join condition
                                val rom = pom.refObjectMaps.head
                                val parentTM = dataTranslator.factory.getMappingDocument.getParentTriplesMap(rom)
                                val joinCond = rom.joinConditions.head

                                // Evaluate the child reference on the current document (of the child triples map)
                                val childMsp = MixedSyntaxPath(joinCond.childRef, sm.refFormulation)
                                val childValues: List[Object] = childMsp.evaluate(document)

                                // Evaluate the parent reference on each parent query result. The result is stored as pairs:
                                // (JSON document, result of the evaluation of the parent reference on the JSON document)
                                val parentMsp = MixedSyntaxPath(joinCond.parentRef, parentTM.logicalSource.refFormulation)
                                val parentValues = parentResultSet.map(res => (res, parentMsp.evaluate(res)))

                                // ---- Make the join between the child and parent values
                                val parentSubjects = parentValues.flatMap(parentVal => {
                                    // For each document returned by the parent query (named parent document),
                                    // if at least one of the child values is in the current parent document values, 
                                    // then generate an RDF term for the subject of the current parent document.
                                    if (!childValues.intersect(parentVal._2).isEmpty) // parentVal._2 is the evaluation of the parent ref
                                        Some(dataTranslator.translateData(parentTM.subjectMap, parentVal._1)) // parentVal._1 is the JSON document itself
                                    else
                                        // There was no match: return an empty list so that the final intersection of candidate return nothing
                                        Some(List())
                                }).flatten
                                if (logger.isTraceEnabled()) logger.trace("Join parent candidates: " + joinCond.toString + ", result:" + parentSubjects)

                                // Optionally convert the result to an RDF collection or container
                                if (rom.isR2RMLTermType)
                                    parentSubjects
                                else
                                    MorphBaseDataTranslator.createCollection(rom.termType.get, parentSubjects)
                            } else
                                List.empty
                        }
                        if (logger.isTraceEnabled()) logger.trace("Document " + i + " refObjects: " + refObjects)

                        // ----- Create the list of resources representing target graphs mentioned in the predicate-object map
                        val predicateObjectGraphs = pom.graphMaps.flatMap(pogmElement => {
                            dataTranslator.translateData(pogmElement, document)
                        });
                        if (logger.isTraceEnabled()) logger.trace("Document" + i + " predicate-object map graphs: " + predicateObjectGraphs)

                        // Compute result triples for the current document
                        val rslt =
                            subjects.flatMap(subject => {
                                predicates.flatMap(predicate => {
                                    refObjects.map(objct => {
                                        new MorphBaseResultRdfTerms(subject, subjectAsVariable, predicate, predicateAsVariable, objct, objectAsVariable)
                                    })
                                })
                            })
                        nbTriples += rslt.size
                        rslt
                    } catch {
                        case e: MorphException => {
                            logger.error("Error while translating data of document " + i + ": " + e.getMessage);
                            e.printStackTrace()
                            List() // empty list will be removed by the flat map of results 
                        }
                        case e: Exception => {
                            logger.error("Unexpected error while translating data of document " + i + ": " + e.getCause() + " - " + e.getMessage);
                            e.printStackTrace()
                            List() // empty list will be removed by the flat map of results 
                        }
                    }
                }
                triples.flatten
            })
            perBindingResult
        })

        logger.info("Inner join Ref computed " + result.size + " triples, in " + (System.currentTimeMillis - start) + " ms.")
        result
    }
}