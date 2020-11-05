source("common.R")

batchBenchmarksPlot <- function(inputFile, outputFile, dimension, unit, getLows, getHighs) {
  if (!file.exists(inputFile))
    return()
  
  data     <- read.csv(file=inputFile, header=TRUE, sep=",")
  variants <- unique(data$variant)
  
  scores <- tapply(data$score,      list(data$variant, data$language), function(x) c(x = x))
  lows   <- tapply(getLows(data),   list(data$variant, data$language), function(x) c(x = x))
  highs  <- tapply(getHighs(data),  list(data$variant, data$language), function(x) c(x = x))
  
  # order by original variant order
  scores <- scores[variants,]
  lows   <-   lows[variants,]
  highs  <-  highs[variants,]
  
  savePlot(function() {
    # https://datascienceplus.com/building-barplots-with-error-bars/
    barCenters <- barplot(height=scores,
                          main=paste("Batch parsing", dimension),
                          xlab="Language",
                          ylab=unit,
                          ylim=c(0, 1.01 * max(getHighs(data))),
                          col=colors[1:length(variants)],
                          legend=variants,
                          beside=TRUE)
    
    segments(barCenters, lows, barCenters, highs, lwd = 1)
    arrows(barCenters, lows, barCenters, highs, lwd = 1, angle = 90, code = 3, length = 0.05)
  }, file=outputFile)
}

batchTimeBenchmarksPlot <- function(inputFile, outputFile, dimension, unit) {
  batchBenchmarksPlot(inputFile, outputFile, "time", "ms", function(data) data$score - data$error, function(data) data$score + data$error)
}

batchThroughputBenchmarksPlot <- function(inputFile, outputFile, dimension, unit) {
  batchBenchmarksPlot(inputFile, outputFile, "throughput", "1000 chars/s", function(data) data$low, function(data) data$high)
}

resultsDir <- args[1]
reportsDir <- args[2]

dir.create(reportsDir, showWarnings = FALSE, recursive = TRUE)

batchTimeBenchmarksPlot(paste(resultsDir, "time.csv", sep="/"),             paste(reportsDir, "time", sep="/"))
batchThroughputBenchmarksPlot(paste(resultsDir, "throughput.csv", sep="/"), paste(reportsDir, "throughput", sep="/"))
