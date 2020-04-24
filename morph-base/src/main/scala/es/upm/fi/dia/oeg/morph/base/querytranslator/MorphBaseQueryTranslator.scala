package es.upm.fi.dia.oeg.morph.base.querytranslator

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.log4j.Logger

import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.query.Query
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.OpAsQuery
import org.apache.jena.sparql.algebra.TableFactory
import org.apache.jena.sparql.algebra.op.OpDistinct
import org.apache.jena.sparql.algebra.op.OpProject
import org.apache.jena.sparql.algebra.op.OpSlice
import org.apache.jena.sparql.algebra.op.OpTable
import org.apache.jena.sparql.engine.binding.BindingFactory

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.TemplateUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.AbstractCondition
import es.upm.fi.dia.oeg.morph.base.query.AbstractConditionEquals
import es.upm.fi.dia.oeg.morph.base.query.AbstractConditionNotNull
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.query.AbstractQueryProjection
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap

/**
 * Abstract class for the engine that shall translate a SPARQL query into a concrete database query
 *
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
abstract class MorphBaseQueryTranslator(val factory: IMorphFactory) {

    val properties = factory.getProperties

    val optimizer = new MorphBaseQueryOptimizer()

    optimizer.selfJoinElimination = properties.selfJoinElimination
    optimizer.selfUnionElimination = properties.selfUnionElimination
    optimizer.propagateConditionFromJoin = properties.propagateConditionFromJoin

    optimizer.subQueryElimination = properties.subQueryElimination
    optimizer.transJoinSubQueryElimination = properties.transJoinSubQueryElimination
    optimizer.transSTGSubQueryElimination = properties.transSTGSubQueryElimination
    optimizer.subQueryAsView = properties.subQueryAsView

    val logger = Logger.getLogger(this.getClass());

    /**
     * High level entry point to the query translation process.
     *
     * 1. A query that simply contains <code>DESCRIBE &lt;uri&gt;</code> is expanded to the following
     * before it is translated:
     * <code>DESCRIBE <uri> WHERE {
     *   { <uri> ?p ?x. } UNION { ?y ?q <uri> . }
     * } </code>
     *
     * 2. SPARQL query simplification: a query like this<br>
     * <code>SELECT DISTINCT ?p WHERE { ?s ?p ?o } </code><br>
     * is transformed into the SPARQL 1.1. query:<br>
     * <code>SELECT DISTINCT ?p WHERE { VALUES ?p ( :abc :def ... ) } </code><br>
     * and the abstract query is not executed at all.
     *
     * @param sparqlQuery the SPARQL query to translate
     * @return a couple in which one is None and the other is set:<br>
     * - Case (None, AbstractQuery): in the AbstractQuery instance, the targetQuery parameter has been set with
     * a set of concrete queries. In the RDB case there is only one query;
     * in the MongoDB case there may be several queries, which means a union of the results of all queries.
     * The result is None in case an error occurred.<br>
     * - Case (SPARQL query, None): no abstract query will be executed, only the SPARQL query will be
     * (see point 2 above)
     */
    def translate(sparqlQuery: Query): (Option[Query], Option[AbstractQuery]) = {

        val start = System.currentTimeMillis()
        var sparqlQ: Query = sparqlQuery

        // In case of a "DESCRBIE <URI>" expand the query with a graph pattern
        sparqlQ = SparqlQueryRewriter.expandDescribe(sparqlQ)

        // In case of an ASK query, add a "LIMIT 1" if there is no limiy
        sparqlQ = SparqlQueryRewriter.expandAsk(sparqlQ)

        // Compile and optimize the SPARQL query
        val opQuery = SparqlQueryRewriter.rewriteOptimize(sparqlQ, factory.getProperties.sparqlOptimization)

        // Compute the bindings and translate to the Abstract Query Language
        val abstractQuery = this.translate(opQuery)

        if (abstractQuery.isDefined) {
            /* -- CONSTANT PROJECTION OPTIMIZATION --
             * Check if the query can be simplified if all projected variables have constant values.
             * A query like:
             *   SELECT DISTINCT ?p WHERE { ?s ?p ?o }
             * is transformed into:
             *   SELECT DISTINCT ?p WHERE { VALUES ?p ( :abc :def ... ) }
             * if ?p is always bound with constant values
             */
            val opMod = allVarsProjectedAsConstantTermMaps(opQuery, abstractQuery.get)
            logger.info("Query translation time (including bindings) = " + (System.currentTimeMillis() - start) + "ms.");
            if (opMod.isDefined) {
                val queryMod = OpAsQuery.asQuery(opMod.get)

                if (sparqlQ.isAskType)
                    queryMod.setQueryAskType
                else if (sparqlQ.isSelectType)
                    queryMod.setQuerySelectType
                else if (sparqlQ.isDescribeType)
                    queryMod.setQueryDescribeType
                else if (sparqlQ.isConstructType)
                    queryMod.setQueryConstructType

                (Some(queryMod), None)
            } else
                (None, abstractQuery)

        } else (None, None)
    }

    protected def translate(op: Op): Option[AbstractQuery]

    /**
     * Translation of a triple pattern into an abstract query under a set of xR2RML triples maps
     *
     * @param tpBinding a SPARQL triple pattern and the triples maps bound to it
     * @param limit the value of the optional LIMIT keyword in the SPARQL graph pattern
     * @return abstract query. This may be an UNION if there are multiple triples maps,
     * and this may contain INNER JOINs for triples maps that have a referencing object map (parent triples map).
     * If there is only one triples map and no parent triples map, the result is an atomic abstract query.
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException
     */
    def transTPm(tpBinding: TpBinding, limit: Option[Long]): AbstractQuery

    /**
     * Generate the set of xR2RML references that are evaluated when generating the triples that match tp.
     * Those references are used to select the data elements to mention in the projection part of the
     * target database query.
     *
     * @param tp a SPARQL triple pattern
     * @param tm a triples map that has been assessed as a candidate triples map for the translation of tp into a query
     * @return set of projections
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if the triples map has no object map
     */
    def genProjection(tp: Triple, tm: R2RMLTriplesMap): Set[AbstractQueryProjection] = {

        var refs: Set[AbstractQueryProjection] = Set.empty

        if (tp.getSubject().isVariable())
            refs = refs + new AbstractQueryProjection(tm.subjectMap.getReferences.toSet, Some(tp.getSubject.toString))

        val pom = tm.predicateObjectMaps.head
        if (tp.getPredicate().isVariable())
            refs = refs + new AbstractQueryProjection(pom.predicateMaps.head.getReferences.toSet, Some(tp.getPredicate.toString))

        if (pom.hasRefObjectMap) {
            // The joined fields must always be projected, whether tp.obj is an IRI or a variable.
            // Useless for an RDB, but necessary for MongoDB since it cannot compute joins itself.
            val rom = pom.getRefObjectMap(0)
            for (jc <- rom.joinConditions)
                refs = refs + new AbstractQueryProjection(jc.childRef)
        } else if (tp.getObject().isVariable()) {
            refs = refs + new AbstractQueryProjection(pom.objectMaps.head.getReferences.toSet, Some(tp.getObject.toString))
        }
        refs
    }

    /**
     * Generate the set of xR2RML references that are evaluated when generating the triples that match tp.
     * Those references are used to select the data elements to mention in the projection part of the
     * target database query.
     * This version applies to the parent triples map of a relation child/parent triples map.
     *
     * @param tp a SPARQL triple pattern
     * @param tm a triples map that has been assessed as a candidate triples map for the translation of tp into a query
     * @return set of projections
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if the triples map has no referencing object map
     */
    def genProjectionParent(tp: Triple, tm: R2RMLTriplesMap): Set[AbstractQueryProjection] = {

        var refs: Set[AbstractQueryProjection] = Set.empty

        val pom = tm.predicateObjectMaps.head
        if (!pom.hasRefObjectMap)
            throw new MorphException("Triples map " + tm + " has no parent triples map")

        // The joined fields must always be projected, whether tp.obj is an IRI or a variable: since MongoDB
        // cannot compute joins, the xR2RML processor has to do it, thus joined fields must be returned by both queries.
        val rom = pom.getRefObjectMap(0)
        for (jc <- rom.joinConditions) {
            refs = refs + new AbstractQueryProjection(jc.parentRef)
        }

        // In addition, if tp.obj is a variable, the subject of the parent TM must be projected too.
        if (tp.getObject().isVariable()) {
            val parentTM = factory.getMappingDocument.getParentTriplesMap(rom)
            refs = refs + new AbstractQueryProjection(parentTM.subjectMap.getReferences.toSet, Some(tp.getObject.toString))
        }

        refs
    }

    /**
     * Generate the set of conditions to apply to the database query (the from part), so that triples map TM
     * generates triples that match tp.
     * If a triple pattern term is constant (IRI or literal), genCond generates an equality condition,
     * only if the term map is reference-valued or template-valued. Indeed, if the term map is constant-valued and
     * the tp term is constant too, we should already have checked that they were compatible in the bind_m function.
     *
     * If a triple pattern term is variable, genCond generates a not-null condition, only
     * if the term map is reference-valued or template-valued. Indeed, if the term map is constant-valued, then the variable
     * will simply be bound to the constant value of the term map but that does not entail any condition in the query.
     *
     * A condition is either isNotNull(JSONPath expression) or equals(JSONPath expression, value)
     *
     * @param tp a SPARQL triple pattern
     * @param tm a triples map that has been assessed as a candidate triples map for the translation of tp into a query
     * @return set of conditions, either non-null or equality
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if the triples map has no object map
     */
    def genCond(tp: Triple, tm: R2RMLTriplesMap): Set[AbstractCondition] = {

        var conditions: Set[AbstractCondition] = Set.empty

        { // === Subject map ===
            val tpTerm = tp.getSubject
            val termMap = tm.subjectMap

            if (termMap.isReferenceOrTemplateValued)
                if (tpTerm.isVariable)
                    // Add a not-null condition for each reference in the term map
                    conditions = termMap.getReferences.map(ref => new AbstractConditionNotNull(ref)).toSet
                else if (tpTerm.isURI) {
                    // Add an equality condition for each reference in the term map
                    conditions = genEqualityConditions(termMap, tpTerm)
                }
        }
        { // === Predicate map ===
            val tpTerm = tp.getPredicate
            val termMap = tm.predicateObjectMaps.head.predicateMaps.head

            if (termMap.isReferenceOrTemplateValued)
                if (tpTerm.isVariable) {
                    // Add a not-null condition for each reference in the term map
                    conditions = conditions ++ termMap.getReferences.map(ref => new AbstractConditionNotNull(ref)).toSet
                } else if (tpTerm.isURI) {
                    // Add an equality condition for each reference in the term map
                    conditions = conditions ++ genEqualityConditions(termMap, tpTerm)
                }
        }
        { // === Object map ===
            val tpTerm = tp.getObject
            val pom = tm.predicateObjectMaps.head

            if (tpTerm.isLiteral) {
                // Add an equality condition for each reference in the term map
                if (!pom.hasObjectMap)
                    throw new MorphException("Triples map " + tm + " has no object map. Presumably an inccorect triple pattern binding.")
                val termMap = pom.objectMaps.head
                if (termMap.isReferenceOrTemplateValued) {
                    conditions = conditions ++ genEqualityConditions(termMap, tpTerm)
                }

            } else if (tpTerm.isURI) {

                if (pom.hasRefObjectMap) {
                    val rom = pom.getRefObjectMap(0)
                    // Add non-null condition on the child reference 
                    for (jc <- rom.joinConditions) {
                        conditions = conditions + new AbstractConditionNotNull(jc.childRef)
                    }
                } else {
                    // tp.obj is a constant IRI and there is no RefObjectMap: add an equality condition for each reference in the term map
                    val termMap = pom.objectMaps.head
                    if (termMap.isReferenceOrTemplateValued) {
                        conditions = conditions ++ genEqualityConditions(termMap, tpTerm)
                    }
                }

            } else if (tpTerm.isVariable) {

                if (pom.hasRefObjectMap) {
                    val rom = pom.getRefObjectMap(0)
                    // Add non-null condition on the child reference 
                    for (jc <- rom.joinConditions)
                        conditions = conditions + new AbstractConditionNotNull(jc.childRef)
                } else {
                    // tp.obj is a Variable and there is no RefObjectMap: add a non-null condition for each reference in the term map
                    val termMap = pom.objectMaps.head
                    if (termMap.isReferenceOrTemplateValued) {
                        conditions = conditions ++ termMap.getReferences.map(ref => new AbstractConditionNotNull(ref)).toSet
                    }
                }
            }
        }

        if (logger.isDebugEnabled()) logger.debug("Translation returns conditions: " + conditions)
        conditions
    }

    /**
     * Generate the set of conditions to match the object of a triple pattern with the subject
     * of a referencing object map.
     * A condition is either isNotNull(JSONPath expression) or equals(JSONPath expression, value)
     *
     * @param tp a SPARQL triple pattern
     * @param tm a triples map that has been assessed as a candidate triples map for the translation of tp into a query
     * @return set of conditions, either non-null or equality
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if the triples map has no referencing object map
     */
    def genCondParent(tp: Triple, tm: R2RMLTriplesMap): Set[AbstractCondition] = {

        var conditions: Set[AbstractCondition] = Set.empty

        // === Object map ===
        val tpTerm = tp.getObject
        val pom = tm.predicateObjectMaps.head

        if (!pom.hasRefObjectMap)
            throw new MorphException("Triples map " + tm + " has no parent triples map. Presumably an inccorect triple pattern binding.")

        if (tpTerm.isURI) {
            // tp.obj is a constant IRI to be matched with the subject of the parent TM:
            // add an equality condition for each reference in the subject map of the parent TM
            val rom = pom.getRefObjectMap(0)
            val parentSM = factory.getMappingDocument.getParentTriplesMap(rom).subjectMap
            if (parentSM.isReferenceOrTemplateValued)
                conditions = conditions ++ genEqualityConditions(parentSM, tpTerm)

            // Add non-null condition on the parent reference
            for (jc <- rom.joinConditions) {
                conditions = conditions + new AbstractConditionNotNull(jc.parentRef)
            }

        } else if (tpTerm.isVariable) {
            // tp.obj is a SPARQL variable to be matched with the subject of the parent TM
            val rom = pom.getRefObjectMap(0)
            val parentSM = factory.getMappingDocument.getParentTriplesMap(rom).subjectMap
            if (parentSM.isReferenceOrTemplateValued)
                conditions = conditions ++ parentSM.getReferences.map(ref => new AbstractConditionNotNull(ref)).toSet

            // Add non-null condition on the parent reference
            for (jc <- rom.joinConditions)
                conditions = conditions + new AbstractConditionNotNull(jc.parentRef)
        }

        if (logger.isDebugEnabled()) logger.debug("Translation returns Parent conditions: " + conditions)
        conditions
    }

    /**
     * Generate one equality condition between a reference in the term map (Column name, JSONPath expression...)
     * and a term of a triple pattern.
     *
     * If the term map is reference-valued, one equality condition is generated.
     * If the term map is template-value, possibly several equality conditions are generated, i.e. one for each
     * capturing group in the template string.
     *
     * Return an empty set for other types of term map.
     *
     * @param targetQuery child or parent, i.e. the query to which references of termMap relate to
     * @param termMap the term map that should possibly generate terms that match the triple pattern term tpTerm
     * @param tpTerm a term from a triple pattern
     * @return a set of one equality condition for a reference-value term map, possibly several conditions
     * for a template-valued term map
     */
    private def genEqualityConditions(termMap: R2RMLTermMap, tpTerm: Node): Set[AbstractCondition] = {

        if (termMap.isReferenceValued) {
            // Make a new equality condition between the reference in the term map and the value in the triple pattern term
            if (tpTerm.isLiteral)
                Set(new AbstractConditionEquals(termMap.getOriginalValue, tpTerm.getLiteralValue.toString))
            else
                Set(new AbstractConditionEquals(termMap.getOriginalValue, tpTerm.toString(false)))
        } else if (termMap.isTemplateValued) {
            // Get the references of the template string and the associated values in the triple pattern term
            val refValueCouples = TemplateUtility.getTemplateMatching(termMap.getOriginalValue, tpTerm.toString(false))

            // For each reference and associated value, build a new equality condition
            val refValueConds = refValueCouples.map(m => {
                if (termMap.inferTermType == Constants.R2RML_IRI_URI)
                    new AbstractConditionEquals(m._1, GeneralUtility.decodeURI(m._2))
                else
                    new AbstractConditionEquals(m._1, m._2)
            })
            refValueConds.toSet
        } else
            Set.empty
    }

    /**
     * Optimization "Constant Projection" aka. "Projection Pushing".
     *
     * Deal with queries prototypical of schema exploration, like:<br>
     * <code>SELECT DISTINCT ?p WHERE { ?s ?o ?p } LIMIT 100</code>
     *
     * In a the naive approach, this will entail a massive UNION of all triples maps.
     *
     * If all triples maps have a constant predicate map then the abstract query variable "?p"
     * will always be projected with constant URIs. Thx to the DISTINCT keyword, we don't need to perform
     * the abstract query, it is sufficient to transform the SPARQL query into the SPARQL 1.1. query:
     * {{{SELECT DISTINCT ?p WHERE
     * VALUES ?p { :abc :def ... }
     * LIMIT 100 }}}
     *
     * @param sparqlQuery the query under Jena algrebra form
     * @param abstractQuery the abstract query that was built from the SPARQL query
     * @return None if no change was performed of another SPARQL query if the transformation was possible
     */
    private def allVarsProjectedAsConstantTermMaps(sparqlQuery: Op, abstractQuery: AbstractQuery): Option[Op] = {

        var op: Op = sparqlQuery

        if (sparqlQuery.isInstanceOf[OpSlice]) // LIMIT
            op = sparqlQuery.asInstanceOf[OpSlice].getSubOp

        if (op.isInstanceOf[OpDistinct]) { // DISTINCT
            op = op.asInstanceOf[OpDistinct].getSubOp

            if (op.isInstanceOf[OpProject]) { // variables projected in the SPARQL query
                val opProject = op.asInstanceOf[OpProject]
                opProject.getVars.foreach { x =>
                    // For each variable x projected in the SPARQL query, get the xR2RML references that are matched with it.
                    // This could be "$.codeField AS ?x" or "http://domain.org/something AS ?x".
                    val absProj = abstractQuery.getProjectionsForVariable(x.toString)
                    absProj.foreach { proj =>
                        // If x has more than one xR2RML reference, it means it was matched with a template term map e.g. "http://...{ref1}... {ref2}"
                        // => this is not a constant term map, no need to continue.
                        if (proj.references.size > 1)
                            return None
                        else {
                            val ref = proj.references.head
                            // If the projection "... AS ?x" is with an xR2RML reference but not a URI, then we can't simplify the query
                            if (!ref.toLowerCase.startsWith("http")) return None
                        }
                    }
                }

                val table = TableFactory.create(opProject.getVars)
                opProject.getVars.foreach { x =>
                    val absProj = abstractQuery.getProjectionsForVariable(x.toString)
                    absProj.foreach { proj =>
                        table.addBinding(BindingFactory.binding(x, NodeFactory.createURI(proj.references.head)))
                    }
                }

                // Create distinct ( project ( ?x ( table ( row [...] ) ) ) )
                op = new OpDistinct(new OpProject(OpTable.create(table), opProject.getVars))

                // If there was a LIMIT ... encapsulate in an slice ( ... )
                if (sparqlQuery.isInstanceOf[OpSlice]) { // LIMIT
                    val opSlice = sparqlQuery.asInstanceOf[OpSlice]
                    op = new OpSlice(op, opSlice.getStart, opSlice.getLength)
                }
                if (logger.isInfoEnabled()) {
                    logger.info("Constant Projection Optimization (aka Projection Pushing): all projected variables " + opProject.getVars + " are matched with a constant URI")
                    logger.info("SPARQL query is replaced with query: \n" + op)
                }

                return Some(op)
            }
        }
        None
    }
}