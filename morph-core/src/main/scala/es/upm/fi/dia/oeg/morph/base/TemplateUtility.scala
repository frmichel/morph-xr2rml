package es.upm.fi.dia.oeg.morph.base

/**
 * @author Freddy Priyatna
 * @author Franck Michel, I3S laboratory
 */
import java.util.regex.Pattern

import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.mutable.Queue
import scala.util.matching.Regex

import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.path.MixedSyntaxPath

object TemplateUtility {

    val logger = Logger.getLogger("es.upm.fi.dia.oeg.morph.base.TemplateUtility")

    val TemplatePattern = Constants.R2RML_TEMPLATE_PATTERN.r
    val TemplatePatternCapGrp = Constants.R2RML_TEMPLATE_PATTERN_WITH_CAPTURING_GRP.r

    val TemplatePatternCapGrpPat = Pattern.compile(Constants.R2RML_TEMPLATE_PATTERN_WITH_CAPTURING_GRP);

    /**
     * Match an URI with a template string. The result is a set of couples where the
     * key is the path in the template string, and the value is the corresponding value in the given URI.
     * This method applies to R2RML templates as well as xR2RML templates including mixed-syntax paths.
     * <br>
     * Example:<br>
     * if tplStr = "http://example.org/{ID}/{Column(NAME)/JSONPath(...)}",
     * and inputURIString = "http://example.org/id1/mixed",<br>
     * the method returns: Map("ID" -> "id1", "Column(NAME)/JSONPath(...)" -> "mixed").
     */
    def getTemplateMatching(tplStr: String, inputURIString: String): Map[String, String] = {

        var newTplStr = tplStr;
        if (!newTplStr.startsWith("<")) newTplStr = "<" + newTplStr;
        if (!newTplStr.endsWith(">")) newTplStr = newTplStr + ">";

        var newURIStr = inputURIString;
        if (!newURIStr.startsWith("<")) newURIStr = "<" + newURIStr;
        if (!newURIStr.endsWith(">")) newURIStr = newURIStr + ">";

        val groups = this.getTemplateGroups(newTplStr)
        for (group <- groups) {
            // Replace each group "{...}" with the regex "(.+?)"
            newTplStr = newTplStr.replace("{" + group + "}", "(.+?)");
        }

        val pattern = new Regex(newTplStr);
        val firstMatch = pattern.findFirstMatchIn(newURIStr);

        // Create a map of couples (reference, value)
        val result: Map[String, String] =
            if (firstMatch != None) {
                // Subgroups return values corresponding to the (.+?) groups
                val subgroups = firstMatch.get.subgroups;
                var i = 0;
                groups.map(ref => {
                    val resultAux = (ref -> subgroups(i));
                    i = i + 1;
                    resultAux
                }).toMap
            } else
                Map.empty
        result
    }

    /**
     * Get the list of capturing groups between '{' and '}' in a template string, without the '{' and '}'.
     * This method applies to R2RML templates as well as xR2RML templates including
     * mixed-syntax paths.
     * Example: if tplStr is "http://example.org/{ID}/{Column(NAME)/JSONPath(...)}/...",
     * the method returns List("ID", "Column(NAME)/JSONPath(...)").
     *
     * Extracting encapsulating groups at once with a regex is quite complicated due to mixed syntax paths
     * since they can contain '{' and '}'. As a result this method does this in several steps. We exemplify this
     * on template string http://example.org/{ID}/{Column(NAME)/JSONPath(...)}
     * <ol>
     * <li>Save all mixed-syntax paths from the template string to a list i.e. List("Column(NAME)", "JSONPath(...)").</li>
     * <li>Replace each path expression with a place holder "xR2RML_replacer" i.e. "http://example.org/{ID}/{xR2RML_replacer/xR2RML_replacer}".</li>
     * <li>Extract all template groups between '{' and '}' to a list i.e. List("{ID}", "{xR2RML_replacer/xR2RML_replacer}").</li>
     * <li>Then, in this last list, we replace place holders with original mixed syntax path
     * expressions saved in step 1: List("{ID}", "{"Column(NAME)/JSONPath(...)}").</li>
     * <li>Finally, remove the first '{' and last '}' characters i.e. List("ID", "Column(NAME)/JSONPath(...)")</li>
     * </ol>
     */
    def getTemplateGroups(tplStr: String): List[String] = {

        // (1) Save all mixed-syntax path expressions in the template string
        val mixedSntxRegex = Constants.xR2RML_MIXED_SYNTX_PATH_REGEX
        val mixedSntxPaths: Queue[String] = Queue(mixedSntxRegex.findAllMatchIn(tplStr).toList.map(item => item.toString): _*)

        // (2) Replace each path expression with a place holder "xR2RML_replacer"
        val tpl2 = mixedSntxRegex.replaceAllIn(tplStr, "xR2RML_replacer")

        // (3) Make a list of the R2RML template groups between '{' '}'
        val listPattern = TemplatePatternCapGrp.findAllIn(tpl2).toList

        // (4) Restore the path expressions in each of the place holders
        val listReplaced = MixedSyntaxPath.replaceTplPlaceholder(listPattern, mixedSntxPaths)

        // Extract the references of each template group between '{' and '}'
        val groupsFromTpl = listReplaced.map(group =>
            {
                // For non mixed-syntax paths (like simple column name), there has been no parsing
                // at all above so they still have the '{' and '}'
                if (group.startsWith("{") && group.endsWith("}"))
                    group.substring(1, group.length() - 1)
                else group
            })
        //if (logger.isTraceEnabled()) logger.trace("Extracted groups: " + groupsFromTpl + " from template " + tplStr)
        groupsFromTpl;
    }

