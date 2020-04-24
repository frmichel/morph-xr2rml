package es.upm.fi.dia.oeg.morph.base.querytranslator

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpDistinct
import org.apache.jena.sparql.algebra.op.OpFilter
import org.apache.jena.sparql.algebra.op.OpGroup
import org.apache.jena.sparql.algebra.op.OpJoin
import org.apache.jena.sparql.algebra.op.OpLeftJoin
import org.apache.jena.sparql.algebra.op.OpOrder
import org.apache.jena.sparql.algebra.op.OpProject
import org.apache.jena.sparql.algebra.op.OpSlice
import org.apache.jena.sparql.algebra.op.OpUnion
import org.apache.jena.sparql.core.BasicPattern
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.TemplateUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLRefObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap

/**
 * Implementation of the functions that figure out which triples maps are bound to a triple pattern,
 * i.e. those triples maps that are possible candidates to generate RDF triples matching the triple pattern.
 *
 * See full definitions in https://hal.archives-ouvertes.fr/hal-01245883.
 *
 * @author Franck Michel, I3S laboratory
 */
class MorphBaseTriplePatternBinder(factory: IMorphFactory) {

    val logger = Logger.getLogger(this.getClass());

    /**
     * Compute the triple pattern bindings for all triple patterns in a graph pattern.
     *
     * Each triple pattern has an entry even if no triples map can be bound to it.
     *
     * @param op compiled SPARQL query or SPARQL query element
     * @return bindings as a map of triple patterns and associated triples maps.
     */
    def bindm(op: Op): TpBindings = {

        op match {

            case opProject: OpProject => { // list of projected variables
                bindm(opProject.getSubOp())
            }

            case bgp: OpBGP => { // Basic Graph Pattern
                val triples: List[Triple] = bgp.getPattern.getList.toList
                if (triples.size == 0) new TpBindings // quite unexpected, probably result of a bug (?)
                else {
                    // Compute the bindings of the first triple pattern
                    val tp1 = triples.head
                    val boundTp1 = bindmTP(tp1)

                    var results = new TpBindings

                    if (triples.size == 1) {
                        // Only one triple pattern in the BGP, return its bindings straight away
                        results.addOrUpdate(tp1, boundTp1)
                        if (logger.isTraceEnabled()) logger.trace("Binding of single triple pattern:\n  " + results)
                        results
                    } else {
                        // Several triple patterns in the BGP

                        // Compute the bindings of the remaining triples
                        val rightTps = new OpBGP(BasicPattern.wrap(triples.tail))
                        val rightBindings = bindm(rightTps)

                        // For each of the remaining triples, join its bindings with those of tp1
                        for ((tp2str, tpb2) <- rightBindings.bindingsMap) {
                            val tp2 = tpb2.triple
                            val boundTp2 = tpb2.boundTMs

                            if (haveSharedVariables(tp1, tp2)) {

                                // There are shared variables between tp1 and tp2: add reduced bindings of tp1 and tp2
                                val joined = join(tp1, boundTp1, tp2, boundTp2)
                                if (results.contains(tp1))
                                    results.addOrUpdate(tp1, takeLeft(joined) intersect results.getBoundTMs(tp1)) // add or update the bindings of tp1
                                else
                                    results.addOrUpdate(tp1, takeLeft(joined))
                                results.addOrUpdate(tp2, takeRight(joined)) // add or update the bindings of tp2: if they already exist, it can only be reduced

                            } else {
                                // There is no shared variable between tp1 and tp2: simply keep the bindings of both
                                if (results.contains(tp1))
                                    // bindings of tp1 may be changed at each loop => keep the intersection with possibly previous bindings
                                    results.addOrUpdate(tp1, boundTp1 intersect results.getBoundTMs(tp1)) // add or update the bindings of tp1
                                else
                                    results.addOrUpdate(tp1, boundTp1)

                                if (!results.contains(tp2))
                                    results.addOrUpdate(tp2, boundTp2) // add the bindings of tp2 (if already present, there is no change)
                            }
                        }

                        if (logger.isDebugEnabled()) logger.debug("Binding of BGP:\n   " + results.toString)
                        results
                    }
                }
            }

            case opJoin: OpJoin => { // AND pattern
                val results = new TpBindings

                // Compute the bindings of the two graph patterns
                val leftBindings = this.bindm(opJoin.getLeft)
                val rightBindings = this.bindm(opJoin.getRight)

                for ((tp1str, tpb1) <- leftBindings.bindingsMap) {
                    val tp1 = tpb1.triple

                    for ((tp2str, tpb2) <- rightBindings.bindingsMap) {
                        val tp2 = tpb2.triple

                        if (haveSharedVariables(tp1, tp2)) {
                            // There are shared variables between tp1 and tp2
                            val joined = join(tp1, tpb1.boundTMs, tp2, tpb2.boundTMs)

                            // Add or update the bindings for tp1 and tp2 with reduced bindings
                            results.addOrUpdate(tp1, takeLeft(joined) union results.getBoundTMs(tp1))
                            results.addOrUpdate(tp2, takeRight(joined) union results.getBoundTMs(tp2))
                        } else {
                            // There is no shared variable between tp1 and tp2: keep all the bindings of tp1 and tp2
                            results.addOrUpdate(tp1, tpb1.boundTMs union results.getBoundTMs(tp1))
                            results.addOrUpdate(tp2, tpb2.boundTMs union results.getBoundTMs(tp2))
                        }
                    }
                }
                if (logger.isDebugEnabled()) logger.debug("Binding of AND graph pattern:\n   " + results.toString)
                results
            }

            case opLeftJoin: OpLeftJoin => { // OPT pattern
                val results = new TpBindings

                // Compute the bindings of the two graph patterns
                val leftBindings = this.bindm(opLeftJoin.getLeft)
                val rightBindings = this.bindm(opLeftJoin.getRight)

                for ((tp1str, tpb1) <- leftBindings.bindingsMap) {
                    val tp1 = tpb1.triple

                    for ((tp2str, tpb2) <- rightBindings.bindingsMap) {
                        val tp2 = tpb2.triple

                        if (haveSharedVariables(tp1, tp2)) {
                            // There are shared variables between tp1 and tp2
                            val joined = join(tp1, tpb1.boundTMs, tp2, tpb2.boundTMs)

                            // Keep the bindings of tp1 and reduce those of tp2
                            results.addOrUpdate(tp1, tpb1.boundTMs union results.getBoundTMs(tp1))
                            results.addOrUpdate(tp2, takeRight(joined) union results.getBoundTMs(tp2))
                        } else {
                            // There is no shared variable between tp1 and tp2: keep all the bindings of tp1 and tp2
                            results.addOrUpdate(tp1, tpb1.boundTMs union results.getBoundTMs(tp1))
                            results.addOrUpdate(tp2, tpb2.boundTMs union results.getBoundTMs(tp2))
                        }
                    }
                }
                if (logger.isDebugEnabled()) logger.debug("Binding of OPTIONAL graph pattern: " + results.toString)
                results
            }

            case opUnion: OpUnion => { // UNION pattern
                var results = new TpBindings
                val leftBindings = this.bindm(opUnion.getLeft)
                val rightBindings = this.bindm(opUnion.getRight)

                for ((tp1str, tpb1) <- leftBindings.bindingsMap) {
                    val tp1 = tpb1.triple
                    results.addOrUpdate(tp1, (tpb1.boundTMs union results.getBoundTMs(tp1)))

                    for ((tp2str, tpb2) <- rightBindings.bindingsMap) {
                        val tp2 = tpb2.triple
                        results.addOrUpdate(tp2, (tpb2.boundTMs union results.getBoundTMs(tp2)))
                    }
                }
                if (logger.isDebugEnabled()) logger.debug("Binding of UNION graph pattern:\n  " + results.toString)
                results
            }

            case opFilter: OpFilter => {
                bindm(opFilter.getSubOp)
            }
            case opSlice: OpSlice => {
                bindm(opSlice.getSubOp)
            }
            case opDistinct: OpDistinct => {
                bindm(opDistinct.getSubOp)
            }
            case opOrder: OpOrder => {
                bindm(opOrder.getSubOp)
            }
            case opGroup: OpGroup => {
                bindm(opGroup.getSubOp)
            }
            case _ => {
                new TpBindings
            }
        }
    }

