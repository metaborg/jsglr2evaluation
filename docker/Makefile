.PHONY: all pull-spoofax-releng pull-jsglr2 build-spoofax build-jsglr2 build-jsglr2-benchmark build-jsglr2-measure evaluation

all: build-spoofax build-jsglr2-benchmark build-jsglr2-measure evaluation


pull-spoofax-releng:
	if [ ! -d data/spoofax/.git ]; then git clone --recursive https://github.com/metaborg/spoofax-releng.git data/spoofax; fi;
	cd data/spoofax && \
		git checkout $(SPOOFAX_VERSION) && \
		git pull --rebase && \
		git submodule update --init --remote --recursive && \
		./b checkout -y && \
		./b update

pull-jsglr2: pull-spoofax-releng
	cd /jsglr2evaluation/data/spoofax/jsglr && git checkout $(JSGLR_VERSION) && git pull

build-spoofax: pull-jsglr2
	cd /jsglr2evaluation/data/spoofax && ./b build java

build-jsglr2: pull-jsglr2
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr && mvn install
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2 && mvn install
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.integration && mvn install

build-jsglr2-benchmark: pull-jsglr2
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.benchmark && mvn install

build-jsglr2-measure: pull-jsglr2
	cd /jsglr2evaluation/data/spoofax/jsglr/org.spoofax.jsglr2.measure && mvn install


# Note that DATA_DIR and SPOOFAX_DIR are set as environment variables in Dockerfile
evaluation:
	cd scripts && make $(EVALUATION_TARGET)
