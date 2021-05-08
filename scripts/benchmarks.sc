import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Executing benchmarks...")

suite.languages.foreach { language =>
    println(" " + language.name)

    val benchmarksMvnDir = (suite.spoofaxDir / "jsglr" / "org.spoofax.jsglr2.benchmark")

    val warmupIterations = suite.warmupIterations
    val benchmarkIterations = suite.benchmarkIterations

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

    def benchmarkTreeSitter(resultsPath: Path, sourcePath: Path) =
        benchmark(
            "TreeSitterBenchmark.benchmark",
            resultsPath,
            Seq(
                s"language=${language.id}",
                s"extension=${language.extension}",
                s"sourcePath=${sourcePath}",
                s"type=multiple"
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

    def benchmarkTreeSitterIncremental(resultsPath: Path, sourcePath: Path, params: Map[String, String] = Map.empty) = {
        for (i <- -1 until (ls! sourcePath).length) {
            println(f"    iteration $i%3d: start @ ${java.time.LocalDateTime.now}")
            if (i >= 0 && (ls! sourcePath / f"$i").isEmpty) {
                println(f"      Skipped (no valid sources)")
            } else {
                benchmark(
                    "TreeSitterBenchmarkIncremental.benchmark",
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

    def batchBenchmarks(implode: Boolean, source: Option[BatchSource]) = {
        val dir = if (implode) "parse+implode" else "parse"
        val (sourcesDir, reportDir) = source match {
            case None => (
                language.sourcesDir / "batch",
                language.benchmarksDir / "batch" / dir
            )
            case Some(source) => (
                language.sourcesDir / "batch" / source.id,
                language.benchmarksDir / "batch" / dir / source.id
            )
        }

        mkdir! reportDir
        
        timed(s"benchmark [JSGLR2/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
            benchmarkJSGLR("JSGLR2BenchmarkExternal", reportDir / "jsglr2.csv", sourcesDir, "multiple", Map("implode" -> implode.toString, "variant" -> suite.jsglr2variants.mkString(",")))
        }

        timed(s"benchmark [JSGLR1/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
            benchmarkJSGLR("JSGLR1BenchmarkExternal", reportDir / "jsglr1.csv", sourcesDir, "multiple", Map("implode" -> implode.toString))
        }

        if (implode) {
            language.antlrBenchmarks.foreach { antlrBenchmark =>
                timed(s"benchmark [${antlrBenchmark.id}/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
                    benchmarkANTLR(antlrBenchmark.benchmark, reportDir / s"${antlrBenchmark.id}.csv", sourcesDir, "multiple")
                }
            }

            if (language.extension == "java") {
                timed(s"benchmark [TreeSitter/batch] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.fold("")("/" + _.id)) {
                    benchmarkTreeSitter(reportDir / s"tree-sitter.csv", sourcesDir)
                }
            }
        }
    }

    if (language.sourcesBatchNonEmpty.nonEmpty) {
        if (!suite.implode.getOrElse(false)) {
            batchBenchmarks(false, None)
        }

        if (suite.implode.getOrElse(true)) {
            batchBenchmarks(true, None)
        }

        if (suite.individualBatchSources) {
            language.sourcesBatchNonEmpty.foreach { source =>
                if (!suite.implode.getOrElse(false)) {
                    batchBenchmarks(false, Some(source))
                }

                if (suite.implode.getOrElse(true)) {
                    batchBenchmarks(true, Some(source))
                }
            }
        }
    }

    language.sources.incremental.foreach { source => {
        mkdir! language.benchmarksDir / "jsglr2incremental" / source.id
        timed(s"benchmark [JSGLR2/incremental] (w: $warmupIterations, i: $benchmarkIterations) ${language.id} ${source.id}") {
            benchmarkJSGLRIncremental("JSGLR2BenchmarkIncrementalExternal", language.benchmarksDir / "jsglr2incremental" / source.id, language.sourcesDir / "incremental" / source.id)
        }

        if (language.extension == "java") {
            mkdir! language.benchmarksDir / "tree-sitter-incremental" / source.id
            timed(s"benchmark [TreeSitter/incremental] (w: $warmupIterations, i: $benchmarkIterations) " + language.id + source.id) {
                benchmarkTreeSitterIncremental(language.benchmarksDir / "tree-sitter-incremental" / source.id, language.sourcesDir / "incremental" / source.id)
            }
        }
    }}
}
