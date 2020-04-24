package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import scala.collection.JavaConversions.asJavaCollection
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.vocabulary.RDF
import org.apache.log4j.Logger

import Zql.ZExpression
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseUnfolder
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphAlphaResult
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphAlphaResultUnion
import es.upm.fi.dia.oeg.morph.base.sql.SQLJoinTable
import es.upm.fi.dia.oeg.morph.base.sql.SQLLogicalTable
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLPredicateObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLLogicalSource
import es.upm.fi.dia.oeg.morph.rdb.engine.MorphRDBUnfolder

class MorphRDBAlphaGenerator(md: RDBR2RMLMappingDocument, baseUnfolder: MorphBaseUnfolder) {

    val unfolder = baseUnfolder.asInstanceOf[MorphRDBUnfolder]
    
    val databaseType = if (md.dbMetaData.isDefined) { md.dbMetaData.get.dbType; }
    else { Constants.DATABASE_DEFAULT }

    val logger = Logger.getLogger("MorphQueryTranslator");

    var owner: MorphRDBQueryTranslator = null;

    def calculateAlpha(tp: Triple, abstractConceptMapping: R2RMLTriplesMap, predicateURI: String): MorphAlphaResult = {
        //ALPHA SUBJECT
        val tpSubject = tp.getSubject();
        val alphaSubject = this.calculateAlphaSubject(tpSubject, abstractConceptMapping);
        val logicalTableAlias = alphaSubject.getAlias();

        val pmsAux = abstractConceptMapping.getPropertyMappings(predicateURI);
        val alphaResult: MorphAlphaResult = {
            if (RDF.`type`.getURI().equalsIgnoreCase(predicateURI)) {
                val alphaPO = List((null, predicateURI));
                new MorphAlphaResult(alphaSubject, alphaPO);
            } else {
                val pms = {
                    if (pmsAux == null) { Nil }
                    else { pmsAux }
                }

                //ALPHA PREDICATE OBJECT
                val alphaPredicateObjects: List[(SQLJoinTable, String)] = {
                    if (pms.size > 1) {
                        val errorMessage = "Multiple mappings of a predicate is not supported.";
                        logger.error(errorMessage);
                    }

                    val pm = pms.iterator.next().asInstanceOf[R2RMLPredicateObjectMap];
                    val refObjectMap = pm.getRefObjectMap(0);
                    if (refObjectMap != null) {
                        val alphaPredicateObject = this.calculateAlphaPredicateObject(
                            tp, abstractConceptMapping, pm, logicalTableAlias);
                        List(alphaPredicateObject);
                    } else {
                        Nil;
                    }

                }
                new MorphAlphaResult(alphaSubject, alphaPredicateObjects);
            }
        }
        alphaResult;
    }

    def calculateAlphaPredicateObject(triple: Triple, abstractConceptMapping: R2RMLTriplesMap, abstractPropertyMapping: R2RMLPredicateObjectMap, logicalTableAlias: String): (SQLJoinTable, String) = {

        val pm = abstractPropertyMapping.asInstanceOf[R2RMLPredicateObjectMap];
        val refObjectMap = pm.getRefObjectMap(0);

        val result: SQLJoinTable = {
            if (refObjectMap != null) {
                val parentTriplesMap = md.getParentTriplesMap(refObjectMap);
                val parentLogicalTable = parentTriplesMap.logicalSource.asInstanceOf[xR2RMLLogicalSource];

                if (parentLogicalTable == null) {
                    val errorMessage = "Parent logical table is not found for RefObjectMap : " + refObjectMap;
                    logger.error(errorMessage);
                }

                val sqlParentLogicalTableAux = unfolder.unfoldLogicalSource(parentLogicalTable);
                val sqlParentLogicalTable = new SQLJoinTable(sqlParentLogicalTableAux, Constants.JOINS_TYPE_INNER, null);

                val sqlParentLogicalTableAuxAlias = sqlParentLogicalTableAux.generateAlias();
                this.owner.mapTripleAlias += (triple -> sqlParentLogicalTableAuxAlias);
                val joinQueryAlias = sqlParentLogicalTableAuxAlias;

                val joinConditions = refObjectMap.joinConditions;
                val onExpression = unfolder.unfoldJoinConditions(joinConditions, logicalTableAlias, joinQueryAlias, databaseType).asInstanceOf[ZExpression]
                if (onExpression != null) {
                    sqlParentLogicalTable.onExpression = onExpression;
                }

                sqlParentLogicalTable;
            } else {
                null
            }
        }

        val predicateURI = triple.getPredicate().getURI();
        (result, predicateURI);
    }

