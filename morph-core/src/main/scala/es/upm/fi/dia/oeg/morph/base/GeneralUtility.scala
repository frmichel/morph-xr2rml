package es.upm.fi.dia.oeg.morph.base

import java.io.File
import java.net.URL
import java.util.regex.Pattern

import scala.util.Random

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import org.apache.log4j.Logger

object GeneralUtility {
    val logger = Logger.getLogger("GeneralUtility");

    def encodeLiteral(originalLiteral: String): String = {
        var result = originalLiteral;
        try {
            if (result != null) {
                result = result.replaceAll("\\\\", "/");
                result = result.replaceAll("\"", "%22");
                result = result.replaceAll("\\\\n", " ");
                result = result.replaceAll("\\\\r", " ");
                result = result.replaceAll("\\\\ ", " ");
                result = result.replaceAll("_{2,}+", "_");
                result = result.replaceAll("\n", "");
                result = result.replaceAll("\r", "");
                result = result.replace("\\ ", "/");
            }
        } catch {
            case e: Exception => {
                logger.error("Error encoding literal for literal = " + originalLiteral + " because of " + e.getMessage());
            }
        }
        result;
    }

    def encodeURI(originalURI: String, mapURIEncodingChars: Map[String, String]): String = {
        val resultAux = originalURI.trim();
        var result = resultAux;
        try {
            if (mapURIEncodingChars != null)
                mapURIEncodingChars.foreach {
                    case (key, value) => result = result.replaceAll(key, value)
                }
        } catch {
            case e: Exception => {
                logger.error("Error encoding uri for uri = " + originalURI + " because of " + e.getMessage());
                resultAux
            }
        }
        result;
    }

    def isNetResource(resourceAddress: String): Boolean = {
        val result = try {
            val url = new URL(resourceAddress);
            val conn = url.openConnection();
            conn.connect();
            true;
        } catch {
            case e: Exception => { false }
        }
        result;
    }

    /**
     * URL-encode special characters in a template string (this is different from the special chars read from the database).
     * URL special chars '#', '?', "/", ":" and '&' are unchanged: if they are in a template string this must be intentional.
     */
    def encodeUrl(originalValue: String): String = {
        var result = originalValue;
        if (result != null) {
            result = result.replaceAll(" ", "%20");
            result = result.replaceAll("!", "%21");
            result = result.replaceAll("\"", "%22");
            result = result.replaceAll("\\$", "%24");
            result = result.replaceAll("'", "%27");
            result = result.replaceAll("\\(", "%28");
            result = result.replaceAll("\\)", "%29");
            result = result.replaceAll("\\*", "%2A");
            result = result.replaceAll("\\+", "%2B");
            result = result.replaceAll(",", "%2C");
            result = result.replaceAll(";", "%3B");
            result = result.replaceAll("<", "%3C");
            result = result.replaceAll("=", "%3D");
            result = result.replaceAll(">", "%3E");
            result = result.replaceAll("@", "%40");
            result = result.replaceAll("\\^", "%5E");
            result = result.replaceAll("\\[", "%5B");
            result = result.replaceAll("\\\\", "%5C");
            result = result.replaceAll("\\]", "%5D");
            result = result.replaceAll("`", "%60");
            result = result.replaceAll("\\{", "%7B");
            result = result.replaceAll("\\|", "%7C");
            result = result.replaceAll("\\}", "%7D");
            result = result.replaceAll("~", "%7E");
        }
        result;
    }