    /**
     * Compute the list of triples maps TM that are bound to triple pattern tp.
     * TM is bound to tp if and only if it is compatible with to, that is:<br>
     * 		compatible(TM.sub, tp.sub) AND compatible(TM.pred, tp.pred) AND compatible(TM.obj, tp.obj)}
     *
     * @param tp triple pattern
     * @return set of triples maps bound to tp
     */
    private def bindmTP(tp: Triple): Set[R2RMLTriplesMap] = {

        val boundTMs = factory.getMappingDocument.triplesMaps.map(tm => {
            val pom = tm.predicateObjectMaps.head

            if (tm.predicateObjectMaps.size > 1 || pom.predicateMaps.size > 1 ||
                pom.objectMaps.size > 1 || pom.refObjectMaps.size > 1)
                throw new MorphException("Error, non-normalized triples map: " + tm)

            var bound: Boolean =
                compatible(tm.subjectMap, tp.getSubject) &&
                    compatible(pom.predicateMaps.head, tp.getPredicate) &&
                    (if (pom.hasObjectMap)
                        compatible(pom.objectMaps.head, tp.getObject)
                    else if (pom.hasRefObjectMap)
                        compatible(pom.refObjectMaps.head, tp.getObject)
                    else {
                        logger.warn("Unormalized triples map " + tm.toString + ": no object maps nor referencing object maps")
                        false
                    })

            if (bound) Some(tm) else None

        }).flatten // flatten removes None, keeping possibly an empty list of bound triples maps

        boundTMs.toSet
    }

