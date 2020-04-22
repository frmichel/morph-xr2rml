package fr.unice.i3s.morph.xr2rml.mongo

import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.GeneralUtility
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLLogicalSource
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLQuery

/**
 * Simple representation of a MongoDB shell query:
 *
 * In query: <code>db.collection.find({ 'a': { \$exists: true} })</code>
 * 'collection' is the collection name while <code>{ 'a': { \$exists: true} }</code> is the query string
 *
 * Optionally, an iterator of the logical source can be set after object creation.
 *
 * @author Franck Michel, I3S laboratory
 */
class MongoDBQuery(
        val collection: String,
        val query: String,
        val projection: String) {

    /** Constructor without projection nor limit */
    def this(collection: String, query: String) = this(collection, query, "")

    var iterator: Option[String] = None

    def setIterator(iter: Option[String]): MongoDBQuery = {
        this.iterator = iter
        this
    }

    override def toString(): String = {
        var str = "MongoDBQuery[Collection: " + collection + ". Query: " + query
        if (iterator.isDefined)
            str = str + ". Iterator: " + iterator.get
        if (!projection.isEmpty)
            str = str + ". Projection: " + projection
        str + "]"
    }

    override def equals(a: Any): Boolean = {
        val m = a.asInstanceOf[MongoDBQuery]

        this.collection == m.collection && this.query == m.query && {
            if (this.iterator.isDefined && m.iterator.isDefined)
                GeneralUtility.cleanString(this.iterator.get) == GeneralUtility.cleanString(m.iterator.get)
            else
                this.iterator == m.iterator
        }
    }
}

object MongoDBQuery {

    val logger = Logger.getLogger(this.getClass().getName());

    /**
     * Create a MongoDBQuery with no iterator.
     *
     * A query string looks like this: <code>db.myCollection.find({ 'a': { \$exists: true} })</code>.
     *
     * This method returns a MongoDBQuery instance where collection = myCollection
     * and query string = <code>{'a':{\$exists:true} }</code>
     */
    def parseQueryString(q: String, stripCurlyBracket: Boolean): MongoDBQuery = {

        // Fix 20190618: the cleanString removes all spaces. This is a pb when the string contains 
        // a quoted string with spaces, e.g. " Foo ' Bar ' " should be cleaned as "Foo' Bar '".
        // val query = GeneralUtility.cleanString(q)
        val query = GeneralUtility.cleanStringExceptWithinQuotes(q)
        
        var tokens = query.trim.split("\\.")
        if (!tokens(0).equals("db")) {
            logger.error("Invalid query string: " + query)
            return null
        }
        val collection = tokens(1)

        // The query string starts after the '(' and finished before the trailing ')'
        // @TODO: this is too simple and does not allow to add someting after "db.collecion.find(...)" e.g. distinct() or sort().
        val indexFirstPar = query.indexOf('(')
        var queryStr = query.substring(indexFirstPar + 1, query.length - 1).trim
        
        if (stripCurlyBracket)
            if (queryStr.startsWith("{") && queryStr.endsWith("}"))
                queryStr = queryStr.substring(1, queryStr.length - 1).trim

        new MongoDBQuery(collection, queryStr)
    }

    /**
     * Create a MongoDBQuery and initialize the iterator.
     */
    def parseQueryString(query: String, iterator: Option[String], stripCurlyBracket: Boolean): MongoDBQuery = {
        parseQueryString(query, stripCurlyBracket).setIterator(iterator)
    }

