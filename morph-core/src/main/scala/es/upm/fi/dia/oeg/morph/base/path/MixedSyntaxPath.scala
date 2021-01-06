package es.upm.fi.dia.oeg.morph.base.path

import scala.collection.immutable.List
import scala.collection.mutable.Queue

import org.apache.log4j.Logger

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ParseContext

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.Constants

/**
 * A mixed syntax path consists of a sequence of path constructors each containing a path expression, example:
 * Column(NAME)/JSONPath($.*) : this expression retrieves the value of column NAME in a row-based database,
 * then apply the JSONPath expression to the value.
 *
 * A instance of MixedSyntaxPath holds a list of PathExpression instances, each corresponding to a part
 * of the path. A simple expression with no path constructor, like a simple column name or an XPath expression
 * are considered as a mixed syntax path with only one implicit path constructor.
 * The implicit path constructor is figured out from the reference formulation of the logical source.
 *
 * @author Franck Michel, I3S laboratory
 */
class MixedSyntaxPath(
        /**
         *  Raw value of the mixed-syntax path as provided by the mapping file.
         *  This is either the value of property xrr:reference, or the value of one
         *  capturing group {...} in a rr:template property
         */
        val rawValue: String,

        /** Reference formulation from the triples map logical source */
        val refFormulation: String,

        /** List of paths that compose the mixed syntax path */
        val paths: List[PathExpression]) {

    val logger = Logger.getLogger(this.getClass().getName())

    override def toString: String = {
        "MixedSyntaxPath - refFormulation: " + refFormulation + ", paths: " + paths
    }

    /**
     * This method <b>only</b> applies in the context of a row-based database, i.e. in which a mixed-syntax path
     * MUST start with a "Column(...)" path constructor.
     * It returns the column name referenced in the first path constructor if this is a "Column()",
     * otherwise None is returned.
     */
    def getReferencedColumn(): Option[String] = {
        if (refFormulation == Constants.xR2RML_REFFORMULATION_COLUMN) {
            val path = paths.head
            // In a column-based database, the first expression MUST be a Column() expression
            if (path.isInstanceOf[Column_PathExpression]) {
                Option(path.pathExpression)
            } else {
                logger.error("Error: the first path expression applied to a row-based database MUST be a Column() expression. Ignoring.")
                None
            }
        } else {
            logger.error("Error: there is no column reference in a non row-based database. Ignoring.")
            None
        }
    }

    /**
     * Check if <i>this</i> is an single reference to a column, i.e. it was build from a reference
     * either in the form "COL_NAME" or "Column(COL_NAME). Basically, it says if we are in
     * a regular R2RML column reference.
     */
    def isSimpleColumnExpression(): Boolean = {
        (paths.size == 1) && (paths.head.isInstanceOf[Column_PathExpression])
    }

    /**
     * Evaluate an expression against the mixed-syntax path represented by this instance.
     * The expression is evaluated against the first path, then results are evaluated against the second path, etc.
     * until there is no more path to evaluate against.
     *
     * @param value an object representing the data to evaluate. May be null, in that case List() is returned.
     * @return a list of objects representing the result of the evaluation (string, boolean, integer...).
     * Possibly an empty list.
     */
    def evaluate(value: Object): List[Object] = {

        // If the first path is a Column() path, then it has been evaluated by the database:
        // the value passed in not a row but the cell value itself. Thus, we continue straight to the
        // evaluation of the subsequent paths
        var pathsToProcess = this.paths
        if (paths.head.isInstanceOf[Column_PathExpression])
            pathsToProcess = paths.tail

        MixedSyntaxPath.recursiveEval(value, pathsToProcess)
    }

    /**
     * Reconstruct the mixed-syntax path from the list of paths.
     * The result is returned with un-escaped characters '/', '(', ')', '{', '}'
     * thus it may not equal the mixed syntax path passed in the constructor (apply).
     * Example: if the MixedSyntaxPath was constructed with "NAME", this method
     * return "Column(NAME)".
     */
    def reconstructMixedSyntaxPath(): String = {
        val reconstruct = paths.map(path =>
            path match {
                case p: Column_PathExpression => "Column(" + p.pathExpression + ")"
                case p: XPath_PathExpression => "XPath(" + p.pathExpression + ")"
                case p: JSONPath_PathExpression => "JSONPath(" + p.pathExpression + ")"
                case p: CSV_PathExpression => "CSV(" + p.pathExpression + ")"
                case p: TSV_PathExpression => "TSV(" + p.pathExpression + ")"
                case _ => throw new Exception("Unknown type of path: " + path)
            })
        var pathStr: String = ""
        for (p <- reconstruct)
            pathStr = if (pathStr.isEmpty()) p else pathStr + "/" + p

        pathStr
    }
}

object MixedSyntaxPath {

    val logger = Logger.getLogger(this.getClass().getName())

