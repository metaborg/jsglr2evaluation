# JSGLR2 Evaluation Suite

An evaluation suite for JSGLR2 parsing.
Results are published at https://metaborg.github.io/jsglr2evaluation-site/.

## Run

There are two options for running the evaluation suite:
 - **make**: Requires installing all dependencies locally, including the [Spoofax development environment](http://www.metaborg.org/en/latest/source/dev/index.html). Recommended for development of the suite and for precise measurements.
 - **Docker**: Only depends on Docker. Recommended for running the suite remotely or for running the suite for evaluating grammars.

### Make

See [scripts/README.md](scripts/README.md).

### Docker

See [docker/README.md](docker/README.md).

#### Quickstart

Build and run the Docker image tagged `jsglr2evaluation`, setup Spoofax and JSGLR2, and run the complete evaluation:

```
docker build -f docker/Dockerfile -t jsglr2evaluation .
docker run --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=all" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

As a local working directory, `~/jsglr2evaluation-data` will be used for storing Spoofax sources, languages, the corpus, and evaluation results and reports.
The default [config](scripts/config.yml) can be overwritten by providing a config file in the working directory, e.g. at `~/jsglr2evaluation-data/config.yml`.

For consecutive runs, only the evaluation has to be re-run (`-e "TARGET=evaluation"`):

```
docker run --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=evaluation" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

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
