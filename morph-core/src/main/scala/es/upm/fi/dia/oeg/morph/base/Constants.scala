package es.upm.fi.dia.oeg.morph.base

import com.hp.hpl.jena.rdf.model.ResourceFactory

import Zql.ZConstant
import Zql.ZExpression

class Constants {

}

object Constants {

    val SEPARATOR = System.getProperty("line.separator")

    /**
     * Types of term maps apply to both nested term maps and regular term maps.
     * Only exception: SimpleNestedTermMap is the type of a nested term map with no xrr:reference nor rr:template property inside.
     * But a nested term map with an xrr:reference resp. rr:template property takes the type ReferenceTermMap resp. TemplateTermMap.
     */
    object MorphTermMapType extends Enumeration {
        type MorphTermMapType = Value
        val ConstantTermMap, ColumnTermMap, TemplateTermMap, ReferenceTermMap, SimpleNestedTermMap, InvalidTermMapType = Value
    }

    object MorphPOS extends Enumeration {
        type MorphPOS = Value
        val sub, pre, obj, graph = Value
    }

    object LogicalTableType extends Enumeration {
        type LogicalTableType = Value
        val TABLE_NAME, QUERY = Value
    }

    object DatabaseType extends Enumeration {
        type DatabaseType = Value
        val Relational, MongoDB = Value
    }

    // Namespaces
    val R2RML_NS = "http://www.w3.org/ns/r2rml#";
    val xR2RML_NS = "http://i3s.unice.fr/xr2rml#";
    val RML_NS = "http://semweb.mmlab.be/ns/rml#";
    val QL_NS = "http://semweb.mmlab.be/ns/ql#";
    val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

    // SQL Vocabulary
    val JOINS_TYPE_INNER = "INNER";
    val JOINS_TYPE_LEFT = "LEFT";
    val SQL_LOGICAL_OPERATOR_AND = "AND";
    val SQL_LOGICAL_OPERATOR_OR = "OR";
    val SQL_KEYWORD_UNION = "UNION";
    val SQL_KEYWORD_ORDER_BY = "ORDER BY";
    val SQL_EXPRESSION_TRUE = new ZExpression("=", new ZConstant("1", ZConstant.NUMBER), new ZConstant("1", ZConstant.NUMBER));
    val SQL_EXPRESSION_FALSE = new ZExpression("=", new ZConstant("1", ZConstant.NUMBER), new ZConstant("0", ZConstant.NUMBER));
    val MAP_ZSQL_CUSTOM_FUNCTIONS = Map("CONCAT" -> 2, "SUBSTRING" -> 3, "CONVERT" -> 2, "COALESCE" -> 2, "ABS" -> 1, "LOWER" -> 1, "UPPER" -> 1, "REPLACE" -> 3, "TRIM" -> 1);

    // Document databases
    val DATABASE_MONGODB = "MongoDB";

    // Relational databases
    val DATABASE_MYSQL = "MySQL";
    val DATABASE_DEFAULT = DATABASE_MYSQL;
    val DATABASE_MONETDB = "MonetDB";
    val DATABASE_ORACLE = "Oracle";
    val DATABASE_SQLSERVER = "SQLServer";
    val DATABASE_POSTGRESQL = "PostgreSQL";
    val DATABASE_GFT = "GFT";

    val DATABASE_POSTGRESQL_ENCLOSED_CHARACTER = "\"";
    val DATABASE_MONETDB_ENCLOSED_CHARACTER = "\"";
    val DATABASE_MYSQL_ENCLOSED_CHARACTER = "`";
    val DATABASE_GFT_ENCLOSED_CHARACTER = "'";
    val DATABASE_ENCLOSED_CHARACTERS = List(
        DATABASE_POSTGRESQL_ENCLOSED_CHARACTER, DATABASE_MONETDB_ENCLOSED_CHARACTER, DATABASE_MYSQL_ENCLOSED_CHARACTER, DATABASE_GFT_ENCLOSED_CHARACTER)

