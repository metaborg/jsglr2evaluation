import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $ivy.`io.circe::circe-generic-extras:0.13.0`
import $ivy.`io.circe::circe-yaml:0.11.0-M1`

import cats.syntax.either._
import io.circe._
import io.circe.generic.extras.auto._
import io.circe.generic.extras.Configuration
import io.circe.yaml._
import java.time.LocalDateTime
import java.io.{FileInputStream, InputStream}

// This allows default arguments in ADTs: https://stackoverflow.com/a/47644276
implicit val customConfig: Configuration = Configuration.default.withDefaults

case class Config(
    warmupIterations: Int = 1,
    benchmarkIterations: Int = 1,
    batchSamples: Option[Int] = None,
    shrinkBatchSources: Option[Int] = None,
    individualBatchSources: Boolean = true,
    implode: Option[Boolean],
    variants: Seq[String] = Seq("standard", "elkhound", "recovery", "incremental", "jsglr1"),
    //variants: Seq[String] = Seq("standard", "elkhound", "recovery", "recoveryElkhound", "incremental", "recoveryIncremental", "jsglr1"),
    languages: Seq[Language],
)

case class Language(id: String, name: String, extension: String, parseTable: ParseTable, sources: Sources, antlrBenchmarks: Seq[ANTLRBenchmark] = Seq.empty) {
    def parseTableStream(implicit suite: Suite) = parseTable match {
        case parseTable @ GitSpoofax(_, _, _, dynamic) if dynamic => parseTable.bin(this)
        case _ => parseTable.term(this)
    }
    def parseTablePath(implicit suite: Suite) = parseTable match {
        case parseTable @ GitSpoofax(_, _, _, dynamic) if dynamic => parseTable.binPath(this)
        case _ => parseTable.termPath(this)
    }
    def parseTableTermPath(implicit suite: Suite) = parseTable.termPath(this)
    def dynamicParseTableGeneration = parseTable match {
        case GitSpoofax(_, _, _, dynamic) => dynamic
        case _ => false
    }

    def sourcesDir(implicit suite: Suite) = suite.sourcesDir / id
    
    def measurementsDir(implicit suite: Suite) = suite.measurementsDir / id

    def measurementsBatch(source: Option[BatchSource], variant: String = "standard")(implicit suite: Suite) = {
        val measurementsCSV = (source match {
            case None         => measurementsDir / "batch"
            case Some(source) => measurementsDir / "batch" / source.id
        }) / "parsing.csv"

        CSV.parse(measurementsCSV).rows.find(_("name") == variant).get
    }

    def measurementsIncremental(source: Option[IncrementalSource])(implicit suite: Suite): (
        Seq[Map[String, Long]], Seq[Map[String, Double]], Seq[Map[String, Long]], Seq[Map[String, Double]],
        Map[String, Long], Map[String, Double], Map[String, Long], Map[String, Double]
    ) = {
        import IncrementalMeasurementsTableUtils.relativeTo
        source match {
            case None =>
                val (rows, percs, skewRows, skewPercs) = sources.incremental.map { source =>
                    val (_, _, _, _, row, perc, skewRow, skewPerc) = measurementsIncremental(Some(source))
                    (row, perc, skewRow, skewPerc)
                }.unzip4
                (rows, percs, skewRows, skewPercs, rows.avgMaps, percs.avgMaps, skewRows.avgMaps, skewPercs.avgMaps)
            case Some(source) =>
                val measurementsCSV = measurementsDir / "incremental" / source.id / "parsing-incremental.csv"
                val csvRows = CSV.parse(measurementsCSV).rows.map { row =>
                    row.values.map{ case k -> v => k -> v.toLong }
                }.filter(_.values.size > 1) // Dropping empty rows
                val csvRowsExceptLast = csvRows.dropRight(1)
                val skewRows = csvRowsExceptLast.zip(csvRows.drop(1)).map { case (prevRow, row) =>
                    (prevRow - "version").map{ case k -> v => s"${k}Prev" -> v} ++ row
                }
                val skewRowsWithFirst = csvRows(0) +: skewRows
                val avgs = csvRowsExceptLast.avgMaps
                val avgPercs = csvRowsExceptLast.avgPercs(relativeTo)
                val skewAvgs = skewRows.avgMaps
                val skewAvgPercs = skewRows.avgPercs(relativeTo)
                (
                    csvRows, csvRows.percs(relativeTo), skewRowsWithFirst, skewRowsWithFirst.percs(relativeTo),
                    avgs, avgPercs, skewAvgs, skewAvgPercs
                )
        }
    }

    def measurementsParseTable(implicit suite: Suite) =
        CSV.parse(measurementsDir / "batch" / "parsetable.csv").rows.head

    def benchmarksDir(implicit suite: Suite) = suite.benchmarksDir / id
    def memoryBenchmarksDir(implicit suite: Suite) = suite.memoryBenchmarksDir / id

    def sourceFilesBatch(source: Option[BatchSource] = None)(implicit suite: Suite) = ls.rec! (source match {
        case Some(source) => sourcesDir / "batch" / source.id
        case None => sourcesDir / "batch"
    }) |? (_.ext == extension)

    def sourcesBatchNonEmpty(implicit suite: Suite) =
        sources.batch.filter { source =>
            sourceFilesBatch(Some(source)).nonEmpty
        }
    
    def sourceFilesBatchSampled(implicit suite: Suite): Seq[Path] = {
        if (suite.batchSamples.isEmpty)
            Nil
        else {
            val files = sourceFilesBatch() sortBy(-_.size)
            val trimPercentage: Float = 10F

            val from = (trimPercentage / 100F) * files.size
            val to = ((100F - trimPercentage) / 100F) * files.size

            val filesTrimmed = files.slice(from.round, to.round)

            val fileCount = filesTrimmed.size
            val step = fileCount / suite.batchSamples.get

            if (fileCount > 0)
                for (i <- 0 until suite.batchSamples.get) yield filesTrimmed(i * step)
            else
                Nil
        }
    }

    def sourceFilesIncremental(implicit suite: Suite) = ls.rec! sourcesDir / "incremental" |? (_.ext == extension)

    def sourceFilesRecovery(source: Option[BatchSource] = None)(implicit suite: Suite) = ls.rec! (source match {
        case Some(source) => sourcesDir / "recovery" / source.id
        case None => sourcesDir / "recovery"
    }) |? (_.ext == extension)
}

