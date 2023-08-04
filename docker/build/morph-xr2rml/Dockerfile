FROM openjdk:11-jdk-bullseye

RUN apt-get update -y 

ENV XR2RML="/morph-xr2rml"
RUN mkdir -p $XR2RML
WORKDIR "$XR2RML"

COPY morph-xr2rml-dist-1.3.2-20211126.142114-3-jar-with-dependencies.jar $XR2RML

ENV CONFIG="$XR2RML/config"
RUN mkdir -p $CONFIG

ENV OUTPUT="$XR2RML/output"
RUN mkdir -p $OUTPUT

ENV LOG="$XR2RML/log"
RUN mkdir -p $LOG

# Morph-xR2RML is not run at container startup, only when the run_xr2rml.sh script is run from outside.
# So instead just use sleep so that the container never kicks out.
CMD ["sleep", "infinity"]
