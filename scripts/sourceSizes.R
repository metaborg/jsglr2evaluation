source("common.R")

path <- args[1]
name <- args[2]
sizes <- read.csv(file=paste(path, "sizes.csv", sep="/"), header=FALSE, sep=",")

savePlot(function() {
  hist(sizes[,1] / 1024,
       main=name,
       xlab="file size (kb)",
       ylab="# files")
}, file=paste(path, "sizes", sep="/"))