    /**
     * Join the bindings of two triple patterns on the set of variables that they share.
     *
     * join(tp1, boundTp1, tp2, boundTp2) is the set of pairs (TM1, TM2) of boundTp1 x boundTp2, such that
     * for each shared variable v, it holds that compatibleTermMaps(TM1.postp1(v), TM2.postp2(v)),
     * in other words the term map of TM1 at the position of v in tp1 is compatible with the term map of TM2
     * at the position of v in tp2.
     *
     * If there is no shared variable, return an empty result.
     *
     * See full definitions in https://hal.archives-ouvertes.fr/hal-01245883.
     *
     * @param tp1 triple pattern 1
     * @param boundTp1 set of triples maps that are bound to tp1
     * @param tp2 triple pattern 2
     * @param boundTp2 set of triples maps that are bound to tp2
     * @return Set of pairs of triples maps from tp1 and tp2 that are compatible with each other
     */
    def join(tp1: Triple, boundTp1: Set[R2RMLTriplesMap], tp2: Triple, boundTp2: Set[R2RMLTriplesMap]): Set[(R2RMLTriplesMap, R2RMLTriplesMap)] = {

        // Compute the set of variables in tp1 (varsTp1) and keep track of their position in (varPosTp1)
        var varPosTp1 = getTpVarsPos(tp1)
        var varsTp1 = getTpVars(tp1)

        // Compute the set of variables in tp2 (varsTp2) and keep track of their position in (varPosTp2)
        var varPosTp2 = getTpVarsPos(tp2)
        var varsTp2 = getTpVars(tp2)

        // Compute the set of variables shared by tp1 and tp2
        var result: Set[(R2RMLTriplesMap, R2RMLTriplesMap)] = Set.empty
        val sharedVars = varsTp1.intersect(varsTp2)
        for (x <- sharedVars) {
            if (logger.isTraceEnabled())
                logger.trace("Checking compatibility of term maps for variable " + x)

            // Search the positions of variable x in each triple pattern
            // e.g.: ?x :prop ?x -> positions sub and obj
            val xInTp1 = varPosTp1.filter(q => q._2 == x).map(_._1) // positions of variable x in tp1
            val xInTp2 = varPosTp2.filter(q => q._2 == x).map(_._1) // positions of variable x in tp2

            for (tm1 <- boundTp1)
                for (tm2 <- boundTp2) {
                    // For (tm1, tm2) to be an acceptable result their term maps must be compatible
                    // at all positions of variable x in tp1 and tp2
                    var compat = true
                    for (postp1 <- xInTp1)
                        for (postp2 <- xInTp2) {
                            val c = compatibleTermMaps(getTermMapAtPos(tm1, postp1), getTermMapAtPos(tm2, postp2))
                            if (logger.isTraceEnabled())
                                if (c)
                                    logger.trace(tm1.toString + "." + postp1 + " compatible with " + tm2 + "." + postp2)
                                else
                                    logger.trace(tm1.toString + "." + postp1 + " incompatible with " + tm2 + "." + postp2)

                            compat = compat && compatibleTermMaps(getTermMapAtPos(tm1, postp1), getTermMapAtPos(tm2, postp2))
                        }
                    if (compat)
                        result = result + ((tm1, tm2))
                }
        }
        if (logger.isDebugEnabled())
            logger.debug("Joined bindings of [" + tp1 + "] and [" + tp2 + "]:\n   " + result)
        result
    }

