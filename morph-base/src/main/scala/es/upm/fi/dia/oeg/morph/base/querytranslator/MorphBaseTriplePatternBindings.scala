package es.upm.fi.dia.oeg.morph.base.querytranslator

import org.apache.jena.graph.Triple

import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap

/**
 * Representation of a triple pattern binding: i.e. the binding of a set of triples maps
 * to single triple patterns.
 *
 * @author Franck Michel, I3S laboratory
 */
class TpBinding(
        val triple: Triple,
        val boundTMs: Set[R2RMLTriplesMap]) {

    def isEmpty = boundTMs.isEmpty

    override def equals(c: Any): Boolean = {
        c.isInstanceOf[TpBinding] &&
            this.triple == c.asInstanceOf[TpBinding].triple &&
            this.boundTMs == c.asInstanceOf[TpBinding].boundTMs
    }

    override def hashCode(): Int = {
        this.triple.hashCode + this.boundTMs.map { x => x.hashCode }.sum
    }

    override def toString = {
        " Binding(" + triple + " -> " +
            (
                if (boundTMs.size == 0) ")"
                else if (boundTMs.size == 1) boundTMs.head + ")"
                else boundTMs.mkString("\n      ", ",\n      ", ")")
            )
    }
}

object TpBinding {
    def apply(triple: Triple, boundTM: R2RMLTriplesMap) = {
        new TpBinding(triple, Set(boundTM))
    }
}

/**
 * Representation of the bindings of triples maps to triple patterns.
 * This is essentially a map whose key is the string representation of the triple pattern.
 * The value is an instance of TpBinding that gives the triple and the triples maps bound to it
 *
 * @author Franck Michel, I3S laboratory
 */
class TpBindings {

    /**
     * Bindings map: the key is a triple pattern (as a string), the object a TpBinding instance
     * that contains the triples maps bound to that triple pattern
     */
    var bindingsMap: Map[String, TpBinding] = Map.empty

    /** True if there is are least one binding */
    def nonEmpty = this.bindingsMap.nonEmpty

    /** True if there is no bindings at all */
    def isEmpty = this.bindingsMap.isEmpty

    def contains(tp: Triple): Boolean = this.bindingsMap contains tp.toString

    def size = this.bindingsMap.size

    /** Get the bindings of triple pattern tp */
    def get(tp: Triple): Option[TpBinding] = {
        if (this contains tp)
            Some(this.bindingsMap(tp.toString))
        else
            None
    }

    /**
     *  Get the triple maps bound to triple pattern tp
     *  @return list of triples maps, possibly empty
     */
    def getBoundTMs(tp: Triple): Set[R2RMLTriplesMap] = {
        if (this contains tp)
            this.bindingsMap(tp.toString).boundTMs
        else
            Set.empty
    }

    /** Return triple patterns with empty bindings, i.e. with no bound triples map */
    def getTriplesWithEmptyBindings: List[Triple] = {
        this.bindingsMap.values.filter(b => b.isEmpty).map(f => f.triple).toList
    }

    /** Return bindings for triple patterns with at least one bound triples map */
    def getNonEmptyBindings: Set[TpBinding] = {
        this.bindingsMap.values.filter(tpb => !(tpb.isEmpty)).toSet
    }

    /** Add or update a triple pattern binding */
    def addOrUpdate(tp: Triple, boundTMs: Set[R2RMLTriplesMap]) {
        this.bindingsMap += (tp.toString -> new TpBinding(tp, boundTMs))
    }

    def delete(tp: Triple) { this.bindingsMap -= tp.toString }

    /**
     * Merge 2 sets of bindings by doing a union of the bindings for common triple patterns
     */
    def merge(tpb: TpBindings): TpBindings = {
        val result = new TpBindings

        for ((tp1str, tpb1) <- this.bindingsMap) {
            val tp1 = tpb1.triple
            if (tpb.contains(tp1))
                result.addOrUpdate(tp1, (tpb1.boundTMs union tpb.getBoundTMs(tp1)))
            else
                result.addOrUpdate(tp1, tpb1.boundTMs)
        }

        for ((tp2str, tpb2) <- tpb.bindingsMap) {
            val tp2 = tpb2.triple
            if (!this.contains(tp2))
                result.addOrUpdate(tp2, tpb2.boundTMs)
        }
        result
    }

    override def toString = { this.bindingsMap.values.mkString("\n  ") }
}

object TpBindings {
    /**
     * Initialize a TpBindings with just one binding of one triple pattern and one bound triples map
     */
    def apply(tp: Triple, boundTM: R2RMLTriplesMap) = {
        val result = new TpBindings
        result.addOrUpdate(tp, Set(boundTM))
        result
    }
}