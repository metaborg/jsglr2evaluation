# JSGLR2 Evaluation Scripts

## Prerequisites

 - Java 8
 - Ammonite for Scala scripting: https://ammonite.io/
 - R: https://www.r-project.org/ (in particular, `Rscript`)
 - Python

## Run

Execute everything with defaults, which expects the Spoofax sources at `~/spoofax/releng` and will use `~/jsglr2evaluation-data` as working directory:

```
make all
```

## Config

Specify Spoofax location with `SPOOFAX_DIR`:

```
make SPOOFAX_DIR=~/spoofax/releng all
```

Specify working directory for languages, sources, measurements, results, etc., with `DATA_DIR` (defaults to `~/jsglr2evaluation-data`):

```
make DATA_DIR=~/jsglr2evaluation-data all
```

Specify path to generate figures (LaTeX tables and plots) to with `FIGURES_DIR` (defaults to `~/jsglr2evaluation-data/figures`):

```
make FIGURES_DIR=~/path/to/paper/generated all
```

During development, automatically regenerate the website at path `/dev` when `addToWebsite.sc` changes:

```
make DEV=true addToWebsiteWatch
```
