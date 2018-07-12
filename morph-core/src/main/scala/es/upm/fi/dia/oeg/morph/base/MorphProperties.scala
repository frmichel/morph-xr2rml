package es.upm.fi.dia.oeg.morph.base

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

import org.apache.log4j.Logger

import es.upm.fi.dia.oeg.morph.base.exception.MorphException

class MorphProperties extends java.util.Properties {
    val logger = Logger.getLogger(this.getClass());

    var configurationFileURL: String = null;
    var configurationDirectory: String = null

    var serverActive: Boolean = false
    var serverPort: Int = -1

    var mappingDocumentFilePath: String = null;
    var outputFilePath: String = null;
    var queryFilePath: Option[String] = None;
    var outputSyntaxRdf: String = null;
    var outputSyntaxResult: String = null;
    var outputDisplay: Boolean = true;
    var jenaMode: String = null;
    var databaseType: String = null;
    var runnerFactoryClassName: String = null;

    //query optimizer
    var reorderSTG = true;
    var selfJoinElimination = true;
    var selfUnionElimination = true;
    var propagateConditionFromJoin = true;
    var subQueryElimination = true;
    var transJoinSubQueryElimination = true;
    var transSTGSubQueryElimination = true;
    var subQueryAsView = false;
    var cacheQueryResult: Boolean = false
    var sparqlOptimization: Boolean = true

    //batch upgrade
    var literalRemoveStrangeChars: Boolean = true;
    var literalTrim: Boolean = true;
    
    var encodeUnsafeCharsInUri: Boolean = true;
    var encodeUnsafeCharsInDbValues: Boolean = true;
    var transformString: Option[String] = None;
    var mapDataTranslationLimits: Map[String, String] = Map.empty;
    var mapDataTranslationOffsets: Map[String, String] = Map.empty;

    //database
    var noOfDatabase = 0;
    var databaseDriver: String = null;
    var databaseURL: String = null;
    var databaseName: String = null;
    var databaseUser: String = null;
    var databasePassword: String = null;
    var databaseTimeout = 0;
    var databaseReferenceFormulation: String = null;

    //uri encoding
    //var mapURIEncodingChars: Map[String, String] = Map.empty;

    var apacheSpark: Boolean = false
    var apacheSparkMaster: String = null
    var apacheSparkThreshold: Int = 0

