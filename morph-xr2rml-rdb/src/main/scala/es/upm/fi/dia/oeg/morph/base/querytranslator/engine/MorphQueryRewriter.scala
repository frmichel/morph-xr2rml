package es.upm.fi.dia.oeg.morph.base.querytranslator.engine

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.log4j.Logger

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
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
import org.apache.jena.sparql.algebra.optimize.Optimize
import org.apache.jena.sparql.algebra.optimize.Rewrite
import org.apache.jena.sparql.algebra.optimize.TransformFilterConjunction
import org.apache.jena.sparql.core.BasicPattern
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS

import es.upm.fi.dia.oeg.morph.base.querytranslator.SparqlUtility

/** Utility methods to rewrite SPARQL queries */
class MorphQueryRewriter(mapNodeLogicalTableSize: Map[Node, Long], reorderSTG: Boolean)

        extends Rewrite {

    private def logger = Logger.getLogger(this.getClass());

    def rewrite(op: Op): Op = {
        val result: Op = op match {
            case bgp: OpBGP => {
                this.rewriteBGP(bgp);
            }
            case opJoin: OpJoin => { // AND pattern
                this.rewriteJoin(opJoin);
            }
            case opLeftJoin: OpLeftJoin => { //OPT pattern
                this.rewriteLeftJoin(opLeftJoin);
            }
            case opUnion: OpUnion => { //UNION pattern
                val leftChild = opUnion.getLeft();
                val rightChild = opUnion.getRight();
                val leftChildRewritten = this.rewrite(leftChild);
                val rightChildRewritten = this.rewrite(rightChild);
                new OpUnion(leftChildRewritten, rightChildRewritten);
            }
            case opFilter: OpFilter => { //FILTER pattern
                val exprs = opFilter.getExprs();
                val subOp = opFilter.getSubOp();

                val tfc = new TransformFilterConjunction();
                Optimize.apply(tfc, opFilter);
                val subOpRewritten = this.rewrite(subOp);
                OpFilter.filter(exprs, subOpRewritten);
            }
            case opProject: OpProject => {
                val subOp = opProject.getSubOp();
                val subOpRewritten = this.rewrite(subOp);
                new OpProject(subOpRewritten, opProject.getVars());
            }
            case opSlice: OpSlice => {
                val subOp = opSlice.getSubOp();
                val subOpRewritten = this.rewrite(subOp);
                new OpSlice(subOpRewritten, opSlice.getStart(), opSlice.getLength());
            }
            case opDistinct: OpDistinct => {
                val subOp = opDistinct.getSubOp();
                val subOpRewritten = this.rewrite(subOp);
                new OpDistinct(subOpRewritten);
            }
            case opOrder: OpOrder => {
                val subOp = opOrder.getSubOp();
                val subOpRewritten = this.rewrite(subOp);
                new OpOrder(subOpRewritten, opOrder.getConditions());
            }
            case _ => {
                op;
            }
        }
        result;
    }

    private def reorderSTGs(triples: List[Triple]): List[Triple] = {

        var result: List[Triple] = Nil;

        if (triples == null)
            result = null;
        else if (triples.size() == 1)
            result = triples;
        else {
            var mapNodeTriples: Map[Node, List[Triple]] = Map.empty;
            var mapNodeTableSize: Map[Node, Long] = Map.empty;

            for (tp <- triples) {
                val tpSubject = tp.getSubject();
                val logicalTableSize = {
                    if (tpSubject.isURI())
                        this.mapNodeLogicalTableSize(tpSubject).longValue() - 1;
                    else
                        this.mapNodeLogicalTableSize(tpSubject).longValue();
                }

                if (mapNodeTriples.contains(tpSubject)) {
                    var mappedTriples = mapNodeTriples(tpSubject);
                    mappedTriples = mappedTriples ::: List(tp);
                    mapNodeTriples += (tpSubject -> mappedTriples);
                } else {
                    val mappedTriples = List(tp);
                    mapNodeTriples += (tpSubject -> mappedTriples);
                }

                val mappedTableSize = {
                    if (mapNodeTableSize.contains(tpSubject)) {
                        mapNodeTableSize(tpSubject);
                    } else {
                        mapNodeTableSize += (tpSubject -> logicalTableSize);
                        logicalTableSize
                    }
                }
            }

            val triplesSubjects = SparqlUtility.getSubjects(triples).distinct;
            if (mapNodeTableSize != null && mapNodeTableSize.size() == triplesSubjects.size()) {

                val mapNodeTableSizeResorted = mapNodeTableSize.toList sortBy { _._2 };
                for (tuple <- mapNodeTableSizeResorted) {
                    result = result ::: mapNodeTriples(tuple._1)
                }
            } else
                result = triples;
        }

        result;
    }

    private def rewriteLeftJoin(opLeftJoin: OpLeftJoin): Op = {

        val exprList = opLeftJoin.getExprs();
        val leftChild = opLeftJoin.getLeft();
        val rightChild = opLeftJoin.getRight();
        val leftChildRewritten = this.rewrite(leftChild);
        val rightChildRewritten = this.rewrite(rightChild);

        val result: Op = {
            if (leftChildRewritten.isInstanceOf[OpBGP] && rightChildRewritten.isInstanceOf[OpBGP]) {
                val leftChildRewrittenBGP = leftChildRewritten.asInstanceOf[OpBGP];
                val rightChildRewrittenBGP = rightChildRewritten.asInstanceOf[OpBGP];
                val rightBasicPattern = rightChildRewrittenBGP.getPattern();
                val rightBasicPatternSize = rightBasicPattern.size();

                if (rightBasicPatternSize == 1) {
                    logger.debug("Optional pattern with only one triple pattern.");
                    val leftChildRewrittenTPList = leftChildRewrittenBGP.getPattern().getList().toList;

                    val rightTp = rightChildRewrittenBGP.getPattern().get(0);
                    val rightTpSubject = rightTp.getSubject();
                    val rightTpPredicate = rightTp.getPredicate();
                    val rightTpObject = rightTp.getObject();

                    val leftChildTriples = leftChildRewrittenBGP.getPattern().getList().toList;
                    val leftChildSubjects = SparqlUtility.getSubjects(leftChildTriples);
                    val leftChildObjects = SparqlUtility.getObjects(leftChildTriples);
                    val needsPhantomTP = !leftChildSubjects.contains(rightTpSubject);

                    if (needsPhantomTP) {
                        val phantomSubject = rightTpSubject;
                        val phantomPredicate = RDF.`type`.asNode();
                        val phantomObject = RDFS.Resource.asNode();

                        val phantomTriple = new Triple(phantomSubject, phantomPredicate, phantomObject);
                        val newLeftChildRewrittenTPList = leftChildRewrittenTPList ::: List(phantomTriple);
                        val bgpGrouped = SparqlUtility.groupTriplesBySubject(newLeftChildRewrittenTPList);
                        val triplesGrouped = bgpGrouped.toList;

                        val bgpWithPhantomTP = {
                            try {
                                val triplesReordered = this.reorderSTGs(triplesGrouped);
                                val basicPattern = BasicPattern.wrap(triplesReordered);
                                new OpBGP(basicPattern);
                            } catch {
                                case e: Exception => {
                                    val errorMesssage = "error occured while reodering STG.";
                                    logger.warn(errorMesssage);
                                    val basicPattern = BasicPattern.wrap(triplesGrouped);
                                    new OpBGP(basicPattern);
                                }
                            }
                        }

                        val newOpLeftJoin = OpLeftJoin.create(bgpWithPhantomTP, rightChild, exprList);
                        this.rewriteLeftJoin(newOpLeftJoin.asInstanceOf[OpLeftJoin]);
                    } else {
                        if (leftChildSubjects.contains(rightTpSubject) //&& !leftChildObjects.contains(rightTpObject)
                        ) {

                            val rightEtp = new Triple(rightTpSubject, rightTpPredicate, rightTpObject);
                            val newLeftChildRewrittenTPList = leftChildRewrittenTPList ::: List(rightEtp);

                            val bgpGrouped = SparqlUtility.groupTriplesBySubject(newLeftChildRewrittenTPList);
                            val triplesGrouped = bgpGrouped.toList;

                            try {
                                val triplesReordered = this.reorderSTGs(triplesGrouped);
                                val basicPattern = BasicPattern.wrap(triplesReordered);
                                new OpBGP(basicPattern);
                            } catch {
                                case e: Exception => {
                                    val errorMesssage = "error occured while reodering STG.";
                                    logger.warn(errorMesssage);
                                    //result = bgpGrouped;
                                    val basicPattern = BasicPattern.wrap(triplesGrouped);
                                    new OpBGP(basicPattern);
                                }
                            }
                        } else {
                            OpLeftJoin.create(leftChildRewritten, rightChildRewritten, exprList);
                        }
                    }
                } else {
                    OpLeftJoin.create(leftChildRewritten, rightChildRewritten, exprList);
                }
            } else {
                OpLeftJoin.create(leftChildRewritten, rightChildRewritten, exprList);
            }

        }

        result
    }

    private def rewriteBGP(bgp: OpBGP): Op = {
        val bgpTriples = bgp.getPattern().getList().toList;
        val triplesGrouped = SparqlUtility.groupTriplesBySubject(bgpTriples);

        val basicPattern = {
            if (this.reorderSTG) {
                val triplesReordered = this.reorderSTGs(triplesGrouped.toList);
                BasicPattern.wrap(triplesReordered);
            } else {
                BasicPattern.wrap(triplesGrouped);
            }
        }
        val result = new OpBGP(basicPattern);
        result;
    }

    private def rewriteJoin(opJoin: OpJoin): Op = {
        val leftChild = opJoin.getLeft();
        val rightChild = opJoin.getRight();
        val leftChildRewritten = this.rewrite(leftChild);
        val rightChildRewritten = this.rewrite(rightChild);
        val result = {
            if (leftChildRewritten.isInstanceOf[OpBGP] && rightChildRewritten.isInstanceOf[OpBGP]) {
                val leftChildRewrittenBGP = leftChildRewritten.asInstanceOf[OpBGP];
                val rightChildRewrittenBGP = rightChildRewritten.asInstanceOf[OpBGP];
                val leftChildRewrittenBGPTriplesList = leftChildRewrittenBGP.getPattern().getList().toList;
                val rightChildRewrittenBGPTriplesList = rightChildRewrittenBGP.getPattern().getList().toList;
                val newTriplesList = leftChildRewrittenBGPTriplesList ::: rightChildRewrittenBGPTriplesList;
                val newBasicPattern = BasicPattern.wrap(newTriplesList);
                new OpBGP(newBasicPattern);
            } else {
                OpJoin.create(leftChildRewritten, rightChildRewritten);
            }
        }
        result;
    }
}

