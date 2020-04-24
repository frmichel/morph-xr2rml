package es.upm.fi.dia.oeg.morph.base

import java.io.StringWriter
import java.io.Writer

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.xml.serialize.OutputFormat
import org.apache.xml.serialize.XMLSerializer
import org.w3c.dom.Document

import javax.xml.parsers.DocumentBuilderFactory

class XMLUtility {
}

object XMLUtility {
    def createNewXMLDocument(): Document = {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance();
        val documentBuilder = documentBuilderFactory.newDocumentBuilder();
        val document = documentBuilder.newDocument();
        document;
    }

    /**
     * This method uses Xerces specific classes
     * prints the XML document to file.
     */
    def saveXMLDocument(document: Document, outputStream: Writer) = {
        try {
            //print
            val format = new OutputFormat(document);
            format.setIndenting(true);

            //to generate output to console use this serializer
            //XMLSerializer serializer = new XMLSerializer(System.out, format);

            //to generate a file output use fileoutputstream instead of system.out
            val serializer = new XMLSerializer(outputStream, format);
            serializer.serialize(document);
        } catch {
            case e: Exception => {
                e.printStackTrace();
            }
        }
    }

    def printXMLDocument(document: Document, indenting: Boolean, omitXMLDeclaration: Boolean): String = {
        val writer = new StringWriter();
        val format = new OutputFormat(document);
        format.setIndenting(indenting);
        format.setOmitXMLDeclaration(omitXMLDeclaration);

        val serializer = new XMLSerializer(writer, format);
        serializer.serialize(document);
        val inputString = writer.toString();
        inputString;
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