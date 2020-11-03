options(warn=1)

args = commandArgs(trailingOnly=TRUE)

if (Sys.getenv("JSGLR2EVALUATION_DATA_DIR") != "" && Sys.getenv("JSGLR2EVALUATION_REPORTS_DIR") != "") {
  dataDir    <- Sys.getenv("JSGLR2EVALUATION_DATA_DIR")
  reportsDir <- Sys.getenv("JSGLR2EVALUATION_REPORTS_DIR")
} else {
  dataDir    <- "~/jsglr2evaluation-data"
  reportsDir <- "~/jsglr2evaluation-data/reports"
}

savePlot <- function(plot, filename) {
  png(file=paste(filename, ".png", sep=""))
  plot()
  invisible(dev.off())
  
  pdf(file=paste(filename, ".pdf", sep=""))
  plot()
  invisible(dev.off())
}