package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import java.sql.Connection
import java.util.regex.Matcher

import scala.collection.JavaConversions.asJavaCollection
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.mutableSetAsJavaSet
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.mutable.LinkedHashSet

import org.apache.log4j.Logger

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.query.Query
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpDistinct
import org.apache.jena.sparql.algebra.op.OpExtend
import org.apache.jena.sparql.algebra.op.OpFilter
import org.apache.jena.sparql.algebra.op.OpGroup
import org.apache.jena.sparql.algebra.op.OpJoin
import org.apache.jena.sparql.algebra.op.OpLeftJoin
import org.apache.jena.sparql.algebra.op.OpOrder
import org.apache.jena.sparql.algebra.op.OpProject
import org.apache.jena.sparql.algebra.op.OpSlice
import org.apache.jena.sparql.algebra.op.OpUnion
import org.apache.jena.sparql.core.BasicPattern
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.expr.E_Bound
import org.apache.jena.sparql.expr.E_Function
import org.apache.jena.sparql.expr.E_LogicalAnd
import org.apache.jena.sparql.expr.E_LogicalNot
import org.apache.jena.sparql.expr.E_LogicalOr
import org.apache.jena.sparql.expr.E_NotEquals
import org.apache.jena.sparql.expr.E_OneOf
import org.apache.jena.sparql.expr.E_Regex
import org.apache.jena.sparql.expr.Expr
import org.apache.jena.sparql.expr.ExprFunction
import org.apache.jena.sparql.expr.ExprFunction1
import org.apache.jena.sparql.expr.ExprFunction2
import org.apache.jena.sparql.expr.ExprList
import org.apache.jena.sparql.expr.NodeValue
import org.apache.jena.sparql.expr.aggregate.AggAvg
import org.apache.jena.sparql.expr.aggregate.AggCount
import org.apache.jena.sparql.expr.aggregate.AggMax
import org.apache.jena.sparql.expr.aggregate.AggMin
import org.apache.jena.sparql.expr.aggregate.AggSum
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.XSD

import Zql.ZConstant
import Zql.ZExp
import Zql.ZExpression
import Zql.ZGroupBy
import Zql.ZOrderBy
import Zql.ZSelectItem
import es.upm.fi.dia.oeg.morph.abstractquery.AbstractQuerySql
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.DBUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphAlphaResult
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryTranslator
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphTransTPResult
import es.upm.fi.dia.oeg.morph.base.querytranslator.SparqlUtility
import es.upm.fi.dia.oeg.morph.base.querytranslator.TpBinding
import es.upm.fi.dia.oeg.morph.base.querytranslator.TriplePatternPredicateBounder
import es.upm.fi.dia.oeg.morph.base.querytranslator.engine.MorphMappingInferrer
import es.upm.fi.dia.oeg.morph.base.querytranslator.engine.MorphQueryRewriter
import es.upm.fi.dia.oeg.morph.base.querytranslator.engine.MorphQueryTranslatorUtility
import es.upm.fi.dia.oeg.morph.base.sql.ISqlQuery
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLConstant
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLSelectItem
import es.upm.fi.dia.oeg.morph.base.sql.MorphSQLUtility
import es.upm.fi.dia.oeg.morph.base.sql.SQLFromItem
import es.upm.fi.dia.oeg.morph.base.sql.SQLJoinTable
import es.upm.fi.dia.oeg.morph.base.sql.SQLQuery
import es.upm.fi.dia.oeg.morph.base.sql.SQLUnion
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.r2rml.model.RDBR2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.rdb.engine.NameGenerator

class MorphRDBQueryTranslator(factory: IMorphFactory) extends MorphBaseQueryTranslator(factory) {

    val unfolder = factory.getUnfolder
    val mappingDocument = factory.getMappingDocument.asInstanceOf[RDBR2RMLMappingDocument]

    val nameGenerator: NameGenerator = new NameGenerator()
    val alphaGenerator: MorphRDBAlphaGenerator = new MorphRDBAlphaGenerator(mappingDocument, unfolder)
    val betaGenerator: MorphRDBBetaGenerator = new MorphRDBBetaGenerator(mappingDocument, unfolder)
    val condSQLGenerator: MorphRDBCondSQLGenerator = new MorphRDBCondSQLGenerator(mappingDocument, unfolder)
    val prSQLGenerator: MorphRDBPRSQLGenerator = new MorphRDBPRSQLGenerator(mappingDocument, unfolder)

    val connection: Connection = factory.getConnection.concreteCnx.asInstanceOf[Connection];

    val databaseType: String = factory.getProperties.databaseType

    var mapAggreatorAlias: Map[String, ZSelectItem] = Map.empty; //varname - selectitem

    var mapTripleAlias: Map[Triple, String] = Map.empty;

    /** Is-Null of Is-Not-null conditions for variables in a SPARQL query */
    var mapVarNotNull: Map[Node, Boolean] = Map.empty;

    /** Map of nodes of a SPARQL query and the candidate triples maps for each node **/
    var mapInferredTMs: Map[Node, Set[RDBR2RMLTriplesMap]] = Map.empty;

    override val logger = Logger.getLogger(this.getClass());

    /**
     * These two methods are necessary for the inheritance but it is actually not used in the RDB case:
     * the query translation method is still the one defined in Morph-RDB, it has not been upgrade
     * to support the abstract query mechanism used in the MongoDB case.
     */
    override def transTPm(tpBindingds: TpBinding, limit: Option[Long]): AbstractQuery = {
        throw new MorphException("Not supported")
    }

    this.alphaGenerator.owner = this;
    this.betaGenerator.owner = this;

    private var mapTemplateMatcher: Map[String, Matcher] = Map.empty;

    private var mapTemplateAttributes: Map[String, List[String]] = Map.empty;

    val functionsMap: Map[String, String] = Map(
        "E_Bound" -> "IS NOT NULL", "E_LogicalNot" -> "NOT", "E_LogicalOr" -> "OR", "E_LogicalAnd" -> "AND", "E_Regex" -> "LIKE", "E_OneOf" -> "IN")

    def getPRSQLGen(): MorphRDBPRSQLGenerator = { prSQLGenerator }

    /**
     * High level entry point to the query translation process.
     */
    override def translate(op: Op): Option[AbstractQuery] = {
        logger.debug("opSparqlQuery = " + op);

        // Find candidate triples maps
        val typeInferrer = new MorphMappingInferrer(this.mappingDocument);
        this.mapInferredTMs = typeInferrer.infer(op);
        logger.info("Inferred Types : \n" + typeInferrer.printInferredTypes());

        // Set not-null conditions for all variables
        this.initializeMapVarIsNullable(op);

        val resultQuery = {
            // Eliminate self-joins from the SPARQL query before translation
            if (this.optimizer != null && this.optimizer.selfJoinElimination) {
                val mapNodeLogicalTableSize = mapInferredTMs.keySet.map(node => {
                    val cms = this.mapInferredTMs(node);
                    val cm = cms.iterator.next();
                    val logicalTableSize = cm.getLogicalTableSize();
                    (node -> logicalTableSize);
                })

                val reorderSTG = factory.getProperties.reorderSTG;

                val queryRewritter = new MorphQueryRewriter(mapNodeLogicalTableSize.toMap, reorderSTG);
                val opSparqlQuery2 = {
                    try {
                        queryRewritter.rewrite(op);
                    } catch {
                        case e: Exception => {
                            e.printStackTrace();
                            op;
                        }
                    }
                }
                logger.debug("Query rewritten after self join elimintation = \n" + opSparqlQuery2);

                // Do the actual query translation
                this.trans(opSparqlQuery2);
            } else
                // Do the actual query translation
                this.trans(op);
        }

        if (resultQuery != null) {
            resultQuery.cleanupSelectItems();
            resultQuery.cleanupOrderBy();
        }

        logger.debug("Rewritten sql query = \n" + resultQuery + "\n");
        Some(new AbstractQuerySql().setTargetQuery(List(new GenericQuery(Constants.DatabaseType.Relational, resultQuery, None))))
    }

    private def getColumnsByNode(node: Node, oldSelectItems: List[ZSelectItem]): LinkedHashSet[String] = {
        val nameSelectVar = nameGenerator.generateName(node);
        val oldSelectItemsIterator = oldSelectItems.iterator;
        var i = 0;

        val result: LinkedHashSet[String] = LinkedHashSet.empty;

        for (oldSelectItem <- oldSelectItems) {
            val oldAlias = oldSelectItem.getAlias();
            val selectItemName = {
                if (oldAlias == null || oldAlias.equals("")) {
                    oldSelectItem.getColumn();
                } else
                    oldAlias;
            }

            val resultAux = {
                if (selectItemName.equalsIgnoreCase(nameSelectVar)) {
                    selectItemName;
                } else if (selectItemName.contains(nameSelectVar + "_")) {
                    val selectItemNameParts = nameSelectVar + "_" + i;
                    i += 1;
                    selectItemNameParts;
                } else
                    null
            }

            if (resultAux != null)
                result += resultAux;
        }

        result;
    }