    val POSTGRESQL_COLUMN_TYPE_TEXT = "text";
    val POSTGRESQL_COLUMN_TYPE_INTEGER = "integer";
    val MONETDB_COLUMN_TYPE_TEXT = "clob";
    val MONETDB_COLUMN_TYPE_INTEGER = "integer";
    val MAP_DATABASE_COLUMN_TYPE_TEXT = Map(
        DATABASE_POSTGRESQL -> POSTGRESQL_COLUMN_TYPE_TEXT, DATABASE_MONETDB -> MONETDB_COLUMN_TYPE_TEXT);

    // Jena specific properties
    val JENA_MODE_TYPE = "jena.mode.type";
    val JENA_MODE_TYPE_MEMORY = "memory";
    val JENA_MODE_TYPE_HSQL = "hsql";
    val JENA_MODE_TYPE_TDB = "tdb";
    val JENA_TDB_DIRECTORY = "jena.tdb.dir";
    val JENA_TDB_FILEBASE = "jena.tdb.filebase";

    // SPARQL result format
    val OUTPUT_FORMAT_RESULT_XML = "XML";
    val OUTPUT_FORMAT_RESULT_JSON = "JSON";
    val OUTPUT_FORMAT_RESULT_CSV = "CSV";
    val OUTPUT_FORMAT_RESULT_TSV = "TSV";

    // RDF format 
    val OUTPUT_FORMAT_RDFXML = "RDF/XML";
    val OUTPUT_FORMAT_RDFXML_ABBREV = "RDF/XML-ABBREV";
    val OUTPUT_FORMAT_NTRIPLE = "N-TRIPLE";
    val OUTPUT_FORMAT_NQUAD = "N-QUAD";
    val OUTPUT_FORMAT_TURTLE = "TURTLE";
    val OUTPUT_FORMAT_N3 = "N3";
    val OUTPUT_FORMAT_JSONLD = "JSON-LD";
    val DEFAULT_OUTPUT_FORMAT = OUTPUT_FORMAT_TURTLE

    // ------------------------------------------------------
    // Config file properties
    // ------------------------------------------------------

    val SERVER_ACTIVE = "server.active"
    val SERVER_PORT = "server.port"

    // Database
    val NO_OF_DATABASE_NAME_PROP_NAME = "no_of_database";
    val DATABASE_NAME_PROP_NAME = "database.name";
    val DATABASE_DRIVER_PROP_NAME = "database.driver";
    val DATABASE_URL_PROP_NAME = "database.url";
    val DATABASE_USER_PROP_NAME = "database.user";
    val DATABASE_PWD_PROP_NAME = "database.pwd";
    val DATABASE_TYPE_PROP_NAME = "database.type";
    val DATABASE_TIMEOUT_PROP_NAME = "database.timeout";
    val DATABASE_REFERENCE_FORMULATION = "database.reference_formulation";

    val MAPPINGDOCUMENT_FILE_PATH = "mappingdocument.file.path";
    val QUERYFILE_PROP_NAME = "query.file.path";
    val ONTOURL_PROP_NAME = "onto.url.path";
    val OUTPUTFILE_PROP_NAME = "output.file.path";
    val OUTPUT_SYNTAX_RDF = "output.syntax.rdf";
    val OUTPUT_SYNTAX_RESULT = "output.syntax.result";
    val OUTPUTFILE_DISPLAY = "output.display";
    val SPLIT_OUTPUT_PER_CONCEPT = "split_output_per_concept";

    val OPTIMIZE_TB = "querytranslator.abstract.selfjoinelimination";
    val OPTIMIZE_SU = "querytranslator.abstract.selfunionelimination";
    val OPTIMIZE_PROPCONDJOIN = "querytranslator.abstract.propagateconditionfromjoin";
    val OPTIMIZE_SPARQL = "querytranslator.sparql.optimize"

