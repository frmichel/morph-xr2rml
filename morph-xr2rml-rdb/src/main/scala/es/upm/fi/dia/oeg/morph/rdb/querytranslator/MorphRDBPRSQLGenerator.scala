package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import scala.collection.JavaConversions.asJavaCollection
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.mutable.LinkedHashSet

import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.vocabulary.RDF
import org.apache.log4j.Logger

import Zql.ZConstant
import Zql.ZSelectItem
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseUnfolder
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphAlphaResult
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphPRSQLResult
import es.upm.fi.dia.oeg.morph.base.querytranslator.SparqlUtility
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLSelectItem
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLPredicateObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.rdb.engine.NameGenerator

class MorphRDBPRSQLGenerator(md: RDBR2RMLMappingDocument, unfolder: MorphBaseUnfolder) {

    var mapHashCodeMapping: Map[Integer, Object] = Map.empty

    def getMappedMapping(hashCode: Integer) = {
        this.mapHashCodeMapping.get(hashCode);
    }

    def putMappedMapping(key: Integer, value: Object) {
        this.mapHashCodeMapping += (key -> value);
    }
    val logger = Logger.getLogger(this.getClass());

    val dbType =
        if (md.dbMetaData.isDefined) { md.dbMetaData.get.dbType; }
        else { Constants.DATABASE_DEFAULT }

    def genPRSQL(
        tp: Triple,
        alphaResult: MorphAlphaResult,
        betaGenerator: MorphRDBBetaGenerator,
        nameGenerator: NameGenerator,
        cmSubject: R2RMLTriplesMap,
        predicateURI: String,
        unboundedPredicate: Boolean): MorphPRSQLResult = {

        val tpSubject = tp.getSubject();
        val tpPredicate = tp.getPredicate();
        val tpObject = tp.getObject();

        val selectItemsSubjectsAux = this.genPRSQLSubject(tp, alphaResult, betaGenerator, nameGenerator, cmSubject);
        val selectItemSubjects = if (selectItemsSubjectsAux != null) { selectItemsSubjectsAux.toList }
        else { Nil }

        val selectItemPredicates: List[ZSelectItem] = if (tpPredicate != tpSubject) {
            //line 22
            if (unboundedPredicate) {
                val selectItemPredicatesAux = this.genPRSQLPredicate(tp, cmSubject, alphaResult, betaGenerator, nameGenerator, predicateURI);
                if (selectItemPredicatesAux != null) { selectItemPredicatesAux.toList; }
                else { Nil }
            } else { Nil }
        } else { Nil }

        val selectItemObjects = if (tpObject != tpSubject && tpObject != tpPredicate) {
            val columnType = {
                if (tpPredicate.isVariable()) {
                    if (Constants.DATABASE_POSTGRESQL.equalsIgnoreCase(dbType)) {
                        Constants.POSTGRESQL_COLUMN_TYPE_TEXT;
                    } else if (Constants.DATABASE_MONETDB.equalsIgnoreCase(dbType)) {
                        Constants.MONETDB_COLUMN_TYPE_TEXT;
                    } else {
                        Constants.MONETDB_COLUMN_TYPE_TEXT;
                    }
                } else { null }
            }

            //line 23
            val selectItemObjectsAux = this.genPRSQLObject(tp, alphaResult, betaGenerator, nameGenerator, cmSubject, predicateURI, columnType);
            if (selectItemObjectsAux != null) { selectItemObjectsAux.toList }
            else { Nil }
        } else { Nil }

        val prSQLResult = new MorphPRSQLResult(selectItemSubjects, selectItemPredicates, selectItemObjects)
        logger.debug("prSQLResult = " + prSQLResult);
        prSQLResult;
    }

    def genPRSQLSTG(stg: Iterable[Triple], alphaResult: MorphAlphaResult, betaGenerator: MorphRDBBetaGenerator, nameGenerator: NameGenerator, cmSubject: R2RMLTriplesMap): MorphPRSQLResult = {

        val firstTriple = stg.iterator.next();
        val selectItemsSubjects = this.genPRSQLSubject(firstTriple, alphaResult, betaGenerator, nameGenerator, cmSubject);

        val tpSubject = firstTriple.getSubject();
        var selectItemsSTGPredicates: LinkedHashSet[ZSelectItem] = new LinkedHashSet[ZSelectItem]();
        var selectItemsSTGObjects: LinkedHashSet[ZSelectItem] = new LinkedHashSet[ZSelectItem]();

        for (tp <- stg) {
            val tpPredicate = tp.getPredicate();
            if (!tpPredicate.isURI()) {
                val errorMessage = "Only bounded predicate is supported in STG.";
                logger.warn(errorMessage);
            }
            val predicateURI = tpPredicate.getURI();

            if (RDF.`type`.getURI().equals(predicateURI)) {
                //do nothing
            } else {
                val tpObject = tp.getObject();
                if (tpPredicate != tpSubject) {
                    val selectItemPredicates = this.genPRSQLPredicate(tp, cmSubject, alphaResult, betaGenerator, nameGenerator, predicateURI);
                    if (selectItemPredicates != null) {
                        selectItemsSTGPredicates = selectItemsSTGPredicates ++ selectItemPredicates;
                    }

                }
                if (tpObject != tpSubject && tpObject != tpPredicate) {
                    val selectItemsObject = this.genPRSQLObject(tp, alphaResult, betaGenerator, nameGenerator, cmSubject, predicateURI, null);
                    selectItemsSTGObjects = selectItemsSTGObjects ++ selectItemsObject;
                } else {
                }
            }
        }

        val prSQLResult = new MorphPRSQLResult(selectItemsSubjects.toList, selectItemsSTGPredicates.toList, selectItemsSTGObjects.toList);
        prSQLResult
    }