    private def getSelectItemsByNode(node: Node, oldSelectItems: List[ZSelectItem]): LinkedHashSet[ZSelectItem] = {
        var result: LinkedHashSet[ZSelectItem] = LinkedHashSet.empty;
        val nameSelectVar = nameGenerator.generateName(node);

        var i = 0;
        for (oldSelectItem <- oldSelectItems) {
            val oldAlias = oldSelectItem.getAlias();
            val selectItemName = {
                if (oldAlias == null || oldAlias.equals("")) {
                    oldSelectItem.getColumn();
                } else {
                    oldAlias;
                }
            }

            val resultAux = {
                if (selectItemName.equalsIgnoreCase(nameSelectVar)) {
                    oldSelectItem;
                } else if (selectItemName.contains(nameSelectVar + "_")) {
                    oldSelectItem;
                } else {
                    null
                }
            }

            result += resultAux;

        }
        result;
    }

    /**
     *  Implementation of the trans algorithm as described by Chebotko
     */
    private def trans(op: Op): ISqlQuery = {
        val result = {
            op match {
                case bgp: OpBGP => {
                    if (bgp.getPattern().size() == 1) {
                        val tp = bgp.getPattern().get(0);
                        this.transTP(tp)
                    } else
                        this.transBGP(bgp);
                }
                case opJoin: OpJoin => {
                    this.transInnerJoin(opJoin);
                }
                case opLeftJoin: OpLeftJoin => {
                    this.transLeftJoin(opLeftJoin);
                }
                case opUnion: OpUnion => {
                    this.transUnion(opUnion);
                }
                case opFilter: OpFilter => {
                    this.transFilter(opFilter);
                }
                case opProject: OpProject => {
                    this.transProject(opProject);
                }
                case opSlice: OpSlice => {
                    this.transSlice(opSlice);
                }
                case opDistinct: OpDistinct => {
                    this.transDistinct(opDistinct);
                }
                case opOrder: OpOrder => {
                    this.transOrder(opOrder);
                }
                case opExtend: OpExtend => {
                    this.transExtend(opExtend);
                }
                case opGroup: OpGroup => {
                    this.transGroup(opGroup);
                }
                case _ => {
                    val message = "Unsupported type of query";
                    logger.error(message);
                    null
                }
            }
        }

        if (result != null) {
            result.setDatabaseType(databaseType);
        }

        return result;
    }

    private def transBGP(bgp: OpBGP): ISqlQuery = {
        val transBGPSQL = {
            if (MorphQueryTranslatorUtility.isTriplePattern(bgp)) { //triple pattern
                val tp = bgp.getPattern().getList().get(0);
                this.transTP(tp);
            } else { //bgp pattern
                val triples = bgp.getPattern().getList().toList;
                val isSTG = MorphQueryTranslatorUtility.isSTG(bgp);

                if (this.optimizer != null && this.optimizer.selfJoinElimination && isSTG) {
                    this.transSTG(triples);
                } else {
                    val separationIndex = {
                        if (this.optimizer != null && this.optimizer.selfJoinElimination) {
                            MorphQueryTranslatorUtility.getFirstTBEndIndex(triples);
                        } else {
                            1
                        }
                    }

                    val gp1TripleList = triples.subList(0, separationIndex);
                    val gp1 = new OpBGP(BasicPattern.wrap(gp1TripleList));
                    val gp2TripleList = triples.subList(separationIndex, triples.size());
                    val gp2 = new OpBGP(BasicPattern.wrap(gp2TripleList));

                    this.transJoin(bgp, gp1, gp2, Constants.JOINS_TYPE_INNER);
                }
            }
        }

        return transBGPSQL;
    }

    private def transDistinct(opDistinct: OpDistinct): ISqlQuery = {
        val opDistinctSubOp = opDistinct.getSubOp();
        val opDistinctSubOpSQL = this.trans(opDistinctSubOp);
        opDistinctSubOpSQL match {
            case sqlQuery: SQLQuery => { sqlQuery.setDistinct(true); }
        }
        opDistinctSubOpSQL;
    }

    private def transExtend(opExtend: OpExtend): ISqlQuery = {
        val subOp = opExtend.getSubOp();
        val subOpSQL = this.trans(subOp);
        val selectItems = subOpSQL.getSelectItems();

        val opExtendExprs = opExtend.getVarExprList().getExprs();
        for (varExpr <- opExtendExprs.keySet()) {
            val expr = opExtendExprs.get(varExpr);
            val exprVarName = expr.getVarName();
            val selectItem = this.mapAggreatorAlias.get(exprVarName);
            if (selectItem.isDefined) {
                val alias = this.nameGenerator.generateName(varExpr);
                selectItem.get.setAlias(alias);
            }

            val mapPrefixIdOldAlias = Constants.PREFIX_MAPPING_ID + exprVarName.replaceAll("\\.", "dot_");
            val mapPrefixIdNewAlias = Constants.PREFIX_MAPPING_ID + varExpr.getName();
            val mapPrefixIdSelectItems = MorphSQLUtility.getSelectItemsByAlias(selectItems.toList, mapPrefixIdOldAlias);
            val mapPrefixIdSelectItem = mapPrefixIdSelectItems.iterator().next();
            mapPrefixIdSelectItem.setAlias(mapPrefixIdNewAlias);

            expr.toString();
        }
        return subOpSQL;
    }

    private def transFilter(opFilter: OpFilter): ISqlQuery = {
        val opFilterSubOp = opFilter.getSubOp();
        val subOpSQL = this.trans(opFilterSubOp);
        val transGPSQLAlias = subOpSQL.generateAlias();
        val subOpSelectItems = subOpSQL.getSelectItems().toList;
        val exprList = opFilter.getExprs();

        val resultFrom = {
            if (this.optimizer != null && this.optimizer.subQueryAsView) {
                val conn = this.connection;
                val subQueryViewName = "sqf" + Math.abs(opFilterSubOp.hashCode());
                val dropViewSQL = "DROP VIEW IF EXISTS " + subQueryViewName;
                logger.info(dropViewSQL + ";\n");
                DBUtility.execute(conn, dropViewSQL, 0);
                val createViewSQL = "CREATE VIEW " + subQueryViewName + " AS " + subOpSQL;
                logger.info(createViewSQL + ";\n");
                DBUtility.execute(conn, createViewSQL, 0);
                val sqlFromItem = new SQLFromItem(subQueryViewName, Constants.LogicalTableType.TABLE_NAME);
                sqlFromItem.databaseType = this.databaseType
                sqlFromItem;
            } else {
                val sqlFromItem = new SQLFromItem(subOpSQL.toString(), Constants.LogicalTableType.QUERY);
                sqlFromItem.databaseType = this.databaseType;
                sqlFromItem
            }
        }
        resultFrom.setAlias(transGPSQLAlias);

        val exprListSQL = this.transExprList(opFilterSubOp, exprList, subOpSelectItems, subOpSQL.getAlias());

        val transFilterSQL = {
            if (this.optimizer != null && this.optimizer.subQueryElimination) {
                subOpSQL.pushFilterDown(exprListSQL);
                subOpSQL;
            } else {
                val oldSelectItems = subOpSQL.getSelectItems();
                val newSelectItems = oldSelectItems.map(oldSelectItem => {
                    val oldSelectItemAlias = oldSelectItem.getAlias();
                    val columnType = null;
                    val newSelectItem = MorphSQLSelectItem.apply(oldSelectItemAlias, null, databaseType, columnType);
                    newSelectItem.setAlias(oldSelectItemAlias);
                    newSelectItem;
                })

                val resultAux = new SQLQuery(subOpSQL);
                resultAux.setSelectItems(newSelectItems);
                resultAux.addWhere(exprListSQL);
                resultAux;
            }
        }

        return transFilterSQL;
    }

