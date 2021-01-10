import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Executing benchmarks (batch sampled)...")

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

    if (language.sourcesBatchNonEmpty.nonEmpty) {
        timed(s"benchmark [JSGLR2/batch-sampled] (w: $warmupIterations, i: $benchmarkIterations) " + language.id) {
            mkdir ! (language.benchmarksDir / "batch-sampled")

            language.sourceFilesBatchSampled.foreach { file =>
                benchmarkJSGLR("JSGLR2BenchmarkExternal", language.benchmarksDir / "batch-sampled" / s"${file.last.toString}.csv", file, "single", Map("implode" -> "true", "variant" -> "standard"))
            }
        }
    }
}
