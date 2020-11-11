import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Executing benchmarks...")

suite.languages.foreach { language =>
    println(" " + language.name)

    val benchmarksMvnDir = (suite.spoofaxDir / "jsglr" / "org.spoofax.jsglr2.benchmark")

    val warmupIterations = suite.iterations
    val benchmarkIterations = suite.iterations

    mkdir! language.benchmarksDir

    def benchmark(name: String, resultsPath: Path, testSetArgs: Seq[String], params: Map[String, String] = Map.empty) =
        println(%%(
            Seq(
                "java", "-jar", "target/org.spoofax.jsglr2.benchmark.jar",
                "-wi", warmupIterations.toString,
                "-i", benchmarkIterations.toString,
                "-f", 1.toString,
                "-rff", resultsPath.toString,
                name,
                "-jvmArgs=\"-DtestSet=" + testSetArgs.mkString(" ") + "\""
            ) ++ params.toSeq.flatMap {
                case (param, value) => Seq("-p", s"$param=$value")
            }
        )(benchmarksMvnDir))

    def benchmarkJSGLR(name: String, resultsPath: Path, sourcePath: Path, cardinality: String, params: Map[String, String] = Map.empty) =
        benchmark(
            name,
            resultsPath,
            Seq(
                s"language=${language.id}",
                s"extension=${language.extension}",
                s"parseTablePath=${language.parseTableTermPath}",
                s"sourcePath=${sourcePath}",
                s"type=${cardinality}"
            ),
            params
        )

    def benchmarkANTLR(name: String, resultsPath: Path, sourcePath: Path, cardinality: String) =
        benchmark(
            name,
            resultsPath,
            Seq(
                s"language=${language.id}",
                s"extension=${language.extension}",
                s"sourcePath=${sourcePath}",
                s"type=${cardinality}"
            ),
            Map.empty
        )

    def benchmarkJSGLRIncremental(name: String, resultsPath: Path, sourcePath: Path, params: Map[String, String] = Map.empty) = {
        for (i <- -1 until (ls! sourcePath).length) {
            println(f"    iteration $i%3d: start @ ${java.time.LocalDateTime.now}")
            if (i >= 0 && (ls! sourcePath / f"$i").isEmpty) {
                println(f"      Skipped (no valid sources)")
            } else {
                benchmark(
                    name,
                    resultsPath / s"$i.csv",
                    Seq(
                        s"language=${language.id}",
                        s"extension=${language.extension}",
                        s"parseTablePath=${language.parseTableTermPath}",
                        s"sourcePath=${sourcePath}",
                        s"iteration=${i}",
                    ),
                    params + ("i" -> s"$i")
                )
            }
        }
    }

    def batchBenchmarks(source: Option[BatchSource]) = {
        val (sourcesDir, reportDir) = source match {
            case None => (
                language.sourcesDir / "batch",
                language.benchmarksDir / "batch"
            )
            case Some(source) => (
                language.sourcesDir / "batch" / source.id,
                language.benchmarksDir / "batch" / source.id
            )
        }

        mkdir! reportDir

        timed(s"benchmark [JSGLR2/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
            benchmarkJSGLR("JSGLR2BenchmarkExternal", reportDir / "jsglr2.csv", sourcesDir, "multiple", Map("implode" -> "true"))
        }

        timed(s"benchmark [JSGLR1/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
            benchmarkJSGLR("JSGLR1BenchmarkExternal", reportDir / "jsglr1.csv", sourcesDir, "multiple", Map("implode" -> "true"))
        }

        language.antlrBenchmarks.foreach { antlrBenchmark =>
            timed(s"benchmark [${antlrBenchmark.id}/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
                benchmarkANTLR(antlrBenchmark.benchmark, reportDir / s"${antlrBenchmark.id}.csv", sourcesDir, "multiple")
            }
        }
    }

    if (language.sourcesBatchNonEmpty.nonEmpty) {
        batchBenchmarks(None)

        language.sourcesBatchNonEmpty.foreach { source =>
            batchBenchmarks(Some(source))
        }

        timed(s"benchmark [JSGLR2/batch-sampled] (w: $warmupIterations, i: $benchmarkIterations) " + language.id) {
            mkdir ! (language.benchmarksDir / "batch-sampled")

            language.sourceFilesBatchSampled.foreach { file =>
                benchmarkJSGLR("JSGLR2BenchmarkExternal", language.benchmarksDir / "batch-sampled" / s"${file.last.toString}.csv", file, "single", Map("implode" -> "true", "variant" -> "standard"))
            }
        }
    }

    language.sources.incremental.foreach { source => {
        mkdir! language.benchmarksDir / "jsglr2incremental" / source.id
        timed(s"benchmark [JSGLR2/incremental] (w: $warmupIterations, i: $benchmarkIterations) ${language.id} ${source.id}") {
            benchmarkJSGLRIncremental("JSGLR2BenchmarkIncrementalExternal", language.benchmarksDir / "jsglr2incremental" / source.id, language.sourcesDir / "incremental" / source.id)
        }
    }}
}
