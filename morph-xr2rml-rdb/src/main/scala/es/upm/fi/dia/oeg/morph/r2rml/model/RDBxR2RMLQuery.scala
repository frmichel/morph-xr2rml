package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.GeneralUtility

class RDBxR2RMLQuery(
    val query: String,
    refFormulation: String,
    iterator: Option[String],
    uniqueRefs: Set[String],
    override val listPushDown: List[xR2RMLPushDown])
    
        extends RDBxR2RMLLogicalSource(Constants.LogicalTableType.QUERY, refFormulation, iterator, uniqueRefs, listPushDown) {

    /**
     * Return true if both xR2RMLQueries have the same query, reference formulation and iterator.
     */
    override def equals(q: Any): Boolean = {
        q.isInstanceOf[RDBxR2RMLQuery] && {
            val ls = q.asInstanceOf[RDBxR2RMLQuery]
            this.logicalTableType == ls.logicalTableType && this.refFormulation == ls.refFormulation &&
                this.docIterator == ls.docIterator && GeneralUtility.cleanString(this.query) == GeneralUtility.cleanString(ls.query)
        }
    }

    override def getValue(): String = { this.query }
}
