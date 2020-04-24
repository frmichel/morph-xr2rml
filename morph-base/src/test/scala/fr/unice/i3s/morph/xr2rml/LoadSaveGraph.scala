package fr.unice.i3s.morph.xr2rml

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

import org.apache.jena.rdf.model.ModelFactory
import org.junit.Test

class LoadSaveGraph {

    @Test def taxonConcept() {
        val model = ModelFactory.createDefaultModel

        println("Reading file 1")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000001.ttl", null, "TURTLE")
        println("Reading file 2")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000002.ttl", null, "TURTLE")
        println("Reading file 3")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000003.ttl", null, "TURTLE")
        println("Reading file 4")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/TaxonConcept/txn_lod000004.ttl", null, "TURTLE")

        println("Saving to output")
        val OUTPUT = "/C:/Users/fmichel/Desktop/txn_lod.ttl"
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "TURTLE", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }

    @Test def ncbiTaxon() {
        val model = ModelFactory.createDefaultModel

        println("Reading file")
        model.read("file:///C:/Users/fmichel/Documents/Projets/Zoomathia/Reference Ontologies/NCBITaxon/ncbitaxon.owl", null, "RDF/XML")

        println("Saving to output")
        val OUTPUT = "/C:/Users/fmichel/Desktop/ncbitaxon.ttl"
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "TURTLE", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }

    @Test def convertTurtleToXml() {
        val model = ModelFactory.createDefaultModel

        println("Reading file")
        model.read("file:///C:/Users/fmichel/Documents/Projets/TAXREF/TAXREF-LD/Taxrefld10_BN_explicit.ttl", null, "TURTLE")

        println("Saving to output")
        val OUTPUT = "/C:/Users/fmichel/Documents/Projets/TAXREF/TAXREF-LD/Taxrefld10_BN_explicit.xml"
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "RDF/XML", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }

    @Test def convertJson() {
        val model = ModelFactory.createDefaultModel

        println("Reading file")
        //model.read("file:///C:/Users/fmichel/Desktop/flickr.jsonld", null, "JSON-LD")
        //model.read("https://erebe-vm2.i3s.unice.fr/~fmichel/flickr-jsonld.json", null, "JSON-LD")
        model.read("http://erebe-vm2.i3s.unice.fr/jsonld/flickr.json", null, "JSON-LD")
        
        println("Saving to output")
        val OUTPUT = "/C:/Users/fmichel/Desktop/flickr.ttl"
        val outputStream = new FileOutputStream(new File(OUTPUT))
        val writer = new OutputStreamWriter(outputStream, "UTF-8")
        model.write(writer, "TURTLE", null)
        outputStream.flush
        outputStream.close

        println("Done.")
    }
}
