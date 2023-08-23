#!/bin/bash
# Authors: Franck MICHEL, University Cote d'Azur, CNRS, Inria
#          Anna BOBASHEVA, University Cote d'Azur, Inria
#
# Import into MongoDb a file (json/csv/tsv) located in directory /mongo_import; create an index,
# and optionally run a js script in MongoDB
#
# Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)


# Path (inside the contaier) of the files to import 
idir=/mongo_import

help()
{
  exe=$(basename $0)
  echo "Usage: $exe <file path> <file type> <database name> <collection> <index_column> <additional script to run (optional)>"
  echo "Example:"
  echo "   $exe  path/file.csv  csv  database  collection  index"
  echo "   $exe  path/file.csv  csv  database  collection  index  script.js"
  exit 1
}

# --- Read input arguments
file=$1
if [[ -z "$file" ]] ; then help; fi

file_type=$2
if [[ -z "$file_type" ]] ; then help; fi

database=$3
if [[ -z "$database" ]] ; then help; fi

collection=$4
if [[ -z "$collection" ]] ; then help; fi

index_col=$5
if [[ -z "$index_col" ]] ; then help; fi

# Optional
extra_script=$6


# Functions definitions
. ./import-tools.sh

echo "Importing from $idir/$file to $collection in $database..."
echo "------------------------------------------------------------------------------"

mongo_drop_import_file_index $idir/$file $file_type $database $collection $index_col

if [[ ! -z "$extra_script" ]] ; then

	echo "Executing additional script $extra_script..."
	echo "------------------------------------------------------------------------------"

	mongo localhost/$database "$extra_script" #--verbose   
fi
