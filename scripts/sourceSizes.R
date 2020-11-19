source("common.R")

path <- args[1]
name <- args[2]
sizes <- read.csv(file=paste(path, "sizes.csv", sep="/"), header=FALSE, sep=",")

savePlot(function() {
  kbs <- sizes[,1] / 1024

  hist(kbs,
       main="File sizes",
       xlab="file size (kB)",
       ylab="# files",
       breaks=seq(from=0, to=ceiling(max(kbs)), by=1))
}, file=paste(path, "sizes", sep="/"))