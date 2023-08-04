# Configuration file reference

Below if the default configuration file, morph.properties:

```
# -- xR2RML mapping file (Mandatory):
# path relative to the configuration directory given in parameter --configDir
mappingdocument.file.path=mapping1.ttl

# -- Server mode: true|false. Default: false
# false: stand-alone application that performs either graph materialization or query rewriting
# true:  SPARQL endpoint with query rewriting
server.active=false

# -- Server port number, ignored when "server.active=false". Default: 8080
server.port=8080

# -- Processing result output file, relative to --configDir. Default: result.txt
output.file.path=result.txt

# -- Max number of triples to generate in output file. Default: 0 (no limit)
# If the max number is reached, file name is suffixed with an index e.g. result.txt.0, result.txt.1, result.txt.2 etc.
output.file.max_triples=0

# -- Output RDF syntax: RDF/XML|N-TRIPLE|TURTLE|N3|JSON-LD. Default: TURTLE
# Applies to the graph materialization and the rewriting of SPARQL CONSTRUCT and DESCRIBE queries
output.syntax.rdf=TURTLE

# -- Output syntax for SPARQL result set (SPARQL SELECT and ASK queries): XML|JSON|CSV|TSV. Default: XML
# When "server.active = true", this may be overridden by the Accept HTTP header of the request
output.syntax.result=XML

# -- Display the result on the std output after the processing: true|false. Default: true
output.display=false

# -- File containing the SPARQL query to process, relative to --configDir. Default: none. 
# Ignored when "server.active = true"
query.file.path=query.sparql

# -- Database connection type and configuration
no_of_database=1
database.type[0]=MongoDB
database.driver[0]=
database.url[0]=mongodb://127.0.0.1:27017
database.name[0]=test
database.user[0]=user
database.pwd[0]=user


# -- Reference formulation: Column|JSONPath|XPath. Default: Column
database.reference_formulation[0]=JSONPath

# -- Runner factory. Mandatory.
# For MongoDB: fr.unice.i3s.morph.xr2rml.mongo.engine.MorphJsondocRunnerFactory
# For RDBs:    es.upm.fi.dia.oeg.morph.rdb.engine.MorphRDBRunnerFactory
runner_factory.class.name=fr.unice.i3s.morph.xr2rml.mongo.engine.MorphMongoRunnerFactory


# -- URL-encode reserved chars in database values. Default: true
# uricolumn.encode_unsafe_chars_dbvalues=true

# -- URL-encode reserved chars IRI template string. Default: true 
# uricolumn.encode_uri=true


# -- Cache the result of previously executed queries for MongoDB. Default: false
# Caution: high memory consumption, to be used for RefObjectMaps only
querytranslator.cachequeryresult=false


# -- Primary SPARQL query optimization. Default: true
querytranslator.sparql.optimize=true

# -- Abstract query optimization: self join elimination. Default: true
querytranslator.abstract.selfjoinelimination=true

# -- Abstract query optimization: self union elimination. Default: true
querytranslator.abstract.selfunionelimination=true

# -- Abstract query optimization: propagation of conditions in a inner/left join. Default: true
querytranslator.abstract.propagateconditionfromjoin=true

```

