#!/bin/bash

# -- Example script to run xR2RML --

# Set the variables below first
XR2RML=/home/user/xR2RML
mappingFile=my_mapping.ttl


java -Xmx4g \
     -Dlog4j.configuration=file:$XR2RML/log4j.properties \
     -jar "$XR2RML/morph-xr2rml-dist-1.2-jar-with-dependencies.jar" \
     --configDir $XR2RML \
     --configFile xr2rml.properties \
     --mappingFile $mappingFile \
     --output $XR2RML/output.ttl
