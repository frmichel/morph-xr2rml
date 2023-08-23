#!/bin/bash
# Author: Franck MICHEL, University Cote d'Azur, CNRS, Inria
#         Anna BOBASHEVA, University Cote d'Azur, Inria
#
# Set of generic tools meant to import data into a MongoDB database.
#
# Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)


# Max sie of JSON files to import at once into MongoDB (16MB)
MONGO_IMPORT_MAXSIZE=16000000


# Import, into a MongoDB collection, the JSON files listed in a file.
# Files are imported one by one.
#   $1: MongoDB database name
#   $2: MongoDB collection name
#   $2: file containing the list of JSON files to import
mongo_import_filelist_onebyone() {
    _database_a=$1
    _collection_a=$2
    _filelist_a=$3
    index=0

    for jsonfile in `cat $_filelist_a`; do
    
        filesize=$(stat --format=%s $jsonfile)
        if [ $filesize -ge $MONGO_IMPORT_MAXSIZE ]; then
            echo "WARNING - Ignoring oversized document $jsonfile ($filesize bytes)"
        else
            echo "Importing file $index: $jsonfile"
            mongoimport --type=json -d $_database_a -c $_collection_a $jsonfile
        fi
        index=$(($index + 1))
    done
}


# Import, into a MongoDB collection, the JSON files listed in a file.
# Files are grouped until a max size limit (see $MONGO_IMPORT_MAXSIZE)
#   $1: MongoDB database name
#   $2: MongoDB collection name
#   $3: file containing the list of JSON files to import
mongo_import_filelist() {
    _database_a=$1
    _collection_a=$2
    _filelist_a=$3
    
    # Import the files by groups
    jsondump=jsondump-$$
    echo -n '' > $jsondump

    for jsonfile in `cat $_filelist_a`; do
    
        filesize=$(stat --format=%s $jsonfile)
        if [ $filesize -ge $MONGO_IMPORT_MAXSIZE ]; then
            echo "WARNING - Ignoring oversized document $jsonfile ($filesize bytes)"
        else
            currentsize=$(stat --format=%s $jsondump)
            newsize=$(($currentsize + $filesize))
            if [ $newsize -gt $MONGO_IMPORT_MAXSIZE ]; then
                echo "Importing documents from $jsondump"
                mongoimport --type=json -d $_database_a -c $_collection_a $jsondump
                echo -n '' > $jsondump
            fi

            echo "Appending to $jsondump document $jsonfile"
            cat $jsonfile >> $jsondump

        fi
    done
	
	# Import the last group that could be less than $MONGO_IMPORT_MAXSIZE
    if [ -s $jsondump ]; then 
		echo "Importing last documents from $jsondump"
        mongoimport --type=json -d $_database_a -c $_collection_a $jsondump
        echo -n '' > $jsondump
	fi	
	
    
    rm -f $jsondump
}


# Import, into a MongoDB collection, all JSON files of a directory including sub-directories
#   $1: directory where to find the JSON files
#   $2: MongoDB database name
#   $3: MongoDB collection name
mongo_import_dir() {
    _dir_b=$1
    _database_b=$2
    _collection_b=$3

    # Get the list of files to import into MongoDB
    _filelist_b=/tmp/filelist-$$.txt
    find $_dir_b -type f -name '*.json' > $_filelist_b
    echo "Importing $(wc -l $_filelist_b | cut -d' ' -f1) files..."

    mongo_import_filelist $_database_b $_collection_b $_filelist_b
    #mongo_import_filelist_onebyone $_database_b $_collection_b $_filelist_b
    rm -f $_filelist_b
}


# Same as mongo_import_dir + creates an index afterwards. 
#   $1: directory where to find the JSON files
#   $2: MongoDB database name
#   $3: MongoDB collection name
#   $4: MongoDB collection's index column
mongo_import_dir_index() {
    _database_c=$2
    _collection_c=$3
    _index_col_c=$4

    mongo_import_dir $1 $_database_c $_collection_c
    mongo --eval "db.${_collection_c}.createIndex({${_index_col_c}: 1})" localhost/$_database_c --quiet
    mongo --eval "db.${_collection_c}.count()" localhost/$_database_c --quiet
}


# Same as mongo_import_dir + first drops the collection and creates an index afterwards.
#   $1: directory where to find the JSON files
#   $2: MongoDB database name
#   $3: MongoDB collection name
#   $4: MongoDB collection's index column
mongo_drop_import_dir_index() {
    _database_c=$2
    _collection_c=$3
    _index_col_c=$4

    mongo --eval "db.${_collection_c}.drop()" localhost/$_database_c
    mongo_import_dir $1 $_database_c $_collection_c
    mongo --eval "db.${_collection_c}.createIndex({${_index_col_c}: 1})" localhost/$_database_c --quiet
    mongo --eval "db.${_collection_c}.count()" localhost/$_database_c --quiet
}



# Import all JSON files of a directory into multiple MongoDB collections of a maximum number of files each.
#   $1: directory where to find the JSON files
#   $2: MongoDB database name
#   $3: prefix of the collection names: prefix_0, prefix_1, ...
#   $4: max number of files per collection
mongo_drop_import_dir_split() {
    _dir_c=$1
    _database_c=$2
    _collection_c=$3
    _maxfilesPerCollection=$4

    # Get the whole list of files to import into MongoDB
    _filelist_c=/tmp/filelist-collection-$$.txt
    find $_dir_c -type f -name '*.json' > $_filelist_c
    echo "Importing $(wc -l $_filelist_c | cut -d' ' -f1) files..."

    # Split the list of files into multiple pieces of $_maxfilesPerCollection files
    _prefix_c=/tmp/filelist-collection-$$-
    split -d -l $_maxfilesPerCollection $_filelist_c $_prefix_c
    rm -f $_filelist_c

    # Import the lists of files into separate collections
    colIndex=0
    for _filelist_c in `ls ${_prefix_c}*`; do

        col=${_collection_c}_${colIndex}
        echo "----- Creating collection $col"
        mongo --eval "db.${col}.drop()" localhost/$_database_c
        mongo_import_filelist $col $_filelist_c

        # Next collection
        colIndex=$(($colIndex + 1))
    done
    mongo --eval "db.${_collection_c}.count()" localhost/$_database_c --quiet
    rm -f ${_prefix_c}*
}

# Import a file into a MongoDB collection and create an index afterwards. 
# The exsiting collection would be replaced. 
#   $1: file name
#   $2: file type: csv, tsv, json
#   $3: MongoDB database name
#   $4: MongoDB collection name
#   $5: MongoDB collection's index column
mongo_drop_import_file_index() {
    _file_c=$1
    _type=$2
    _database_c=$3
    _collection_c=$4
    _index_col_c=$5

    mongoimport --drop --type=$_type --headerline --ignoreBlanks -d $_database_c -c $_collection_c $_file_c
    mongo --eval "db.${_collection_c}.createIndex({${_index_col_c}: 1})" localhost/$_database_c --quiet
    mongo --eval "db.${_collection_c}.count()" localhost/$_database_c --quiet
}