sealed trait ParseTable {
    def term(language: Language)(implicit suite: Suite) = new FileInputStream(termPath(language).toString)
    def termPath(language: Language)(implicit suite: Suite): Path
}
case class GitSpoofax(repo: String, subDir: String, version: String = "master", dynamic: Boolean = false) extends ParseTable {
    def repoDir(language: Language)(implicit suite: Suite) = Suite.languagesDir / language.id
    def spoofaxProjectDir(language: Language)(implicit suite: Suite) = repoDir(language) / RelPath(subDir)
    
    def termPath(language: Language)(implicit suite: Suite) = spoofaxProjectDir(language) / "target" / "metaborg" / "sdf.tbl"
    
    def binPath(language: Language)(implicit suite: Suite) = spoofaxProjectDir(language) / "target" / "metaborg" / "table.bin"
    def bin(language: Language)(implicit suite: Suite) = new FileInputStream(binPath(language).toString)
}
case class LocalParseTable(file: String) extends ParseTable {
    def termPath(language: Language)(implicit suite: Suite) = pwd / RelPath(file)
}

object ParseTable {
    implicit val decodeParseTable: Decoder[ParseTable] =
        Decoder[GitSpoofax]     .map[ParseTable](identity) or
        Decoder[LocalParseTable].map[ParseTable](identity)
}

case class Sources(batch: Seq[BatchSource] = Seq.empty, incremental: Seq[IncrementalSource] = Seq.empty, recovery: Seq[BatchLocalSource] = Seq.empty)

sealed trait Source {
    def id: String
    def getName: String
}
sealed trait RepoSource extends Source {
    def repo: String
}
sealed trait LocalSource extends Source {
    def path: String
}

sealed trait BatchSource extends Source
case class BatchRepoSource(id: String, repo: String, name: String = "") extends BatchSource with RepoSource {
    def getName = if (name == "") id else name
}
case class BatchLocalSource(id: String, path: String, name: String = "") extends BatchSource with LocalSource {
    def getName = if (name == "") id else name
}

object BatchSource {
    implicit val decodeBatchSource: Decoder[BatchSource] =
        Decoder[BatchRepoSource] .map[BatchSource](identity) or
        Decoder[BatchLocalSource].map[BatchSource](identity)
}

case class IncrementalSource(id: String, repo: String, name: String = "",
        fetchOptions: Seq[String] = Seq.empty, files: Seq[String] = Seq.empty, versions: Int = -1) extends RepoSource {
    def getName = if (name == "") id else name
}

case class ANTLRBenchmark(id: String, benchmark: String)

sealed trait Comparison {
    def name: String
    def dir: String
    def implode: Boolean
}
case object InternalParse extends Comparison {
    def name = "internal comparison, without imploding"
    def dir = "internal-parse"
    def implode = false
}
case object Internal extends Comparison {
    def name = "internal comparison, with imploding"
    def dir = "internal"
    def implode = true
}
case object External extends Comparison {
    def name = "external comparison, with imploding"
    def dir = "external"
    def implode = true
}