    private def transGroup(opGroup: OpGroup): ISqlQuery = {
        val dbType = this.databaseType;
        val selectItemGenerator = new MorphSQLSelectItemGenerator(this.nameGenerator, dbType);

        val subOp = opGroup.getSubOp();
        val subOpSQL = this.trans(subOp);
        val subOpSQLAlias = subOpSQL.getAlias();
        val transOpGroup = subOpSQL;
        val oldSelectItems = subOpSQL.getSelectItems().toList;
        var newSelectItems: List[ZSelectItem] = Nil;
        var mapPrefixSelectItems: List[ZSelectItem] = Nil;

        val groupVars = opGroup.getGroupVars();
        val vars = groupVars.getVars();
        var groupByExps: List[ZExp] = Nil;
        for (groupVar <- vars) {
            val selectItemsByVars = this.getSelectItemsByNode(groupVar, oldSelectItems).toList;
            newSelectItems = newSelectItems ::: selectItemsByVars;

            for (selectItemByVar <- selectItemsByVars) {
                val selectItemValue = MorphSQLUtility.getValueWithoutAlias(selectItemByVar);
                val zExp = new ZConstant(selectItemValue, ZConstant.COLUMNNAME);
                groupByExps = groupByExps ::: List(zExp);
            }

            val mapPrefixSelectItemsAux = MorphSQLUtility.getSelectItemsMapPrefix(
                oldSelectItems, groupVar, subOpSQLAlias, this.databaseType);
            mapPrefixSelectItems = mapPrefixSelectItems ::: mapPrefixSelectItemsAux.toList;
        }

        val aggregators = opGroup.getAggregators();
        for (exprAggregator <- aggregators) {
            val aggregator = exprAggregator.getAggregator();
            val functionName = {
                aggregator match {
                    case _: AggAvg => { Constants.AGGREGATION_FUNCTION_AVG; }
                    case _: AggSum => { Constants.AGGREGATION_FUNCTION_SUM; }
                    case _: AggCount => { Constants.AGGREGATION_FUNCTION_COUNT; }
                    case _: AggMax => { Constants.AGGREGATION_FUNCTION_MAX; }
                    case _: AggMin => { Constants.AGGREGATION_FUNCTION_MIN; }
                    case _ => {
                        val errorMessage = "Unsupported aggregation function " + aggregator;
                        logger.error(errorMessage);
                        null
                    }
                }
            }

            val varsMentioned = aggregator.getExprList().get(0).getVarsMentioned();
            if (varsMentioned.size() > 1) {
                val errorMessage = "Multiple variables in aggregation function is not supported: " + aggregator;
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }

            val varMentioned = varsMentioned.iterator().next();
            val exprAggregatorVar = exprAggregator.getVar();
            val aggregatorVarName = exprAggregatorVar.getName();
            val aggregatorAlias = aggregatorVarName.replaceAll("\\.", "dot_");
            val aggregatedSelectItems = selectItemGenerator.generateSelectItem(
                varMentioned, subOpSQLAlias, oldSelectItems, true);

            if (aggregatedSelectItems.size() > 1) {
                val errorMessage = "Multiple columns in aggregation function is not supported: " + aggregatedSelectItems;
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }

            transOpGroup.pushProjectionsDown(aggregatedSelectItems);
            val pushedAggregatedSelectItems = transOpGroup.getSelectItems();
            val pushedAggregatedSelectItem = pushedAggregatedSelectItems.iterator().next();
            pushedAggregatedSelectItem.setAggregate(functionName);
            pushedAggregatedSelectItem.setAlias(Constants.PREFIX_VAR + aggregatorAlias);
            newSelectItems = newSelectItems ::: List(pushedAggregatedSelectItem);
            this.mapAggreatorAlias += (aggregatorVarName -> pushedAggregatedSelectItem);

            val mapPrefixSelectItemsAux = MorphSQLUtility.getSelectItemsMapPrefix(
                oldSelectItems, varMentioned, subOpSQLAlias, this.databaseType);
            val mapPrefixSelectItemAux = mapPrefixSelectItemsAux.iterator.next();
            val mapPrefixSelectItemAuxAlias = Constants.PREFIX_MAPPING_ID + aggregatorAlias;
            mapPrefixSelectItemAux.setAlias(mapPrefixSelectItemAuxAlias); ;

            mapPrefixSelectItems = mapPrefixSelectItems ::: mapPrefixSelectItemsAux.toList;
        }

        val zGroupBy = new ZGroupBy(groupByExps.asInstanceOf[java.util.Vector[ZExp]]);
        transOpGroup.addGroupBy(zGroupBy);
        transOpGroup.setSelectItems(newSelectItems);
        transOpGroup.addSelectItems(mapPrefixSelectItems);

        transOpGroup;
    }

    private def transInnerJoin(opJoin: OpJoin): ISqlQuery = {
        val opLeft = opJoin.getLeft();
        val opRight = opJoin.getRight();
        val transJoinSQL = this.transJoin(opJoin, opLeft, opRight, Constants.JOINS_TYPE_INNER);
        transJoinSQL;
    }

    private def transLeftJoin(opLeftJoin: OpLeftJoin): ISqlQuery = {
        val opLeft = opLeftJoin.getLeft();
        val opRight = opLeftJoin.getRight();
        val transLeftJoinSQL = this.transJoin(opLeftJoin, opLeft, opRight, Constants.JOINS_TYPE_LEFT);
        return transLeftJoinSQL;
    }

    private def transOrder(opOrder: OpOrder): ISqlQuery = {
        val opOrderSubOp = opOrder.getSubOp();
        val opOrderSubOpSQL = this.trans(opOrderSubOp);

        val orderByConditions = opOrder.getConditions().map(sortCondition => {
            val sortConditionDirection = sortCondition.getDirection();
            val sortConditionExpr = sortCondition.getExpression();
            val sortConditionVar = sortConditionExpr.asVar();

            val nameSortConditionVar = nameGenerator.generateName(sortConditionVar);
            val zExp = MorphSQLConstant.apply(nameSortConditionVar, ZConstant.COLUMNNAME, this.databaseType, null);
            //ZExp zExp = new ZConstant(sortConditionVar.getVarName(), ZConstant.COLUMNNAME);

            val zOrderBy = new ZOrderBy(zExp);
            if (sortConditionDirection == Query.ORDER_DEFAULT) {
                zOrderBy.setAscOrder(true);
            } else if (sortConditionDirection == Query.ORDER_ASCENDING) {
                zOrderBy.setAscOrder(true);
            }
            zOrderBy;
        }).toList;

        //always push order by, if not, the result is incorrect!
        opOrderSubOpSQL.setOrderBy(orderByConditions);
        val transOpOrder = opOrderSubOpSQL;
        return transOpOrder;
    }

    private def transProject(opProject: OpProject): ISqlQuery = {

        val selectItemGenerator = new MorphSQLSelectItemGenerator(this.nameGenerator, this.databaseType);
        val opProjectSubOp = opProject.getSubOp();
        val opProjectSubOpSQL = this.trans(opProjectSubOp);
        val oldSelectItems = opProjectSubOpSQL.getSelectItems().toList;
        val subOpSQLAlias = opProjectSubOpSQL.generateAlias();
        val selectVars = opProject.getVars();

        val newSelectItemsTuple = selectVars.map(selectVar => {
            val selectItemsByVars = selectItemGenerator.generateSelectItem(
                selectVar, subOpSQLAlias, oldSelectItems, true);

            val mapPrefixSelectItemsAux = MorphSQLUtility.getSelectItemsMapPrefix(
                oldSelectItems, selectVar, subOpSQLAlias, this.databaseType);
            (selectItemsByVars, mapPrefixSelectItemsAux.toList);
        })
        val newSelectItemsVar = newSelectItemsTuple.map(tuple => tuple._1);
        val newSelectItemsMappingId = newSelectItemsTuple.map(tuple => tuple._2);

        if (this.optimizer != null && this.optimizer.subQueryAsView) {
            val conn = this.connection;
            val subQueryViewName = "sqp" + Math.abs(opProject.hashCode());
            val dropViewSQL = "DROP VIEW IF EXISTS " + subQueryViewName;
            logger.info(dropViewSQL + ";\n");
            DBUtility.execute(conn, dropViewSQL, 0);
            val createViewSQL = "CREATE VIEW " + subQueryViewName + " AS " + opProjectSubOpSQL;
            logger.info(createViewSQL + ";\n");
            DBUtility.execute(conn, createViewSQL, 0);
            val sqlFromItem = new SQLFromItem(subQueryViewName, Constants.LogicalTableType.TABLE_NAME);
            sqlFromItem.databaseType = this.databaseType;
            sqlFromItem;
        }

        val newSelectItems: List[ZSelectItem] = newSelectItemsVar.flatten.toList ::: newSelectItemsMappingId.flatten.toList;
        val transProjectSQL = {
            if (this.optimizer != null && this.optimizer.subQueryElimination) {
                //push group by down
                opProjectSubOpSQL.pushGroupByDown();

                //pushing down projections
                opProjectSubOpSQL.pushProjectionsDown(newSelectItems);

                //pushing down order by
                opProjectSubOpSQL.pushOrderByDown(newSelectItems);

                opProjectSubOpSQL;
            } else {
                val resultAux = new SQLQuery(opProjectSubOpSQL);
                resultAux.setSelectItems(newSelectItems);
                val orderByConditions = opProjectSubOpSQL.getOrderByConditions;
                if (orderByConditions != null) {
                    resultAux.pushOrderByDown(newSelectItems);
                    opProjectSubOpSQL.setOrderBy(null);
                }
                resultAux;
            }
        }

        transProjectSQL;
    }

