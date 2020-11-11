options(warn=1)

args = commandArgs(trailingOnly=TRUE)

if (Sys.getenv("JSGLR2EVALUATION_DATA_DIR") != "" && Sys.getenv("JSGLR2EVALUATION_FIGURES_DIR") != "") {
  dataDir    <- Sys.getenv("JSGLR2EVALUATION_DATA_DIR")
  figuresDir <- Sys.getenv("JSGLR2EVALUATION_FIGURES_DIR")
} else {
  dataDir    <- "~/jsglr2evaluation-data"
  figuresDir <- "~/jsglr2evaluation-data/figures"
}

savePlot <- function(plot, filename) {
  png(file=paste(filename, ".png", sep=""))
  plot()
  invisible(dev.off())
  
  pdf(file=paste(filename, ".pdf", sep=""))
  plot()
  invisible(dev.off())
}

colors      <- c("#8c510a",  "#d8b365",  "#f6e8c3",     "#f5f5f5",             "#c7eae5", "#5ab4ac", "#01665e") # Color per parser variant, colorblind safe: http://colorbrewer2.org/#type=diverging&scheme=BrBG&n=6
allVariants <- c("standard", "recovery", "incremental", "recoveryIncremental", "jsglr1",  "antlr",   "antlr-optimized")
symbols     <- c(0,2,5,3) # Symbol per language