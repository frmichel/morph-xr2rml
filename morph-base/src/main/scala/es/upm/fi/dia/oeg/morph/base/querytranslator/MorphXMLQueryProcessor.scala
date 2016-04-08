package es.upm.fi.dia.oeg.morph.base.querytranslator

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.log4j.Logger

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype
import com.hp.hpl.jena.query.Query

import es.upm.fi.dia.oeg.morph.base.XMLUtility
import es.upm.fi.dia.oeg.morph.base.engine.IMorphFactory
import java.io.OutputStreamWriter
import java.io.PrintWriter

abstract class MorphXMLQueryProcessor(factory: IMorphFactory)
        extends MorphBaseQueryProcessor(factory) {

    val logger = Logger.getLogger(this.getClass().getName());

    val xmlDoc = XMLUtility.createNewXMLDocument();
    val resultsElement = xmlDoc.createElement("results");

    /**
     * Initialize the XML tree for the SPARQL result set with one variable node
     * for each projected variable in  the SPARQL query
     * <pre>
     * 	<sparql>
     *  	<head>
     *   		</variable name="var1>
     *   		</variable name="var2>
     *     		...
     *  	</head>
     * 	</sparql>
     * </pre>
     */
    override def preProcess(sparqlQuery: Query) = {
        //create root element
        val rootElement = xmlDoc.createElement("sparql");
        xmlDoc.appendChild(rootElement);

        //create head element
        val headElement = xmlDoc.createElement("head");
        rootElement.appendChild(headElement);
        val varNames = sparqlQuery.getResultVars();
        for (varName <- varNames) {
            val variableElement = xmlDoc.createElement("variable");
            variableElement.setAttribute("name", varName);
            headElement.appendChild(variableElement);
        }

        //create results element
        rootElement.appendChild(resultsElement);
    }

    /**
     * Save the XML document to a file
     */
    override def postProcess() = {
        val writer = new PrintWriter(factory.getProperties.outputFilePath, "UTF-8")
        XMLUtility.saveXMLDocument(xmlDoc, writer)
    }

    override def getOutput() = {
        this.xmlDoc;
    }

    def transformToLexical(originalValue: String, pDatatype: Option[String]): String = {
        if (pDatatype.isDefined && originalValue != null) {
            val datatype = pDatatype.get;
            val xsdDateTimeURI = XSDDatatype.XSDdateTime.getURI().toString();
            val xsdBooleanURI = XSDDatatype.XSDboolean.getURI().toString();

            if (datatype.equals(xsdDateTimeURI)) {
                originalValue.trim().replaceAll(" ", "T");
            } else if (datatype.equals(xsdBooleanURI)) {
                if (originalValue.equalsIgnoreCase("T") || originalValue.equalsIgnoreCase("True")) {
                    "true";
                } else if (originalValue.equalsIgnoreCase("F") || originalValue.equalsIgnoreCase("False")) {
                    "false";
                } else {
                    "false";
                }
            } else
                originalValue
        } else
            originalValue
    }
}