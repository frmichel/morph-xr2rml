package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility

/**
 * @author Franck Michel, I3S laboratory
 */
class xR2RMLQuery(
    val query: String,
    refFormulation: String,
    iterator: Option[String],
    uniqueRefs: Set[String],
    override val listPushDown: List[xR2RMLPushDown],
    override val split: Option[Integer])
        extends xR2RMLLogicalSource(Constants.LogicalTableType.QUERY, refFormulation, iterator, uniqueRefs, listPushDown, split) {

    /**
     * Return true if both xR2RMLQueries have the same query, reference formulation and iterator.
     *
     * @todo Improve the comparison to take into account queries with same semantic despite
     * a different order of sub-queries.
     * E.g. <code>{'p': {\$eq 5}, 'q': {\$eq 6}} == {'q': {\$eq 6}, 'p': {\$eq 5}}</code>
     */
    override def equals(q: Any): Boolean = {
        q.isInstanceOf[xR2RMLQuery] && {
            val ls = q.asInstanceOf[xR2RMLQuery]
            this.logicalTableType == ls.logicalTableType && this.refFormulation == ls.refFormulation &&
                this.docIterator == ls.docIterator && 
                GeneralUtility.cleanString(this.query) == GeneralUtility.cleanString(ls.query)
        }
    }

    override def getValue(): String = { this.query; }
}
