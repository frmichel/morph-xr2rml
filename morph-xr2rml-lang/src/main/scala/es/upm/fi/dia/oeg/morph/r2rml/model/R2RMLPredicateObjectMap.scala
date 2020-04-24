package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.seqAsJavaList

import org.apache.log4j.Logger

import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.exception.MorphException

class R2RMLPredicateObjectMap(
    val predicateMaps: List[R2RMLPredicateMap],
    val objectMaps: List[R2RMLObjectMap],
    val refObjectMaps: List[R2RMLRefObjectMap],
    val graphMaps: Set[R2RMLGraphMap])

        extends java.io.Serializable {

    var resource: Resource = null;

    if (predicateMaps.isEmpty)
        throw new MorphException("Error: predicateObjectMap with no predicate map will be ignored.")
    if (!this.hasObjectMap && !this.hasRefObjectMap)
        throw new MorphException("Error: predicateObjectMap with no object map will be ignored.")

    var alias: String = null;

    def getMappedPredicateNames(): Iterable[String] = {
        val result = this.predicateMaps.map(pm => {
            pm.getOriginalValue();
        });
        result;
    }

    def getMappedPredicateName(index: Int): String = {
        val result = if (this.predicateMaps != null && !this.predicateMaps.isEmpty()) {
            this.predicateMaps.get(index).getOriginalValue();
        } else
            null;
        result;
    }

    def getObjectMap(index: Int): R2RMLObjectMap = {
        if (this.objectMaps != null && !this.objectMaps.isEmpty()) {
            this.objectMaps.get(index);
        } else
            null
    }

    def getPredicateMap(index: Int): R2RMLPredicateMap = {
        val result = if (this.predicateMaps != null && !this.predicateMaps.isEmpty()) {
            predicateMaps.get(index);
        } else
            null;
        result;
    }

    def getRangeClassMapping(index: Int): String = {
        val result = if (this.refObjectMaps != null && !this.refObjectMaps.isEmpty() && this.refObjectMaps.get(index) != null) {
            this.refObjectMaps.get(index).getParentTripleMapName();
        } else
            null;
        result;
    }

    def getRefObjectMap(index: Int): R2RMLRefObjectMap = {
        if (this.refObjectMaps != null && !this.refObjectMaps.isEmpty) {
            this.refObjectMaps(index);
        } else
            null;
    }

    def hasObjectMap(): Boolean = {
        (this.objectMaps != null && !this.objectMaps.isEmpty)
    }

    def hasRefObjectMap(): Boolean = {
        (this.refObjectMaps != null && !this.refObjectMaps.isEmpty)
    }

    override def toString(): String = {
        val result = "R2RMLPredicateObjectMap[predicateMaps=" + predicateMaps + ", objectMaps=" + objectMaps + ", refObjectMaps=" + refObjectMaps + "]";
        result;
    }
}

object R2RMLPredicateObjectMap {
    object ObjectMapType extends Enumeration {
        type ObjectMapType = Value
        val ObjectMap, RefObjectMap = Value
    }

    def apply(resource: Resource, refFormulation: String): R2RMLPredicateObjectMap = {

        val predicateMaps = R2RMLPredicateMap.extractPredicateMaps(resource, refFormulation).toList;
        val objectMaps = R2RMLObjectMap.extractObjectMaps(resource, refFormulation).toList;
        val refObjectMaps = R2RMLRefObjectMap.extractRefObjectMaps(resource).toList;
        val graphMaps = R2RMLGraphMap.extractGraphMaps(resource, refFormulation);

        val pom = new R2RMLPredicateObjectMap(predicateMaps, objectMaps, refObjectMaps, graphMaps);
        pom;
    }
}