case class Suite(configPath: Path, languages: Seq[Language], variants: Seq[String], dir: Path, implode: Option[Boolean], individualBatchSources: Boolean, warmupIterations: Int, benchmarkIterations: Int, batchSamples: Option[Int], shrinkBatchSources: Option[Int], spoofaxDir: Path, figuresDir: Path, dev: Boolean) {
    def languagesDir        = dir / "languages"
    def sourcesDir          = dir / "sources"
    def measurementsDir     = dir / "measurements"
    def benchmarksDir       = dir / "benchmarks"
    def memoryBenchmarksDir = dir / "memoryBenchmarks"
    def resultsDir          = dir / "results"
    def websiteDir          = dir / "website"

    def scopes = Seq(
        if (languages.exists(_.sources.batch.nonEmpty)) Some("batch") else None,
        if (languages.exists(_.sources.incremental.nonEmpty)) Some("incremental") else None
    ).flatten
}

object Suite {

    implicit val suite = {
        val dir        = sys.env.get("JSGLR2EVALUATION_DATA_DIR").map(getPath).getOrElse(throw new IllegalArgumentException("missing 'JSGLR2EVALUATION_DATA_DIR' environment variable"))
        val spoofaxDir = sys.env.get("JSGLR2EVALUATION_SPOOFAX_DIR").map(getPath).getOrElse(pwd / up / up / up)
        val figuresDir = sys.env.get("JSGLR2EVALUATION_FIGURES_DIR").map(getPath).getOrElse(dir / "figures")
        val dev        = sys.env.get("JSGLR2EVALUATION_DEV").map(_.toBoolean).getOrElse(false)

        val configPath = {
            val filename = RelPath(sys.env.get("CONFIG").getOrElse("config.yml"))

            if (exists! (dir / filename))
                dir / filename
            else
                pwd / filename
        }
        val configJson = parser.parse(read! configPath)
        val config = configJson.flatMap(_.as[Config]).valueOr(throw _)

        Suite(configPath, config.languages, config.variants, dir,config.implode, config.individualBatchSources,  config.warmupIterations, config.benchmarkIterations, config.batchSamples, config.shrinkBatchSources, spoofaxDir, figuresDir, dev)
    }

    implicit def languagesDir        = suite.languagesDir
    implicit def sourcesDir          = suite.sourcesDir
    implicit def measurementsDir     = suite.measurementsDir
    implicit def benchmarksDir       = suite.benchmarksDir
    implicit def memoryBenchmarksDir = suite.memoryBenchmarksDir
    implicit def resultsDir          = suite.resultsDir
    implicit def figuresDir          = suite.figuresDir
    implicit def websiteDir          = suite.websiteDir
    implicit def dev                 = suite.dev

    implicit def inScope(scope: String) = suite.scopes.contains(scope)

    implicit def parseTableMeasurementsPath = resultsDir / "measurements-parsetable.csv"
    implicit def parsingMeasurementsPath    = resultsDir / "measurements-parsing.csv"

    implicit def batchResultsDir        = resultsDir / "batch"
    implicit def batchSampledResultsDir = resultsDir / "batch-sampled"

    implicit def incrementalResultsDir = resultsDir / "incremental"

}

def getPath(path: String) =
    if (path.startsWith("~/"))
        Path(System.getProperty("user.home") + path.substring(1))
    else if (path.startsWith("./"))
        pwd / RelPath(path.substring(2))
    else if (path.startsWith(".."))
        pwd / RelPath(path)
    else
        Path(path)

def timed(name: String)(block: => Unit)(implicit suite: Suite): Unit = {
    println(s"$name: start @ ${LocalDateTime.now}")
    val t0 = System.currentTimeMillis()
    
    block
    val t1 = System.currentTimeMillis()

    val seconds = (BigDecimal(t1 - t0)) / 1000

    val report =
        s"$name: finished in " +
        (if (seconds < 60)
            s"${round(seconds, 1)}s"
        else if (seconds < 3600)
            s"${round(seconds / 60, 1)}m"
        else
            s"${round(seconds / 3600, 1)}h")

    println(report)

    write.append(suite.dir / "timing.txt", s"${LocalDateTime.now} $report\n")
}

case class CSV(columns: Seq[String], rows: Seq[CSVRow])
case class CSVRow(values: Map[String, String]) {
    def apply(column: String) = values.get(column).getOrElse("")
}

object CSV {
    // Source: https://stackoverflow.com/a/13336039
    private val commaRegex = """,(?=([^\"]*\"[^\"]*\")*[^\"]*$)"""