    private def takeLeft(joinedTM: Set[(R2RMLTriplesMap, R2RMLTriplesMap)]): Set[R2RMLTriplesMap] = {
        joinedTM.map(_._1)
    }

    private def takeRight(joinedTM: Set[(R2RMLTriplesMap, R2RMLTriplesMap)]): Set[R2RMLTriplesMap] = {
        joinedTM.map(_._2)
    }

    /**
     * Check the compatibility between a term map and a triple pattern term.
     *
     * Note in case of a RefObjectMap: if the RefObjectMap has a termType (xrr:RdfList, xrr:RdfBag etc.)
     * we could also verify that the members of the tpTerm (list/bag etc.) have a termType,
     * language tag or datatype compatible with the definition of the xxr:nestedTermType of the RefObjectMam.
     * We choose not to go there as we assume this shall be rare.
     *
     * @param tm an instance of R2RMLTermMap or R2RMLRefObjectMap
     * @return true if compatible
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if tm is neither an R2RMLTermMap or R2RMLRefObjectMap
     */
    def compatible(tm: Object, tpTerm: Node): Boolean = {

        if (tm.isInstanceOf[R2RMLTermMap]) {

            // ---- Case of an xR2RML TermMap
            val termMap = tm.asInstanceOf[R2RMLTermMap]
            if (tpTerm.isVariable())
                // A variable is always compatible with a term map
                true
            else {
                // Check type of tpTerm vs. termType of term map
                val termType = termMap.inferTermType
                var incompatible: Boolean =
                    (tpTerm.isLiteral && termType != Constants.R2RML_LITERAL_URI) ||
                        (tpTerm.isURI && termType != Constants.R2RML_IRI_URI) ||
                        (tpTerm.isBlank && (termType != Constants.R2RML_BLANKNODE_URI && !termMap.isRdfCollectionTermType))

                if (tpTerm.isLiteral) {
                    // Check language tag
                    val L = tpTerm.getLiteralLanguage
                    if (L == null || L.isEmpty())
                        incompatible = incompatible || (termMap.languageTag.isDefined)
                    else
                        incompatible = incompatible ||
                            (!termMap.languageTag.isDefined || (termMap.languageTag.isDefined && termMap.languageTag.get != L))

                    // Check data type
                    val T = tpTerm.getLiteralDatatypeURI
                    if (T != null && !T.isEmpty())
                        incompatible = incompatible ||
                            (termMap.datatype.isDefined && termMap.datatype.get != T)
                }

                // Constant term map and the triple pattern term does not have the same value
                incompatible = incompatible ||
                    (termMap.isConstantValued && tpTerm.isLiteral && termMap.getConstantValue != tpTerm.getLiteralValue.toString) ||
                    (termMap.isConstantValued && tpTerm.isURI && termMap.getConstantValue != tpTerm.getURI)

                // Template term map and the triple pattern does not match the template string
                incompatible = incompatible ||
                    termMap.isTemplateValued && tpTerm.isLiteral && termMap.getTemplateValues(tpTerm.getLiteralValue.toString).isEmpty ||
                    termMap.isTemplateValued && tpTerm.isURI && termMap.getTemplateValues(tpTerm.getURI).isEmpty

                !incompatible
            }
        } else if (tm.isInstanceOf[R2RMLRefObjectMap]) {

            // ---- Case of an xR2RML RefObjectMap
            val termMap = tm.asInstanceOf[R2RMLRefObjectMap]
            if (tpTerm.isVariable())
                // A variable is always compatible with a term map
                true
            else if (termMap.termType.isDefined && !tpTerm.isBlank)
                // An xR2RML RefObjectMap may have a termType, but only a RDF collection or list term type.
                // In that case, it necessarily produces blank nodes.
                false
            else {
                val parentTM = factory.getMappingDocument.getParentTriplesMap(termMap)
                compatible(parentTM.subjectMap, tpTerm)
            }
        } else
            throw new MorphException("Method compatible: object passed is neither an R2RMLTermMap or R2RMLRefObjectMap: " + tm)
    }

