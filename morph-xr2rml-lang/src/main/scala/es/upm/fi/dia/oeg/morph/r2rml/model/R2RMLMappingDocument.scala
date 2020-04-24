package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConversions.setAsJavaSet

import org.apache.log4j.Logger

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.util.FileManager
import org.apache.jena.vocabulary.RDF

import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.base.exception.MorphException

class R2RMLMappingDocument(
        val triplesMaps: Iterable[R2RMLTriplesMap],
        val mappingDocumentPath: String,
        val mappingDocumentPrefixMap: Map[String, String]) {

    val id: String = null;

    val logger = Logger.getLogger(this.getClass());

    def getPropertyMappingsByPropertyURI(propertyURI: String): Iterable[R2RMLPredicateObjectMap] = {
        this.triplesMaps.map(cm => cm.getPropertyMappings(propertyURI)).flatten
    }

    def getClassMappingByPropertyURIs(propertyURIs: Iterable[String]): Iterable[R2RMLTriplesMap] = {
        this.triplesMaps.filter(cm =>
            propertyURIs.toSet.subsetOf(cm.predicateObjectMaps.flatMap(_.getMappedPredicateNames).toSet)
        )
    }

    def getClassMappingsByClassURI(classURI: String): Iterable[R2RMLTriplesMap] =
        this.triplesMaps.filter(_.getMappedClassURIs.exists(_.equals(classURI)))

    /**
     * Find the list of predicates that are used in all PredicateObjectMaps of all TriplesMaps of the document
     * @return a list of predicate names
     */
    def getMappedProperties(): Iterable[String] = {
        val cms = this.triplesMaps.toList;
        val resultAux = cms.map(cm => {
            val tm = cm.asInstanceOf[R2RMLTriplesMap];
            val poms = tm.predicateObjectMaps.toList;
            val mappedPredicateNames = poms.map(pom => { pom.getMappedPredicateNames().toList });
            val flatMappedPredicateNames = mappedPredicateNames.flatten;
            flatMappedPredicateNames;
        })

        val result = resultAux.flatten;
        result;
    }

    def getParentTriplesMap(refObjectMap: R2RMLRefObjectMap): R2RMLTriplesMap = {
        // Build the list of JENA resources that correspond to all triples maps that are
        // referenced as a parent triples map by the given referencing object map.
        val parentTripleMapResources = this.triplesMaps.map(tm => {
            val poms = tm.predicateObjectMaps;
            poms.map(pom => {
                val roms = pom.refObjectMaps; // pom.refObjectMaps may be an empty list but not null
                roms.flatMap(rom => {
                    if (rom == refObjectMap) {
                        Some(rom.parentTriplesMapResource);
                    } else { None }
                });
            }).flatten
        }).flatten;

        val parentTripleMaps = this.triplesMaps.filter(cm => {
            val tm = cm.asInstanceOf[R2RMLTriplesMap];
            parentTripleMapResources.exists(parentTripleMapResource => {
                tm.resource == parentTripleMapResource;
            })
        })

        if (parentTripleMaps.iterator.hasNext) {
            val parentTripleMap = parentTripleMaps.iterator.next;
            parentTripleMap.asInstanceOf[R2RMLTriplesMap]
        } else {
            throw new Exception("Error: referenced parent triples map des not exist: " + parentTripleMapResources)
        }
    }

    def getPossibleRange(predicateURI: String): Iterable[R2RMLTriplesMap] = {
        val pms = this.getPropertyMappingsByPropertyURI(predicateURI).toList;
        val resultAux = if (pms != null) {
            pms.map(pm => {
                val possibleRange = this.getPossibleRange(pm).toList;
                possibleRange;
            })
        } else {
            Nil
        }

        val resultInList = resultAux.flatten;
        val resultInSet = resultInList.toSet;
        resultInSet
    }

    def getPossibleRange(predicateURI: String, cm: R2RMLTriplesMap): Iterable[R2RMLTriplesMap] = {
        val pms = cm.getPropertyMappings(predicateURI);
        val result = if (pms != null) {
            pms.toList.map(pm => {
                val possibleRange = this.getPossibleRange(pm);
                possibleRange;
            })
        } else {
            Nil
        }

        val resultInList = result.flatten;
        val resultInSet = resultInList.toSet;
        resultInSet;
    }

    def getPossibleRange(pm: R2RMLPredicateObjectMap): Iterable[R2RMLTriplesMap] = {

        val pom = pm.asInstanceOf[R2RMLPredicateObjectMap];
        val om = pom.getObjectMap(0);
        val rom = pom.getRefObjectMap(0);
        val cms = this.triplesMaps

        val result: Iterable[R2RMLTriplesMap] = if (om != null && rom == null) {
            val inferredTermType = om.inferTermType;
            if (Constants.R2RML_IRI_URI.equals(inferredTermType)) {
                if (cms != null) {
                    Nil
                } else {
                    cms.toList.flatMap(cm => {
                        val tm = cm.asInstanceOf[R2RMLTriplesMap];
                        if (Constants.MorphTermMapType.TemplateTermMap == om.termMapType) {
                            val objectTemplateString = om.getTemplateString();
                            if (tm.isPossibleInstance(objectTemplateString)) {
                                Some(cm);
                            } else {
                                None
                            }
                        } else {
                            None
                        }
                    })
                }
            } else {
                Nil
            }
        } else if (rom != null && om == null) {
            val parentTriplesMap = this.getParentTriplesMap(rom);
            val parentSubjectMap = parentTriplesMap.subjectMap;
            if (parentSubjectMap.termMapType == Constants.MorphTermMapType.TemplateTermMap) {
                val templateString = parentSubjectMap.getTemplateString();
                if (cms == null) {
                    Nil
                } else {
                    cms.flatMap(cm => {
                        if (cm.isPossibleInstance(templateString)) {
                            val tm2 = cm.asInstanceOf[R2RMLTriplesMap];
                            val classURIs = tm2.subjectMap.classURIs;
                            if (classURIs != null && !classURIs.isEmpty()) {
                                Some(cm);
                            } else {
                                None
                            }
                        } else {
                            None
                        }
                    })
                }
            } else {
                List(parentTriplesMap);
            }
        } else {
            Nil
        }

        val resultInSet = result.toSet;
        resultInSet
    }

    def getClassMappingsByInstanceTemplate(templateValue: String): Iterable[R2RMLTriplesMap] = {
        this.triplesMaps.filter(cm => {
            val tm = cm.asInstanceOf[R2RMLTriplesMap]
            tm.subjectMap.templateString.startsWith(templateValue)
        })
    }

    def getClassMappingsByInstanceURI(instanceURI: String): Iterable[R2RMLTriplesMap] = {
        this.triplesMaps.filter(cm => {
            val possibleInstance = cm.isPossibleInstance(instanceURI)
            possibleInstance
        })
    }

    def getClassMappingsByName(name: String): R2RMLTriplesMap = {
        val filtered = this.triplesMaps.filter(cm => {
            cm.asInstanceOf[R2RMLTriplesMap].name == name
        })
        if (filtered.isEmpty)
            null
        else filtered.head
    }

}

