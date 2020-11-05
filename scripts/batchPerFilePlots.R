source("common.R")

perFileBenchmarksPlot <- function(inputFile, outputFile, dimension, unit) {
  if (!file.exists(inputFile))
    return()
  
  data <- read.csv(file=inputFile, header=TRUE, sep=",")
  data <- data[data$variant == "standard",]
  languages <- unique(data$language)

  savePlot(function() {
    plot(data$size / 1000,
        data$score,
        main=paste("Batch parsing", dimension, "vs. file size"),
        xlab="File size (1000 characters)",
        ylab=unit,
        pch=symbols[data$language])
    
    legend("top", inset=0.05, legend=languages, pch=symbols[languages])
  }, file=outputFile)
}

resultsDir <- args[1]
figuresDir <- args[2]

dir.create(figuresDir, showWarnings = FALSE, recursive = TRUE)

perFileBenchmarksPlot(paste(resultsDir, "time.csv", sep="/"),       paste(figuresDir, "time", sep="/"),       "time",       "ms")
perFileBenchmarksPlot(paste(resultsDir, "throughput.csv", sep="/"), paste(figuresDir, "throughput", sep="/"), "throughput", "1000 chars/s")
