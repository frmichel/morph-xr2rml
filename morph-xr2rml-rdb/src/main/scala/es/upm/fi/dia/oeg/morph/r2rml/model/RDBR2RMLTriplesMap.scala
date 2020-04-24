package es.upm.fi.dia.oeg.morph.r2rml.model

import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.sql.MorphTableMetaData

class RDBR2RMLTriplesMap(
    resource: Resource,
    logicalSource: RDBxR2RMLLogicalSource,
    refFormulation: String,
    subjectMap: R2RMLSubjectMap,
    predicateObjectMaps: Set[R2RMLPredicateObjectMap])

        extends R2RMLTriplesMap(resource, logicalSource, refFormulation, subjectMap, predicateObjectMaps) {

    def getLogicalTableSize(): Long = {
        this.logicalSource.getLogicalTableSize
    }

    def getTableMetaData(): Option[MorphTableMetaData] = {
        this.logicalSource.tableMetaData;
    }

    override def getLogicalSource(): RDBxR2RMLLogicalSource = {
        this.logicalSource;
    }
}

