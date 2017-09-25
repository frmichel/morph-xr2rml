package fr.unice.i3s.morph.xr2rml

import org.junit.Test
import com.hp.hpl.jena.rdf.model.ModelFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.FileReader

class LoadSaveGraph {

    @Test def taxonConcept() {
        val model = ModelFactory.createDefaultModel
        val OUTPUT = "/C:/Users/fmichel/Desktop/txn_lod.ttl"

        println("Reading file 1")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000001.ttl", null, "TURTLE")
        println("Reading file 2")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000002.ttl", null, "TURTLE")
        println("Reading file 3")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000003.ttl", null, "TURTLE")
        println("Reading file 4")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000004.ttl", null, "TURTLE")

        println("Saving to output")
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "TURTLE", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }

    @Test def ncbiTaxon() {
        val model = ModelFactory.createDefaultModel
        val OUTPUT = "/C:/Users/fmichel/Desktop/ncbitaxon.ttl"

        println("Reading file")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/NCBITaxon/ncbitaxon.owl", null, "RDF/XML")

        println("Saving to output")
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "TURTLE", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }
}