    /** JSON parse configuration */
    val jsonConf: Configuration = Configuration.defaultConfiguration()
        .addOptions(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
        .addOptions(com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS);

    /** JSON parse context */
    val jsonParseCtx: ParseContext = JsonPath.using(jsonConf)

    /**
     *  Parse the mixed syntax path value read from the mapping (properties xrr:reference or rr:template)
     *  and create corresponding PathExpression instances.
     *
     *  @param rawValue string value representing the path. This may be a mixed syntax path or
     *  a simple expression such as a column name or a JSONPath expression. If this is a simple expression,
     *  the reference formulation is used to know what type of expression this is
     *  @param refForm the reference formulation of the logical source of the current triples map
     *  @return an instance of MixedSyntaxPath
     */
    def apply(rawValue: String, refFormulation: String): MixedSyntaxPath = {

        // Split the mixed syntax path into individual path construct expressions, like "Column(NAME)"
        val rawPathList = Constants.xR2RML_MIXED_SYNTX_PATH_REGEX.findAllMatchIn(rawValue).toList
        val result =
            if (rawPathList.isEmpty) {
                // The value is a simple path expression without any path construct
                val res = refFormulation match {
                    case Constants.xR2RML_REFFORMULATION_COLUMN => new Column_PathExpression(rawValue)
                    case Constants.xR2RML_REFFORMULATION_XPATH => new XPath_PathExpression(rawValue)
                    case Constants.xR2RML_REFFORMULATION_JSONPATH => new JSONPath_PathExpression(rawValue)
                    case _ => throw new Exception("Unknown reference formulation: " + refFormulation)
                }
                List(res)

            } else {
                // The value is a mixed syntax path. Each individual path (in the path constructor) is parsed
                rawPathList.map(rawPath =>
                    rawPath match {
                        case Constants.xR2RML_PATH_COLUMN_REGEX(_*) => { Column_PathExpression.parse(rawPath.toString) }
                        case Constants.xR2RML_PATH_XPATH_REGEX(_*) => { XPath_PathExpression.parse(rawPath.toString) }
                        case Constants.xR2RML_PATH_JSONPATH_REGEX(_*) => { JSONPath_PathExpression.parse(rawPath.toString) }
                        case Constants.xR2RML_PATH_CSV_REGEX(_*) => { CSV_PathExpression.parse(rawPath.toString) }
                        case Constants.xR2RML_PATH_TSV_REGEX(_*) => { TSV_PathExpression.parse(rawPath.toString) }
                        case _ => throw new Exception("Unknown type of path: " + rawPath)
                    })
            }

        val mxp = new MixedSyntaxPath(rawValue, refFormulation, result)
        //logger.trace("Built " + mxp.toString + " from " + rawValue)
        mxp
    }

    /**
     * Unescape the chars escaped in a path construct expression, name '/', '(', ')', '{' and '}'.
     */
    def unescapeChars(expr: String): String = {
        // replaceAll takes a regex as first argument i.e. '\' must be escaped: to replace '\/', the rexeg is '\\/'
        expr.replaceAll("""\\/""", "/").replaceAll("""\\\(""", "(").replaceAll("""\\\)""", ")").replaceAll("""\\\}""", "}").replaceAll("""\\\{""", "{")
    }

    private def replaceStrRec(template: String, replacers: Queue[String]): String = {
        if (replacers == Nil || template == Nil || !template.contains("xR2RML_replacer")) {
            template
        } else {
            val hd = replacers.dequeue
            val idx = template.indexOf("xR2RML_replacer")
            val str = template.substring(0, idx) + hd.toString + template.substring(idx + "xR2RML_replacer".length)
            replaceStrRec(str, replacers)
        }
    }

    /**
     * Replace placeholder 'xR2RML_replacer' in each template of the list tplList by
     * its original value that is a mixed-syntax path.
     * This method is used to deal with templates including mixed syntax path.
     * Those are quite complicated to parse at once with a regular expression, thus
     * the need for this method.
     */
    def replaceTplPlaceholder(tplList: List[String], replacers: Queue[String]): List[String] = {
        if (tplList == Nil)
            Nil
        else {
            val str = replaceStrRec(tplList.head.toString, replacers)
            str :: replaceTplPlaceholder(tplList.tail, replacers)
        }
    }

    /**
     * Recursively evaluate an expression against a list of paths.
     * This method should be called by an instance of class MixedSyntaxPath.
     *
     * @return a list of objects representing the result of the evaluation (string, boolean, integer...)
     */
    private def recursiveEval(value: Object, paths: List[PathExpression]): List[Object] = {

        if (value == null) return List()
        if (value.toString.isEmpty()) return List()

        // Stop condition: this case happens when the mixed syntax path is a single Column()
        if (paths == Nil) return List(value)

        // Evaluate the value against the first path in the list of paths
        val currentEval = paths.head.evaluate(value.toString)

        if (paths.tail == Nil) {
            // If there is no more path, then we have finished.
            // Just optionally remove empty values to avoid creating empty RDF terms
            if (currentEval != null)
                currentEval.filter(_ != null).filterNot(_.toString().isEmpty())
            else currentEval
        } else
            // For each value produced by the evaluation above, run the evaluation with the next path in the list
            currentEval.flatMap(value => recursiveEval(value, paths.tail))
    }
}