    def genPRSQLSubject(tp: Triple, alphaResult: MorphAlphaResult, betaGenerator: MorphRDBBetaGenerator, nameGenerator: NameGenerator, cmSubject: R2RMLTriplesMap): List[ZSelectItem] = {
        val triplesMap = cmSubject.asInstanceOf[R2RMLTriplesMap];
        val subjectMap = triplesMap.subjectMap;

        val tpSubject = tp.getSubject();
        val result: List[ZSelectItem] = {
            if (!SparqlUtility.isBlankNode(tpSubject)) {
                val parentResult = genPRSQLSubject2(tp, alphaResult, betaGenerator, nameGenerator, cmSubject).toList;

                val subject = tp.getSubject();
                val selectItemsMappingId = this.genPRSQLMappingId(subject, subjectMap);
                if (selectItemsMappingId == null) {
                    parentResult
                } else {
                    parentResult ::: selectItemsMappingId;
                }
            } else {
                Nil;
            }
        }

        result;
    }

    def genPRSQLSubject2(tp: Triple, alphaResult: MorphAlphaResult, betaGenerator: MorphRDBBetaGenerator, nameGenerator: NameGenerator, cmSubject: R2RMLTriplesMap): List[ZSelectItem] = {
        val tpSubject = tp.getSubject();

        val prSubjects = {
            if (!tpSubject.isBlank()) {
                val betaSubSelectItems = betaGenerator.calculateBetaSubject(tp, cmSubject, alphaResult);
                for (i <- 0 until betaSubSelectItems.size()) yield {
                    val betaSub = betaSubSelectItems.get(i);

                    val selectItem = MorphSQLSelectItem.apply(betaSub, dbType);
                    val selectItemSubjectAliasAux = nameGenerator.generateName(tpSubject);
                    val selectItemSubjectAlias = {
                        if (betaSubSelectItems.size() > 1) {
                            selectItemSubjectAliasAux + "_" + i;
                        } else {
                            selectItemSubjectAliasAux
                        }
                    }

                    selectItem.setAlias(selectItemSubjectAlias);
                    selectItem;
                }
            } else {
                Nil
            }
        }
        prSubjects.toList
    }

    def genPRSQLPredicate(
        tp: Triple,
        cm: R2RMLTriplesMap,
        alphaResult: MorphAlphaResult,
        betaGenerator: MorphRDBBetaGenerator,
        nameGenerator: NameGenerator,
        predicateURI: String): Iterable[ZSelectItem] = {

        val betaPre = betaGenerator.calculateBetaPredicate(predicateURI);
        val selectItem = MorphSQLSelectItem.apply(betaPre, this.dbType, "text");
        val tpPredicate = tp.getPredicate();

        val alias = nameGenerator.generateName(tpPredicate);
        selectItem.setAlias(alias);

        val predicateMappingIdSelectItems = this.genPRSQLPredicateMappingId(tpPredicate, cm, predicateURI);
        return List(selectItem) ::: predicateMappingIdSelectItems;
    }

    def genPRSQLObject(
        tp: Triple,
        alphaResult: MorphAlphaResult,
        betaGenerator: MorphRDBBetaGenerator,
        nameGenerator: NameGenerator,
        cmSubject: R2RMLTriplesMap,
        predicateURI: String,
        columnType: String): List[ZSelectItem] = {

        val tpObject = tp.getObject();

        val result: List[ZSelectItem] = {
            if (!SparqlUtility.isBlankNode(tpObject)) {
                if (RDF.`type`.getURI().equalsIgnoreCase(predicateURI)) {
                    val tm = cmSubject.asInstanceOf[R2RMLTriplesMap];
                    val classURIs = tm.subjectMap.classURIs;
                    val resultAux = classURIs.map(classURI => {
                        val zConstant = new ZConstant(classURI, ZConstant.STRING);
                        val selectItem: ZSelectItem = new ZSelectItem();
                        selectItem.setExpression(zConstant);
                        val selectItemAlias = nameGenerator.generateName(tpObject);
                        selectItem.setAlias(selectItemAlias);
                        selectItem;
                    })
                    resultAux.toList;
                } else {
                    val parentResult = genPRSQLObject2(tp, alphaResult, betaGenerator, nameGenerator, cmSubject, predicateURI, columnType).toList;

                    val childResult = this.genPRSQLObjectMappingId(tpObject, cmSubject, predicateURI);

                    if (childResult == null) {
                        parentResult
                    } else {
                        parentResult ::: childResult
                    }
                }
            } else {
                Nil;
            }
        }

        result;
    }