    /**
     * URL-encode reserved chars in the database values when they must be used to build IRIs
     */
    def encodeReservedChars(originalValue: String): String = {
        var result = originalValue;
        if (result != null) {
            result = result.replaceAll("%", "%25"); // keep in first place!
            result = result.replaceAll(" ", "%20");
            result = result.replaceAll("!", "%21");
            result = result.replaceAll("\"", "%22");
            result = result.replaceAll("#", "%23");
            result = result.replaceAll("\\$", "%24");
            result = result.replaceAll("&", "%26");
            result = result.replaceAll("'", "%27");
            result = result.replaceAll("\\(", "%28");
            result = result.replaceAll("\\)", "%29");
            result = result.replaceAll("\\*", "%2A");
            result = result.replaceAll("\\+", "%2B");
            result = result.replaceAll(",", "%2C");
            result = result.replaceAll("/", "%2F");
            result = result.replaceAll(":", "%3A");
            result = result.replaceAll(";", "%3B");
            result = result.replaceAll("<", "%3C");
            result = result.replaceAll("=", "%3D");
            result = result.replaceAll(">", "%3E");
            result = result.replaceAll("\\?", "%3F");
            result = result.replaceAll("@", "%40");
            result = result.replaceAll("\\^", "%5E");
            result = result.replaceAll("\\[", "%5B");
            result = result.replaceAll("\\\\", "%5C");
            result = result.replaceAll("\\]", "%5D");
            result = result.replaceAll("`", "%60");
            result = result.replaceAll("\\{", "%7B");
            result = result.replaceAll("\\|", "%7C");
            result = result.replaceAll("\\}", "%7D");
            result = result.replaceAll("~", "%7E");
        }
        result;
    }

    def decodeURI(originalValue: String): String = {
        var result = originalValue;
        if (result != null) {
            result = result.replaceAll("\\%20", " ");
            result = result.replaceAll("\\%21", "!");
            result = result.replaceAll("\\%22", "\"");
            result = result.replaceAll("\\%23", "#");
            result = result.replaceAll("\\%24", "$");
            result = result.replaceAll("\\%26", "&");
            result = result.replaceAll("\\%27", "'");
            result = result.replaceAll("\\%28", "(");
            result = result.replaceAll("\\%29", ")");
            result = result.replaceAll("\\%2A", "*");
            result = result.replaceAll("\\%2a", "*");
            result = result.replaceAll("\\%2B", "+");
            result = result.replaceAll("\\%2b", "+");
            result = result.replaceAll("\\%2C", ",");
            result = result.replaceAll("\\%2c", ",");
            result = result.replaceAll("\\%2D", "-");
            result = result.replaceAll("\\%2d", "-");
            result = result.replaceAll("\\%2E", ".");
            result = result.replaceAll("\\%2e", ".");
            result = result.replaceAll("\\%2F", "/");
            result = result.replaceAll("\\%2f", "/");
            result = result.replaceAll("\\%25", "%");
        }
        result;
    }

    /**
     * Recursive method to compute the intersection of multiple sets of RDFNode
     *
     * @return the intersection, possibly empty
     */
    def intersectMultipleNodeSets(sets: Set[List[RDFNode]]): List[RDFNode] = {
        if (sets.size == 0)
            List()
        else if (sets.size > 1)
            sets.head.intersect(intersectMultipleNodeSets(sets.tail))
        else sets.head
    }

    /**
     * Recursive method to compute the intersection of multiple sets of RDFTerm
     *
     * @return the intersection, possibly empty
     */
    def intersectMultipleTermSets(sets: Set[List[RDFTerm]]): List[RDFTerm] = {
        if (sets.size == 0)
            List()
        else if (sets.size > 1)
            sets.head.intersect(intersectMultipleTermSets(sets.tail))
        else sets.head
    }

    /**
     * Compare 2 RDF Lists. Return true is they have the same elements.
     * This method does not apply to lists of which members are lists or containers.
     */
    def compareRdfList(lst1: Resource, lst2: Resource): Boolean = {

        if ((lst1 == null) && (lst2 != null)) return false
        if ((lst1 != null) && (lst2 == null)) return false
        if ((lst1 == null) && (lst2 == null)) return true

        val first1 = lst1.getProperty(RDF.first).getObject
        val first2 = lst2.getProperty(RDF.first).getObject

        if (first1 != first2) return false

        val rest1 = lst1.getProperty(RDF.rest).getObject
        val rest2 = lst2.getProperty(RDF.rest).getObject

        if ((rest1 == RDF.nil && rest2 != RDF.nil) || (rest1 != RDF.nil && rest2 == RDF.nil))
            false
        else if (rest1 == RDF.nil && rest2 == RDF.nil)
            true
        else
            GeneralUtility.compareRdfList(rest1.asResource, rest2.asResource)
    }