    val REORDER_STG = "querytranslator.reorderstg";
    val SUBQUERY_ELIMINATION = "querytranslator.subqueryelimination";
    val TRANSJOIN_SUBQUERY_ELIMINATION = "querytranslator.transjoin.subqueryelimination";
    val TRANSSTG_SUBQUERY_ELIMINATION = "querytranslator.transstg.subqueryelimination";
    val SUBQUERY_AS_VIEW = "querytranslator.subqueryasview";
    val CACHE_QUERY_RESULT = "querytranslator.cachequeryresult";

    val RUNNER_FACTORY_CLASSNAME = "runner_factory.class.name";

    val QUERY_RESULT_XMLWRITER_OUTPUT_DEFAULT = "output.rdf.xml";

    val REMOVE_STRANGE_CHARS_FROM_LITERAL = "literal.removestrangechars";
    val TRIM_LITERAL = "literal.trim";
    
    val ENCODE_UNSAFE_CHARS_IN_URI = "uricolumn.encode_uri";
    val ENCODE_UNSAFE_CHARS_IN_DB_VALUES = "uricolumn.encode_unsafe_chars_dbvalues";

    val URI_ENCODE = "uri.encode";

    //val SPARK = "apache.spark"
    //val SPARK_MASTER = "apache.spark.master"
    //val SPARK_THRESHOLD = "apache.spark.threshold"

    //aliases
    val URI_AS_ALIAS = "uri_";
    val RANGE_TABLE_ALIAS = "rt_";
    val VIEW_ALIAS = "v_";
    val TABLE_ALIAS_PREFIX = "t_";
    val VIEW_ALIAS_RANDOM_LIMIT = 100000;

    //prefixes and suffixes
    val PREFIX_URI = "uri_";
    val PREFIX_VAR = "var_";
    val PREFIX_LIT = "lit_";
    val KEY_SUFFIX = "_key";
    val PREFIX_SUBJECT_MAPPING = "sm_";
    val PREFIX_MAPPING_ID = "mappingid_";

    //aggregation function
    val AGGREGATION_FUNCTION_AVG = "AVG";
    val AGGREGATION_FUNCTION_MAX = "MAX";
    val AGGREGATION_FUNCTION_MIN = "MIN";
    val AGGREGATION_FUNCTION_COUNT = "COUNT";
    val AGGREGATION_FUNCTION_SUM = "SUM";

    // ------------------------------------------------------
    // R2RML vocabulary 
    // ------------------------------------------------------

    //--- TriplesMap

    val R2RML_TRIPLESMAP_URI = R2RML_NS + "TriplesMap";
    val R2RML_TRIPLESMAP_CLASS = ResourceFactory.createResource(R2RML_TRIPLESMAP_URI);

    val R2RML_OBJECTMAPCLASS_URI = R2RML_NS + "ObjectMap";
    val R2RML_OBJECTSMAP_CLASS = ResourceFactory.createResource(R2RML_OBJECTMAPCLASS_URI);

    val R2RML_LOGICALTABLE_URI = R2RML_NS + "logicalTable";
    val R2RML_LOGICALTABLE_PROPERTY = ResourceFactory.createProperty(R2RML_LOGICALTABLE_URI);

    val xR2RML_LOGICALSOURCE_URI = xR2RML_NS + "logicalSource";
    val xR2RML_LOGICALSOURCE_PROPERTY = ResourceFactory.createProperty(xR2RML_LOGICALSOURCE_URI);

    val R2RML_SUBJECT_URI = R2RML_NS + "subject";
    val R2RML_SUBJECT_PROPERTY = ResourceFactory.createProperty(R2RML_SUBJECT_URI);

    val R2RML_PREDICATE_URI = R2RML_NS + "predicate";
    val R2RML_PREDICATE_PROPERTY = ResourceFactory.createProperty(R2RML_PREDICATE_URI);

    val R2RML_OBJECT_URI = R2RML_NS + "object";
    val R2RML_OBJECT_PROPERTY = ResourceFactory.createProperty(R2RML_OBJECT_URI);