    private def transSlice(opSlice: OpSlice): ISqlQuery = {
        val sliceLength = opSlice.getLength();
        val offset = opSlice.getStart();

        val opSliceSubOp = opSlice.getSubOp();
        val sqlQuery = this.trans(opSliceSubOp);
        if (sliceLength > 0) {
            sqlQuery.setSlice(sliceLength);
        }

        if (offset > 0) {
            sqlQuery.setOffset(offset);
        }

        sqlQuery;
    }

    private def transUnion(opUnion: OpUnion): ISqlQuery = {
        val gp1 = opUnion.getLeft();
        val gp2 = opUnion.getRight();
        val r1 = this.trans(gp1);
        val r2 = this.trans(gp2);

        val transUnion = {
            if (r1 == null && r2 == null) {
                null;
            } else if (r1 == null && r2 != null) {
                r2;
            } else if (r1 != null && r2 == null) {
                r1;
            } else {
                val selectItemGenerator = new MorphSQLSelectItemGenerator(this.nameGenerator, this.databaseType);

                val r3 = this.trans(gp2);
                val r4 = this.trans(gp1);

                val r1Alias = r1.generateAlias() + "r1";
                r1.setAlias(r1Alias);
                val r2Alias = r2.generateAlias() + "r2";
                r2.setAlias(r2Alias);
                val r3Alias = r3.generateAlias() + "r3";
                r3.setAlias(r3Alias);
                val r4Alias = r4.generateAlias() + "r4";
                r4.setAlias(r4Alias);

                val r1SelectItems = r1.getSelectItems().toList;
                val r2SelectItems = r2.getSelectItems().toList;

                val termsGP1 = MorphQueryTranslatorUtility.getTerms(gp1);
                val termsGP2 = MorphQueryTranslatorUtility.getTerms(gp2);
                val termsA = termsGP1.diff(termsGP2);
                val termsB = termsGP2.diff(termsGP1);
                val termsC = termsGP1.intersect(termsGP2);

                //Collection<ZSelectItem> selectItemsA1 = this.generateSelectItems(termsA, r1Alias, r1SelectItems, false);
                val selectItemsA1 = selectItemGenerator.generateSelectItems(termsA, r1Alias, r1SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsA1);
                val selectItemsB1 = selectItemGenerator.generateSelectItems(termsB, r2Alias, r2SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsB1);
                val selectItemsC1 = selectItemGenerator.generateSelectItems(termsC, r1Alias, r1SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsC1);

                val a1MappingIdSelectItems = this.generateMappingIdSelectItems(termsA.toList, r1SelectItems, r1Alias, this.databaseType);
                val b1MappingIdSelectItems = this.generateMappingIdSelectItems(termsB.toList, r2SelectItems, r2Alias, this.databaseType);
                val c1MappingIdSelectItems = this.generateMappingIdSelectItems(termsC.toList, r1SelectItems, r1Alias, this.databaseType);
                val selectItemsMappingId1 = a1MappingIdSelectItems ::: b1MappingIdSelectItems ::: c1MappingIdSelectItems;

                val query1 = new SQLQuery();
                query1.setDatabaseType(this.databaseType);
                val r1JoinTable = new SQLJoinTable(r1, null, null);
                val r2JoinTable = new SQLJoinTable(r2, Constants.JOINS_TYPE_LEFT, Constants.SQL_EXPRESSION_FALSE);
                query1.addFromItem(r1JoinTable);
                query1.addFromItem(r2JoinTable);
                query1.addSelectItems(selectItemsA1);
                query1.addSelectItems(selectItemsB1);
                query1.addSelectItems(selectItemsC1);
                query1.addSelectItems(selectItemsMappingId1);

                val r3SelectItems = r3.getSelectItems().toList;
                val selectItemsA2 = selectItemGenerator.generateSelectItems(termsA, r4Alias, r1SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsA2);
                val selectItemsB2 = selectItemGenerator.generateSelectItems(termsB, r3Alias, r2SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsB2);
                val termsCList = termsC;
                val selectItemsC2 = selectItemGenerator.generateSelectItems(termsCList, r3Alias + ".", r3SelectItems, false);
                MorphSQLUtility.setDefaultAlias(selectItemsC2);

                val a2MappingIdSelectItems = this.generateMappingIdSelectItems(termsA.toList, r1SelectItems, r4Alias, this.databaseType);
                val b2MappingIdSelectItems = this.generateMappingIdSelectItems(termsB.toList, r2SelectItems, r3Alias, this.databaseType);
                val c2MappingIdSelectItems = this.generateMappingIdSelectItems(termsC.toList, r1SelectItems, r3Alias, this.databaseType);
                val selectItemsMappingId2 = a2MappingIdSelectItems ::: b2MappingIdSelectItems ::: c2MappingIdSelectItems;

                val query2 = new SQLQuery();
                query2.setDatabaseType(this.databaseType);
                val r3JoinTable = new SQLJoinTable(r3, null, null);
                val r4JoinTable = new SQLJoinTable(r4, Constants.JOINS_TYPE_LEFT, Constants.SQL_EXPRESSION_FALSE);
                query2.addFromItem(r3JoinTable);
                query2.addFromItem(r4JoinTable);
                query2.addSelectItems(selectItemsA2);
                query2.addSelectItems(selectItemsB2);
                query2.addSelectItems(selectItemsC2);
                query2.addSelectItems(selectItemsMappingId2);

                val queries = List(query1, query2);
                val transUnionSQL = new SQLUnion(List(query1, query2));
                transUnionSQL.databaseType = this.databaseType;
                logger.debug("transUnionSQL = \n" + transUnionSQL);

                transUnionSQL;
            }
        }

        transUnion;
    }

    /**
     * Translation of a triple pattern into an SQL UNION query,
     * with one sub-query in the union for each candidate triples map.
     */
    private def transTP(tp: Triple): ISqlQuery = {
        val tpSubject = tp.getSubject();
        val tpPredicate = tp.getPredicate();
        val tpObject = tp.getObject();

        val skipRDFTypeStatement = false;

        val result = {
            if (tpPredicate.isURI() && RDF.`type`.getURI().equals(tpPredicate.getURI())
                && tpObject.isURI() && skipRDFTypeStatement) {
                null;
            } else {
                // Get the candidate triple maps for the subject of the triple pattern 
                val tmOption = this.mapInferredTMs.get(tpSubject);
                val triplesMaps = {
                    if (tmOption.isDefined) {
                        tmOption.get;
                    } else {
                        logger.warn("Undefined TriplesMap for triple: " + tp + ". All triple patterns will be used");
                        this.mappingDocument.triplesMaps.toSet
                    }
                }

                // Build a union of per-triples-map queries:
                // In the RDB case each tp is associated with a list of one query, possibly an SQL UNION. 
                // The flatMap will flatten those lists into a single list of queries

                // Build a union of per-triples-map queries
                val unionOfQueries = triplesMaps.flatMap(cm => {
                    val resultAux = this.transTP(tp, cm);
                    if (resultAux != null) {
                        Some(resultAux);
                    } else
                        None
                })

                if (unionOfQueries.size() == 0)
                    null;
                else if (unionOfQueries.size() == 1)
                    unionOfQueries.head;
                else if (unionOfQueries.size() > 1)
                    SQLUnion(unionOfQueries);
                else
                    null

            }
        }
        result
    }

