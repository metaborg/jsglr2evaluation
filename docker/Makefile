.PHONY: all pull-spoofax-releng pull-jsglr2 build-spoofax build-jsglr2 build-jsglr2-benchmark build-jsglr2-measure evaluation


# Configuration (using environment variables or arguments passed to make)
SPOOFAX_VERSION?=master
SDF_VERSION?=develop/jsglr2
JSGLR_VERSION?=develop/jsglr2
EVALUATION_TARGET?=all


all: build-spoofax build-jsglr2-benchmark build-jsglr2-measure evaluation


pull-spoofax-releng:
	if [ ! -d data/spoofax/.git ]; then git clone --recursive https://github.com/metaborg/spoofax-releng.git data/spoofax; fi;
	cd data/spoofax && \
		git checkout $(SPOOFAX_VERSION) && \
		git pull --rebase && \
		git submodule update --init --remote --recursive && \
		./b checkout -y && \
		./b update

pull-sdf: pull-spoofax-releng
	cd /jsglr2evaluation/data/spoofax/sdf && git checkout $(SDF_VERSION) && git pull

pull-jsglr: pull-spoofax-releng
	cd /jsglr2evaluation/data/spoofax/jsglr && git checkout $(JSGLR_VERSION) && git pull


build-spoofax: pull-sdf pull-jsglr
	cd /jsglr2evaluation/data/spoofax && ./b build java

build-jsglr: pull-jsglr
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr && mvn install
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2 && mvn install
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.integration && mvn install

build-jsglr2-benchmark: pull-jsglr tree-sitter-jar
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.benchmark && mvn install

tree-sitter-jar:
	cd scripts && make treeSitterJar

build-jsglr2-measure: pull-jsglr
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.measure && mvn install


# Note that DATA_DIR and SPOOFAX_DIR are set as environment variables in Dockerfile
evaluation:
	cd scripts && make $(EVALUATION_TARGET)
