#!/bin/bash
# Author: Anna BOBASHEVA, University Cote d'Azur, CNRS, Inria
#
# Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)

DB=database
COLLECTION=movies


MONGO_CONTAINER=$(docker ps --format=='{{.Names}}' | grep "mongo-xr2rml" | cut -d= -f2)
XR2RML_CONTAINER=$(docker ps --format=='{{.Names}}' | grep "morph-xr2rml" | cut -d= -f2)


# --- Import the JSON files of a directory into MongoDB
docker exec -w /mongo_tools $MONGO_CONTAINER \
   /bin/bash import-json-files.sh $DB $COLLECTION id

# --- Run the translation to RDF
# Note that at start-up Morph-xR2RML shows 3 SLF4J warning messages, just ignore them.
docker exec -w /xr2rml_config $XR2RML_CONTAINER \
   /bin/bash run_xr2rml_template.sh mapping_movies.ttl movies.ttl dataset1.0 $COLLECTION