    /**
     * Check the compatibility between two term maps
     *
     * @param tm1 an instance of R2RMLTermMap or R2RMLRefObjectMap
     * @param tm2 an instance of R2RMLTermMap or R2RMLRefObjectMap
     * @return true if compatible
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException if tm1 or tm2 is neither an R2RMLTermMap or R2RMLRefObjectMap
     */
    def compatibleTermMaps(tm1: Object, tm2: Object): Boolean = {

        if (tm1.isInstanceOf[R2RMLTermMap]) {

            val termMap1 = tm1.asInstanceOf[R2RMLTermMap]
            if (tm2.isInstanceOf[R2RMLTermMap]) {
                // ---- tm1 and tm2 are TermMaps
                val termMap2 = tm2.asInstanceOf[R2RMLTermMap]
                var incompatible: Boolean =
                    (termMap1.inferTermType != termMap2.inferTermType) ||
                        (termMap1.languageTag != termMap2.languageTag) ||
                        (termMap1.datatype != termMap2.datatype) ||
                        (termMap1.isTemplateValued && termMap2.isTemplateValued &&
                            !TemplateUtility.compatibleTemplateStrings(termMap1.templateString, termMap2.templateString))
                !incompatible

            } else if (tm2.isInstanceOf[R2RMLRefObjectMap]) {
                // ---- tm1 is a TermMap and tm2 is a RefObjectMap
                val termMap2 = tm2.asInstanceOf[R2RMLRefObjectMap]
                compatibleTermMaps(termMap1, factory.getMappingDocument.getParentTriplesMap(termMap2).subjectMap)
            } else
                throw new MorphException("tm2 is neither an R2RMLTermMap or R2RMLRefObjectMap: " + tm2)

        } else if (tm1.isInstanceOf[R2RMLRefObjectMap]) {

            val termMap1 = tm1.asInstanceOf[R2RMLRefObjectMap]
            if (tm2.isInstanceOf[R2RMLTermMap]) {
                // ---- tm1 is a RefObjectMMap and tm2 is a TermMap
                val termMap2 = tm2.asInstanceOf[R2RMLTermMap]
                compatibleTermMaps(factory.getMappingDocument.getParentTriplesMap(termMap1).subjectMap, termMap2)

            } else if (tm2.isInstanceOf[R2RMLRefObjectMap]) {
                // ---- tm1 and tm2 are RefObjectMMaps
                val termMap2 = tm2.asInstanceOf[R2RMLRefObjectMap]
                compatibleTermMaps(factory.getMappingDocument.getParentTriplesMap(termMap1).subjectMap, factory.getMappingDocument.getParentTriplesMap(termMap2).subjectMap)
            } else
                throw new MorphException("tm2 is neither an R2RMLTermMap or R2RMLRefObjectMap: " + tm2)
        } else
            throw new MorphException("tm1 is neither an R2RMLTermMap or R2RMLRefObjectMap: " + tm1)
    }

    /**
     * Return the term map at position pos (subject, predicate or object) or a triples map.
     * The triples map must be normalized.
     *
     * @param tm a triple map
     * @param pos sub, pre or obj
     * @return an instance of R2RMLSubjectMap, R2RMLPredicatetMap, R2RMLObjectMap or R2RMLRefObjectMap
     */
    private def getTermMapAtPos(tm: R2RMLTriplesMap, pos: Constants.MorphPOS.Value): Object = {
        pos match {
            case Constants.MorphPOS.sub => tm.subjectMap

            case Constants.MorphPOS.pre => {
                val poms = tm.predicateObjectMaps
                if (poms.isEmpty || poms.size > 1)
                    throw new MorphException("Unormalized triples map " + tm.toString + " must have exactly one predicate-object map.")
                val pom = poms.head
                if (pom.predicateMaps.isEmpty)
                    throw new MorphException("Unormalized triples map " + tm.toString + " must have exactly one PredicateMap.")
                pom.predicateMaps.head
            }

            case Constants.MorphPOS.obj => {
                val poms = tm.predicateObjectMaps
                if (poms.isEmpty || poms.size > 1)
                    throw new MorphException("Unormalized triples map " + tm.toString + " must have exactly one predicate-object map.")
                val pom = poms.head
                if (!pom.objectMaps.isEmpty)
                    pom.objectMaps.head
                else if (!pom.refObjectMaps.isEmpty)
                    pom.refObjectMaps.head
                else
                    throw new MorphException("Unormalized triples map " + tm.toString + " must have exactly one ObjectMap or one RefObjectMap .")
            }

            case Constants.MorphPOS.graph =>
                throw new MorphException("Unexpected retrieval of graph map for triples map " + tm.toString)
        }
    }

    /**
     * Get the list of variables in a triple pattern
     */
    private def getTpVars(tp: Triple): List[String] = {
        var varsTp: Set[String] = Set.empty

        if (tp.getSubject.isVariable)
            varsTp = varsTp + tp.getSubject.toString
        if (tp.getPredicate.isVariable)
            varsTp = varsTp + tp.getPredicate.toString
        if (tp.getObject.isVariable)
            varsTp = varsTp + tp.getObject.toString

        varsTp.toList
    }