    /**
     * Return the most specific logical source of q1 and q2, i.e. when one is a sub-query of the other.
     * Sub-query meaning that they have the same type, reference formulation and iterator,
     * and the query string of the one starts like the query string of the other.
     *
     * @example
     * <code>q1 = db.collection.find({field1: 10})</code> and
     * <code>q2 = db.collection.find({field1: 10, field2: 20})</code>.<br>
     * q2 is more specific than q1 =&gt; return an xR2RMLQuery with query string q2
     *
     * @param q1 an xR2RMLQuery with a MongoDB query string
     * @param q2 an xR2RMLQuery with a MongoDB query string
     * @return the most specific query if one is a sub-query of the other, or None otherwise.
     */
    def mostSpecificQuery(q1: xR2RMLLogicalSource, q2: xR2RMLLogicalSource): Option[xR2RMLQuery] = {

        if (q1.logicalTableType != q2.logicalTableType || q1.refFormulation != q2.refFormulation || q1.docIterator != q2.docIterator)
            return None
        if (!q1.isInstanceOf[xR2RMLQuery] || !q2.isInstanceOf[xR2RMLQuery])
            return None

        val mq1 = q1.asInstanceOf[xR2RMLQuery]
        val mq2 = q2.asInstanceOf[xR2RMLQuery]

        val q = mostSpecificQuery(mq1.query, mq2.query)
        if (q.isDefined)
            Some(new xR2RMLQuery(q.get, q1.refFormulation, q1.docIterator, q1.uniqueRefs union q2.uniqueRefs, q1.listPushDown ++ q2.listPushDown))
        else None
    }

    /**
     * Return the most specific query string of q1 and q2, i.e. when one is a sub-query of the other.
     * Sub-query meaning that they the query string of the one starts like the query string of the other.
     *
     * @example
     * <code>q1 = db.collection.find({field1: 10})</code> and
     * <code>q2 = db.collection.find({field1: 10, field2: 20})</code>.<br>
     * q2 is more specific than q1 =&gt; return Some(q2)
     *
     * @param q1 an MongoDB query string
     * @param q2 an MongoDB query string
     * @return the most specific query string if one is a sub-query of the other, or None otherwise.
     */
    private def mostSpecificQuery(q1: String, q2: String): Option[String] = {

        val mq1 = parseQueryString(q1, true)
        val mq2 = parseQueryString(q2, true)

        if (mq1.collection != mq2.collection)
            return None

        if (mq1.query.startsWith(mq2.query))
            return Some("db." + mq1.collection + ".find({" + mq1.query + "})")
        else if (mq2.query.startsWith(mq1.query))
            return Some("db." + mq2.collection + ".find({" + mq2.query + "})")
        else
            None
    }

    /**
     * Check if the left query is more specific than or equals the right query,
     * i.e. they that they have the same type, reference formulation and iterator,
     * and the left query string starts like the right query string.
     * They may also equal each other.
     *
     * @example
     * left  = <code>db.collection.find({field1: 10, field2: 20})</code>, and
     * right = <code>db.collection.find({field1: 10})</code>.
     * left is more specific than right =&gt; return true
     *
     * @param left  an xR2RMLQuery with a MongoDB query string
     * @param right an xR2RMLQuery with a MongoDB query string
     * @return true if left is more specific than right
     */
    def isLeftMoreSpecificOrEqual(left: xR2RMLLogicalSource, right: xR2RMLLogicalSource): Boolean = {

        if (left.logicalTableType != right.logicalTableType || left.refFormulation != right.refFormulation || left.docIterator != right.docIterator)
            return false
        if (!left.isInstanceOf[xR2RMLQuery] || !right.isInstanceOf[xR2RMLQuery])
            return false

        val mqLeft = parseQueryString(left.asInstanceOf[xR2RMLQuery].query, true)
        val mqRight = parseQueryString(right.asInstanceOf[xR2RMLQuery].query, true)

        (mqLeft.collection == mqRight.collection) && (mqLeft.query.startsWith(mqRight.query))
    }

    /**
     * Check if the left query equals the right query, regardless of encapsulating '{' and '}'
     *
     * @param left  an xR2RMLQuery with a MongoDB query string
     * @param right an xR2RMLQuery with a MongoDB query string
     * @return true if they are the same regardless of encapsulating '{' and '}'
     */
    def sameQueries(left: xR2RMLLogicalSource, right: xR2RMLLogicalSource): Boolean = {

        if (left.logicalTableType != right.logicalTableType || left.refFormulation != right.refFormulation || left.docIterator != right.docIterator)
            return false
        if (!left.isInstanceOf[xR2RMLQuery] || !right.isInstanceOf[xR2RMLQuery])
            return false

        val mqLeft = parseQueryString(left.asInstanceOf[xR2RMLQuery].query, true)
        val mqRight = parseQueryString(right.asInstanceOf[xR2RMLQuery].query, true)

        (mqLeft.collection == mqRight.collection) && (mqLeft.query == mqRight.query)
    }
}