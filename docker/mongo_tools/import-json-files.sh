#!/bin/bash
# Authors: Franck MICHEL, University Cote d'Azur, CNRS, Inria
#          Anna BOBASHEVA, University Cote d'Azur, Inria
#
# Import into MongoDb multiple json files located in directory /mongo_import; create an index,
# and optionally run a js script in MongoDB
#
# Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)


# Path (inside the contaier) of the files to import 
idir=/mongo_import

help()
{
  exe=$(basename $0)
  echo "Usage: $exe <database name> <collection> <index_column> <additional scripts to run (optional)>"
  echo "Examples:"
  echo "   $exe  database  collection  index"
  echo "   $exe  database  collection  index  script.js"
  exit 1
}

# --- Read input arguments
database=$1
if [[ -z "$database" ]] ; then help; fi

collection=$2
if [[ -z "$collection" ]] ; then help; fi

index_col=$3
if [[ -z "$index_col" ]] ; then help; fi


# Optional
extra_script=$5


# Functions definitions
. ./import-tools.sh

echo "Importing from $idir to $collection in $database..."
echo "------------------------------------------------------------------------------"

mongo_drop_import_dir_index $idir $database $collection $index_col

if [[ ! -z "$extra_script" ]] ; then

	echo "Executing additional script $extra_script..."
	echo "------------------------------------------------------------------------------"

	mongo localhost/$database "$extra_script" #--verbose   
fi