    val R2RML_GRAPH_URI = R2RML_NS + "graph";
    val R2RML_GRAPH_PROPERTY = ResourceFactory.createProperty(R2RML_GRAPH_URI);

    val R2RML_SUBJECTMAP_URI = R2RML_NS + "subjectMap";
    val R2RML_SUBJECTMAP_PROPERTY = ResourceFactory.createProperty(R2RML_SUBJECTMAP_URI);

    val R2RML_PREDICATEOBJECTMAP_URI = R2RML_NS + "predicateObjectMap";
    val R2RML_PREDICATEOBJECTMAP_PROPERTY = ResourceFactory.createProperty(R2RML_PREDICATEOBJECTMAP_URI);

    val R2RML_OBJECTMAP_URI = R2RML_NS + "objectMap";
    val R2RML_OBJECTMAP_PROPERTY = ResourceFactory.createProperty(R2RML_OBJECTMAP_URI);

    val R2RML_PARENTTRIPLESMAP_URI = R2RML_NS + "parentTriplesMap";
    val R2RML_PARENTTRIPLESMAP_PROPERTY = ResourceFactory.createProperty(R2RML_PARENTTRIPLESMAP_URI);

    val R2RML_GRAPHMAP_URI = R2RML_NS + "graphMap";
    val R2RML_GRAPHMAP_PROPERTY = ResourceFactory.createProperty(R2RML_GRAPHMAP_URI);

    val R2RML_CLASS_URI = R2RML_NS + "class";
    val R2RML_CLASS_PROPERTY = ResourceFactory.createProperty(R2RML_CLASS_URI);

    //--- TermType
    val R2RML_TERMTYPE_URI = R2RML_NS + "termType";
    val R2RML_TERMTYPE_PROPERTY = ResourceFactory.createProperty(R2RML_TERMTYPE_URI);

    val R2RML_LITERAL_URI = R2RML_NS + "Literal";
    val R2RML_IRI_URI = R2RML_NS + "IRI";
    val R2RML_BLANKNODE_URI = R2RML_NS + "BlankNode";

    val xR2RML_RDFLIST_URI = xR2RML_NS + "RdfList";
    val xR2RML_RDFLIST_CLASS = ResourceFactory.createResource(xR2RML_RDFLIST_URI);

    val xR2RML_RDFBAG_URI = xR2RML_NS + "RdfBag";
    val xR2RML_RDFBAG_CLASS = ResourceFactory.createResource(xR2RML_RDFBAG_URI);

    val xR2RML_RDFSEQ_URI = xR2RML_NS + "RdfSeq";
    val xR2RML_RDFSEQ_CLASS = ResourceFactory.createResource(xR2RML_RDFSEQ_URI);

    val xR2RML_RDFALT_URI = xR2RML_NS + "RdfAlt";
    val xR2RML_RDFALT_CLASS = ResourceFactory.createResource(xR2RML_RDFALT_URI);

    //--- TermMap
    val R2RML_DATATYPE_URI = R2RML_NS + "datatype";
    val R2RML_DATATYPE_PROPERTY = ResourceFactory.createProperty(R2RML_DATATYPE_URI);

    val R2RML_LANGUAGE_URI = R2RML_NS + "language";
    val R2RML_LANGUAGE_PROPERTY = ResourceFactory.createProperty(R2RML_LANGUAGE_URI);

    val R2RML_LANGUAGE_MAP_URI = xR2RML_NS + "languageReference";
    val R2RML_LANGUAGE_MAP_PROPERTY = ResourceFactory.createProperty(R2RML_LANGUAGE_MAP_URI);

    //--- TermMap Types
    val R2RML_CONSTANT_URI = R2RML_NS + "constant";
    val R2RML_CONSTANT_PROPERTY = ResourceFactory.createProperty(R2RML_CONSTANT_URI);

    val R2RML_COLUMN_URI = R2RML_NS + "column";
    val R2RML_COLUMN_PROPERTY = ResourceFactory.createProperty(R2RML_COLUMN_URI);