    def genPRSQLObject2(
        tp: Triple,
        alphaResult: MorphAlphaResult,
        betaGenerator: MorphRDBBetaGenerator,
        nameGenerator: NameGenerator,
        cmSubject: R2RMLTriplesMap,
        predicateURI: String,
        columnType: String): List[ZSelectItem] = {

        val betaObjSelectItems = betaGenerator.calculateBetaObject(tp, cmSubject, predicateURI, alphaResult);
        val selectItems = for (i <- 0 until betaObjSelectItems.size()) yield {
            val betaObjSelectItem = betaObjSelectItems.get(i);
            val selectItem = MorphSQLSelectItem.apply(betaObjSelectItem, dbType, columnType);

            val selectItemAliasAux = nameGenerator.generateName(tp.getObject());
            val selectItemAlias = {
                if (selectItemAliasAux != null) {
                    if (betaObjSelectItems.size() > 1) {
                        selectItemAliasAux + "_" + i;
                    } else {
                        selectItemAliasAux
                    }
                } else {
                    selectItemAliasAux
                }
            }

            if (selectItemAlias != null) {
                selectItem.setAlias(selectItemAlias);
            }

            selectItem; //line 23
        }
        selectItems.toList;
    }

    def genPRSQLObjectMappingId(tpObject: Node, cmSubject: R2RMLTriplesMap, predicateURI: String) = {
        val childResult: List[ZSelectItem] = {
            if (tpObject.isVariable() && !SparqlUtility.isBlankNode(tpObject)) {
                val propertyMappings =
                    cmSubject.getPropertyMappings(predicateURI);
                if (propertyMappings == null || propertyMappings.isEmpty()) {
                    logger.warn("no property mappings defined for predicate: " + predicateURI);
                    Nil;
                } else if (propertyMappings.size() > 1) {
                    logger.warn("multiple property mappings defined for predicate: " + predicateURI);
                    Nil;
                } else {
                    val propertyMapping = propertyMappings.iterator.next();
                    if (propertyMapping.isInstanceOf[R2RMLPredicateObjectMap]) {
                        val pom = propertyMapping.asInstanceOf[R2RMLPredicateObjectMap];
                        val om = pom.getObjectMap(0);
                        val mappingHashCode = {
                            if (om != null) {
                                val omHashCode = om.hashCode();
                                this.putMappedMapping(omHashCode, om);
                                omHashCode;
                            } else {
                                val rom = pom.getRefObjectMap(0);
                                if (rom != null) {
                                    //this.getOwner().getMapHashCodeMapping().put(mappingHashCode, rom);
                                    val romHashCode = rom.hashCode();
                                    this.putMappedMapping(romHashCode, rom);
                                    romHashCode;
                                } else {
                                    -1;
                                }
                            }
                        }

                        if (mappingHashCode != -1) {
                            val mappingHashCodeConstant = new ZConstant(
                                mappingHashCode + "", ZConstant.NUMBER);
                            val mappingSelectItem = MorphSQLSelectItem.apply(
                                mappingHashCodeConstant, dbType, Constants.POSTGRESQL_COLUMN_TYPE_INTEGER);
                            val mappingSelectItemAlias = Constants.PREFIX_MAPPING_ID + tpObject.getName();
                            mappingSelectItem.setAlias(mappingSelectItemAlias);

                            List(mappingSelectItem);
                        } else {
                            Nil;
                        }
                    } else {
                        Nil
                    }
                }
            } else {
                Nil;
            }

        }

        childResult;
    }

    def genPRSQLMappingId(node: Node, termMap: R2RMLTermMap): List[ZSelectItem] = {
        val result: List[ZSelectItem] = {
            if (node.isVariable()) {
                val termMapHashCode = termMap.hashCode();
                val mappingHashCodeConstant = new ZConstant(
                    termMapHashCode + "", ZConstant.NUMBER);
                val mappingSelectItem = MorphSQLSelectItem.apply(
                    mappingHashCodeConstant, dbType, Constants.POSTGRESQL_COLUMN_TYPE_INTEGER);
                val mappingSelectItemAlias = Constants.PREFIX_MAPPING_ID + node.getName();
                mappingSelectItem.setAlias(mappingSelectItemAlias);
                val childResult = List(mappingSelectItem);

                this.putMappedMapping(termMapHashCode, termMap);
                childResult;
            } else { Nil; }
        }

        result;
    }

    def genPRSQLPredicateMappingId(node: Node, cm: R2RMLTriplesMap, predicateURI: String): List[ZSelectItem] = {
        val pms = cm.getPropertyMappings(predicateURI);
        val poMap = pms.iterator.next.asInstanceOf[R2RMLPredicateObjectMap];
        val predicateMap = poMap.predicateMaps.iterator.next;
        val selectItemPredicateMappingId = this.genPRSQLMappingId(node, predicateMap);
        selectItemPredicateMappingId
    }
}