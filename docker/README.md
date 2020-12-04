# JSGLR2 Evaluation in Docker

Requires [Docker](https://www.docker.com/get-started).

Checkout this project on the machine where you want to run the evaluation:

```
git clone https://github.com/metaborg/jsglr2evaluation.git
cd jsglr2evaluation
```

Build the image with tag `jsglr2evaluation` (not from this directory, but from the root of the repository):

```
docker build -f docker/Dockerfile -t jsglr2evaluation .
```

Run the evaluation in a container, which will use `~/jsglr2evaluation-data` on the host for persistence:

```
docker run -d --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=all" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

`-d`: run container in the background
`--rm`: removes the container when it exits
`-v ~/jsglr2evaluation-data:/jsglr2evaluation/data`: use `~/jsglr2evaluation-data` as working directory on the host to persist data (Spoofax sources, evaluation corpus, results)
`-e "SPOOFAX_VERSION=master"`: specify the Spoofax version
`-e "JSGLR_VERSION=develop/jsglr2"`: specify the JSGLR version, which will be built again independently after building Spoofax completely. This enables you to run the evaluation with a JSGLR version from specific branch, without having to rebuild Spoofax completely.
`-e "TARGET=evaluation"`: specify the target of the Make build (e.g. `all`, `spoofax`, `evaluation`)
`-e "EVALUATION_TARGET=all"`: specify the target of the evaluation
`-e "EVALUATION_CONFIG=config.yml"`: specify the configuration file to use (in the working directory)
`-e "GITHUB_TOKEN=?"`: provide a GitHub access token to publish evaluation results to https://metaborg.github.io/jsglr2evaluation-site/


To start a shell inside the container:

```
docker run --rm -v ~/jsglr2evaluation:/jsglr2evaluation/data -it --entrypoint /bin/bash jsglr2evaluation
```

`-it`: Run the container interactively
`--entrypoint /bin/bash`: Start a shell instead of directly running the evaluation scripts (`vim` is installed)

## Docker documentattion

- https://docs.docker.com/get-started/part2/
- Dockerfile: https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example
- Running: https://docs.docker.com/engine/reference/commandline/run/.
