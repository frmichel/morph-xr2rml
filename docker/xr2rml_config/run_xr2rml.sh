#!/bin/bash
#
# This script runs Morph-xR2RML in graph materialization mode,
# that is it applies the mappings and outputs the corresponding RDF file.
#
# Input argument:
# - arg1: xR2RML mapping file without path. Must be located in $CONFIG_DIR
# - arg2: output file name without path
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
  echo "Usage: $exe <xR2RML mapping template> <output file name>"
  echo "Example:"
  echo "   $exe mapping_metadata.ttl  metadata.ttl"
  exit 1
}

# --- Read input arguments
mappingFile=$1
if [[ -z "$mappingFile" ]] ; then help; fi

output=$2
if [[ -z "$output" ]] ; then help; fi


# --- Init log file
mkdir -p $LOG_DIR
log=$LOG_DIR/xr2rml_$(date "+%Y%m%d_%H%M%S").log


echo "--------------------------------------------------------------------------------------" >> $log
date >> $log
java -Xmx4g \
     -Dlog4j.configuration=file:$CONFIG_DIR/log4j.properties \
     -jar "$JAR" \
     --configDir $CONFIG_DIR \
     --configFile xr2rml.properties \
     --mappingFile $CONFIG_DIR/$mappingFile \
     --output $OUTPUT_DIR/$output \
     >> $log
date >> $log
