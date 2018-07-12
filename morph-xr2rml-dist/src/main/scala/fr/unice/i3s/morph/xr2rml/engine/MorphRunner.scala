package fr.unice.i3s.morph.xr2rml.engine

import java.net.URL

import scala.io.Source

import org.apache.commons.cli.BasicParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator

import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunnerFactory
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import fr.unice.i3s.morph.xr2rml.server.SparqlEndpoint

/**
 * MorphRunner is the main entry point of the Morph-xR2RML application.
 * It expects two input parameters: the configuration directory (option --configDir) and the
 * configuration file name (option --configFile) that must be located in the configuration directory.
 * The configuration file lists, among others, the name of the mapping file and the output file.
 *
 * @author Franck Michel, I3S laboratory
 */
object MorphRunner {

    def main(args: Array[String]) {

        val overrideLlog4j = System.getProperty("log4j.configuration")
        val log4jfile =
            if (overrideLlog4j != null && !overrideLlog4j.isEmpty)
                new URL(overrideLlog4j)
            else {
                println("To override log4j configuration add JVM option: -Dlog4j.configuration=file:/home/.../your_log4j.properties")
                this.getClass.getClassLoader.getResource("xr2rml-log4j.properties")
            }

        println("Loading log4j configuration: " + log4jfile)
        PropertyConfigurator.configure(log4jfile)
        val logger = Logger.getLogger(this.getClass())

        val whereami = System.getProperty("user.dir")
        logger.info("Current directory    = " + whereami)

        try {
            // Default config dir and file
            var configDir = "example_mongo"
            var configFile = "morph.properties"

            // Parse the command line arguments
            val options: org.apache.commons.cli.Options = new org.apache.commons.cli.Options()
            options.addOption("d", "configDir", true, "Configuration directory")
            options.addOption("f", "configFile", true, "Configuration file name. Must be located within the configuration directory")
            options.addOption("m", "mappingFile", true, "xR2RML mapping file name. Overrides the properties file.")
            options.addOption("o", "output", true, "Output file name. Overrides the properties file.")

            val parser: CommandLineParser = new BasicParser()
            val cmd: CommandLine = parser.parse(options, args)
            if (cmd.hasOption("d")) configDir = cmd.getOptionValue("d")
            if (cmd.hasOption("f")) configFile = cmd.getOptionValue("f")

            logger.info("properties directory = " + configDir)
            logger.info("properties file      = " + configFile)

            val properties = MorphProperties(configDir, configFile)
            // Override mapping and output files
            if (cmd.hasOption("m")) {
                properties.mappingDocumentFilePath = cmd.getOptionValue("m")
                logger.info("Mapping file = " + properties.mappingDocumentFilePath)
            }
            if (cmd.hasOption("o")) {
                properties.outputFilePath = cmd.getOptionValue("o")
                logger.info("Output file = " + properties.outputFilePath)
            }

            // Initialize the runner factory
            MorphBaseRunnerFactory.initFactory(properties)

            if (properties.serverActive) {
                // --- Create the SPARQL endpoint and wait for queries
                SparqlEndpoint.create(properties)

            } else {
                // --- Create a runner factory and a runner
                val factory = MorphBaseRunnerFactory.createFactory
                val runner = factory.createRunner
                runner.run
                if (properties.outputDisplay) {
                    logger.info("Query result:")
                    Source.fromFile(properties.outputFilePath).foreach { print }
                }
            }

            logger.info("Treatment completed, exiting.");

        } catch {
            case e: com.hp.hpl.jena.n3.turtle.TurtleParseException => {
                logger.fatal("Invalid xR2RML document, parsing error: " + e.getMessage)
                e.printStackTrace()
                System.exit(-1)
            }
            case e: MorphException => {
                logger.fatal("An error has occured: " + e.getMessage())
                e.printStackTrace()
                System.exit(-1)
            }
            case e: Exception => {
                logger.fatal("An unexpected error occured: " + e.getMessage())
                e.printStackTrace()
                System.exit(-1)
            }
        }
    }
}