    /**
     * Translation of a triple pattern into a SQL query based on a candidate triples map.
     */
    private def transTP(tp: Triple, cm: RDBR2RMLTriplesMap): ISqlQuery = {
        val tpPredicate = tp.getPredicate();

        val result: ISqlQuery = {
            if (tpPredicate.isURI()) {
                try {
                    val transTPResult = this.transTP(tp, cm, tpPredicate.getURI(), false);
                    transTPResult.toQuery(this.optimizer, databaseType);
                } catch {
                    case e: Exception => {
                        logger.debug("InsatisfiableSQLExpression for tp: " + tp);
                        null
                    }
                }
            } else if (tpPredicate.isVariable()) {
                val triplesMapResource = cm.resource;
                val tableMetaData = cm.getLogicalSource().tableMetaData;
                val tpBounder = new TriplePatternPredicateBounder(tableMetaData);
                val boundedTriplePatterns = tpBounder.expandUnboundedPredicateTriplePattern(tp, triplesMapResource);
                logger.debug("boundedTriplePatterns = " + boundedTriplePatterns);

                val pms = cm.predicateObjectMaps;
                if (boundedTriplePatterns.size() != pms.size()) {
                    logger.debug("boundedTriplePatterns.size() != pms.size()!");
                }

                if (pms != null && pms.size() > 0) {
                    val sqlQueries = pms.flatMap(pm => {
                        val propertyMappingResource = pm.resource;
                        val boundedTriplePatternErrorMessages = boundedTriplePatterns.get(propertyMappingResource);
                        // Strong assumption here: the predicate map are always constants!
                        val predicateURI = pm.getMappedPredicateName(0);
                        if (boundedTriplePatternErrorMessages == null || boundedTriplePatternErrorMessages.isEmpty()) {
                            try {
                                val transTPResult = this.transTP(tp, cm, predicateURI, true);
                                val sqlQuery = transTPResult.toQuery(this.optimizer, databaseType);
                                Some(sqlQuery);
                            } catch {
                                case e: Exception => {
                                    logger.warn("-- Insatifiable sql while translating : " + predicateURI + " in " + cm.getConceptName());
                                    logger.warn(e.getMessage());
                                    logger.warn(boundedTriplePatternErrorMessages);
                                    None
                                }
                            }
                        } else {
                            logger.warn("-- Insatifiable sql while translating : " + predicateURI + " in " + cm.getConceptName());
                            logger.warn(boundedTriplePatternErrorMessages);
                            None
                        }
                    })

                    if (sqlQueries.size() == 1) {
                        sqlQueries.head;
                    } else if (sqlQueries.size() > 1) {
                        SQLUnion(sqlQueries);
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                throw new Exception("invalid tp.predicate : " + tpPredicate);
            }
        }

        result;
    }

    /**
     * Translation of a triple pattern into an SQL query based on a candidate triples map
     * and a bounded (grounded) predicate.
     */
    private def transTP(tp: Triple, cm: RDBR2RMLTriplesMap, predicateURI: String, unboundedPredicate: Boolean): MorphTransTPResult = {

        // Find predicate-object maps with that specific predicate
        val pms = cm.getPropertyMappings(predicateURI);
        if (pms == null || pms.size() == 0 && !RDF.`type`.getURI().equalsIgnoreCase(predicateURI)) {
            val errorMessage = "Candidate selection error. Triples map + " + cm.name + " does not have mappings for predicate " + predicateURI;
            logger.error(errorMessage);
            throw new MorphException(errorMessage);
        }

        //alpha
        val alphaResult = this.alphaGenerator.calculateAlpha(tp, cm, predicateURI);
        if (alphaResult == null) {
            val errorMessage = "Undefined alpha mappings for predicate " + predicateURI;
            logger.error(errorMessage);
            throw new MorphException(errorMessage);
        }

        //PRSQL
        val prSQLResult = this.prSQLGenerator.genPRSQL(tp, alphaResult, this.betaGenerator, this.nameGenerator, cm, predicateURI, unboundedPredicate);

        //CondSQL
        val condSQLResult = this.condSQLGenerator.genCondSQL(tp, alphaResult, this.betaGenerator, cm, predicateURI, mapVarNotNull);

        // Translation to an actual SQL query
        val transTPResult = new MorphTransTPResult(alphaResult, condSQLResult, prSQLResult);
        transTPResult
    }

    private def transConstant(nodeValue: NodeValue): List[ZExp] = {

        val isLiteral = nodeValue.isLiteral();
        val isIRI = nodeValue.getNode().isURI();

        val result: List[ZExp] = {
            if (isLiteral) {
                List(this.transLiteral(nodeValue));
            } else if (isIRI) {
                this.transIRI(nodeValue.getNode());
            } else {
                logger.warn("unsupported nodevalue type in transConstant!");
                Nil;
            }
        }

        return result;
    }

    /**
     * For a node representing a URI, get the first (why?) triples map candidate of that node,
     * and return the list of values from the URI that match the columns in the template string
     * of the subject map of that triples map.
     */
    private def transIRI(node: Node): List[ZExp] = {
        val cms = mapInferredTMs(node);
        val cm = cms.iterator().next();
        val mapColumnsValues = cm.subjectMap.getTemplateValues(node.getURI());
        val result: List[ZExp] = {
            if (mapColumnsValues == null || mapColumnsValues.size() == 0) {
                //do nothing
                Nil
            } else {
                val resultAux = mapColumnsValues.keySet.map(column => {
                    val value = mapColumnsValues(column);
                    val constant = new ZConstant(value, ZConstant.UNKNOWN);
                    constant;
                })
                resultAux.toList;
            }
        }
        result;
    }

    private def transExpr(op: Op, expr: Expr, subOpSelectItems: List[ZSelectItem], prefix: String): List[ZExp] = {
        val result: List[ZExp] = {
            if (expr.isVariable()) {
                logger.debug("expr is var");
                val exprVar = expr.asVar();
                this.transVar(exprVar, subOpSelectItems, prefix);
            } else if (expr.isConstant()) {
                logger.debug("expr is constant");
                val nodeValue = expr.getConstant();
                this.transConstant(nodeValue);
            } else if (expr.isFunction()) {
                logger.debug("expr is function");
                val exprFunction = expr.getFunction();
                List(this.transFunction(op, exprFunction, subOpSelectItems, prefix));
            } else {
                Nil
            }
        }

        result;
    }

    private def transExprList(op: Op, exprList: ExprList, subOpSelectItems: List[ZSelectItem], prefix: String): ZExpression = {
        val exprs = exprList.getList();
        val resultAux = exprs.flatMap(expr => {
            val exprTranslated = this.transExpr(op, expr, subOpSelectItems, prefix);
            exprTranslated;
        })

        val result = MorphSQLUtility.combineExpresions(resultAux.toList, Constants.SQL_LOGICAL_OPERATOR_AND);
        result;
    }

    private def transFunction(op: Op, exprFunction: ExprFunction, subOpSelectItems: List[ZSelectItem], prefix: String): ZExp = {

        val args = exprFunction.getArgs();

        val result: ZExp = {
            exprFunction match {
                case _: ExprFunction1 => {
                    val arg = args.get(0);

                    val functionSymbol: String = exprFunction match {
                        case eBound: E_Bound => {
                            //functionSymbol = "IS NOT NULL";
                            functionsMap("E_Bound");
                        }
                        case eLogicalNot: E_LogicalNot => {
                            //functionSymbol = "NOT";
                            functionsMap("E_LogicalNot");
                        }
                        case _ => {
                            exprFunction.getOpName();
                        }
                    }

                    val argTranslated = this.transExpr(op, arg, subOpSelectItems, prefix);
                    val resultAuxs = argTranslated.map(operand => {
                        val resultAux = new ZExpression(functionSymbol);
                        resultAux.addOperand(operand);
                        resultAux;
                    })

                    MorphSQLUtility.combineExpresions(resultAuxs, Constants.SQL_LOGICAL_OPERATOR_AND);
                }
                case _: ExprFunction2 => {
                    val leftArg = args.get(0);
                    val rightArg = args.get(1);
                    val leftExprTranslated = this.transExpr(op, leftArg, subOpSelectItems, prefix);
                    val rightExprTranslated = this.transExpr(op, rightArg, subOpSelectItems, prefix);

                    exprFunction match {
                        case _: E_NotEquals => {
                            val functionSymbol = {
                                if (Constants.DATABASE_MONETDB.equalsIgnoreCase(databaseType)) {
                                    "<>";
                                } else {
                                    "!=";
                                }
                            }

                            val concatSymbol = {
                                if (Constants.DATABASE_POSTGRESQL.equalsIgnoreCase(databaseType)) {
                                    "||";
                                } else {
                                    "CONCAT";
                                }
                            }

                            val resultAux = new ZExpression(functionSymbol);

                            //concat only when it has multiple arguments
                            val leftExprTranslatedSize = leftExprTranslated.size();
                            if (leftExprTranslatedSize == 1) {
                                resultAux.addOperand(leftExprTranslated.get(0));
                            } else if (leftExprTranslatedSize > 1) {
                                val leftConcatOperand = new ZExpression(concatSymbol);
                                for (leftOperand <- leftExprTranslated) {
                                    leftOperand match {
                                        case leftOperandZConstant: ZConstant => {
                                            if (Constants.DATABASE_POSTGRESQL.equalsIgnoreCase(databaseType)) {
                                                val leftOperandValue = leftOperandZConstant.getValue();
                                                val leftOperandNew = MorphSQLConstant.apply(leftOperandValue, leftOperandZConstant.getType(), databaseType, "text");
                                                leftConcatOperand.addOperand(leftOperandNew);
                                            } else {
                                                leftConcatOperand.addOperand(leftOperand);
                                            }
                                        }
                                        case _ => {
                                            leftConcatOperand.addOperand(leftOperand);
                                        }
                                    }
                                }
                                resultAux.addOperand(leftConcatOperand);
                            }

                            val rightExprTranslatedSize = rightExprTranslated.size();
                            if (rightExprTranslatedSize == 1) {
                                resultAux.addOperand(rightExprTranslated.get(0));
                            } else if (rightExprTranslatedSize > 1) {
                                val rightConcatOperand = new ZExpression(concatSymbol);
                                for (rightOperand <- rightExprTranslated) {
                                    rightOperand match {
                                        case rightOperandZConstant: ZConstant => {
                                            if (Constants.DATABASE_POSTGRESQL.equalsIgnoreCase(databaseType)) {
                                                val rightOperandValue = rightOperandZConstant.getValue();
                                                val rightOperandNew = MorphSQLConstant.apply(rightOperandValue, rightOperandZConstant.getType(), databaseType, "text");
                                                rightConcatOperand.addOperand(rightOperandNew);
                                            } else {
                                                rightConcatOperand.addOperand(rightOperand);
                                            }
                                        }
                                        case _ => {
                                            rightConcatOperand.addOperand(rightOperand);
                                        }
                                    }
                                }
                                resultAux.addOperand(rightConcatOperand);
                            }

                            resultAux;
                        }
                        case _ => {
                            val functionSymbol: String = exprFunction match {
                                case _: E_LogicalAnd => {
                                    functionsMap("E_LogicalAnd");
                                }
                                case _: E_LogicalOr => {
                                    functionsMap("E_LogicalOr");
                                }
                                case _ => {
                                    exprFunction.getOpName();
                                }
                            }

                            val resultAuxs = for (i <- 0 until leftExprTranslated.size()) yield {
                                val resultAux = new ZExpression(functionSymbol);
                                val leftOperand = leftExprTranslated.get(i);
                                resultAux.addOperand(leftOperand);
                                val rightOperand = rightExprTranslated.get(i);
                                resultAux.addOperand(rightOperand);
                                resultAux;
                            }

                            MorphSQLUtility.combineExpresions(resultAuxs.toList, Constants.SQL_LOGICAL_OPERATOR_AND);
                        }
                    }
                }
                case eFunction: E_Function => {
                    val functionIRI = eFunction.getFunctionIRI();
                    val exprs = eFunction.getArgs();
                    if (exprs != null && exprs.size() == 1) {
                        val expr = exprs.get(0);
                        val resultAuxs = this.transExpr(op, expr, subOpSelectItems, prefix);
                        val resultAux = resultAuxs.get(0).toString();

                        if (functionIRI.equals(XSDDatatype.XSDinteger.getURI())) {
                            new ZConstant(resultAux, ZConstant.NUMBER);
                        } else if (functionIRI.equals(XSDDatatype.XSDdouble.getURI())) {
                            new ZConstant(resultAux, ZConstant.NUMBER);
                        } else if (functionIRI.equals(XSDDatatype.XSDdate.getURI())) {
                            new ZConstant(resultAux, ZConstant.UNKNOWN);
                        } else if (functionIRI.equals(XSDDatatype.XSDtime.getURI())) {
                            new ZConstant(resultAux, ZConstant.UNKNOWN);
                        } else if (functionIRI.equals(XSDDatatype.XSDdateTime.getURI())) {
                            new ZConstant(resultAux, ZConstant.UNKNOWN);
                        } else {
                            new ZConstant(resultAux, ZConstant.STRING);
                        }
                    } else {
                        val errorMessage = "unimplemented function";
                        logger.error(errorMessage);
                        null;
                    }
                }
                case _ => {
                    val transArgs = for (i <- 0 until args.size()) yield {
                        val arg = args.get(i);
                        val zExps = this.transExpr(op, arg, subOpSelectItems, prefix);
                        val transArg = zExps.map(zExp => {
                            if (exprFunction.isInstanceOf[E_Regex] && i == 1) {
                                val zExp2 = new ZConstant("%" + zExp.asInstanceOf[ZConstant].getValue() + "%", ZConstant.STRING);
                                zExp2;
                            } else
                                zExp;
                        })

                        transArg;
                    }

                    val functionSymbol = exprFunction match {
                        case _: E_Regex => { functionsMap("E_Regex"); } //functionSymbol = "LIKE";
                        case _: E_OneOf => { functionsMap("E_OneOf"); } //functionSymbol = "IN";
                        case _ => { exprFunction.getOpName(); }
                    }

                    val arg0Size = transArgs.get(0).size();
                    var resultAuxs = for (j <- 0 until arg0Size) yield {
                        val resultAux = new ZExpression(functionSymbol);
                        for (i <- 0 until args.size) {
                            val operand = transArgs.get(i).get(j);
                            resultAux.addOperand(operand);
                        }
                        resultAux;
                    }

                    MorphSQLUtility.combineExpresions(resultAuxs.toList, Constants.SQL_LOGICAL_OPERATOR_AND);
                }
            }
        }

        result;
    }

    private def transJoin(opParent: Op, gp1: Op, pGP2: Op, joinType: String): ISqlQuery = {
        logger.debug("entering transJoin");
        val selectItemGenerator = new MorphSQLSelectItemGenerator(this.nameGenerator, this.databaseType);
        val gp2 = opParent match {
            case opLefJoin: OpLeftJoin => {
                val opLeftJoinExpr = opLefJoin.getExprs();
                if (opLeftJoinExpr != null && opLeftJoinExpr.size() > 0) {
                    OpFilter.filterDirect(opLeftJoinExpr, pGP2);
                } else
                    pGP2
            }
            case _ => pGP2
        }

        val transGP1SQL = this.trans(gp1);
        val transGP2SQL = this.trans(gp2);

        if (transGP1SQL == null && transGP2SQL == null) {
            null;
        } else if (transGP1SQL != null && transGP2SQL == null) {
            transGP1SQL;
        } else if (transGP1SQL == null && transGP2SQL != null) {
            transGP2SQL;
        } else {
            val gp1SelectItems = transGP1SQL.getSelectItems().toList;
            MorphSQLUtility.setDefaultAlias(gp1SelectItems);

            val gp2SelectItems = transGP2SQL.getSelectItems().toList;
            MorphSQLUtility.setDefaultAlias(gp2SelectItems);

            val transGP1Alias = transGP1SQL.generateAlias();
            val transGP1FromItem = {
                if (this.optimizer != null && this.optimizer.subQueryAsView) {
                    val conn = this.connection;
                    val subQueryViewName = "sql" + Math.abs(gp1.hashCode());
                    val dropViewSQL = "DROP VIEW IF EXISTS " + subQueryViewName;
                    logger.info(dropViewSQL + ";\n");
                    DBUtility.execute(conn, dropViewSQL, 0);
                    val createViewSQL = "CREATE VIEW " + subQueryViewName + " AS " + transGP1SQL;
                    logger.info(createViewSQL + ";\n");
                    DBUtility.execute(conn, createViewSQL, 0);
                    SQLFromItem(subQueryViewName, Constants.LogicalTableType.TABLE_NAME, this.databaseType);
                } else {
                    SQLFromItem(transGP1SQL.toString(), Constants.LogicalTableType.QUERY, this.databaseType);
                }
            }
            transGP1FromItem.setAlias(transGP1Alias);

            val transGP2Alias = transGP2SQL.generateAlias();
            val transGP2FromItem = {
                if (this.optimizer != null && this.optimizer.subQueryAsView) {
                    val conn = this.connection;
                    val subQueryViewName = "sqr" + Math.abs(gp2.hashCode());
                    val dropViewSQL = "DROP VIEW IF EXISTS " + subQueryViewName;
                    logger.info(dropViewSQL + ";\n");
                    DBUtility.execute(conn, dropViewSQL, 0);
                    val createViewSQL = "CREATE VIEW " + subQueryViewName + " AS " + transGP2SQL;
                    logger.info(createViewSQL + ";\n");
                    DBUtility.execute(conn, createViewSQL, 0);
                    SQLFromItem(subQueryViewName, Constants.LogicalTableType.TABLE_NAME, this.databaseType);
                } else {
                    SQLFromItem(transGP2SQL.toString(), Constants.LogicalTableType.QUERY, this.databaseType);
                }
            }
            transGP2FromItem.setAlias(transGP2Alias);

            val termsGP1 = MorphQueryTranslatorUtility.getTerms(gp1);
            val termsGP2 = MorphQueryTranslatorUtility.getTerms(gp2);
            val termsA = termsGP1.diff(termsGP2);
            val termsB = termsGP2.diff(termsGP1)
            val termsC = termsGP1.intersect(termsGP2);

            val mappingsSelectItemA = this.generateMappingIdSelectItems(termsA.toList, gp1SelectItems, transGP1Alias, this.databaseType);
            val mappingsSelectItemB = this.generateMappingIdSelectItems(termsB.toList, gp2SelectItems, transGP2Alias, this.databaseType);
            val mappingsSelectItemC = this.generateMappingIdSelectItems(termsC.toList, gp1SelectItems, transGP1Alias, this.databaseType);

            val mappingsSelectItems = mappingsSelectItemA ::: mappingsSelectItemB ::: mappingsSelectItemC;
            MorphSQLUtility.setDefaultAlias(mappingsSelectItems);

            val selectItemsA = selectItemGenerator.generateSelectItems(
                termsA, transGP1Alias, gp1SelectItems, false).toList;
            //MorphSQLUtility.setDefaultAlias(selectItemsA);
            val selectItemsB = selectItemGenerator.generateSelectItems(
                termsB, transGP2Alias, gp2SelectItems, false).toList;
            //MorphSQLUtility.setDefaultAlias(selectItemsB);
            val selectItemsC = selectItemGenerator.generateSelectItems(
                termsC, transGP1Alias, gp1SelectItems, false).toList;
            //MorphSQLUtility.setDefaultAlias(selectItemsC);

            val selectItems = selectItemsA ::: selectItemsB ::: selectItemsC;
            MorphSQLUtility.setDefaultAlias(selectItems);

            //.... JOIN ... ON <joinOnExpression>
            val joinOnExps = termsC.flatMap(termC => {
                val isTermCInSubjectGP1 = SparqlUtility.isNodeInSubjectGraph(termC, gp1);
                val isTermCInSubjectGP2 = SparqlUtility.isNodeInSubjectGraph(termC, gp2);

                if (termC.isVariable()) {
                    val termCColumns1 = this.getColumnsByNode(termC, gp1SelectItems);
                    val termCColumns2 = this.getColumnsByNode(termC, gp2SelectItems);

                    if (termCColumns1.size() == termCColumns2.size()) {
                        //val termCColumns1Iterator = termCColumns1.iterator();
                        val termCColumns2Iterator = termCColumns2.iterator();

                        val expsAux = for (termCColumn1 <- termCColumns1) yield {
                            val termCColumn2 = termCColumns2Iterator.next();
                            val gp1TermC = MorphSQLConstant.apply(transGP1Alias + "." + termCColumn1, ZConstant.COLUMNNAME, this.databaseType, null);
                            val gp2TermC = MorphSQLConstant.apply(transGP2Alias + "." + termCColumn2, ZConstant.COLUMNNAME, this.databaseType, null);
                            val exp1Aux = new ZExpression("=", gp1TermC, gp2TermC);
                            val exp2Aux = {
                                if (!isTermCInSubjectGP1 && !(gp1.isInstanceOf[OpBGP])) {
                                    new ZExpression("IS NULL", gp1TermC);
                                } else { null }
                            }
                            val exp3Aux = {
                                if (!isTermCInSubjectGP2 && !(gp2.isInstanceOf[OpBGP])) {
                                    new ZExpression("IS NULL", gp2TermC);
                                } else { null }
                            }

                            (exp1Aux, exp2Aux, exp3Aux)
                        }
                        val exps1Aux = expsAux.toList.map(x => { x._1 });
                        val exps2Aux = expsAux.toList.flatMap(x => {
                            if (x._2 == null) { None } else { Some(x._2) }
                        });
                        val exps3Aux = expsAux.toList.flatMap(x => {
                            if (x._3 == null) { None } else { Some(x._3) }
                        });

                        val exp1 = MorphSQLUtility.combineExpresions(exps1Aux, Constants.SQL_LOGICAL_OPERATOR_AND);
                        val exp2 = MorphSQLUtility.combineExpresions(exps2Aux, Constants.SQL_LOGICAL_OPERATOR_AND);
                        val exp3 = MorphSQLUtility.combineExpresions(exps3Aux, Constants.SQL_LOGICAL_OPERATOR_AND);

                        if (exps2Aux.isEmpty() && exps3Aux.isEmpty()) {
                            if (exp1 != null) {
                                Some(exp1);
                            } else { None }
                        } else {
                            val exp123 = new ZExpression(Constants.SQL_LOGICAL_OPERATOR_OR);
                            if (exp1 != null) {
                                exp123.addOperand(exp1);
                            }

                            if (!isTermCInSubjectGP1 && exp2 != null) {
                                exp123.addOperand(exp2);
                            }

                            if (!isTermCInSubjectGP2 && exp3 != null) {
                                exp123.addOperand(exp3);
                            }

                            Some(exp123);
                        }
                    } else { None }
                } else { None }
            })

            //			if(joinOnExps == null || joinOnExps.size() == 0) {
            //				joinOnExps.add(Constants.SQL_EXPRESSION_TRUE);
            //			}
            val joinOnExpression = {
                if (joinOnExps == null || joinOnExps.size() == 0) {
                    Constants.SQL_EXPRESSION_TRUE;
                } else {
                    MorphSQLUtility.combineExpresions(joinOnExps.toList, Constants.SQL_LOGICAL_OPERATOR_AND);
                }
            }

            val transJoin: ISqlQuery = {
                if (this.optimizer != null) {
                    val isTransJoinSubQueryElimination =
                        this.optimizer.transJoinSubQueryElimination;
                    if (isTransJoinSubQueryElimination) {
                        try {
                            if (transGP1SQL.isInstanceOf[SQLQuery] && transGP2SQL.isInstanceOf[SQLQuery]) {
                                SQLQuery.create(selectItems ::: mappingsSelectItems, transGP1SQL, transGP2SQL, joinType, joinOnExpression, this.databaseType);
                            } else {
                                null
                            }
                        } catch {
                            case e: Exception => {
                                e.printStackTrace();
                                val errorMessage = "error while eliminating subquery in transjoin: " + e.getMessage();
                                logger.error(errorMessage);
                                null;
                            }
                        }
                    } else { null }
                } else { null }
            }

            val result = {
                if (transJoin == null) { //subquery not eliminated
                    val table1 = new SQLJoinTable(transGP1SQL, null, null);
                    table1.setAlias(transGP1Alias);
                    val table2 = new SQLJoinTable(transGP2SQL, joinType, joinOnExpression);
                    table2.setAlias(transGP2Alias);

                    val transJoinAux = new SQLQuery();
                    transJoinAux.setSelectItems(selectItems);
                    transJoinAux.addSelectItems(mappingsSelectItems);
                    transJoinAux.addFromItem(table1);
                    transJoinAux.addFromItem(table2);
                    transJoinAux;
                } else {
                    transJoin
                }
            }
            result;
        }
    }

    private def transLiteral(nodeValue: NodeValue): ZExp = {
        val result = {
            if (nodeValue.isNumber()) {
                val nodeValueDouble = nodeValue.getDouble();
                new ZConstant(nodeValueDouble + "", ZConstant.NUMBER);
            } else if (nodeValue.isString()) {
                val nodeValueString = nodeValue.getString();
                new ZConstant(nodeValueString + "", ZConstant.STRING);
            } else if (nodeValue.isDate() || nodeValue.isDateTime()) {
                val nodeValueDateTimeString = nodeValue.getDateTime().toString().replaceAll("T", " ");
                new ZConstant(nodeValueDateTimeString, ZConstant.STRING);
            } else if (nodeValue.isLiteral()) {
                val node = nodeValue.getNode();
                val literalLexicalForm = node.getLiteralLexicalForm();
                val literalDatatypeURI = node.getLiteralDatatypeURI();
                if (XSD.date.toString().equals(literalDatatypeURI) || XSD.dateTime.equals(literalDatatypeURI)) {
                    val literalValueString = literalLexicalForm.trim().replaceAll("T", " ");
                    new ZConstant(literalValueString, ZConstant.STRING);
                } else {
                    new ZConstant(literalLexicalForm.toString(), ZConstant.STRING);
                }
            } else {
                new ZConstant(nodeValue.toString(), ZConstant.STRING);
            }
        }

        result;
    }

    private def transSTGUnionFree(stg: List[Triple]): MorphTransTPResult = {
        val stgSubject = stg.get(0).getSubject();
        val cms = this.mapInferredTMs(stgSubject);
        val cm = cms.toList.head;
        this.transSTGUnionFree(stg, cm);
    }

    private def transSTG(stg: List[Triple]): ISqlQuery = {
        val stgSubject = stg.get(0).getSubject();
        val cmsAux = this.mapInferredTMs(stgSubject).toList;
        val cms = {
            if (cmsAux == null) {
                val errorMessage = "Undefined triplesMap for stg : " + stg;
                logger.warn(errorMessage);
                val errorMessage2 = "All class mappings will be used.";
                logger.warn(errorMessage2);
                val allCms = this.mappingDocument.triplesMaps.toList;
                if (allCms == null || allCms.size() == 0) {
                    val errorMessage3 = "Mapping document doesn't contain any class mappings!";
                    logger.error(errorMessage3);
                    null
                } else {
                    allCms
                }
            } else {
                cmsAux
            }
        }

        val resultAux = cms.map(cm => { this.transSTG(stg, cm); })

        val result = {
            if (resultAux.size() == 1) { resultAux.head; }
            else if (cms.size() > 1) { SQLUnion(resultAux); }
            else { null }
        }
        result;
    }

    private def transSTGUnionFree(stg: List[Triple], cm: RDBR2RMLTriplesMap): MorphTransTPResult = {
        //AlphaSTG
        val alphaResultUnionList = this.alphaGenerator.calculateAlphaSTG(stg, cm);

        //ALPHA(stg) returns the same result for subject
        //val alphaResult = alphaResultUnionList.head.get(0);
        val alphaSubject = alphaResultUnionList.head.get(0).alphaSubject;
        val alphaPredicateObjects = alphaResultUnionList.flatMap(alphaTP => {
            val tpAlphaPredicateObjects = alphaTP.get(0).alphaPredicateObjects;
            tpAlphaPredicateObjects;
        }).toList
        val alphaResult = new MorphAlphaResult(alphaSubject, alphaPredicateObjects);

        //PRSQLSTG
        val prSQLSTGResult = this.prSQLGenerator.genPRSQLSTG(stg, alphaResult, betaGenerator, nameGenerator, cm);

        //CondSQLSTG
        val condSQLSQLResult = this.condSQLGenerator.genCondSQLSTG(
            stg, alphaResult, betaGenerator, cm, mapVarNotNull);

        val transTPResult = new MorphTransTPResult(alphaResult, condSQLSQLResult, prSQLSTGResult)
        transTPResult
    }

    private def transSTG(stg: List[Triple], cm: RDBR2RMLTriplesMap): ISqlQuery = {
        //AlphaSTG
        val alphaResultUnionList = this.alphaGenerator.calculateAlphaSTG(stg, cm);

        //check if no union in each of alpha tp
        val unionFree = !alphaResultUnionList.exists(alphaTP => alphaTP.size() > 1);

        val transSTG = {
            if (!unionFree) {
                val basicPatternHead = new BasicPattern();
                basicPatternHead.add(stg.head);
                val opBGPHead = new OpBGP(basicPatternHead);

                val basicPatternTail = BasicPattern.wrap(stg.tail);
                val opBGPTail = new OpBGP(basicPatternTail);

                val opJoin = OpJoin.create(opBGPHead, opBGPTail);
                this.trans(opJoin);
            } else {
                val transSTGResult = this.transSTGUnionFree(stg, cm);
                transSTGResult.toQuery(this.optimizer, databaseType);
            }
        }

        logger.debug("transSTG = " + transSTG);
        transSTG;
    }

    private def transVar(theVar: Var, subOpSelectItems: List[ZSelectItem], prefix: String): List[ZExp] = {
        val columns = this.getColumnsByNode(theVar, subOpSelectItems);
        val result = columns.map(column => {
            val columnName = {
                if (prefix == null) {
                    column;
                } else {
                    if (!prefix.endsWith(".")) {
                        prefix + "." + column;
                    } else {
                        prefix + column;
                    }
                }
            }

            val constant = MorphSQLConstant.apply(columnName, ZConstant.COLUMNNAME, this.databaseType, null);
            constant;
        })

        result.toList;
    }

    private def translateUpdate(stg: OpBGP) = {
        val isSTG = MorphQueryTranslatorUtility.isSTG(stg);
        if (!isSTG) {
            val errorMessage = "Only STG pattern is supported for update operation!"
            logger.error(errorMessage);
            throw new Exception(errorMessage);
        }

        val typeInferrer = new MorphMappingInferrer(this.mappingDocument);
        this.mapInferredTMs = typeInferrer.infer(stg);
        logger.info("Inferred Types : \n" + typeInferrer.printInferredTypes());
        val triples = stg.getPattern().getList().toList;
        val transTPResult = this.transSTGUnionFree(triples);
        transTPResult.toUpdate;
    }

    private def translateInsert(stg: OpBGP) = {
        val isSTG = MorphQueryTranslatorUtility.isSTG(stg);
        if (!isSTG) {
            val errorMessage = "Only STG pattern is supported for insert operation!"
            logger.error(errorMessage);
            throw new Exception(errorMessage);
        }

        val typeInferrer = new MorphMappingInferrer(this.mappingDocument);
        this.mapInferredTMs = typeInferrer.infer(stg);
        logger.info("Inferred Types : \n" + typeInferrer.printInferredTypes());
        val triples = stg.getPattern().getList().toList;
        val transTPResult = this.transSTGUnionFree(triples);
        transTPResult.toInsert;
    }

    private def translateDelete(stg: OpBGP) = {
        val isSTG = MorphQueryTranslatorUtility.isSTG(stg);
        if (!isSTG && stg.getPattern().getList().size() > 1) {
            val errorMessage = "Only STG pattern is supported for delete operation!"
            logger.error(errorMessage);
            throw new Exception(errorMessage);
        }

        val typeInferrer = new MorphMappingInferrer(this.mappingDocument);
        this.mapInferredTMs = typeInferrer.infer(stg);
        logger.info("Inferred Types : \n" + typeInferrer.printInferredTypes());
        val triples = stg.getPattern().getList().toList;
        val transTPResult = this.transSTGUnionFree(triples);
        transTPResult.toDelete;
    }

    /**
     * Create a not null condition for each variable in the subject or object positions
     * of triple pattern of a SPARQL query
     */
    private def initializeMapVarIsNullable(op: Op): Unit = {
        op match {
            case bgp: OpBGP => {
                val triples = bgp.getPattern().getList();
                for (tp <- triples) {
                    val tpSubject = tp.getSubject();
                    if (tpSubject.isVariable()) {
                        this.mapVarNotNull += (tpSubject -> true);
                    }
                    val tpObject = tp.getObject();
                    if (tpObject.isVariable()) {
                        this.mapVarNotNull += (tpObject -> true);
                    }
                }
            }
            case opJoin: OpJoin => { // AND pattern
                this.initializeMapVarIsNullable(opJoin.getLeft())
                this.initializeMapVarIsNullable(opJoin.getRight())
            }
            case opLeftJoin: OpLeftJoin => { //OPT pattern
                this.initializeMapVarIsNullable(opLeftJoin.getLeft())
                this.initializeMapVarIsNullable(opLeftJoin.getRight())
            }
            case opUnion: OpUnion => { //UNION pattern
                this.initializeMapVarIsNullable(opUnion.getLeft())
                this.initializeMapVarIsNullable(opUnion.getRight())
            }
            case opFilter: OpFilter => { //FILTER pattern
                val subOpRewritten = this.initializeMapVarIsNullable(opFilter.getSubOp)
            }
            case opProject: OpProject => {
                val subOpRewritten = this.initializeMapVarIsNullable(opProject.getSubOp)
            }
            case opSlice: OpSlice => {
                val subOpRewritten = this.initializeMapVarIsNullable(opSlice.getSubOp)
            }
            case opDistinct: OpDistinct => {
                val subOpRewritten = this.initializeMapVarIsNullable(opDistinct.getSubOp)
            }
            case opOrder: OpOrder => {
                val subOpRewritten = this.initializeMapVarIsNullable(opOrder.getSubOp)
            }
            case _ => {
                //op;
            }
        }
    }

    private def generateMappingIdSelectItems(nodes: List[Node], selectItems: List[ZSelectItem], pPrefix: String, dbType: String): List[ZSelectItem] = {
        val prefix = {
            if (pPrefix == null) {
                "";
            } else {
                if (!pPrefix.endsWith(".")) {
                    pPrefix + ".";
                } else {
                    pPrefix
                }
            }
        }

        val result = nodes.map(term => {
            if (term.isVariable()) {
                val mappingsSelectItemsAux = MorphSQLUtility.getSelectItemsMapPrefix(
                    selectItems, term, prefix, dbType);
                mappingsSelectItemsAux.map(mappingsSelectItemAux => {
                    val mappingSelectItemAuxAlias = mappingsSelectItemAux.getAlias();
                    val newSelectItem = MorphSQLSelectItem.apply(
                        mappingSelectItemAuxAlias, prefix, dbType, null);
                    newSelectItem;
                })
            } else {
                Nil
            }
        })

        result.flatten;
    }
}
