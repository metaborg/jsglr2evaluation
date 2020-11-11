source("common.R")

batchSampledBenchmarksPlot <- function(inputFile, outputFile, dimension, unit) {
  if (!file.exists(inputFile))
    return()
  
  data <- read.csv(file=inputFile, header=TRUE, sep=",")
  data <- data[data$variant == "standard",]
  languages <- unique(data$language)

  languageSymbols <- symbols[match(languages, languages)]
  plotSymbols <- symbols[match(data$language, languages)]
  
  savePlot(function() {
    plot(data$size / 1000,
        data$score,
        main=paste("Batch parsing", dimension, "vs. file size"),
        xlab="File size (1000 characters)",
        ylab=unit,
        pch=plotSymbols,
        col=colors[match(c("standard"), allVariants)])
    
    legend("top", inset=0.05, legend=languages, pch=languageSymbols)
  }, file=outputFile)
}

resultsDir <- args[1]
figuresDir <- args[2]

dir.create(figuresDir, showWarnings = FALSE, recursive = TRUE)

batchSampledBenchmarksPlot(paste(resultsDir, "time.csv", sep="/"),       paste(figuresDir, "time", sep="/"),       "time",       "ms")
batchSampledBenchmarksPlot(paste(resultsDir, "throughput.csv", sep="/"), paste(figuresDir, "throughput", sep="/"), "throughput", "1000 chars/s")
