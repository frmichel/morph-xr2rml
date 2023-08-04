# Morph-xR2RML: MongoDB-to-RDF translation

Morph-xR2RML is an implementation of the [xR2RML mapping language](http://i3s.unice.fr/~fmichel/xr2rml_specification.html) that enables the description of mappings from relational or non relational databases to RDF. xR2RML is an extension of [R2RML](http://www.w3.org/TR/r2rml/) and [RML](http://semweb.mmlab.be/rml/spec.html).

Morph-xR2RML comes with connectors for relational databases (MySQL, PostgreSQL, MonetDB) and the MongoDB NoSQL document store.
Two running modes are available:
- the *graph materialization* mode creates all possible RDF triples at once, it is a production class 
- the *query rewriting* mode translates a SPARQL 1.0 query into a target database query and returns a SPARQL answer. It can run as a SPARQL 1.0 endpoint or as a stand-alone application.
   - The SPARQL-to-SQL rewriting is an adaptation of the former Morph-RDB implementation, it supports SPARQL SELECT and DESCRIBE queries.
   - The SPARQL-to-MongoDB rewriting is a new component, it supports the SELECT, ASK, CONSTRUCT and DESCRIBE query forms.


Morph-xR2RML was developed by the [I3S laboratory](http://www.i3s.unice.fr/) as an extension of the [Morph-RDB project](https://github.com/oeg-upm/morph-rdb) which is an implementation of R2RML. It is made available under the Apache 2.0 License.


### Maturity

The *graph materialization* mode is mature and has been used in several projects to generate large amounts of RDF triples (-1.3M triples in [Covid-on-the-Web](https://github.com/Wimmics/CovidOnTheWeb/)).

The *query rewriting* mode is a prototype implementation. It was meant to demonstrate the effectiveness of a rewirting method but is not meant for production environment.


## Quick start guide

The easiest to start using Morph-xR2RML to materialize a graph from a MongoDB database is by using Docker. Follow [these instructions](docker/README.md).


## Documentation

- [Deploy with Docker](docker/README.md)
- [Build and install](doc/build.md)
- [Running examples](doc/examples.md)
- [Configuration reference](doc/config.md)
- [Architecture and code description](doc/README_code_architecture.md).
- [Limitations](doc/limitations.md)


## Publications

F. Michel, L. Djimenou, C. Faron-Zucker, and J. Montagnat. Translation of Relational and Non-Relational Databases into RDF with xR2RML.
In Proceedings of the *11th International Confenrence on Web Information Systems and Technologies (WEBIST 2015)*, Lisbon, Portugal, 2015. [HAL](https://hal.science/hal-01207828)

F. Michel, C. Faron-Zucker, and J. Montagnat. A Generic Mapping-Based Query Translation from SPARQL to Various Target Database Query Languages.
In Proceedings of the *12th International Confenrence on Web Information Systems and Technologies (WEBIST 2016)*, Roma, Italy, 2016. [HAL](https://hal.science/hal-01280951)

F. Michel, C. Faron-Zucker, and J. Montagnat. A Mapping-Based Method to Query MongoDB Documents with SPARQL. In *27th International Conference on Database and Expert Systems Applications (DEXA 2016)*, 2016. [HAL](https://hal.science/hal-01330146)


## Cite this work:

Either cite one of the papers above or cite the software itself as this with its SWHID:

**Full text**:

Franck Michel. Morph-xR2RML: MongoDB-to-RDF translation. 2015, ⟨swh:1:dir:8ea716c0d9e69527a5f50378bf135c5952b1a229⟩. ⟨hal-04128090⟩

**Bibtex**:

```bibtex
@softwareversion{michel:hal-04128090v1,
  TITLE = {{Morph-xR2RML: MongoDB-to-RDF translation}},
  AUTHOR = {Michel, Franck},
  URL = {https://hal.science/hal-04128090},
  NOTE = {},
  INSTITUTION = {{University C{\^o}te d'Azur ; CNRS ; Inria}},
  YEAR = {2015},
  MONTH = Apr,
  SWHID = {swh:1:dir:8ea716c0d9e69527a5f50378bf135c5952b1a229},
  VERSION = {1.3.1},
  REPOSITORY = {https://github.com/frmichel/morph-xr2rml},
  LICENSE = {Apache License 2.0},
  KEYWORDS = {knowledge graph ; RDF ; mapping ; SPARQL ; MongoDB},
  HAL_ID = {hal-04128090},
  HAL_VERSION = {v1},
}
```
