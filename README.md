# JSGLR2 Evaluation Suite

This repository contains a suite for evaluating the JSGLR2 parser in an automated way.
Results of evaluation runs are published at https://www.spoofax.dev/jsglr2evaluation-site/.

## Setup

The evaluation suite is set up as a collection of scripts that are run in a [make](https://www.gnu.org/software/make/) build.
These [scripts](./scripts) pull and build the languages used for the evaluation, collect a corpus, run measurements and benchmarks, process results and optionally publish them to the [results website](https://www.spoofax.dev/jsglr2evaluation-site/).

There are two options for running the evaluation suite:
 - **Docker**:
  Running the evaluation in Docker is recommended for quickly getting started, for running the evaluation suite remotely, and for evaluating grammars.
  The only dependency for this option is Docker itself; running the container will install all other dependencies.
  Note that Docker's layer of virtualization could introduce noise in benchmarking measurements.
  See also [docker/README.md](./docker/README.md).
 - **make**:
  Directly running the evaluation via make is not hindered by Docker's virtualization and thus is the recommended approach for obtaining precise measurements.
  However, it does require that all dependencies are installed locally, including the complete [Spoofax development environment](http://www.metaborg.org/en/latest/source/dev/index.html) of which JSGLR2 is a component.
  For more details see [scripts/README.md](./scripts/README.md).

### Working Directory

The evaluation stores data in a working directory on the host machine.
By default, this is the `~/jsglr2evaluation-data` directory.
When running the evaluation via Docker, dependencies will also be pulled into and installed in the working directory.

### Configuration

By default, [scripts/config.yml](scripts/config.yml) serves as the configuration file for the evaluation.
You can override the default configuration by putting a configuration file in the working directory, i.e. at `~/jsglr2evaluation-data/config.yml`.

The default configuration includes multiple languages, sources, and evaluation scopes (batch parsing, incremental parsing, memory measurements) and therefore takes a long to execute.
When trying out the evaluation suite for the first time, one could use a simple configuration such as the following:

```
warmupIterations: 1
benchmarkIterations: 1
individualBatchSources: false
languages:
  - id: java
    name: Java
    extension: java
    parseTable:
      repo: https://github.com/metaborg/java-front.git
      subDir: lang.java
    sources:
      batch:
        - id: apache-commons-lang
          repo: https://github.com/apache/commons-lang.git
```

This will run the evaluation suite for the Java language and takes the apacha-commons-lang project as corpus.

You can add new languages to the evaluation by adding an entry under the `languages` list.
In above example, `parseTable.repo` specifies a Git repository of a Spoofax language of which the parse table will be used.
Alternatively, you can use `parseTable.file` to specify a path to a parse table on the host machine.

Similarly for sources, you can use `path` instead of `repo` to specify a local path that contains input files for the language.
It will automatically be scanned for files with the language's extension.

## Quickstart with Docker

Build and run the Docker image tagged `jsglr2evaluation`, setup Spoofax and JSGLR2, and run the complete evaluation:

```
docker build -f docker/Dockerfile -t jsglr2evaluation .
docker run --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=all" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

For consecutive runs, only the evaluation has to be re-run (`-e "TARGET=evaluation"`), preventing building Spoofax from scratch each time:

```
docker run --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=evaluation" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

If you want to run a subset of the targets of [scripts/Makefile](./scripts/Makefile), change the `EVALUATION_TARGET` parameter to e.g. `-e "EVALUATION_TARGET=languages sources preProcessing"`.

Add the `-it` option to run the container interactively, which allows to cancel its execution with CTRL + C.
Alternatively, to stop execution, look up the container id with `docker ps` and stop and remove the container with `docker rm -f <container-id>`.

#### Evaluating grammars

For evaluating grammars, the run can be limited to `-e "EVALUATION_TARGET=languages sources preProcessing"` for only rebuilding the languages, collecting sources, and preprocessing, respectively.
Files that do not parse unambiguously end up in the `~/jsglr2evaluation-data/sources/{invalid, timout, inconsistent, ambiguous}` directories.

An example configuration for evaluating the Java grammar:

```
languages:
  - id: java
    name: Java
    extension: java
    parseTable:
      repo: https://github.com/metaborg/java-front.git
      subDir: lang.java
    sources:
      batch:
        - id: apache-commons-lang
          repo: https://github.com/apache/commons-lang.git
        - id: custom-java-sources
          path: ~/custom-java-sources
```

This will use sources from the remote repository at https://github.com/apache/commons-lang.git and from the local directory `~/custom-java-sources`.

When changing the grammar locally, rebuild the language with `mvn install` at `~/jsglr2evaluation-data/languages/java/lang.java`, and re-run the evaluation with `-e "EVALUATION_TARGET=sources preProcessing"`.