    /**
     * Get the list of columns referenced in a template string.
     * This method applies to R2RML templates as well as xR2RML templates including
     * mixed-syntax paths like: "http://example.org/{Column(NAME)/JSONPath(...)}/...
     *
     * Example: on template string "http://example.org/{ID}/{Column(NAME)/JSONPath(...)}"
     * this method returns "List("ID", "NAME")"
     */
    def getTemplateColumns(tplStr: String): List[String] = {

        val groups = getTemplateGroups(tplStr)

        // Extract the column references of each template group between
        val columnsFromTemplate = groups.map(group =>
            MixedSyntaxPath(group, Constants.xR2RML_REFFORMULATION_COLUMN).getReferencedColumn.getOrElse(""))
        if (logger.isTraceEnabled()) logger.trace("Extracted columns: " + columnsFromTemplate + " from template " + tplStr)
        columnsFromTemplate;
    }

    /**
     * Replace template tokens (i.e. capturing groups between { and }) with replacement values.
     * This method applies to R2RML templates as well as xR2RML templates including
     * mixed-syntax paths like: "http://example.org/{Column(NAME)/JSONPath(...)}/...
     *
     * Replacement values come as N lists corresponding to the N groups. Values are produced by doing
     * a Cartesian product, that is, by making all the possible combinations between all possible values
     * of each group.
     */
    def replaceTemplateGroups(tplStr: String, replacements: List[List[Object]]): List[String] = {

        if (replacements.isEmpty) { return List(tplStr) }

        if (getTemplateGroups(tplStr).length > replacements.length) {
            logger.error("Unexpected error: there are more template groups than replacement values. Template: " + tplStr + ". Replacements: " + replacements)
            // Return the template as is, not to raise an exception... not sure that's the best behavior
            return List(tplStr)
        }

        // Save all mixed-syntax path expressions in the template string
        val mixedSntxRegex = Constants.xR2RML_MIXED_SYNTX_PATH_REGEX
        val mixedSntxPaths = mixedSntxRegex.findAllMatchIn(tplStr).toList

        // Replace each path expression with a place holder "xR2RML_replacer"
        val tpl2 = mixedSntxRegex.replaceAllIn(tplStr, "xR2RML_replacer")

        // Compute all possible combinations between all values of the groups
        val combinations = cartesianProduct(replacements)

        if (logger.isTraceEnabled()) logger.trace("Template replacement combinations: " + combinations)

        val templateResults = new Queue[String]

        for (combination <- combinations) {
            var buffer: StringBuffer = new StringBuffer()
            var grpIdx = 0

            // Make a list of the template groups between '{' '}'
            val matcher = TemplatePatternCapGrpPat.matcher(tpl2)
            var appendIdx = 0
            while (matcher.find()) {
                val path = matcher.group(1)

                val replacement = combination.get(grpIdx)

                // Copy the part before the match
                buffer.append(tpl2.substring(appendIdx, matcher.start()))

                // Append the replacement
                buffer.append(replacement.toString)

                grpIdx = grpIdx + 1
                appendIdx = matcher.end()
            }

            // Copy the end part after the last match
            if (appendIdx < tpl2.length())
                buffer.append(tpl2.substring(appendIdx, tpl2.length()))

            templateResults += buffer.toString
        }

        // When the result of a template ends with "sha1(anything)" then replace
        // sha1(anything) with the SHA1 hash of "anything"
        val result = templateResults.toList.map(res => {
            if (res.contains(Constants.xR2RML_PATH_CONSTR_SHA1 + "(")) {
                Constants.xR2RML_PATH_SHA1_REGEX.replaceAllIn(
                    res,
                    m => org.apache.commons.codec.digest.DigestUtils.sha1Hex(m.group(1)))
            } else res
        })

        if (logger.isTraceEnabled()) logger.trace("Generated templates: " + result)
        result
    }