    def calculateAlphaSubject(subject: Node, abstractConceptMapping: R2RMLTriplesMap): SQLLogicalTable = {
        val cm = abstractConceptMapping.asInstanceOf[R2RMLTriplesMap];
        val r2rmlLogicalTable = cm.logicalSource.asInstanceOf[xR2RMLLogicalSource];
        val sqlLogicalTable = unfolder.unfoldLogicalSource(r2rmlLogicalTable).asInstanceOf[SQLLogicalTable]

        val cmLogicalTableAlias = r2rmlLogicalTable.alias;
        val logicalTableAlias = {
            if (cmLogicalTableAlias == null || cmLogicalTableAlias.equals("")) {
                sqlLogicalTable.generateAlias();
            } else {
                cmLogicalTableAlias
            }
        }

        sqlLogicalTable.setAlias(logicalTableAlias);
        sqlLogicalTable.databaseType = this.databaseType;
        return sqlLogicalTable;
    }

    def calculateAlphaPredicateObjectSTG(tp: Triple, cm: R2RMLTriplesMap, tpPredicateURI: String, logicalTableAlias: String): List[(SQLJoinTable, String)] = {

        val isRDFTypeStatement = RDF.`type`.getURI().equals(tpPredicateURI);
        val alphaPredicateObjects: List[(SQLJoinTable, String)] = {
            if (isRDFTypeStatement) {
                //do nothing
                Nil;
            } else {
                val pms = cm.getPropertyMappings(tpPredicateURI);
                if (pms != null && !pms.isEmpty()) {
                    val pm = pms.iterator.next().asInstanceOf[R2RMLPredicateObjectMap];
                    val refObjectMap = pm.getRefObjectMap(0);
                    if (refObjectMap != null) {
                        val alphaPredicateObject = this.calculateAlphaPredicateObject(tp, cm, pm, logicalTableAlias);
                        List(alphaPredicateObject);
                    } else {
                        Nil;
                    }
                } else {
                    if (!isRDFTypeStatement) {
                        val errorMessage = "Undefined mapping for : " + tpPredicateURI + " in : " + cm.toString();
                        logger.error(errorMessage);
                        Nil;
                    } else {
                        Nil;
                    }
                }
            }
        }
        alphaPredicateObjects;
    }

    def calculateAlphaSTG(triples: Iterable[Triple], cm: R2RMLTriplesMap): java.util.List[MorphAlphaResultUnion] = {
        var alphaResultUnionList: List[MorphAlphaResultUnion] = Nil;

        val firstTriple = triples.iterator.next();
        val tpSubject = firstTriple.getSubject();
        val alphaSubject = this.calculateAlphaSubject(tpSubject, cm);
        val logicalTableAlias = alphaSubject.getAlias();

        for (tp <- triples) {
            val tpPredicate = tp.getPredicate();
            var alphaPredicateObjects: List[(SQLJoinTable, String)] = Nil;
            var alphaPredicateObjects2: List[(SQLLogicalTable, String)] = Nil;
            if (tpPredicate.isURI()) {
                val tpPredicateURI = tpPredicate.getURI();

                val mappedClassURIs = cm.getMappedClassURIs();
                val processableTriplePattern = {
                    if (tp.getObject().isURI()) {
                        val objectURI = tp.getObject().getURI();
                        if (RDF.`type`.getURI().equals(tpPredicateURI) && mappedClassURIs.contains(objectURI) && triples.size > 1) {
                            false;
                        } else
                            true;
                    } else
                        true;
                }

                if (processableTriplePattern) {
                    val alphaPredicateObjectAux = calculateAlphaPredicateObjectSTG(
                        tp, cm, tpPredicateURI, logicalTableAlias);
                    if (alphaPredicateObjectAux != null) {
                        alphaPredicateObjects = alphaPredicateObjects ::: alphaPredicateObjectAux.toList;
                    }

                    val alphaResult = new MorphAlphaResult(alphaSubject, alphaPredicateObjects);

                    val alphaTP = new MorphAlphaResultUnion(alphaResult);
                    alphaResultUnionList = alphaResultUnionList ::: List(alphaTP);
                }
            } else if (tpPredicate.isVariable()) {
                val pms = cm.predicateObjectMaps;
                val alphaTP = new MorphAlphaResultUnion();
                for (pm <- pms) {
                    val tpPredicateURI = pm.getMappedPredicateName(0);
                    val alphaPredicateObjectAux = calculateAlphaPredicateObjectSTG(
                        tp, cm, tpPredicateURI, logicalTableAlias);
                    if (alphaPredicateObjectAux != null) {
                        alphaPredicateObjects = alphaPredicateObjects ::: alphaPredicateObjectAux;
                    }

                    val alphaResult = new MorphAlphaResult(alphaSubject, alphaPredicateObjects);

                    alphaTP.add(alphaResult);
                }

                if (alphaTP != null) {
                    alphaResultUnionList = alphaResultUnionList ::: List(alphaTP);
                }

            } else {
                val errorMessage = "Predicate has to be either an URI or a variable";
                logger.error(errorMessage);

            }
        }

        return alphaResultUnionList;
    }
}
