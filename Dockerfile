FROM maven:3.8.4-openjdk-17 as maven

# copy source code and POM

WORKDIR /usr/src/morph-xr2rml

COPY morph-base morph-base
COPY morph-core morph-core
COPY morph-xr2rml-dist morph-xr2rml-dist
COPY morph-xr2rml-lang morph-xr2rml-lang
COPY morph-xr2rml-mongo morph-xr2rml-mongo
COPY morph-xr2rml-rdb morph-xr2rml-rdb
COPY pom.xml pom.xml

RUN mvn clean install

# find the fat JAR file and copy it to a fixed location
RUN JAR_FILE=$(ls morph-xr2rml-dist/target/*jar-with-dependencies.jar) && \
    cp "$JAR_FILE" morph-xr2rml-dist/target/dist.jar

# ==================================================================================================

FROM openjdk:11-jdk-bullseye

RUN apt-get update -y && \
    apt-get install -y && \
    rm -rf /var/lib/apt/lists/*

ENV XR2RML="/morph-xr2rml"
RUN mkdir -p $XR2RML
WORKDIR "$XR2RML"

# copy the JAR file from the build stage
COPY --from=maven /usr/src/morph-xr2rml/morph-xr2rml-dist/target/dist.jar "$XR2RML"

ENV CONFIG="$XR2RML/config"
ENV OUTPUT="$XR2RML/output"
ENV LOG="$XR2RML/log"
RUN mkdir -p "$CONFIG" "$OUTPUT" "$LOG"

# Morph-xR2RML is not run at container startup, only when the run_xr2rml.sh script is run from outside.
# So instead just use sleep so that the container never kicks out.
CMD ["sleep", "infinity"]
