/*
 * Translated and adapted from Jena Java DescribeBNodeCloser.
 * 
 * (c) Copyright 2004, 2005, 2006, 2007, 2008, 2009 Hewlett-Packard Development Company, LP All rights reserved.
 * [See end of file]
 */
package es.upm.fi.dia.oeg.morph.base.materializer

import org.apache.jena.query.Dataset
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QuerySolutionMap
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.sparql.ARQConstants
import org.apache.jena.sparql.core.describe.DescribeHandler
import org.apache.jena.sparql.util.Closure
import org.apache.jena.sparql.util.Context

/**
 * Describe handler that considers all statements for which the resource is either a subject or an object,
 * and for every object a bNode, it recursively includes its statements.
 * 
 * @author Franck Michel, I3S laboratory
 */
class ExtendedDescribeBNodeCloser extends DescribeHandler() {

    var acc: Model = null
    var ds: Dataset = null

    override def start(accumulateResultModel: Model, cxt: Context): Unit = {
        this.acc = accumulateResultModel;
        this.ds = cxt.get(ARQConstants.sysCurrentDataset).asInstanceOf[Dataset]
    }

    override def describe(res: Resource) = {
        describeDefltModel(res)
    }

    private def describeAllModels(res: Resource) = {

        // Default model.
        Closure.closure(otherModel(res, this.ds.getDefaultModel), false, this.acc);

        // Find all the named graphs in which this resource occurs as a subject.
        // Faster than iterating in the names of graphs in the case of very large numbers of graphs,
        // few of which contain the resource, in some kind of persistent storage.

        val qsm: QuerySolutionMap = new QuerySolutionMap()
        qsm.add("r", res)
        val query: Query = QueryFactory.create("SELECT DISTINCT ?g ?r ?s { GRAPH ?g { {?r ?p1 ?o.} UNION {?s ?p2 ?r.} } }");
        val qExec: QueryExecution = QueryExecutionFactory.create(query, this.ds, qsm)
        val rs: ResultSet = qExec.execSelect
        while (rs.hasNext) {
            val qs = rs.next
            val gName: String = qs.getResource("g").getURI
            val gModel: Model = this.ds.getNamedModel(gName)

            val res2: Resource = otherModel(res, gModel)
            Closure.closure(res2, false, this.acc)

            val sub = qs.getResource("s")
            if (sub != null)
                Closure.closure(sub, false, this.acc)
        }

        Closure.closure(res, false, acc);
    }

    private def otherModel(r: Resource, model: Model): Resource = {
        if (r.isURIResource)
            return model.createResource(r.getURI)
        if (r.isAnon())
            return model.createResource(r.getId)
        // Literals do not need converting.
        return r
    }

    private def describeDefltModel(res: Resource) = {

        val model = this.ds.getDefaultModel

        var stmtIter = model.listStatements(res, null, null)
        while (stmtIter.hasNext) {
            val stmt = stmtIter.nextStatement
            val obj = stmt.getObject
            if (obj.isAnon() || obj.isResource())
                this.acc.add(stmt)
            //Closure.closure(obj.asResource(), false, this.acc)
        }

        stmtIter = model.listStatements(null, null, res)
        while (stmtIter.hasNext) {
            val stmt = stmtIter.nextStatement
            val sub = stmt.getSubject
            if (sub.isAnon() || sub.isResource())
                this.acc.add(stmt)
            //Closure.closure(sub.asResource(), false, this.acc)
        }

        Closure.closure(res, false, acc)
    }

    override def finish() = {}
}

/*
 * (c) Copyright 2004, 2005, 2006, 2007, 2008, 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */