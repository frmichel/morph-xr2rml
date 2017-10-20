package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.Constants

/**
 * @author Franck Michel, I3S laboratory
 */
class xR2RMLTable(
    val tableName: String,
    override val listPushDown: List[xR2RMLPushDown])

        extends xR2RMLLogicalSource(
            Constants.LogicalTableType.TABLE_NAME, Constants.xR2RML_REFFORMULATION_COLUMN, None, Set.empty, listPushDown) {

    override def equals(q: Any): Boolean = {
        q.isInstanceOf[xR2RMLTable] && {
            val ls = q.asInstanceOf[xR2RMLTable]
            this.logicalTableType == ls.logicalTableType && this.refFormulation == ls.refFormulation &&
                this.docIterator == ls.docIterator && this.tableName == ls.tableName
        }
    }

    override def getValue(): String = { this.tableName; }
}