    /**
     * Compare 2 RDF containers (alt, seq, bag). Return true is they have the same elements.
     * This method does not apply to containers of which members are lists or containers.
     */
    def compareRdfContainer(lst1: Resource, lst2: Resource): Boolean = {
        var i = 1
        var continue: Boolean = true
        var equal: Boolean = true
        while (continue && equal) {
            val item1 = lst1.getProperty(RDF.li(i))
            val item2 = lst2.getProperty(RDF.li(i))
            if (item1 == null && item2 == null)
                continue = false
            else if ((item1 == null && item2 != null) || (item1 != null && item2 == null))
                equal = false
            else
                equal = (item1.getObject == item2.getObject)
            i = i + 1
        }
        equal
    }

    /**
     * Recursive removal of all triples concerning an RDF List.
     * This method does not apply to lists of which members are nested lists or containers.
     */
    def removeRdfList(model: Model, res: Resource) {
        val rest = model.getProperty(res, RDF.rest)
        if (rest != null)
            GeneralUtility.removeRdfList(model, rest.getResource)
        model.removeAll(res, null, null)
    }

    /**
     * Removal of all triples concerning an RDF bag, seq or alt.
     * This method does not apply to container of which members are nested lists or containers.
     */
    def removeRdfContainer(model: Model, res: Resource) {
        model.removeAll(res, null, null)
    }

    def isRdfList(model: Model, res: Resource): Boolean = {
        val stmtType = model.getProperty(res.asResource(), RDF.`type`)
        (stmtType != null && stmtType.getObject == RDF.List)
    }

    def isRdfContainer(model: Model, res: Resource): Boolean = {
        val stmtType = model.getProperty(res.asResource(), RDF.`type`)
        stmtType != null && (
            (stmtType.getObject == RDF.Bag) ||
            (stmtType.getObject == RDF.Alt) ||
            (stmtType.getObject == RDF.Seq))
    }

    /**
     * Remove any blank character from the string
     */
    def cleanString(str: String) = str.trim.replaceAll("\\s", "")

    /**
     * Remove any blank character from the string except when blanks are within double/single quotes
     */
    def cleanStringExceptWithinQuotes(str: String) = {

        // Match:
        // 1- [^"']+ : any string without single/double quote
        // 2- "([^"]|(?<=[\\])")*" : any string within double quotes, that either contains no double-quote or an escaped double-quote e.g. "a\"b"
        //    "(?:[^"]|(?<=[\\])")*" is the same but the "?:" just means that the inside is not a capturing group
        // 3- '([^']|(?<=[\\])')*' : any string within single quotes, that either contains no single-quote or an escaped single-quote e.g. 'a\'b'
        val regex = Pattern.compile("""[^"']+|("(?:[^"]|(?<=[\\])")*")|('(?:[^']|(?<=[\\])')*')""")
        val matcher = regex.matcher(str.trim)

        val sb: StringBuffer = new StringBuffer()
        while (matcher.find()) {
            if (matcher.group(1) != null)
                // Add double-quoted string without any change
                sb.append(matcher.group(1))
            else if (matcher.group(2) != null)
                // Add single-quoted string without any change
                sb.append(matcher.group(2))
            else
                // Add unquoted string after removing all blanks
                sb.append(matcher.group().replaceAll("""\s""", ""))
        }
        sb.toString()
    }

    /**
     * Get an available random file name in the specified directory
     *
     * @param dir the directory, "" for current running directory
     * @param prefix file prefix
     * @param suffix file suffix (typically the extension)
     * @param nameSize Size of generated filename. Note: 5 chars is about 60^5 = 777 millions of possible names
     * @return A file Path, or None if can't found anyone
     */
    def createRandomFile(dir: String, prefix: String = "", suffix: String = "", maxTries: Int = 10, nameSize: Int = 6): Option[File] = {
        val alphabet = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

            def generateName = (1 to nameSize).map(_ => alphabet(Random.nextInt(alphabet.size))).mkString

        val dirPath = new File(dir).toPath
        var newFile: File = null
        for (_ <- (1 to maxTries).iterator) {
            newFile = dirPath.resolve(prefix + generateName + suffix).toFile
            if (!newFile.exists)
                return Some(newFile)
        }
        None
    }
}