    val R2RML_TEMPLATE_URI = R2RML_NS + "template";
    val R2RML_TEMPLATE_PROPERTY = ResourceFactory.createProperty(R2RML_TEMPLATE_URI);

    val R2RML_TEMPLATE_PATTERN = """\{"*\w+\s*[\s\w/]*\"*}"""
    val R2RML_TEMPLATE_PATTERN_WITH_CAPTURING_GRP = """\{(.+?)\}"""

    val xR2RML_REFERENCE_URI = xR2RML_NS + "reference";
    val xR2RML_REFERENCE_PROPERTY = ResourceFactory.createProperty(xR2RML_REFERENCE_URI);

    val xR2RML_PUSHDOWN_URI = xR2RML_NS + "pushDown";
    val xR2RML_PUSHDOWN_PROPERTY = ResourceFactory.createProperty(xR2RML_PUSHDOWN_URI);

    val xR2RML_AS_URI = xR2RML_NS + "as";
    val xR2RML_AS_PROPERTY = ResourceFactory.createProperty(xR2RML_AS_URI);

    //--- Logical table
    val R2RML_TABLENAME_URI = R2RML_NS + "tableName";
    val R2RML_TABLENAME_PROPERTY = ResourceFactory.createProperty(R2RML_TABLENAME_URI);

    val R2RML_SQLQUERY_URI = R2RML_NS + "sqlQuery";
    val R2RML_SQLQUERY_PROPERTY = ResourceFactory.createProperty(R2RML_SQLQUERY_URI);

    // Logical source
    val xR2RML_QUERY_URI = xR2RML_NS + "query";
    val xR2RML_QUERY_PROPERTY = ResourceFactory.createProperty(xR2RML_QUERY_URI);

    val RML_ITERATOR_URI = RML_NS + "iterator";
    val RML_ITERATOR_PROPERTY = ResourceFactory.createProperty(RML_ITERATOR_URI);

    val xR2RML_UNIQUEREF_URI = xR2RML_NS + "uniqueRef";
    val xR2RML_UNIQUEREF_PROPERTY = ResourceFactory.createProperty(xR2RML_UNIQUEREF_URI);

    val xR2RML_SPLIT_URI = xR2RML_NS + "split";
    val xR2RML_SPLIT_PROPERTY = ResourceFactory.createProperty(xR2RML_SPLIT_URI);

    // Reference formulation
    val xR2RML_REFFORMULATION_COLUMN = "Column";
    val xR2RML_REFFORMULATION_JSONPATH = "JSONPath";
    val xR2RML_REFFORMULATION_XPATH = "XPath";

    val xR2RML_AUTHZD_REFERENCE_FORMULATION = Set(xR2RML_REFFORMULATION_JSONPATH, xR2RML_REFFORMULATION_XPATH, xR2RML_REFFORMULATION_COLUMN)

    //--- PredicateObjectMap
    val R2RML_PREDICATEMAP_URI = R2RML_NS + "predicateMap";
    val R2RML_PREDICATEMAP_PROPERTY = ResourceFactory.createProperty(R2RML_PREDICATEMAP_URI);

    val R2RML_REFOBJECTMAP_URI = R2RML_NS + "refObjectMap";
    val R2RML_REFOBJECTMAP_PROPERTY = ResourceFactory.createProperty(R2RML_REFOBJECTMAP_URI);

    val R2RML_JOINCONDITION_URI = R2RML_NS + "joinCondition";
    val R2RML_JOINCONDITION_PROPERTY = ResourceFactory.createProperty(R2RML_JOINCONDITION_URI);

    val R2RML_CHILD_URI = R2RML_NS + "child";
    val R2RML_CHILD_PROPERTY = ResourceFactory.createProperty(R2RML_CHILD_URI);

    val R2RML_PARENT_URI = R2RML_NS + "parent";
    val R2RML_PARENT_PROPERTY = ResourceFactory.createProperty(R2RML_PARENT_URI);

