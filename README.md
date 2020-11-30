# What is it?

Morph-xR2RML is an implementation of the [xR2RML mapping language](http://i3s.unice.fr/~fmichel/xr2rml_specification.html) that enables the description of mappings from relational or non relational databases to RDF. xR2RML is an extension of [R2RML](http://www.w3.org/TR/r2rml/) and [RML](http://semweb.mmlab.be/rml/spec.html).

Morph-xR2RML comes with connectors for relational databases (MySQL, PostgreSQL, MonetDB) and the MongoDB NoSQL document store.
Two running modes are available:
- the *graph materialization* mode creates all possible RDF triples at once.
- the *query rewriting* mode translates a SPARQL 1.0 query into a target database query and returns a SPARQL answer. It can run as a SPARQL 1.0 endpoint or as a stand-alone application.

Morph-xR2RML was developed by the [I3S laboratory](http://www.i3s.unice.fr/) as an extension of the [Morph-RDB project](https://github.com/oeg-upm/morph-rdb) which is an implementation of R2RML. It is made available under the Apache 2.0 License.

#### SPARQL-to-SQL
The SPARQL-to-SQL rewriting is an adaptation of the former Morph-RDB implementation, it supports SPARQL SELECT and DESCRIBE queries.

#### SPARQL-to-MongoDB
The SPARQL-to-MongoDB rewriting is a fully new component, it supports the SELECT, ASK, CONSTRUCT and DESCRIBE query forms.



## Publications
[1] F. Michel, L. Djimenou, C. Faron-Zucker, and J. Montagnat. Translation of Relational and Non-Relational Databases into RDF with xR2RML.
In Proceedings of the *11th International Confenrence on Web Information Systems and Technologies (WEBIST 2015)*, Lisbon, Portugal, 2015.

[2] F. Michel, L. Djimenou, C. Faron-Zucker, and J. Montagnat. xR2RML: Relational and Non-Relational Databases to RDF Mapping Language.
Research report, CNRS, 2015. https://hal.archives-ouvertes.fr/hal-01066663

[3] C. Callou, F. Michel, C. Faron-Zucker, C. Martin, J. Montagnat. Towards a Shared Reference Thesaurus for Studies on History of Zoology, Archaeozoology and Conservation Biology. In *Semantic Web For Scientific Heritage (SW4SH), Workshops of the ESWC’15 conference*.

[4] F. Michel, C. Faron-Zucker, and J. Montagnat. A Generic Mapping-Based Query Translation from SPARQL to Various Target Database Query Languages.
In Proceedings of the *12th International Confenrence on Web Information Systems and Technologies (WEBIST 2016)*, Roma, Italy, 2016.

[5] F. Michel, C. Faron-Zucker, and J. Montagnat. Mapping-based SPARQL access to a MongoDB database. Research report, CNRS, 2016. 
https://hal.archives-ouvertes.fr/hal-01245883.

[6] F. Michel, C. Faron-Zucker, and J. Montagnat. A Mapping-Based Method to Query MongoDB Documents with SPARQL. In *27th International Conference on Database and Expert Systems Applications (DEXA 2016)*, 2016.


## Limitations

##### xR2RML Language support
- The generation of RDF collection and containers is supported in all cases (from a list of values resulting of the evaluation of a mixed syntax path typically, from the result of a join query implied by a referencing object map), except in the case of a regular R2RML join query applied to a relational database: the result of the join SQL query cannot be translated into an RDF collection or container.
- Named graphs are supported although they are not printed out in Turtle which does not support named graphs. It would be quite easy to extend it with a N-Quad or Trig serialization to allow for writing triples in named graphs.

The former limitation on NestedTermMaps was lifted in Sept. 2017. All types of NestedTermMaps are now fully implemented, so that any complex iterations and collection/container nesting can be defined.


##### Query rewriting 
The query rewriting is implemented for RDBs and MongoDB, with the restriction that _no mixed syntax paths be used_. Doing query rewriting with mixed syntax paths is a much more complex problem, that may not be possible in all situations (it would require to "revert" expressions such as JSONPath or XPath to retrieve source data base values).

Only one join condition is supported in a referencing object map.

----------

# Code description

See a detailed [description of the project code and architecture](doc/README_code_architecture.md).

----------

# Want to try it?

### Download, Build

Pre-requisite: have **Java SDK 10** installed

You can download the last release or snapshot published in [this repository](https://www.dropbox.com/sh/djnztipsclvcskw/AABT1JagzD4K4aCALDNVj-yra?dl=0).

Alternatively, you can build the application using [Maven](http://maven.apache.org/): in a shell, CD to the root directory morph-xr2rml, then run the command: `mvn clean package`. A jar with all dependencies is generated in `morph-xr2rml-dist/target`.


### Run it

The application takes two options: `--configDir` gives the configuration directory and `--configFile` give the configuration file within this directory. Option `--configFile` defaults to `morph.properties`.

Additionally, several parameter given in the configuration file can be overridden using the following options: 
- mapping file: `--mappingFile` 
- output file : `--output`
- maximum number of triples generated in a single output file: `--outputMaxTriples`


**From a command line interface**, CD to directory morph-xr2rml-dist and run the application as follows:

```
java -jar target/morph-xr2rml-dist-<version>-jar-with-dependencies.jar \
   --configDir <configuration directory> \
   --configFile <configuration file within this directory>
```

Besides, the logger configuration can be overriden by passing the `log4j.configuration` parameter to the JVM:

```
java -Dlog4j.configuration=file:/path/to/my/log4j.configuration -jar ...
```

**From an IDE** such as Eclipse or IntelliJ: In project morph-xr2rml-dist locate main class `fr.unice.i3s.morph.xr2rml.engine.MorphRunner`, and run it as a Scala application with arguments `--configDir` and `--configFile`.

### SPARQL endpoint

To run Morph-xR2RML as a SPARQL endpoint, simply edit the configuration file (see reference) and set the property `sever.active=true`. The default access URL is:
```
http://localhost:8080/sparql
```
Property `query.file.path` is ignored and queries can be submitted using either HTTP GET or POST methods as described in the [SPARQL protocol](https://www.w3.org/TR/rdf-sparql-protocol/) recommendation.

For SPARQL SELECT and ASK queries, the XML, JSON, CSV and TSV serializations are supported.

For SPARQL DESCRIBE and CONSTRUCT queries, the supported serializations are RDF/XML, N-TRIPLE, N-QUAD, TURTLE, N3 and JSON-LD.

### Examples for MongoDB

In directories `morph-xr2rml-dist/example_mongo` and `morph-xr2rml-dist/example_mongo_rewriting` we provide example databases and corresponding mappings. Directory `example_mongo` runs the graph materialization mode, `example_mongo_rewriting` runs the query rewriting mode.

- `testdb_dump.json` is a dump of the MongoDB test database: copy and paste the content of that file into a MongoDB shell window to create the database;
- `morph.properties` provides database connection details;
- `mapping1.ttl` to `mapping4.ttl` contain xR2RML mapping graphs illustrating various features of the language;
- `result1.txt` to `result4.txt` contain the expected result of the mappings 1 to 4;
- `query.sparql` (in directory `example_mongo_rewriting` only) contains a SPARQL query to be executed against the test database.

Edit `morph.properties` and change the database URL, name, user and password with appropriate values.

> _**Note about query optimization**_: the xR2RML xrr:uniqueRef notation is of major importance for query optimization as it allows for self-joins elimination. Check example in `morph-xr2rml-dist/example_taxref_rewriting`.

### Examples for MySQL

In directories `morph-xr2rml-dist/example_mysql` and `morph-xr2rml-dist/example_mysql_rewriting` we provide example databases and corresponding mappings. Directory `example_mysql` runs the graph materialization mode, `example_mysql_rewriting` runs the query rewriting mode.

- `testdb_dump.sql` is a dump of the MySQL test database. You may import it into a MySQL instance by running command `mysql -u root -p test < testdb_dump.sql`;
- `morph.properties` provides database connection details;
- `mapping.ttl` contains an example xR2RML mapping graph;
- `result.txt` contains the expected result of applying this mapping to that database;
- `query.sparql` (in directory `example_mysql_rewriting` only) contains a SPARQL query to be executed against the test database.

Edit `morph.properties` and change the database url, name, user and password with appropriate values.

----------

# Configuration file reference
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

