package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import scala.collection.JavaConversions.asJavaCollection

import org.apache.jena.graph.Triple
import org.apache.jena.vocabulary.RDF
import org.apache.log4j.Logger

import Zql.ZConstant
import Zql.ZSelectItem
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseUnfolder
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphAlphaResult
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLSelectItem
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLPredicateObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument

class MorphRDBBetaGenerator(md: RDBR2RMLMappingDocument, unfolder: MorphBaseUnfolder) {

    val logger = Logger.getLogger(this.getClass());

    val dbType = if (md.dbMetaData.isDefined) { md.dbMetaData.get.dbType; }
    else { Constants.DATABASE_DEFAULT; }

    val alphaGenerator: MorphRDBAlphaGenerator = null;

    var owner: MorphRDBQueryTranslator = null;

    def calculateBetaObject(tp: Triple, cm: R2RMLTriplesMap, predicateURI: String, alphaResult: MorphAlphaResult, pm: R2RMLPredicateObjectMap): List[ZSelectItem] = {

        val predicateObjectMap = pm.asInstanceOf[R2RMLPredicateObjectMap];
        val refObjectMap = predicateObjectMap.getRefObjectMap(0);

        val logicalTableAlias = alphaResult.alphaSubject.getAlias();

        val betaObjects: List[MorphSQLSelectItem] = {
            if (refObjectMap == null) {
                val objectMap = predicateObjectMap.getObjectMap(0);

                objectMap.termMapType match {
                    case Constants.MorphTermMapType.ConstantTermMap => {
                        val constantValue = objectMap.getConstantValue();
                        val zConstant = new ZConstant(constantValue, ZConstant.STRING);
                        val selectItem = MorphSQLSelectItem.apply(zConstant);
                        List(selectItem);
                    }
                    case _ => {
                        val databaseColumnsString = objectMap.getReferencedColumns();
                        val betaObjectsAux = databaseColumnsString.map(databaseColumnString =>
                            MorphSQLSelectItem.apply(databaseColumnString, logicalTableAlias, dbType, null));
                        betaObjectsAux.toList;
                    }
                }
            } else {
                val parentTriplesMap = md.getParentTriplesMap(refObjectMap);
                val parentLogicalTable = parentTriplesMap.logicalSource;
                val parentSubjectMap = parentTriplesMap.subjectMap;
                val parentColumns = parentSubjectMap.getReferencedColumns;

                val refObjectMapAliasAux = this.owner.mapTripleAlias.get(tp);
                val refObjectMapAlias = if (refObjectMapAliasAux.isDefined) { refObjectMapAliasAux.get }
                else { null }

                if (parentColumns != null) {
                    val betaObjectsAux = parentColumns.map(parentColumn => {
                        MorphSQLSelectItem.apply(parentColumn, refObjectMapAlias, dbType, null);
                    })
                    betaObjectsAux.toList;
                } else {
                    Nil;
                }
            }
        }

        betaObjects;
    }

    def calculateBetaSubject(tp: Triple, cm: R2RMLTriplesMap, alphaResult: MorphAlphaResult): List[ZSelectItem] = {

        val triplesMap = cm.asInstanceOf[R2RMLTriplesMap];
        val subjectMap = triplesMap.subjectMap;
        val logicalTableAlias = alphaResult.alphaSubject.getAlias();

        val databaseColumnsString = subjectMap.getReferencedColumns();

        val result: List[ZSelectItem] = {
            if (databaseColumnsString != null) {
                val resultAux = databaseColumnsString.map(databaseColumnString =>
                    MorphSQLSelectItem.apply(databaseColumnString, logicalTableAlias, dbType, null));
                resultAux.toList;
            } else {
                Nil;
            }
        }
        result;
    }

    def calculateBeta(tp: Triple, pos: Constants.MorphPOS.Value, cm: R2RMLTriplesMap, predicateURI: String, alphaResult: MorphAlphaResult): List[ZSelectItem] = {
        val result: List[ZSelectItem] = {
            if (pos == Constants.MorphPOS.sub) {
                this.calculateBetaSubject(tp, cm, alphaResult).toList;
            } else if (pos == Constants.MorphPOS.pre) {
                List(this.calculateBetaPredicate(predicateURI));
            } else if (pos == Constants.MorphPOS.obj) {
                val predicateIsRDFSType = RDF.`type`.getURI().equals(predicateURI);
                if (predicateIsRDFSType) {
                    val className = new ZConstant(cm.getConceptName(), ZConstant.STRING);
                    val selectItem = MorphSQLSelectItem.apply(className);
                    //selectItem.setExpression(className);
                    List(selectItem);
                } else {
                    this.calculateBetaObject(tp, cm, predicateURI, alphaResult).toList;
                }
            } else {
                throw new Exception("invalid Pos value in beta!");
            }
        }

        logger.debug("beta = " + result);
        return result;
    }

    def calculateBetaObject(triple: Triple, cm: R2RMLTriplesMap, predicateURI: String, alphaResult: MorphAlphaResult): List[ZSelectItem] = {
        val betaObjects: List[ZSelectItem] = {
            val pms = cm.getPropertyMappings(predicateURI);
            if (pms == null || pms.isEmpty()) {
                val errorMessage = "Undefined mappings for : " + predicateURI + " for class " + cm.getConceptName();
                logger.debug(errorMessage);
                Nil;
            } else if (pms.size() > 1) {
                val errorMessage = "Multiple property mappings defined, result may be wrong!";
                logger.debug(errorMessage);
                throw new Exception(errorMessage);
            } else { //if(pms.size() == 1)
                val pm = pms.iterator.next();
                this.calculateBetaObject(triple, cm, predicateURI, alphaResult, pm).toList;
            }
        }
        return betaObjects;
    }

    def calculateBetaPredicate(predicateURI: String): ZSelectItem = {
        val predicateURIConstant = new ZConstant(predicateURI, ZConstant.STRING);
        val selectItem = MorphSQLSelectItem.apply(predicateURIConstant);
        selectItem;
    }

}