    //graph
    val R2RML_DEFAULT_GRAPH_URI = R2RML_NS + "defaultGraph";
    val R2RML_DEFAULT_GRAPH_CLASS = ResourceFactory.createResource(R2RML_DEFAULT_GRAPH_URI);

    // Nested Term Map
    val xR2RML_NESTEDTM_URI = xR2RML_NS + "nestedTermMap";
    val xR2RML_NESTEDTM_PROPERTY = ResourceFactory.createProperty(xR2RML_NESTEDTM_URI);

    //--- Mixed-syntax paths
    val xR2RML_PATH_CONSTR_COLUMN = "Column";
    val xR2RML_PATH_CONSTR_XPATH = "XPath";
    val xR2RML_PATH_CONSTR_JSONPATH = "JSONPath";
    val xR2RML_PATH_CONSTR_CSV = "CSV";
    val xR2RML_PATH_CONSTR_TSV = "TSV";

    val xR2RML_PATH_CONSTRUCTORS = "(" + xR2RML_PATH_CONSTR_COLUMN + "|" + xR2RML_PATH_CONSTR_XPATH + "|" + xR2RML_PATH_CONSTR_JSONPATH + "|" + xR2RML_PATH_CONSTR_CSV + "|" + xR2RML_PATH_CONSTR_TSV + ")"

    // In the path expressions, characters '/', '(', ')', '{' and '}' must be escaped with a '\'.
    // In the regex, these will appear as groups (\\\/), (\\\(), (\\\)), (\\\{) or (\\\}): escaped '\' + escaped char '/', '(', ')', '{' or '}'
    // Other characters must not be escaped: alpha numerical chars, as well as: !#%&,-./:;<=>?@_`|~[]"'*+^$
    val xR2RML_PATH_EXPR_CHARS = """([\p{Alnum}\p{Space}!#%&,-.:;<=>?(\\@)_`\|~\[\]\"\'\*\+\^\$]|(\\/)|(\\\()|(\\\))|(\\\{)|(\\\}))+"""

    val xR2RML_MIXED_SYNTX_PATH_REGEX = (xR2RML_PATH_CONSTRUCTORS + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r

    val xR2RML_PATH_COLUMN_REGEX = (xR2RML_PATH_CONSTR_COLUMN + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r
    val xR2RML_PATH_XPATH_REGEX = (xR2RML_PATH_CONSTR_XPATH + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r
    val xR2RML_PATH_JSONPATH_REGEX = (xR2RML_PATH_CONSTR_JSONPATH + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r
    val xR2RML_PATH_CSV_REGEX = (xR2RML_PATH_CONSTR_CSV + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r
    val xR2RML_PATH_TSV_REGEX = (xR2RML_PATH_CONSTR_TSV + """\(""" + xR2RML_PATH_EXPR_CHARS + """\)""").r
    /**
     * Return the enclosed character depending on the type of database
     * @param dbType the database type
     */
    def getEnclosedCharacter(dbType: String): String = {
        if (Constants.DATABASE_GFT.equalsIgnoreCase(dbType)) {
            Constants.DATABASE_GFT_ENCLOSED_CHARACTER;
        } else if (Constants.DATABASE_MONETDB.equalsIgnoreCase(dbType)) {
            Constants.DATABASE_MONETDB_ENCLOSED_CHARACTER;
        } else if (Constants.DATABASE_MYSQL.equalsIgnoreCase(dbType)) {
            Constants.DATABASE_MYSQL_ENCLOSED_CHARACTER;
        } else if (Constants.DATABASE_POSTGRESQL.equalsIgnoreCase(dbType)) {
            Constants.DATABASE_POSTGRESQL_ENCLOSED_CHARACTER;
        } else {
            ""
        }
    }

    val MAP_DEFAULT_URI_ENCODING_CHARS = Map(" " -> "%20", "," -> "%2C", "\\(" -> "%28", "\\)" -> "%29");
}