object R2RMLMappingDocument {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(props: MorphProperties): R2RMLMappingDocument = {

        val mdPath = props.mappingDocumentFilePath

        if (logger.isDebugEnabled) logger.debug("Creating R2RMLMappingDocument ");

        val model = ModelFactory.createDefaultModel();
        // use the FileManager to find the input file
        val in = FileManager.get().open(mdPath);
        if (in == null) {
            throw new IllegalArgumentException("Mapping File: " + mdPath + " not found");
        }

        logger.info("Parsing mapping document " + mdPath);
        // read the Turtle file
        model.read(in, null, "TURTLE");

        // Infer TriplesMap from properties
        inferR2RMLClasses(model)

        // Get the list of JENA resources representing triples maps
        val tmList = model.listResourcesWithProperty(RDF.`type`, Constants.R2RML_TRIPLESMAP_CLASS);

        // Build an equivalent list of R2RMLTriplesMap instances, id is the name of the triples map local name (like #MyTiplesMap) 
        val classMappings = if (tmList != null) {
            tmList.map(tmRes => {
                // From that point on, each triples map is browsed in depth to create the whole model (logical source, term maps)
                val tm = R2RMLTriplesMap(tmRes, R2RMLMappingDocument.readReferenceFormulation(props));
                tm;
            })
        } else {
            logger.warn("The mapping graph contains NO TriplesMap definition.")
            Set.empty
        }