    /**
     * Get the list of variables in a triple pattern along with their position
     */
    private def getTpVarsPos(tp: Triple): Map[Constants.MorphPOS.Value, String] = {
        var varPosTp: Map[Constants.MorphPOS.Value, String] = Map.empty

        if (tp.getSubject.isVariable)
            varPosTp = varPosTp + (Constants.MorphPOS.sub -> tp.getSubject.toString)
        if (tp.getPredicate.isVariable)
            varPosTp = varPosTp + (Constants.MorphPOS.pre -> tp.getPredicate.toString)
        if (tp.getObject.isVariable)
            varPosTp = varPosTp + (Constants.MorphPOS.obj -> tp.getObject.toString)

        varPosTp
    }

    /**
     * Check whether two triple patterns have at least one shared variable
     */
    private def haveSharedVariables(tp1: Triple, tp2: Triple): Boolean = {
        (getTpVars(tp1) intersect getTpVars(tp2)).nonEmpty
    }

    /**
     * Check whether we can "live without a given triple pattern tp", that is, if we can still evaluate
     * the query although this triple pattern cannot be evaluated.
     *
     * This happens when tp is either beneath a UNION or in the right part of a left join (optional):
     * we can compute the rest of the query and we may still get some results.
     *
     * On the contrary, if the query is a join, e.g. a simple BGP, and at least one joined triple pattern
     * of the BGP has no bindings, then for sure we can't evaluate the query.
     *
     * @param tp the triple pattern we look for
     * @param op compiled SPARQL query
     * @return true or false
     */
    def canLiveWithoutTp(tp: Triple, op: Op): Boolean = {

        op match {
            case bgp: OpBGP => false
            case opJoin: OpJoin => // AND pattern
                this.canLiveWithoutTp(tp, opJoin.getLeft) || this.canLiveWithoutTp(tp, opJoin.getRight)

            case opLeftJoin: OpLeftJoin => // OPT pattern
                this.canLiveWithoutTp(tp, opLeftJoin.getLeft) || this.canLiveWithoutTp(tp, opLeftJoin.getRight)

            case opUnion: OpUnion => // UNION pattern
                this.containsTp(tp, opUnion.getLeft) || this.containsTp(tp, opUnion.getRight)

            case opProject: OpProject => this.canLiveWithoutTp(tp, opProject.getSubOp())
            case opFilter: OpFilter => this.canLiveWithoutTp(tp, opFilter.getSubOp)
            case opSlice: OpSlice => this.canLiveWithoutTp(tp, opSlice.getSubOp)
            case opDistinct: OpDistinct => this.canLiveWithoutTp(tp, opDistinct.getSubOp)
            case opOrder: OpOrder => this.canLiveWithoutTp(tp, opOrder.getSubOp)
            case opGroup: OpGroup => this.canLiveWithoutTp(tp, opGroup.getSubOp)
            case _ => false
        }
    }

    /**
     * Check whether a graph pattern contains a given triple pattern
     *
     * @param tp the triple pattern we look for
     * @param op compiled SPARQL query
     * @return true or false
     */
    private def containsTp(tp: Triple, op: Op): Boolean = {
        op match {
            case bgp: OpBGP => // Basic Graph Pattern
                bgp.getPattern.getList.toList.contains(tp)

            case opJoin: OpJoin => // AND pattern
                this.containsTp(tp, opJoin.getLeft) || this.containsTp(tp, opJoin.getRight)

            case opLeftJoin: OpLeftJoin => // OPT pattern
                this.containsTp(tp, opLeftJoin.getLeft) || this.containsTp(tp, opLeftJoin.getRight)

            case opUnion: OpUnion => // UNION pattern
                this.containsTp(tp, opUnion.getLeft) || this.containsTp(tp, opUnion.getRight)

            case opProject: OpProject => this.containsTp(tp, opProject.getSubOp())
            case opFilter: OpFilter => this.containsTp(tp, opFilter.getSubOp)
            case opSlice: OpSlice => this.containsTp(tp, opSlice.getSubOp)
            case opDistinct: OpDistinct => this.containsTp(tp, opDistinct.getSubOp)
            case opOrder: OpOrder => this.containsTp(tp, opOrder.getSubOp)
            case opGroup: OpGroup => this.containsTp(tp, opGroup.getSubOp)
            case _ => false
        }
    }
}
