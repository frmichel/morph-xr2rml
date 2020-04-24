package es.upm.fi.dia.oeg.morph.base.querytranslator.engine

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConverters.asJavaCollectionConverter

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpFilter
import org.apache.jena.sparql.algebra.op.OpJoin
import org.apache.jena.sparql.algebra.op.OpLeftJoin
import org.apache.jena.sparql.algebra.op.OpUnion

import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLTriplesMap

class MorphQueryTranslatorUtility {

}

object MorphQueryTranslatorUtility {

    /**
     * From a set of nodes with their respective candidate triples maps, build the a set of nodes that are
     * in both sets with the intersection of their respective triples maps
     */
    def mapsIntersection(map1: Map[Node, Set[RDBR2RMLTriplesMap]], map2: Map[Node, Set[RDBR2RMLTriplesMap]]): Map[Node, Set[RDBR2RMLTriplesMap]] = {
        val map1KeySet = map1.keySet;
        val map2KeySet = map2.keySet;
        val mapKeySetsIntersection = map1KeySet.intersect(map2KeySet);

        // Rebuild a map for the keys in map1 that are not in map2
        val map1OnlyKeySet = map1KeySet.diff(mapKeySetsIntersection);
        val result1 = map1OnlyKeySet.map(map1Key => {
            (map1Key -> map1(map1Key));
        })

        // Rebuild a map for the keys in map2 that are not in map1
        val map2OnlyKeySet = map2KeySet.diff(mapKeySetsIntersection);
        val result2 = map2OnlyKeySet.map(map2Key => {
            (map2Key -> map2(map2Key));
        })

        // Build the map with keys in map1 and map2 with their intersection of triples maps
        val resultIntersection = mapKeySetsIntersection.map(key => {
            val map1Values = map1(key);
            val map2Values = map2(key);
            val mapValuesIntersection = map1Values.intersect(map2Values);
            (key -> mapValuesIntersection);
        })

        val resultFinal = result1 ++ resultIntersection ++ result2;
        resultFinal.toMap;
    }

    def mapsIntersection(maps: List[Map[Node, Set[RDBR2RMLTriplesMap]]]): Map[Node, Set[RDBR2RMLTriplesMap]] = {
        val result: Map[Node, Set[RDBR2RMLTriplesMap]] = {
            if (maps == null || maps.isEmpty) {
                Map.empty;
            } else if (maps.size == 1) {
                maps(0);
            } else if (maps.size == 2) {
                this.mapsIntersection(maps(0), maps(1));
            } else {
                val head = maps.head;
                val tail = maps.tail;
                val tailIntersection = this.mapsIntersection(tail);
                this.mapsIntersection(head, tailIntersection);
            }
        }

        result
    }

    def isTriplePattern(opBGP: OpBGP): Boolean = {
        val triplesSize = opBGP.getPattern().getList().size();
        val result = {
            if (triplesSize == 1) { true; }
            else { false; }
        }
        result
    }

    def isSTG(triples: List[Triple]): Boolean = {
        val result = {
            if (triples.size() <= 1) { false }
            else {
                val groupedTriples = triples.groupBy(triple => triple.getSubject());
                groupedTriples.size == 1;
            }
        }
        result
    }

    def isSTG(opBGP: OpBGP): Boolean = {
        val triples = opBGP.getPattern().getList();
        this.isSTG(triples.toList);
    }

    def getFirstTBEndIndex(triples: java.util.List[Triple]) = {
        var result = 1;
        for (i <- 1 until triples.size() + 1) {
            val sublist = triples.subList(0, i);
            if (this.isSTG(sublist.toList)) {
                result = i;
            }
        }

        result;
    }

    def terms(op: Op): java.util.Collection[Node] = {
        val result = this.getTerms(op).asJavaCollection;
        result
    }

    def getTerms(op: Op): Set[Node] = {
        val result: Set[Node] = {
            op match {
                case bgp: OpBGP => {
                    val triples = bgp.getPattern().getList();
                    var resultAux: Set[Node] = Set.empty
                    for (triple <- triples) {
                        val tpSubject = triple.getSubject();
                        if (tpSubject.isURI() || tpSubject.isBlank() || tpSubject.isLiteral() || tpSubject.isVariable()) {
                            resultAux = resultAux ++ Set(tpSubject);
                        }

                        val tpPredicate = triple.getPredicate();
                        if (tpPredicate.isURI() || tpPredicate.isBlank() || tpPredicate.isLiteral() || tpPredicate.isVariable()) {
                            resultAux = resultAux ++ Set(tpPredicate);
                        }

                        val tpObject = triple.getObject();
                        if (tpObject.isURI() || tpObject.isBlank() || tpObject.isLiteral() || tpObject.isVariable()) {
                            resultAux = resultAux ++ Set(tpObject);
                        }
                    }
                    resultAux
                }
                case leftJoin: OpLeftJoin => {
                    val resultLeft = this.getTerms(leftJoin.getLeft());
                    val resultRight = this.getTerms(leftJoin.getRight());
                    resultLeft ++ resultRight
                }
                case opJoin: OpJoin => {
                    val resultLeft = this.getTerms(opJoin.getLeft());
                    val resultRight = this.getTerms(opJoin.getRight());
                    resultLeft ++ resultRight
                }
                case filter: OpFilter => {
                    this.getTerms(filter.getSubOp());
                }
                case opUnion: OpUnion => {
                    val resultLeft = this.getTerms(opUnion.getLeft());
                    val resultRight = this.getTerms(opUnion.getRight());
                    resultLeft ++ resultRight
                }
                case _ => Set.empty;
            }
        }

        result;
    }
}