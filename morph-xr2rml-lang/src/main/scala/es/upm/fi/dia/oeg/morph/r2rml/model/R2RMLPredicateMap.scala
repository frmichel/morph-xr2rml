package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions._

import org.apache.log4j.Logger

import com.hp.hpl.jena.rdf.model.RDFNode
import com.hp.hpl.jena.rdf.model.Resource

import es.upm.fi.dia.oeg.morph.base.Constants

class R2RMLPredicateMap(
		override val termMapType: Constants.MorphTermMapType.Value
		, override val termType: Option[String]
				, override val refFormulation: String
				, override val listPushDown:List[xR2RMLPushDown]
		)
extends R2RMLTermMap(termMapType, termType, None, None, None, refFormulation, listPushDown) {

	var termtype = this.inferTermType
}

object R2RMLPredicateMap {
	val logger = Logger.getLogger(this.getClass().getName());

	def apply(rdfNode: RDFNode, refFormulation: String): R2RMLPredicateMap = {
			val coreProperties = AbstractTermMap.extractCoreProperties(rdfNode, refFormulation);
			val termMapType = coreProperties._1;
			val termType = coreProperties._2;
			val nestTM = coreProperties._5;
		  val listPushDown = coreProperties._6;

			if (nestTM.isDefined)
				logger.error("A nested term map cannot be defined in a subject map. Ignoring.")

				if (AbstractTermMap.isRdfCollectionTermType(termType))
					logger.error("A subject map cannot have a term type: " + termType + ". Ignoring.")

					val pm = new R2RMLPredicateMap(termMapType, termType, refFormulation, listPushDown);
			pm.parse(rdfNode);
			pm;
	}

	def extractPredicateMaps(resource: Resource, formatFromLogicalTable: String): Set[R2RMLPredicateMap] = {
			logger.trace("Looking for predicate maps")
			val tms = R2RMLTermMap.extractTermMaps(resource, Constants.MorphPOS.pre, formatFromLogicalTable);
			val result = tms.map(tm => tm.asInstanceOf[R2RMLPredicateMap]);
			result;
	}
}