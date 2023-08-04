# Running examples

## Examples for MongoDB

In directories `morph-xr2rml-dist/example_mongo` and `morph-xr2rml-dist/example_mongo_rewriting` we provide example databases and corresponding mappings. Directory `example_mongo` runs the graph materialization mode, `example_mongo_rewriting` runs the query rewriting mode.

- `testdb_dump.json` is a dump of the MongoDB test database: copy and paste the content of that file into a MongoDB shell window to create the database;
- `morph.properties` provides database connection details;
- `mapping1.ttl` to `mapping4.ttl` contain xR2RML mapping graphs illustrating various features of the language;
- `result1.txt` to `result4.txt` contain the expected result of the mappings 1 to 4;
- `query.sparql` (in directory `example_mongo_rewriting` only) contains a SPARQL query to be executed against the test database.

Edit `morph.properties` and change the database URL, name, user and password with appropriate values.

> _**Note about query optimization**_: the xR2RML xrr:uniqueRef notation is of major importance for query optimization as it allows for self-joins elimination. Check example in `morph-xr2rml-dist/example_taxref_rewriting`.

## Examples for MySQL

In directories `morph-xr2rml-dist/example_mysql` and `morph-xr2rml-dist/example_mysql_rewriting` we provide example databases and corresponding mappings. Directory `example_mysql` runs the graph materialization mode, `example_mysql_rewriting` runs the query rewriting mode.

- `testdb_dump.sql` is a dump of the MySQL test database. You may import it into a MySQL instance by running command `mysql -u root -p test < testdb_dump.sql`;
- `morph.properties` provides database connection details;
- `mapping.ttl` contains an example xR2RML mapping graph;
- `result.txt` contains the expected result of applying this mapping to that database;
- `query.sparql` (in directory `example_mysql_rewriting` only) contains a SPARQL query to be executed against the test database.

Edit `morph.properties` and change the database url, name, user and password with appropriate values.
