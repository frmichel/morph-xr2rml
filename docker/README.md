# Deploy Morph-xR2RML with Docker

You can deploy Morph-xR2RML along with a MongoDB database using Docker.

First make sure your have [docker-compose](https://docs.docker.com/compose/) or [install it](https://docs.docker.com/compose/install/linux/).

If you have cloned this repository, just skip the next step. Otherwise, download file [xr2rml_docker.zip](xr2rml_docker.zip), and unzip it to the directory from where you will run docker-compose. From a Linux terminal that would be:

```bash
wget https://github.com/frmichel/morph-xr2rml/raw/master/docker/xr2rml_docker.zip
unzip xr2rml_docker.zip
```

Set file access rights as shown below:

```bash
chmod -R 755 mongo_import
chmod -R 755 xr2rml_config
chmod -R 777 log
chmod -R 777 mongo_db
chmod -R 777 xr2rml_output
chmod 777 run.sh
```

Then run:

```
docker-compose up -d
```

Now, both containers are started and ready to process data.

Script `run.sh` provides an example of how to run the different steps manually: (1) import data located in `mongo_import` into MongoDB, and (2) run Morph-xR2RML to translate the data to RDF and store the result in `xr2rml_output`.


### Description of each folder

- `mongo_db`: will contain the actual Mongo database, so that you don't need to re-import files everytime you rerun Morph-xR2RML.
- `mongo_tools`: set of handy bash scripts to import json/csv/tsv data into MongoDB.
- `mongo_import`: copy your files to import in this folder, it is moutned in the MongoDB container.
- `xr2rml_config`: mapping files (morph.properties, log configuration file) and bash scripts to run Morph-xR2RML. This folder is mounted in the Morph-xR2RML container.
- `xr2rml_config`: where the RDF files will be written.
- `run.sh`: example script showing how to use evrything from your machine, i.e. to import data into MongoDB and run Morph-xR2RML to translate the data to RDF.



### Accessing Logs

The `docker-compose.yml` mounts the Morph-xR2RML log directory to the Docker host in directory `log`.
Check it if an error occurs or if your mapping does not generate the expected triples.


### Changing Morph-xR2RML configuration and mappings

Put your mapping files in folder `xr2rml_config`. Script `xr2rml_config/run_xr2rml.sh` will be used to run the transltation from within the container.

Mapping files can also be templates, with 2 predefined placeholders: `{{collection}}` and  `{{dataset}}`. Script `xr2rml_config/run_xr2rml_template.sh` will replace them before running the translation. Check the example mapping `xr2rml_config/mapping_movies.ttl` and how it is used by script `run.sh`.

The main Morph-xR2RML configuration file is editable at ```xr2rml_config/morph.properties```. In particular that's where you would change the name of the MongoDB database.

### Importing data into MongoDB

Script `mongo_tools/import_tools.sh` provides various handy function to import json/csv/tsv data into MongoDB. The data can be imported all at once, file by file, or grouping files to comply with the maximum import size of MongoDB.

Scripts `mongo_tools/import-file.sh` and `mongo_tools/import-json-files.sh` provide examples of how to use those functions. They can be used as is, as shown in script `run.sh`.


## Common issues

### log or mongo_db directory not writable

The containers need to write in directories ```log``` and ```mongo_db```. They will fail if you do not set rights 777 (`chmod 777 <dir name>`).

### SLF4J messages at Morph-xR2RML startup

When it starts up, Morph-xR2RML shows the messages below. They are just warning, you can safely ignore them.

```
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
```
