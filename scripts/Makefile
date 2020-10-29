.PHONY: all clean languages cleanLanguages sources cleanSources preProcessing cleanPreProcessing measurements cleanMeasurements benchmarks cleanBenchmarks postProcessing cleanPostProcessing reports reportLatex reportR reportPy cleanReports publish archive cleanArchive websiteRepo addToWebsite websitePush


# Configuration (using environment variables or arguments passed to make)
DATA_DIR?=~/jsglr2evaluation-data
REPORTS_DIR?=$(DATA_DIR)/reports
SPOOFAX_DIR?=~/spoofax/releng

# Shorthands
JSGLR_DIR=$(SPOOFAX_DIR)/jsglr
MEASUREMENTS_JAR=$(JSGLR_DIR)/org.spoofax.jsglr2.measure/target/org.spoofax.jsglr2.measure-2.6.0-SNAPSHOT.jar
BENCHMARKS_JAR=$(JSGLR_DIR)/org.spoofax.jsglr2.benchmark/target/org.spoofax.jsglr2.benchmark-2.6.0-SNAPSHOT.jar

# Environment variables passed to the Ammonite/Python/R scripts
export JSGLR2EVALUATION_DATA_DIR=$(DATA_DIR)
export JSGLR2EVALUATION_SPOOFAX_DIR=$(SPOOFAX_DIR)
export JSGLR2EVALUATION_REPORTS_DIR=$(REPORTS_DIR)


all: languages sources preProcessing $(MEASUREMENTS_JAR) measurements $(BENCHMARKS_JAR) benchmarks postProcessing reports publish

clean: cleanLanguages cleanSources cleanMeasurements cleanBenchmarks cleanPostProcessing cleanReports cleanArchive

cleanAll: clean all


# Pull Spoofax languages from GitHub and build them
languages:
	amm setupLanguages.sc

cleanLanguages:
	-rm -rf $(DATA_DIR)/languages


# Setup evaluation corpus by pulling projects from GitHub
sources:
	amm setupSources.sc

cleanSources:
	-rm -rf $(DATA_DIR)/sources


# Validate absence of invalid programs and aggregate files
preProcessing:
	JAVA_OPTS="-Xmx8G" amm preProcess.sc


# Perform measurements
measurements:
	amm measurements.sc

$(MEASUREMENTS_JAR): $(JSGLR_DIR)/org.spoofax.jsglr2.measure/src
	mvn -f $(JSGLR_DIR)/org.spoofax.jsglr2.measure -q install

cleanMeasurements:
	-rm -r $(DATA_DIR)/measurements
	-rm -r $(JSGLR_DIR)/org.spoofax.jsglr2.measure/target


# Performs benchmarks
benchmarks:
	amm benchmarks.sc

$(BENCHMARKS_JAR): $(JSGLR_DIR)/org.spoofax.jsglr2.benchmark/src
	mvn -f $(JSGLR_DIR)/org.spoofax.jsglr2.benchmark -q install

cleanBenchmarks:
	-rm -r $(DATA_DIR)/benchmarks
	-rm -r $(JSGLR_DIR)/org.spoofax.jsglr2.benchmarks/target


# Post process results from measurements and benchmarks
postProcessing:
	amm postProcess.sc

cleanPostProcessing:
	-rm -r $(DATA_DIR)/results


reports: reportLatex reportR .venv reportPy

# Reporting in Latex tables (batch)
reportLatex:
	amm reportLatex.sc

# Reporting in plots (batch)
reportR:
	Rscript report.R

.venv:
	./setup-venv.sh

# Reporting in plots (incremental)
# Cannot activate venv using `source` in Makefile, so directly calling Python instead: https://stackoverflow.com/a/57257640
reportPy:
	.venv/bin/python report.py

cleanReports:
	-rm -r $(REPORTS_DIR)


# Archive and publish reports to website
publish: cleanArchive archive websiteRepo addToWebsite websitePush

archive:
	cd $(DATA_DIR) && tar -czf archive.tar.gz measurements benchmarks results -C $(REPORTS_DIR)/.. reports

cleanArchive:
	-rm $(DATA_DIR)/archive.tar.gz

websiteRepo:
	if [ ! -d $(DATA_DIR)/website/.git ]; then git clone https://$(GITHUB_TOKEN)@github.com/metaborg/jsglr2evaluation-site.git $(DATA_DIR)/website; fi
	cd $(DATA_DIR)/website && git pull

addToWebsite:
	amm addToWebsite.sc

websitePush:
	cd $(DATA_DIR)/website && \
		git add -A && \
		git commit -m "Evaluation results" && \
		git push


cleanData:
	-rm -r $(DATA_DIR)/*

cleanM2:
	-rm -r $(DATA_DIR)/m2-repo
