package fr.unice.i3s.morph.xr2rml.mongo.query

/**
 * MongoDB query of the form: <code>{\$where: '<condition>'}</code>.<br>
 * The Jongo API (that is used to process MongoDB query strings accepts only single quotes.
 * To avoid confusion, strings in the "<condition>" must be enclosed using the double-quote, 
 * e.g.: <code>\$where: 'this.p == value'</code>.
 * 
 * If single quote are used they are replaced with double-quote.
 * 
 * @author Franck Michel, I3S laboratory
 */
class MongoQueryNodeWhere(val cond: String) extends MongoQueryNode {

    override def isWhere: Boolean = true

    override def equals(q: Any): Boolean = {
        q.isInstanceOf[MongoQueryNodeWhere] && this.cond == q.asInstanceOf[MongoQueryNodeWhere].cond
    }

    override def toString = { "$where: " + "'" + cond.replace("'", "\"") + "'" }
}
