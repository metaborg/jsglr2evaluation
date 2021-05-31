FROM ubuntu:20.04

# Setup directories
WORKDIR /jsglr2evaluation
RUN mkdir /jsglr2evaluation/bin
RUN mkdir /jsglr2evaluation/data

# Run apt-get without interactive dialogue
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl git locales make software-properties-common vim wget \
    openjdk-8-jdk python3-venv

# Set the locale to UTF-8 (http://jaredmarkell.com/docker-and-locales/)
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

# Install Maven 3
RUN cd bin &&\
    wget http://apache.mirror.anlx.net/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz &&\
    tar -zxf apache-maven-3.6.3-bin.tar.gz &&\
    cp -R apache-maven-3.6.3 /usr/local &&\
    ln -s /usr/local/apache-maven-3.6.3/bin/mvn /usr/bin/mvn &&\
    ln -s /usr/local/apache-maven-3.6.3/bin/mvnDebug /usr/bin/mvnDebug

# Local Maven settings (http://metaborg.org/dev/maven/#local-settings-file)
RUN mkdir /root/.m2
COPY docker/maven-settings.xml /root/.m2/settings.xml

# Install Ammonite (Scala scripting)
RUN sh -c '(echo "#!/usr/bin/env sh" && curl -L https://github.com/lihaoyi/Ammonite/releases/download/2.0.4/2.13-2.0.4) > /usr/local/bin/amm && chmod +x /usr/local/bin/amm'
ENV COURSIER_CACHE /jsglr2evaluation/data/coursier-cache

# Install R
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-keys E298A3A825C0D65DFD57CBB651716619E084DAB9
RUN add-apt-repository 'deb https://cloud.r-project.org/bin/linux/ubuntu focal-cran40/'
RUN apt-get update && apt-get -y install r-base

# Git config
RUN git config --global user.name "JSGLR2 Evaluation"
RUN git config --global user.email "j.denkers@tudelft.nl"
RUN echo ".DS_Store" >> ~/.gitignore_global
RUN git config --global core.excludesfile ~/.gitignore_global

# Install required Python packages for scripts into a virtual environment
COPY scripts/setup-venv.sh scripts/setup-venv.sh
RUN ./scripts/setup-venv.sh

# Add scripts
COPY scripts scripts
COPY parsetables parsetables
COPY docker/Makefile .

ENV SPOOFAX_VERSION=master
ENV JSGLR_VERSION=develop/jsglr2
ENV JSGLR_REMOTE_NAME?=origin
ENV JSGLR_REMOTE_URL?=http://github.com/metaborg/jsglr

ENV DATA_DIR=/jsglr2evaluation/data
ENV SPOOFAX_DIR=/jsglr2evaluation/data/spoofax

ENV TARGET=all
ENV EVALUATION_TARGET=all

ENTRYPOINT make $TARGET
