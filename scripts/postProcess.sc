import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.spoofaxDeps

import $ivy.`org.metaborg:org.spoofax.jsglr2:2.6.0-SNAPSHOT`

import org.spoofax.jsglr2.incremental.EditorUpdate
import org.spoofax.jsglr2.incremental.diff.IStringDiff
import org.spoofax.jsglr2.incremental.diff.JGitHistogramDiff

import scala.collection.JavaConverters._

import $file.common, common._, Suite._

val diff: IStringDiff = new JGitHistogramDiff();

println("Processing results...")

mkdir! resultsDir

val languagesWithBatchSources = suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty)
if (languagesWithBatchSources.nonEmpty) {
    val dir = languagesWithBatchSources(0).measurementsDir

    // Copy header from measurements CSV
    write.over(parseTableMeasurementsPath, "language," + read.lines(dir / "batch" / "parsetable.csv")(0))
    write.over(parsingMeasurementsPath,    "language," + read.lines(dir / "batch" / "parsing.csv")(0))

    // Setup header for benchmarks CSV
    mkdir! batchResultsDir
    write.over(batchResultsDir / "time.csv",       "language,variant,score,error,low,high\n")
    write.over(batchResultsDir / "throughput.csv", "language,variant,score,low,high\n")

    suite.languages.foreach { language =>
        val languageResultsDir = batchResultsDir / language.id

        mkdir! languageResultsDir

        write.over(languageResultsDir / "time.csv",       "language,variant,score,error,low,high\n")
        write.over(languageResultsDir / "throughput.csv", "language,variant,score,low,high\n")
        
        language.sourcesBatchNonEmpty.foreach { source =>
            val sourceResultsDir = batchResultsDir / language.id / source.id

            mkdir! sourceResultsDir

            write.over(sourceResultsDir / "time.csv",       "language,variant,score,error,low,high\n")
            write.over(sourceResultsDir / "throughput.csv", "language,variant,score,low,high\n")
        }
    }

    mkdir! perFileResultsDir
    write.over(perFileResultsDir / "time.csv",       "language,variant,score,error,low,high,size\n")
    write.over(perFileResultsDir / "throughput.csv", "language,variant,score,low,high,size\n")
}

// Normalization: chars / ms == 1000 chars / s

