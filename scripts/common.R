options(warn=1)

args = commandArgs(trailingOnly=TRUE)

if (length(args) != 2) {
  dir        <- "~/jsglr2evaluation-data"
  reportsDir <- "~/jsglr2evaluation-data/reports"
} else {
  dir        <- args[1]
  reportsDir <- args[2]
}

savePlot <- function(plot, filename) {
  png(file=paste(filename, ".png", sep=""))
  plot()
  dev.off()
  
  pdf(file=paste(filename, ".pdf", sep=""))
  plot()
  dev.off()
}