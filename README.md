# JSGLR2 Evaluation Suite

An evaluation suite for JSGLR2 parsing.
Results are published at https://metaborg.github.io/jsglr2evaluation-site/.

## Docker

Checkout this project on a server where you want to run the evaluation:

```
git clone https://github.com/metaborg/jsglr2evaluation.git
cd jsglr2evaluation
```

Build and run the Docker image:

```
docker build -f docker/Dockerfile -t jsglr2evaluation . && docker run --rm -v ~/jsglr2evaluation-data:/jsglr2evaluation/data -e "TARGET=all" -e "EVALUATION_TARGET=all" jsglr2evaluation
```

This will use `~/jsglr2evaluation-data` on the host for persistence.

Optionally, you could overwrite the default config by placing a `config.yml` in your working directory (e.g. `~/jsglr2evaluation-data/config.yml`).
If you choose a different filename, e.g. `config_artifact.yml`, you can instruct the Docker run to use it by passing `-e "EVALUATION_CONFIG=config_artifact.yml"`.