    def readConfigurationFile() = {

        this.noOfDatabase = this.readInteger(Constants.NO_OF_DATABASE_NAME_PROP_NAME, 0);
        if (this.noOfDatabase != 0 && this.noOfDatabase != 1) {
            throw new Exception("Only zero or one database is supported.");
        }

        for (i <- 0 until noOfDatabase) {
            val propertyDatabaseDriver = Constants.DATABASE_DRIVER_PROP_NAME + "[" + i + "]";
            this.databaseDriver = this.getProperty(propertyDatabaseDriver);

            val propertyDatabaseURL = Constants.DATABASE_URL_PROP_NAME + "[" + i + "]";
            this.databaseURL = this.getProperty(propertyDatabaseURL);

            val propertyDatabaseName = Constants.DATABASE_NAME_PROP_NAME + "[" + i + "]";
            this.databaseName = this.getProperty(propertyDatabaseName);

            val propertyDatabaseUser = Constants.DATABASE_USER_PROP_NAME + "[" + i + "]";
            this.databaseUser = this.getProperty(propertyDatabaseUser);

            val propertyDatabasePassword = Constants.DATABASE_PWD_PROP_NAME + "[" + i + "]";
            this.databasePassword = this.getProperty(propertyDatabasePassword);

            val propertyDatabaseType = Constants.DATABASE_TYPE_PROP_NAME + "[" + i + "]";
            this.databaseType = this.getProperty(propertyDatabaseType);

            val propertyDatabaseTimeout = Constants.DATABASE_TIMEOUT_PROP_NAME + "[" + i + "]";
            val timeoutPropertyString = this.getProperty(propertyDatabaseTimeout);
            if (timeoutPropertyString != null && !timeoutPropertyString.equals("")) {
                this.databaseTimeout = Integer.parseInt(timeoutPropertyString.trim());
            }

            val propertyrefForm = Constants.DATABASE_REFERENCE_FORMULATION + "[" + i + "]";
            this.databaseReferenceFormulation = this.getProperty(propertyrefForm, Constants.xR2RML_REFFORMULATION_COLUMN);
        }

        this.mappingDocumentFilePath = this.readString(Constants.MAPPINGDOCUMENT_FILE_PATH, null);
        if (this.mappingDocumentFilePath != null) {
            val isNetResourceMapping = GeneralUtility.isNetResource(this.mappingDocumentFilePath);
            if (!isNetResourceMapping && configurationDirectory != null) {
                this.mappingDocumentFilePath = configurationDirectory + mappingDocumentFilePath;
            }
        }

        val queryFilePathPropertyValue = this.getProperty(Constants.QUERYFILE_PROP_NAME);
        if (queryFilePathPropertyValue != null && !queryFilePathPropertyValue.equals("")) {
            this.queryFilePath = Some(queryFilePathPropertyValue);
        }

        this.serverActive = this.readBoolean(Constants.SERVER_ACTIVE, false)
        logger.info("Server active = " + this.serverActive)

        this.serverPort = this.readInteger(Constants.SERVER_PORT, 8080)
        logger.info("Server port number = " + this.serverPort)

        this.outputFilePath = this.getProperty(Constants.OUTPUTFILE_PROP_NAME, "result.txt")
        logger.info("Output file = " + this.outputFilePath)

        if (configurationDirectory != null) {
            this.outputFilePath = configurationDirectory + outputFilePath

            if (this.queryFilePath.isDefined) {
                val isNetResourceQuery = GeneralUtility.isNetResource(queryFilePath.get);
                if (!isNetResourceQuery)
                    this.queryFilePath = Some(configurationDirectory + queryFilePath.get);
            }
        }

        this.outputSyntaxRdf = this.readString(Constants.OUTPUT_SYNTAX_RDF, Constants.DEFAULT_OUTPUT_FORMAT);
        if (outputSyntaxRdf != Constants.OUTPUT_FORMAT_RDFXML &&
            outputSyntaxRdf != Constants.OUTPUT_FORMAT_NTRIPLE &&
            outputSyntaxRdf != Constants.OUTPUT_FORMAT_TURTLE &&
            outputSyntaxRdf != Constants.OUTPUT_FORMAT_N3 &&
            outputSyntaxRdf != Constants.OUTPUT_FORMAT_JSONLD) {
            throw new MorphException("Invalid value \"" + outputSyntaxRdf + "\" for property " + Constants.OUTPUT_SYNTAX_RDF)
        }
        logger.info("Output RDF syntax = " + this.outputSyntaxRdf);

        this.outputSyntaxResult = this.readString(Constants.OUTPUT_SYNTAX_RESULT, Constants.OUTPUT_FORMAT_RESULT_XML);
        if (outputSyntaxResult != Constants.OUTPUT_FORMAT_RESULT_XML &&
            outputSyntaxResult != Constants.OUTPUT_FORMAT_RESULT_JSON &&
            outputSyntaxResult != Constants.OUTPUT_FORMAT_RESULT_CSV &&
            outputSyntaxResult != Constants.OUTPUT_FORMAT_RESULT_TSV) {
            throw new MorphException("Invalid value \"" + outputSyntaxResult + "\" for property " + Constants.OUTPUT_SYNTAX_RESULT)
        }
        logger.info("Output SPARQL result syntax = " + this.outputSyntaxRdf);

        this.outputDisplay = this.readBoolean(Constants.OUTPUTFILE_DISPLAY, true);
        logger.info("Display result on std output = " + this.outputDisplay);

        this.jenaMode = this.readString(Constants.JENA_MODE_TYPE, Constants.JENA_MODE_TYPE_MEMORY);
        logger.info("Jena mode = " + jenaMode);

        this.sparqlOptimization = this.readBoolean(Constants.OPTIMIZE_SPARQL, true);
        logger.info("SPARQL Query Optimization = " + this.sparqlOptimization);

        this.selfJoinElimination = this.readBoolean(Constants.OPTIMIZE_TB, true);
        logger.info("Optimization - Self join elimination = " + this.selfJoinElimination);

        this.selfUnionElimination = this.readBoolean(Constants.OPTIMIZE_SU, true);
        logger.info("Optimization - Self union elimination = " + this.selfUnionElimination);

        this.propagateConditionFromJoin = this.readBoolean(Constants.OPTIMIZE_PROPCONDJOIN, true);
        logger.info("Optimization - Propagate conditions from a joined query = " + this.propagateConditionFromJoin);

        this.reorderSTG = this.readBoolean(Constants.REORDER_STG, true);
        logger.info("Optimization - Reorder STG = " + this.reorderSTG);

        this.subQueryElimination = this.readBoolean(Constants.SUBQUERY_ELIMINATION, true);
        logger.info("Optimization - Subquery elimination = " + this.subQueryElimination);

        this.transJoinSubQueryElimination = this.readBoolean(Constants.TRANSJOIN_SUBQUERY_ELIMINATION, true);
        logger.info("Optimization - Trans join subquery elimination = " + this.transJoinSubQueryElimination);

        this.transSTGSubQueryElimination = this.readBoolean(Constants.TRANSSTG_SUBQUERY_ELIMINATION, true);
        logger.info("Optimization - Trans stg subquery elimination = " + this.transSTGSubQueryElimination);

        this.subQueryAsView = this.readBoolean(Constants.SUBQUERY_AS_VIEW, false);
        logger.info("Optimization - Subquery as view = " + this.subQueryAsView);

        this.cacheQueryResult = this.readBoolean(Constants.CACHE_QUERY_RESULT, false);
        logger.info("Cache the result of queries for join evaluation (non Relational DBs) = " + this.cacheQueryResult);

        this.runnerFactoryClassName = this.readString(Constants.RUNNER_FACTORY_CLASSNAME, null);
        if (runnerFactoryClassName == null) {
            logger.error("Mandatory parameter " + Constants.RUNNER_FACTORY_CLASSNAME + " is missing.")
            System.exit(-1)
        }
        logger.info("RunnerFactory = " + runnerFactoryClassName);

        this.literalRemoveStrangeChars = this.readBoolean(Constants.REMOVE_STRANGE_CHARS_FROM_LITERAL, true);
        logger.info("Remove Strange Chars From Literal Column = " + this.literalRemoveStrangeChars);

        this.literalTrim = this.readBoolean(Constants.TRIM_LITERAL, true);
        logger.info("Trim heading/tailing spaces from literal Column = " + this.literalTrim);

        this.encodeUnsafeCharsInUri = this.readBoolean(Constants.ENCODE_UNSAFE_CHARS_IN_URI, true);
        logger.info("URL-encode reserved chars IRI tempalte string = " + this.encodeUnsafeCharsInUri);

        this.encodeUnsafeCharsInDbValues = this.readBoolean(Constants.ENCODE_UNSAFE_CHARS_IN_DB_VALUES, true);
        logger.info("URL-encode reserved chars in database values = " + this.encodeUnsafeCharsInDbValues);

        this.transformString = this.readString(MorphProperties.TRANSFORM_STRING_PROPERTY, None);
        logger.info("String transformation = " + this.transformString);

        //this.mapURIEncodingChars = this.readMapStringString(MorphProperties.URI_ENCODE_PROPERTY, Map.empty);
        // Example: uri.encode=(" "->"%20"),,(","->"")

        this.mapDataTranslationLimits = this.readMapStringString(MorphProperties.DATATRANSLATION_LIMIT, Map.empty);
        this.mapDataTranslationOffsets = this.readMapStringString(MorphProperties.DATATRANSLATION_OFFSET, Map.empty);

        /* this.apacheSpark = this.readBoolean(Constants.SPARK, false)
        logger.info("Use Apache Spark for parallel processing = " + this.apacheSpark)

        this.apacheSparkThreshold = this.readInteger(Constants.SPARK_THRESHOLD, 10000)
        logger.info("Use Apache Spark only for processing over " + this.apacheSparkThreshold + " documents")

        if (this.apacheSpark) {
            this.apacheSparkMaster = this.readString(Constants.SPARK_MASTER, "local[*]")
            logger.info("Apache Spark master URL = " + this.apacheSparkMaster)
        } */
    }