    /**
     * Make the Cartesian product between any number of lists: from a set of lists l1 to lN,
     * compute a new set containing all the combinations of elements of lists l1, l2... lN.
     * This is used in the production of all template strings when one or several groups
     * of the template are multi-valued.
     */
    def cartesianProduct(lists: List[List[Object]]): List[List[Object]] = {

        val combinations = new Queue[List[Object]]
        val nbLists = lists.length

        // Initialize the index to browse each list
        var indexes = Array.fill[Int](nbLists)(0)

        var stillToGo = true
        while (stillToGo) {

            // Build a list (combination) from the current elements of each list
            val combination = for (j <- 0 to (nbLists - 1)) yield {
                if (lists(j).isEmpty) ""
                else lists(j)(indexes(j))
            }
            combinations += combination.toList

            // Search in which list we can increase the index, starting with the last one, then last but one etc.
            var continue = true
            var lstIdx = nbLists - 1
            while (continue && lstIdx >= 0) {
                if (indexes(lstIdx) < lists(lstIdx).length - 1) {
                    // Found one list to increment the index: increment it and stop for now
                    indexes(lstIdx) = indexes(lstIdx) + 1
                    continue = false
                } else {
                    // Not possible to increment this list anymore, so reset its index
                    // and check if we can decrement the index of another list
                    indexes(lstIdx) = 0
                    lstIdx = lstIdx - 1
                }
            }
            // If we exist the loop without being able to increment the index of any list,
            // that means we have reached the last possible combination => the end
            if (lstIdx == -1)
                stillToGo = false
        }

        combinations.toList
    }

    /**
     * Check if two template string are compatible, i.e. if there fixed parts are the same
     * and they have {...} at the same place. Example:
     * "http://example.org/{xx}/B{zz}"
     * and
     * "http://example.org/{yy}/B{tt}"
     * are compatible
     */
    def compatibleTemplateStrings(tplStr1: String, tplStr2: String): Boolean = {

        var newTplStr1 = tplStr1;
        if (!newTplStr1.startsWith("<")) newTplStr1 = "<" + newTplStr1;
        if (!newTplStr1.endsWith(">")) newTplStr1 = newTplStr1 + ">";

        var newTplStr2 = tplStr2;
        if (!newTplStr2.startsWith("<")) newTplStr2 = "<" + newTplStr2;
        if (!newTplStr2.endsWith(">")) newTplStr2 = newTplStr2 + ">";

        var groups = this.getTemplateGroups(newTplStr1)
        for (group <- groups)
            // Replace each group "{...}" with the regex "PLACEHOLDER"
            newTplStr1 = newTplStr1.replace("{" + group + "}", "PLACEHOLDER");

        groups = this.getTemplateGroups(newTplStr2)
        for (group <- groups)
            // Replace each group "{...}" with the regex "PLACEHOLDER"
            newTplStr2 = newTplStr2.replace("{" + group + "}", "PLACEHOLDER");

        newTplStr1 == newTplStr2
    }

    def main(args: Array[String]) = {
        var template = "Hello {Name} Please find attached {Invoice Number} which is due on {Due Date}";

        val replacements = List(List("Freddy"), List("INV0001"))

        val attributes = TemplateUtility.getTemplateColumns(template);
        System.out.println("attributes = " + attributes);

        val template2 = TemplateUtility.replaceTemplateGroups(template, replacements);
        System.out.println("template2 = " + template2);

        template = """\{\w+\}""";
        val columnsFromTemplate = template.r.findAllIn("http://example.org/{abc}#{def}").toList;
        System.out.println("columnsFromTemplate = " + columnsFromTemplate);

        val date = """(\d\d\d\d)-(\d\d)-(\d\d)""".r
        val dates = "Important dates in history: 2004-01-20, 1958-09-05, 2010-10-06, 2011-07-15"

        val firstDate = date findFirstIn dates getOrElse "No date found."
        System.out.println("firstDate: " + firstDate)

        val firstYear = for (m <- date findAllMatchIn dates) yield m group 1
        System.out.println("firstYear: " + firstYear.toList)

    }

}