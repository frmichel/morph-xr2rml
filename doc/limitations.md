# Limitations

### xR2RML Language support
- The generation of RDF collection and containers is supported in all cases (from a list of values resulting of the evaluation of a mixed syntax path typically, from the result of a join query implied by a referencing object map), except in the case of a regular R2RML join query applied to a relational database: the result of the join SQL query cannot be translated into an RDF collection or container.
- Named graphs are supported although they are not printed out in Turtle which does not support named graphs. It would be quite easy to extend it with a N-Quad or Trig serialization to allow for writing triples in named graphs.

The former limitation on NestedTermMaps was lifted in Sept. 2017. All types of NestedTermMaps are now fully implemented, so that any complex iterations and collection/container nesting can be defined.


### Query rewriting 
The query rewriting is implemented for RDBs and MongoDB, with the restriction that _no mixed syntax paths be used_. Doing query rewriting with mixed syntax paths is a much more complex problem, that may not be possible in all situations (it would require to "revert" expressions such as JSONPath or XPath to retrieve source data base values).

Only one join condition is supported in a referencing object map.
