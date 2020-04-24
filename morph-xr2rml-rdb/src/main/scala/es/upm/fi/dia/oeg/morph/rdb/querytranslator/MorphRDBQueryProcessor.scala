package es.upm.fi.dia.oeg.morph.rdb.querytranslator

import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.jena.query.Query
import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.base.MorphBaseResultSet
import es.upm.fi.dia.oeg.morph.base.TemplateUtility
import es.upm.fi.dia.oeg.morph.base.TermMapResult
import es.upm.fi.dia.oeg.morph.base.XMLUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import es.upm.fi.dia.oeg.morph.base.query.AbstractQuery
import es.upm.fi.dia.oeg.morph.base.query.GenericQuery
import es.upm.fi.dia.oeg.morph.base.querytranslator.MorphBaseQueryProcessor
import es.upm.fi.dia.oeg.morph.base.querytranslator.SparqlResultSetXml
import es.upm.fi.dia.oeg.morph.base.sql.ISqlQuery
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLRefObjectMap
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTermMap
import es.upm.fi.dia.oeg.morph.rdb.engine.MorphRDBResultSet

class MorphRDBQueryProcessor(factory: IMorphFactory) extends MorphBaseQueryProcessor(factory) {

    val logger = Logger.getLogger(this.getClass().getName());

    private var mapTemplateMatcher: Map[String, Matcher] = Map.empty;

    private var mapTemplateAttributes: Map[String, List[String]] = Map.empty;

    var projectionGenerator = factory.getQueryTranslator.asInstanceOf[MorphRDBQueryTranslator].getPRSQLGen

    /**
     * Execute the query and translate the results from the database into triples.<br>
     * In the RDB case the AbstractQuery should contain only one element.<br>
     *
     * @param sparqlQuery the original SPARQL query, needed to format the response
     * @param abstractQuery associated AbstractQuery resulting from the translation of sparqlQuery,
     * in which the executable target queries have been computed
     * @param syntax the output syntax:  XML or JSON for a SPARQL SELECT or ASK query, and RDF
     * syntax for a SPARQL DESCRIBE or CONSTRUCT query
     *
     */
    override def process(sparqlQuery: Query, abstractQuery: Option[AbstractQuery], syntax: String): Option[File] = {
        val start = System.currentTimeMillis();

        // Decide the output file
        var output: Option[File] =
            if (!abstractQuery.isDefined)
                None
            else if (factory.getProperties.serverActive)
                GeneralUtility.createRandomFile("", factory.getProperties.outputFilePath + ".", "")
            else Some(new File(factory.getProperties.outputFilePath))

        if (output.isDefined && abstractQuery.isDefined) {
            // In the RDB case the abstract query should just contain one GenericQuery
            val genQuery = abstractQuery.get.targetQuery(0).asInstanceOf[GenericQuery]
            val iQuery = genQuery.concreteQuery.asInstanceOf[ISqlQuery]

            // Execution of the concrete SQL query against the database
            val resultSet = factory.getDataSourceReader.execute(genQuery, None).asInstanceOf[MorphRDBResultSet];
            val columnNames = iQuery.getSelectItemAliases();
            resultSet.setColumnNames(columnNames);

            // Initialize the XML document and build the content
            val xmlResult = SparqlResultSetXml(factory, sparqlQuery)
            this.makeBody(xmlResult, sparqlQuery, resultSet)

            // Write the XML result set to the output
            xmlResult.save(output.get)
            logger.info("Time for database query execution and result generation = " + (System.currentTimeMillis - start) + "ms.")
        }

        output
    }

