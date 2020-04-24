package es.upm.fi.dia.oeg.morph.r2rml.model

import org.apache.log4j.Logger

import org.apache.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.exception.MorphException

class R2RMLJoinCondition(val childRef: String, val parentRef: String) {

    def logger = Logger.getLogger(this.getClass().getName())

    override def toString(): String = {
        return "JoinCondition[Parent:" + this.parentRef + ", Child:" + this.childRef + "]";
    }
}

object R2RMLJoinCondition {

    def logger = Logger.getLogger(this.getClass().getName())

    def apply(resource: Resource): R2RMLJoinCondition = {
        val childStatement = resource.getProperty(Constants.R2RML_CHILD_PROPERTY);
        if (childStatement == null) {
            val errorMessage = "Missing child reference in join condition.";
            logger.fatal(errorMessage);
            throw new MorphException(errorMessage);
        }
        val childRef = childStatement.getObject().toString();

        val parentStatement = resource.getProperty(Constants.R2RML_PARENT_PROPERTY);
        if (parentStatement == null) {
            val errorMessage = "Missing parent reference in join condition.";
            logger.fatal(errorMessage);
            throw new MorphException(errorMessage);
        }
        val parentRef = parentStatement.getObject().toString();
        new R2RMLJoinCondition(childRef, parentRef);
    }
}
