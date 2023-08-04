# Build and install

## Download & Build

Pre-requisite: have **Java SDK 10** installed

You can download the last release or snapshot published in [this repository](https://www.dropbox.com/sh/djnztipsclvcskw/AABT1JagzD4K4aCALDNVj-yra?dl=0).
The latest on-going version is the 1.3.2 snapshot.

Alternatively, you can build the application using [Maven](http://maven.apache.org/): in a shell, CD to the root directory morph-xr2rml, then run the command: `mvn clean package`. A jar with all dependencies is generated in `morph-xr2rml-dist/target`.


## Run it

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


## SPARQL endpoint

To run Morph-xR2RML as a SPARQL endpoint, simply edit the configuration file (see reference) and set the property `sever.active=true`. The default access URL is:
```
http://localhost:8080/sparql
```
Property `query.file.path` is ignored and queries can be submitted using either HTTP GET or POST methods as described in the [SPARQL protocol](https://www.w3.org/TR/rdf-sparql-protocol/) recommendation.

For SPARQL SELECT and ASK queries, the XML, JSON, CSV and TSV serializations are supported.

For SPARQL DESCRIBE and CONSTRUCT queries, the supported serializations are RDF/XML, N-TRIPLE, N-QUAD, TURTLE, N3 and JSON-LD.