    /**
     * Create the body of the SPARQL result set
     * <pre>
     * &lt;result&gt;
     * 	 &lt;binding name="var1"&gt; &lt;uri&gt;...&lt;/uri&gt; &lt;/binding&gt;
     * 	 &lt;binding name="var2"&gt; &lt;literal&gt;...&lt;/literal&gt; &lt;/binding&gt;
     *   ...
     * &lt;/result&gt;
     * ...
     * </pre>
     */
    private def makeBody(xmlResultSet: SparqlResultSetXml, sparqlQuery: Query, absResultSet: MorphBaseResultSet) = {
        var i = 0;

        val xmlDoc = xmlResultSet.getDoc
        val results = xmlDoc.createElement("results")
        val rootElement = xmlDoc.getElementsByTagName("sparql").item(0)
        rootElement.appendChild(results)

        val resultSet = absResultSet.asInstanceOf[MorphRDBResultSet]

        while (resultSet.next()) {

            val result = xmlDoc.createElement("result")
            results.appendChild(result)

            for (varName <- sparqlQuery.getResultVars()) {
                val translatedColVal = this.translateResultSet(varName, resultSet);
                if (translatedColVal != null) {
                    val lexicalValue = XMLUtility.transformToLexical(translatedColVal.translatedValue, translatedColVal.xsdDatatype)
                    if (lexicalValue != null) {
                        val bindingElt = xmlDoc.createElement("binding");
                        bindingElt.setAttribute("name", varName);
                        result.appendChild(bindingElt);
                        val termType = translatedColVal.termType;
                        if (termType != null) {
                            val termTypeEltName = {
                                if (termType.equalsIgnoreCase(Constants.R2RML_IRI_URI)) "uri";
                                else if (termType.equalsIgnoreCase(Constants.R2RML_LITERAL_URI)) "literal";
                                else null // what if this happens? Is it the case of a blank node?
                            }
                            if (termTypeEltName != null) {
                                val termTypeElt = xmlDoc.createElement(termTypeEltName);
                                bindingElt.appendChild(termTypeElt);
                                termTypeElt.setTextContent(lexicalValue);
                            }
                        } else
                            bindingElt.setTextContent(lexicalValue);
                    }
                }
            }
            i = i + 1;
        }
        logger.info(i + " variable result mappings retrieved")
        println(XMLUtility.printXMLDocument(xmlDoc, true, false))
    }

