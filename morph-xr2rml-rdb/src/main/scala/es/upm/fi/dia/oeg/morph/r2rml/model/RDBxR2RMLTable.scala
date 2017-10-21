package es.upm.fi.dia.oeg.morph.r2rml.model

import es.upm.fi.dia.oeg.morph.base.Constants

class RDBxR2RMLTable(val tableName: String, override val listPushDown: List[xR2RMLPushDown])
        extends RDBxR2RMLLogicalSource(Constants.LogicalTableType.TABLE_NAME, Constants.xR2RML_REFFORMULATION_COLUMN, None, Set.empty, listPushDown) {

    override def equals(q: Any): Boolean = {
        q.isInstanceOf[RDBxR2RMLTable] && {
            val ls = q.asInstanceOf[RDBxR2RMLTable]
            this.logicalTableType == ls.logicalTableType && this.refFormulation == ls.refFormulation &&
                this.docIterator == ls.docIterator && this.tableName == ls.tableName
        }
    }

    override def getValue(): String = { this.tableName }
}
