# Changelog

## [CURRENT 1.3.2 SNAPSHOT] 2021-11-3026
- fix bug in sha1() mixed path
- document the sha1() mixed path
- (partially) fix bug in pushDown: for any field that is an ObjectID with $oid field (such as "_id"), the value of the $oid is pushed instead of the serialization f the field itself

## [1.3.1] 2021-01-06
- fix bug in management of empty values
- document the variable language tag

## [1.3] 2020-04-27
- Add mixed syntax path sha1() to compute sha hash in template

## [1.2] 2020-04-22
- Add config property output.file.max_triples (and parameter --outputMaxTriples) to generate files with a maximum number of triples
- Upgrade to Jena 3
- Update to Scala 2.12.11 + update misc. libraries

## [1.1-RC2] 2019-09-16
Fix bug in Mongo query parsing: Mongo query containing parentheses are now supported, e.g ```find({field: "1(2)3"})```

## [1.1-RC] 2019-07-02
- Support for double-quotes notation + single quotes inside e.g.
```xrr:query """db.locations.find( {"adminLevel": "Collectivité d'outre-mer"} )""".```
 - Upgrade to Scala 2.12.3
 
## 2019-06-19: add term map property xrr:languageReference
- The R2RML rr:language property provides a static language tag to assign to literals. The new xrr:languageReference property allows to do that using a language tag that comes from the data source.
- Update Jongo to 1.4.0
 
## 2018-05-31: add run options
- Options `--output` and `--mappingFile` can be used to override the output.file.path and mappingdocument.file properties respectively.
- Add configuration parameter `literal.trim` set to true to trim the literal values read from the database.

## 2017-10-25: new property xrr:pushDown 
Property xrr:pushDown extends the mapping possibilities when defining iterations within a document (pull request #3 by Freddy Priyatna, to fulfill a need of the [SlideWiki project](https://slidewiki.eu/)). 
When iterating in a sub-part of a document (e.g. a JSON array), that property helps use values of fields that are higher in the document hierarchy, hence not accessible inside this sub-part of the document. See complete description in [2]. Implemented for the MongoDB database.
Example: a property ```xrr:pushDown [ xrr:reference "$.id"; xrr:as "newId"]``` can be defined either in the logical source together with an  rml:iterator, or within a referenced-valued term map that has a nested term map.
  - In a logical source: the xrr:reference "$.id" is evaluated against the current document, then the iterator is applied and in each document that comes out of the iterator, a new field ("newID" in this example) is added.
  - In a reference-valued term map, the xrr:reference "$.id" is evaluated against the document of the current iteration, and a new field ("newID" in this example) is added inside the documents that are passed to the nested term map.

## 2017-09-05: full implementation of the nested term maps
Complex nested term maps (nested term map that embed another nested term map) are now enabled, thus allowing to deal with any level of nested documents (pull request #1 by Freddy Priyatna). Implemented for the MongoDB database.