    private def translateResultSet(varName: String, absResultSet: MorphBaseResultSet): TermMapResult = {
        val resultSet = absResultSet.asInstanceOf[MorphRDBResultSet]
        val result: TermMapResult = {
            try {
                if (resultSet != null) {
                    // Retrieve the columns from the result set, that match this SPARQL variable
                    val columnNames = this.getElementsStartWith(resultSet.getColumnNames(), varName + "_");

                    // Retrieve the values, from the result set, that match this SPARQL variable
                    val mapValue = this.getMappedMappingByVarName(varName, resultSet);

                    if (!mapValue.isDefined) {
                        val originalValue = resultSet.getString(varName);
                        new TermMapResult(originalValue, null, None)
                    } else {
                        val termMap: R2RMLTermMap = {
                            mapValue.get match {
                                case mappedValueTermMap: R2RMLTermMap => { mappedValueTermMap }
                                case mappedValueRefObjectMap: R2RMLRefObjectMap => {
                                    val parentTriplesMap = factory.getMappingDocument.getParentTriplesMap(mappedValueRefObjectMap);
                                    parentTriplesMap.subjectMap;
                                }
                                case _ => {
                                    logger.error("Undefined term map type");
                                    null
                                }
                            }
                        }

                        val resultAux = {
                            if (termMap != null) {
                                termMap.termMapType match {
                                    case Constants.MorphTermMapType.TemplateTermMap => {
                                        val templateString = termMap.getTemplateString();
                                        if (!this.mapTemplateMatcher.contains(templateString)) {
                                            val pattern = Pattern.compile(Constants.R2RML_TEMPLATE_PATTERN);
                                            val matcher = pattern.matcher(templateString);
                                            this.mapTemplateMatcher += (templateString -> matcher);
                                        }

                                        val templateAttributes = {
                                            if (this.mapTemplateAttributes.contains(templateString)) {
                                                this.mapTemplateAttributes(templateString);
                                            } else {
                                                val templateAttributesAux = TemplateUtility.getTemplateColumns(templateString);
                                                this.mapTemplateAttributes += (templateString -> templateAttributesAux);
                                                templateAttributesAux;
                                            }
                                        }

                                        var i = 0;
                                        val replaceMentAux = templateAttributes.map(templateAttribute => {
                                            val columnName = {
                                                if (columnNames == null || columnNames.isEmpty) {
                                                    varName;
                                                } else {
                                                    varName + "_" + i;
                                                }
                                            }
                                            i = i + 1;

                                            val dbValue = resultSet.getString(columnName);
                                            templateAttribute -> dbValue;
                                        })
                                        val replacements = replaceMentAux.toMap;

                                        val templateResult = if (replacements.size > 0) {
                                            TemplateUtility.replaceTemplateGroups(templateString, List(List(replacements)))
                                        } else {
                                            logger.debug("no replacements found for the R2RML template!");
                                            null;
                                        }
                                        // Changed this from templateResult to templateResult(0) because I changed return type of replaceTemplateGroups
                                        // Side effects not guaranteed!
                                        templateResult(0);
                                    }

                                    case Constants.MorphTermMapType.ColumnTermMap | Constants.MorphTermMapType.ReferenceTermMap => {
                                        val rsObjectVarName = resultSet.getObject(varName).toString()
                                        if (rsObjectVarName == null)
                                            null
                                        else
                                            rsObjectVarName.toString();
                                    }

                                    case Constants.MorphTermMapType.ConstantTermMap => {
                                        termMap.getConstantValue();
                                    }
                                    case _ => {
                                        logger.error("Unsupported term map type!");
                                        null;
                                    }
                                }
                            } else
                                null;
                        }

                        val termMapType = termMap.inferTermType;
                        val resultAuxString = {
                            if (resultAux != null) {
                                if (termMapType != null) {
                                    if (termMapType.equals(Constants.R2RML_IRI_URI))
                                        GeneralUtility.encodeURI(resultAux, null) // factory.getProperties.mapURIEncodingChars
                                    else if (termMapType.equals(Constants.R2RML_LITERAL_URI))
                                        GeneralUtility.encodeLiteral(resultAux);
                                    else
                                        resultAux
                                } else {
                                    resultAux
                                }
                            } else {
                                null
                            }
                        }
                        new TermMapResult(resultAuxString, termMapType, termMap.datatype);
                    }
                } else {
                    null
                }
            } catch {
                case e: Exception => {
                    logger.debug("Error occured while translating result set : " + e.getMessage());
                    null;
                }
            }
        }
        result;
    }

    /**
     * Retrieve the column names that start like the SPARQL variable name =&gt; the columns from the result set,
     * that correspond to that SPARQL variable
     */
    private def getElementsStartWith(columnNames: List[String], varName: String): List[String] = {
        val result = columnNames.flatMap(columnName => {
            if (columnName.startsWith(varName))
                Some(columnName);
            else
                None;
        })
        result;
    }

    /**
     * Retrieve the value of the column corresponding to the SPARQL variable name from the result set
     */
    private def getMappedMappingByVarName(varName: String, absResultSet: MorphBaseResultSet) = {
        val resultSet = absResultSet.asInstanceOf[MorphRDBResultSet]
        try {
            val mappingHashCode = resultSet.getInt(Constants.PREFIX_MAPPING_ID + varName);

            //IN CASE OF UNION, A VARIABLE MAY BE MAPPED TO MULTIPLE MAPPINGS
            if (mappingHashCode == null)
                this.projectionGenerator.getMappedMapping(varName.hashCode())
            else
                this.projectionGenerator.getMappedMapping(mappingHashCode)
        } catch {
            case e: Exception => {
                null
            }
        }
    }
}