    def readMapStringString(property: String, defaultValue: Map[String, String]): Map[String, String] = {
        val propertyString = this.readString(property, None);
        if (propertyString.isDefined) {
            val propertyStringSplited = propertyString.get.split(",,");
            val result = propertyStringSplited.map(x => {
                val resultElement = x.substring(1, x.length() - 1).split("->");
                val resultKey = resultElement(0).substring(1, resultElement(0).length() - 1);
                val resultValue = resultElement(1).substring(1, resultElement(1).length() - 1)
                val resultAux = (resultKey -> resultValue);
                resultAux;
            })
            result.toMap;
        } else {
            defaultValue;
        }

    }

    def readBoolean(property: String, defaultValue: Boolean): Boolean = {
        val propertyString = this.getProperty(property);
        val result = if (propertyString != null) {
            if (propertyString.equalsIgnoreCase("yes") || propertyString.equalsIgnoreCase("true")) {
                true;
            } else if (propertyString.equalsIgnoreCase("no") || propertyString.equalsIgnoreCase("false")) {
                false;
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }

        result;
    }

    def readInteger(property: String, defaultValue: Int): Int = {

        val propertyString = this.getProperty(property);
        val result = if (propertyString != null && !propertyString.equals("")) {
            Integer.parseInt(propertyString)
        } else {
            defaultValue
        }

        result;
    }

    def readString(property: String, defaultValue: String): String = {
        val propertyString = this.getProperty(property);
        val result = if (propertyString != null && !propertyString.equals("")) {
            propertyString;
        } else { defaultValue }
        return result;
    }

    def readString(property: String, defaultValue: Option[String]): Option[String] = {
        val propertyString = this.getProperty(property);
        val result = if (propertyString != null && !propertyString.equals("")) {
            Some(propertyString);
        } else { defaultValue }
        result;
    }

    def readListString(property: String, defaultValue: List[String], separator: String): List[String] = {
        val propertyString = this.getProperty(property);
        val result = if (propertyString != null && !propertyString.equals("")) {
            propertyString.split(separator).toList
        } else { defaultValue }
        result;
    }

    def setNoOfDatabase(x: Int) = { this.noOfDatabase = x }
    def setDatabaseUser(dbUser: String) = { this.databaseUser = dbUser }
    def setDatabaseURL(dbURL: String) = { this.databaseURL = dbURL }
    def setDatabasePassword(dbPassword: String) = { this.databasePassword = dbPassword }
    def setDatabaseName(dbName: String) = { this.databaseName = dbName }
    def setDatabaseDriver(dbDriver: String) = { this.databaseDriver = dbDriver }
    def setDatabaseType(dbType: String) = { this.databaseType = dbType }
    def setMappingDocumentFilePath(mdPath: String) = { this.mappingDocumentFilePath = mdPath }

    def setQueryFilePath(queryFilePath: String) = {
        this.queryFilePath = if (queryFilePath == null || queryFilePath.equals("")) {
            None
        } else { Some(queryFilePath) }
    }

    def setOutputFilePath(outputPath: String) = {
        this.outputFilePath =
            if (outputPath == null) ""
            else { outputPath }
    }
}

object MorphProperties {
    val logger = Logger.getLogger(this.getClass());

