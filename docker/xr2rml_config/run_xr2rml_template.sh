#!/bin/bash
#
# This script runs Morph-xR2RML in graph materialization mode,
# that is it applies the mappings and outputs the corresponding RDF file.
# It first replaces the plateholders in the template mapping file.
#
# Input argument:
# - arg1: xR2RML template mapping file without path. Must be located in $CONFIG_DIR
# - arg2: output file name without path
# - arg3: replacement of the dataset parameter {{dataset}} in the mapping template, e.g. "dataset1"
# - arg4: replacement of the MongoDB collection parameter {{collection}} the mapping template, e.g. metadata
#
# Author: Franck MICHEL, University Cote d'Azur, CNRS, Inria
#
# Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)

XR2RML=/morph-xr2rml
CONFIG_DIR=/xr2rml_config
LOG_DIR=/log
OUTPUT_DIR=/xr2rml_output

JAR=$XR2RML/morph-xr2rml-dist-1.3.2-20211126.142114-3-jar-with-dependencies.jar


help()
{
  exe=$(basename $0)
  echo "Usage: $exe <xR2RML mapping template> <output file name> <dataset name> <MongoDB collection name>"
  echo "Example:"
  echo "   $exe mapping_metadata.ttl  metadata.ttl  dataset1  metadata"
  exit 1
}

# --- Read input arguments
mappingTemplate=$1
if [[ -z "$mappingTemplate" ]] ; then help; fi

output=$2
if [[ -z "$output" ]] ; then help; fi

dataset=$3
if [[ -z "$dataset" ]] ; then help; fi

collection=$4
if [[ -z "$collection" ]] ; then help; fi


# --- Init log file
mkdir -p $LOG_DIR
log=$LOG_DIR/xr2rml_${collection}_$(date "+%Y%m%d_%H%M%S").log


# --- Substitute placeholders in the xR2RML template mapping
mappingFile=/tmp/xr2rml_$$.ttl
awk "{ gsub(/{{dataset}}/, \"$dataset\"); \
       gsub(/{{collection}}/, \"$collection\"); \
       print }" \
    $CONFIG_DIR/${mappingTemplate} > $mappingFile
echo "-- xR2RML mapping file --" >> $log
cat $mappingFile >> $log


echo "--------------------------------------------------------------------------------------" >> $log
date  >> $log
java -Xmx4g \
     -Dlog4j.configuration=file:$CONFIG_DIR/log4j.properties \
     -jar "$JAR" \
     --configDir $CONFIG_DIR \
     --configFile xr2rml.properties \
     --mappingFile $mappingFile \
     --output $OUTPUT_DIR/$output \
     >> $log
date >> $log

rm -f $mappingFile