        // From the list of R2RMLTriplesMap, create an R2RML mapping document
        val classMappingsSet = classMappings.toSet;
        val nsPrefixMap = model.getNsPrefixMap().toMap;
        val md = new R2RMLMappingDocument(classMappingsSet, mdPath, nsPrefixMap)
        md
    }

    private def inferR2RMLClasses(model: Model) {

        inferTriplesMaps(model)
        // Expand all shortcut properties to the long version
        //expandConstantShortCuts(model, Constants.R2RML_SUBJECT_PROPERTY,Constants.R2RML_SUBJECTMAP_PROPERTY)
        //expandConstantShortCuts(model, Constants.R2RML_PREDICATE_PROPERTY,Constants.R2RML_PREDICATEMAP_PROPERTY)
        //expandConstantShortCuts(model, Constants.R2RML_OBJECT_PROPERTY,Constants.R2RML_OBJECTMAP_PROPERTY)
        //expandConstantShortCuts(model, Constants.R2RML_GRAPH_PROPERTY,Constants.R2RML_GRAPHMAP_PROPERTY)
    }

    private def expandConstantShortCuts(model: Model, constantProp: Property, longProp: Property) {

        // Find all statements with constant property 
        val stmts = model.listStatements(null, constantProp, null)
        for (stmt <- stmts) {

            // Create a statement _:bnId rr:constant "value"
            val bn = model.createResource()
            val constStmt = model.createStatement(bn, Constants.R2RML_CONSTANT_PROPERTY, stmt.getObject())
            model.add(constStmt)

            // Create the statement with the long property: <resource> rr:subjectMap _:bnId;
            val newStmt = model.createStatement(stmt.getSubject(), longProp, bn)
            model.add(newStmt)

            logger.info("Added statment: " + newStmt)
            logger.info("Added statment: " + constStmt)
        }
    }

    /**
     *  Add triples with rdf:type rr:TriplesMap for resources that have one rr:logicalTable
     *  or one xrr:logicalSource property.
     */
    private def inferTriplesMaps(model: Model) {
        val stmtsLogTab = model.listStatements(null, Constants.R2RML_LOGICALTABLE_PROPERTY, null)
        for (stmt <- stmtsLogTab) {
            val stmtType = model.createStatement(stmt.getSubject, RDF.`type`, Constants.R2RML_TRIPLESMAP_CLASS)
            model.add(stmtType)
        }

        val stmtsLogSrc = model.listStatements(null, Constants.xR2RML_LOGICALSOURCE_PROPERTY, null)
        for (stmt <- stmtsLogSrc) {
            val stmtType = model.createStatement(stmt.getSubject, RDF.`type`, Constants.R2RML_TRIPLESMAP_CLASS)
            model.add(stmtType)
        }
    }

    /**
     * Read the reference formulation from the config properties and checks it has is an authorized value.
     * @throws es.upm.fi.dia.oeg.morph.base.exception.MorphException in case the value in the configuration is invalid
     */
    private def readReferenceFormulation(props: MorphProperties): String = {
        var result = Constants.xR2RML_REFFORMULATION_COLUMN

        val refFormConf = props.databaseReferenceFormulation
        if (refFormConf != null) {
            // Verify that the formulation is part of the list of authorized reference formulations
            if (Constants.xR2RML_AUTHZD_REFERENCE_FORMULATION.contains(refFormConf))
                result = refFormConf
            else {
                val msg = "Unknown reference formulation: " + refFormConf
                logger.fatal(msg)
                throw new MorphException(msg)
            }
        }
        result
    }

}