    val TRANSFORM_STRING_PROPERTY = "transform.string";

    val URI_ENCODE_PROPERTY = "uri.encode";
    val URI_TRANSFORM_PROPERTY = "uri.transform";

    val DATATRANSLATION_LIMIT = "datatranslation.limit";
    val DATATRANSLATION_OFFSET = "datatranslation.offset";

    def apply(pConfigurationDirectory: String, configurationFile: String): MorphProperties = {
        val properties = new MorphProperties();

        var configFile = configurationFile;
        var configDir = pConfigurationDirectory;

        if (configDir != null) {
            if (!configDir.endsWith(File.separator)) {
                configDir = configDir + File.separator;
            }
            configFile = configDir + configurationFile;
        }
        properties.configurationFileURL = configFile;
        properties.configurationDirectory = configDir;

        println("Reading configuration file : " + configFile);
        try {
            properties.load(new FileInputStream(configFile));
        } catch {
            case e: FileNotFoundException => {
                val errorMessage = "Configuration file not found: " + configFile;
                logger.error(errorMessage);
                e.printStackTrace();
                throw e;
            }
            case e: IOException => {
                val errorMessage = "Error reading configuration file: " + configFile;
                logger.error(errorMessage);
                e.printStackTrace();
                throw e;
            }
        }

        properties.readConfigurationFile();
        properties
    }
}