suite.languages.foreach { language =>
    println(" " + language.name)

    if (language.sourcesBatchNonEmpty.nonEmpty) {

        // Measurements

        write.append(parseTableMeasurementsPath, "\n" + language.id + "," + read.lines(language.measurementsDir / "batch" / "parsetable.csv")(1))
        write.append(parsingMeasurementsPath, "\n" + language.id + "," + read.lines(language.measurementsDir / "batch" / "parsing.csv")(1))

        // Benchmarks (batch)
        def processBenchmarkCSV(benchmarkCSV: CSV, variant: CSVRow => String, destinationPath: Path, destinationPathNormalized: Path, normalize: BigDecimal => BigDecimal, append: String = "") = {
            benchmarkCSV.rows.foreach { row =>
                val rawScore = row("Score")
                val rawError = row("Score Error (99.9%)")

                val score = BigDecimal(rawScore)
                val error = if (rawError != "NaN") BigDecimal(rawError) else BigDecimal(0)

                write.append(destinationPath, language.id + "," + variant(row) + "," + round(score) + "," + round(error) + "," + round(score - error) + "," + round(score + error) + append + "\n")
                write.append(destinationPathNormalized, language.id + "," + variant(row) + "," + round(normalize(score)) + "," + round(normalize(score + error)) + "," + round(normalize(score - error)) + append + "\n")
            }
        }

        def batchBenchmarks(source: Option[BatchSource]) = {
            val (measurementsDir, benchmarksDir, resultsDirs) = source match {
                case None => (
                    language.measurementsDir / "batch",
                    language.benchmarksDir / "batch",
                    Seq(batchResultsDir, batchResultsDir / language.id)
                )
                case Some(source) => (
                    language.measurementsDir / "batch" / source.id,
                    language.benchmarksDir / "batch" / source.id,
                    Seq(batchResultsDir / language.id / source.id)
                )
            }

            val characters = BigDecimal(CSV.parse(measurementsDir / "parsing.csv").rows.head("characters"))
            val normalize: BigDecimal => BigDecimal = score => characters / score

            resultsDirs.foreach { resultsDir =>
                processBenchmarkCSV(CSV.parse(benchmarksDir / "jsglr2.csv"), row => row("Param: variant"), resultsDir / "time.csv", resultsDir / "throughput.csv", normalize)
                processBenchmarkCSV(CSV.parse(benchmarksDir / "jsglr1.csv"), _   => "jsglr1",              resultsDir / "time.csv", resultsDir / "throughput.csv", normalize)

                language.antlrBenchmarks.foreach { antlrBenchmark =>
                    processBenchmarkCSV(CSV.parse(benchmarksDir / s"${antlrBenchmark.id}.csv"), _ => antlrBenchmark.id, resultsDir / "time.csv", resultsDir / "throughput.csv", normalize)
                }
            }
        }

        batchBenchmarks(None)

        language.sourcesBatchNonEmpty.foreach { source =>
            batchBenchmarks(Some(source))
        }

        // Benchmarks (per file)

        language.sourceFilesPerFileBenchmark.foreach { file =>
            val characters = (read ! file).length
            val normalize: BigDecimal => BigDecimal = score => characters / score

            processBenchmarkCSV(CSV.parse(language.benchmarksDir / "perFile" / s"${file.last.toString}.csv"), row => row("Param: variant"), perFileResultsDir / "time.csv", perFileResultsDir / "throughput.csv", normalize, "," + characters)
        }
    }

    // Benchmarks (incremental)

    val parserTypes = Seq("Batch", "Incremental", "IncrementalNoCache")
    language.sources.incremental.foreach { source => {
        Map(false -> "parse", true -> "parse+implode").foreach { case (implode, parseImplodeStr) =>
            mkdir! incrementalResultsDir / language.id
            val resultPath = incrementalResultsDir / language.id / s"${source.id}-${parseImplodeStr}.csv"

            // CSV header
            write.over(resultPath, """"i"""")
            parserTypes.foreach { parserType =>
                write.append(resultPath, s""","$parserType","$parserType Error (99.9%)"""")
            }
            write.append(resultPath, ""","Size (bytes)","Removed","Added","Changes"""" + "\n")

            val sourceDir = language.sourcesDir / "incremental" / source.id
            for (i <- 0 until (ls! sourceDir).length) {
                val csv = try {
                    CSV.parse(language.benchmarksDir / "jsglr2incremental" / source.id / s"$i.csv")
                } catch {
                    case _ => CSV(Seq.empty, Seq.empty)
                }
                val rows = csv.rows.filter(_("Param: implode") == implode.toString)

                write.append(resultPath, i.toString)

                parserTypes.foreach { parserType => {
                    write.append(resultPath, rows.find(_("Param: parserType") == parserType) match {
                        case Some(row) => "," + row("Score") + "," + row("Score Error (99.9%)").replace("NaN", "")
                        case None => ",,"
                    })
                }}

                val totalSize = ((ls! sourceDir / s"$i") | stat | (_.size)).sum
                write.append(resultPath, "," + totalSize)

                val diffs: Seq[java.util.List[EditorUpdate]] = (ls! sourceDir / s"$i").map(file => diff.diff(
                    try {
                        read! file / up / up / s"${i-1}" / file.last
                    } catch {
                        case _ => "" // This case is reached when i == 0 or when a file is new in this iteration
                    },
                    read! file)
                )
                val deleted = diffs.map(diff => diff.asScala.map(_.deletedLength).sum).sum
                val inserted = diffs.map(diff => diff.asScala.map(_.insertedLength).sum).sum
                val numChanges = diffs.map(diff => diff.size).sum
                write.append(resultPath, "," + deleted + "," + inserted + "," + numChanges + "\n")
            }
        }
    }}
}