    private def stripQuotes(s: String) =
        if (s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else s

    private def parseLine(line: String) =
        line.split(commaRegex).map(stripQuotes)

    def parse(file: Path): CSV = {
        read.lines(file) match {
            case headerLine +: rowLines =>
                val columns = parseLine(headerLine).toSeq

                val rows = rowLines.map { rowLine =>
                    CSVRow((columns zip parseLine(rowLine)).toMap)
                }

                CSV(columns, rows)
        }
    }

}

import scala.math.BigDecimal.RoundingMode

def round(number: BigDecimal, scale: Int = 0): BigDecimal = number.setScale(scale, RoundingMode.HALF_UP)
def round(number: String): String = if (number != "NaN" && number != "") round(BigDecimal(number)).toString else number

import java.util.concurrent._

def withTimeout[T](body: => T, timeout: Long)(onTimeOut: => T)(onFailure: Throwable => T): T = {
    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit(new Callable[T] {
        def call: T = body
    })

    val res = try {
        future.get(timeout, TimeUnit.SECONDS)
    } catch {
        case _: TimeoutException => onTimeOut
        case e => onFailure(e)
    }

    // Explicitly calling shutdown to prevent garbage collection of executor before future task is finished:
    // https://bugs.openjdk.java.net/browse/JDK-8145304?focusedCommentId=13877642&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-13877642
    executor.shutdown()

    res
}

implicit class List4Tuple[A, B, C, D](val list: TraversableOnce[(A, B, C, D)]) extends AnyVal {
    def unzip4(): (List[A], List[B], List[C], List[D]) = list match {
        case Nil => (Nil, Nil, Nil, Nil)
        case (a, b, c, d) :: tail =>
            val (aa, bb, cc, dd) = tail.unzip4
            (a :: aa, b :: bb, c :: cc, d :: dd)
    }
}

implicit class SumMapsLong(val maps: Seq[Map[String, Long]]) extends AnyVal {
    def sumMaps(): Map[String, Long] =
        maps.fold(Map[String, Long]()) { (acc, row) =>
            row.keys.map(k => k -> (acc.getOrElse(k, 0L) + row(k))).toMap
        }

    def avgMaps(): Map[String, Long] =
        maps.sumMaps.map { case k -> v => k -> (v / maps.length + (if ((v % maps.length) * 2 >= maps.length) 1L else 0L))}

    def percs(relativeTo: Map[String, String]): Seq[Map[String, Double]] =
        maps.map(row => relativeTo.keys.filter(key => row.contains(key) && row.contains(relativeTo(key))).map { k =>
            k -> (row(k).toDouble * 100.0 / row(relativeTo(k)).toDouble)
        }.toMap)

    def avgPercs(relativeTo: Map[String, String]): Map[String, Double] = maps.percs(relativeTo).avgMaps
}

implicit class SumMapsDouble(val maps: Seq[Map[String, Double]]) extends AnyVal {
    def sumMaps(): Map[String, Double] =
        maps.fold(Map[String, Double]()) { (acc, row) =>
            row.keys.map(k => k -> (acc.getOrElse(k, 0.0) + (if (row(k).isNaN) 0 else row(k)))).toMap
        }

    def avgMaps(): Map[String, Double] =
        maps.sumMaps.map { case k -> v => k -> (v / maps.filter(row => !row(k).isNaN).length)}
}

object IncrementalMeasurementsTableUtils {
    val measurementsCells = Seq("parseNodes", "parseNodesAmbiguous", "parseNodesIrreusable", "characterNodes")

    val measurementsCellsSkew = Seq(
        "createParseNode", "parseNodesReused", "parseNodesRebuilt", "shiftParseNode", "shiftCharacterNode",
        "breakDowns", "breakDownIrreusable", "breakDownNoActions", "breakDownTemporary", "breakDownWrongState"
    )

    val measurementsCellsSummary = Seq(
        "parseNodesIrreusable", "parseNodesReused", "breakDowns", "parseNodesRebuilt",
        "breakDownIrreusable", "breakDownNoActions", "breakDownTemporary", "breakDownWrongState"
    )

    val relativeTo = Map(
        "parseNodesAmbiguous" -> "parseNodes",
        "parseNodesIrreusable" -> "parseNodes",
        "parseNodesReused" -> "parseNodesPrev",
        "parseNodesRebuilt" -> "parseNodesPrev",
        "breakDowns" -> "parseNodesPrev",
        "breakDownIrreusable" -> "breakDowns",
        "breakDownNoActions" -> "breakDowns",
        "breakDownTemporary" -> "breakDowns",
        "breakDownWrongState" -> "breakDowns",
    )

    def cellMapper(row: Map[String, Long], percentages: Map[String, Double], percentageOnly: Boolean = false)(key: String) = {
        if (percentages.contains(key)) {
            val percentage = f"${percentages(key)}%2.2f%%"
            if (percentageOnly) percentage else s"${row(key)}\n($percentage)"
        } else
            row(key).toString
    }

    def getAllMeasurements(languages: TraversableOnce[Language])(implicit suite: Suite) =
        languages.map(_.measurementsIncremental(None)).map {
            case (_, _, _, _, row, perc, skewRow, skewPerc) => (row, perc, skewRow, skewPerc)
        }.unzip4
}
