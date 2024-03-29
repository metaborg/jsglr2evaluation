import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $ivy.`org.jsoup:jsoup:1.7.2`, org.jsoup._
import $file.common, common._, Suite._
import java.io.File
import java.time._, java.time.format._

println("Adding to website...")

def indent(spaces: Int, str: String) = str.replaceAll("\n", s"\n${" " * spaces}")

def withTooltip(text: String, tooltip: String) =
    s"""<a data-toggle="tooltip" data-placement="top" title="$tooltip">$text</a>"""

def withNav(title: String, tabs: Seq[(String, String, String)]) = {
    val active = tabs.filter(_._3 != "").headOption.map(_._1).getOrElse("")

    val tabHeaders = tabs.map { case (id, name, content) =>
        s"""|<li class="nav-item" role="presentation">
            |  <a class="nav-link${if (id == active) " active" else ""}${if (content == "") " disabled" else ""}" id="$id-tab" data-toggle="tab" href="#$id" role="tab" aria-controls="$id" aria-selected="${if(id == active) "true" else "false"}">$name</a>
            |</li>""".stripMargin
    }.mkString("\n")

    val tabContent = tabs.filter(_._3 != "").map { case (id, _, content) =>
        s"""|<div class="tab-pane fade${if (id == active) " show active" else ""}" id="$id" role="tabpanel" aria-labelledby="$id-tab">
            |  ${indent(2, content)}
            |</div>""".stripMargin
    }.mkString("\n")

    s"""|${title}
        |<ul class="nav nav-tabs" role="tablist">
        |  ${indent(2, tabHeaders)}
        |</ul>
        |<div class="tab-content">
        |  ${indent(2, tabContent)}
        |</div>""".stripMargin
}

def withTemplate(title: String, config: String, content: String) =
    s"""|<!doctype html>
        |<html lang="en">
        |<head>
        |  <meta charset="utf-8">
        |  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
        |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/css/bootstrap.min.css" integrity="sha384-TX8t27EcRE3e/ihU7zmQxVncDAy5uIKz4rEkgIXeMed4M0jlfIDPvg6uqKI2xXr2" crossorigin="anonymous">
        |  <link rel="stylesheet" href="./style.css">
        |  <title>$title</title>
        |</head>
        |<body>
        |  <div class="container">
        |    <div class="row">
        |      <div class="col">
        |        <p><a href="../index.html">&larr; Back to index</a></p>
        |        <h1>$date</h1>
        |        <p><a href="./archive.tar.gz" class="btn btn-primary">Download Archive</a></p>
        |        <details>
        |          <summary>Contents of <code>config.yml</code></summary>
        |          <pre>
        |$config
        |          </pre>
        |        </details>
        |        <br />
        |        ${indent(8, content)}
        |      </div>
        |    </div>
        |  </div>
        |  <script src="https://code.jquery.com/jquery-3.5.1.slim.min.js" integrity="sha384-DfXdz2htPH0lsSSs5nCTpuj/zy4C+OGpamoFVy38MVBnE+IbbVYUew+OrCXaRkfj" crossorigin="anonymous"></script>
        |  <script src="https://cdn.jsdelivr.net/npm/bootstrap@4.6.0/dist/js/bootstrap.bundle.min.js" integrity="sha384-Piv4xVNRyMGpqkS2by6br4gNJ7DXjqk09RmUpJ8jgGtD7zP9yug3goQfGII0yAns" crossorigin="anonymous"></script>
        |  <script>$$(() => $$('[data-toggle="tooltip"]').tooltip());</script>
        |</body>
        |</html>
        |""".stripMargin

def removeCommentedLines(text: String) = text.replaceAll("[ \t\r]*\n[ \t]*#[^\n]+", "")

val date = if (dev) "dev" else ZonedDateTime.now(ZoneId.of("Europe/Amsterdam")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
val dateId = date.replace(" ", "_")

val dir = websiteDir / dateId

if (!dev) {
    val indexFile = websiteDir / "index.html"

    val index = Jsoup.parse(new File(indexFile.toString), "UTF-8")
    val ul = index.select("#runs").first

    val isTestRun = suite.warmupIterations <= 3 || suite.benchmarkIterations <= 3
    val badges = (
        suite.scopes.map(scope => s"""<span class="badge badge-primary badge-pill">$scope</span>""")
        ++ (if (isTestRun) Seq("""<span class="badge badge-warning badge-pill">test-run</span>""") else Seq.empty)
    ).mkString("\n")
    ul.prepend(
        s"""|<a href="./$dateId/index.html" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
            |  $date
            |  <span>
            |    ${indent(4, badges)}
            |  </span>
            |</a>""".stripMargin)

    write.over(indexFile, index.toString + "\n")
}

mkdir! dir

cp.over(pwd / "website-style.css", dir / "style.css")
try {
    cp.over(suite.dir / "archive.tar.gz", dir / "archive.tar.gz")
} catch {
    case e => if (!dev) throw e;  // Do not throw NoSuchFileException in dev mode
}
cp.over(figuresDir, dir / "figures")

suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty).map { language =>
    mkdir! dir / "figures" / "batch" / language.id

    if (exists! language.sourcesDir / "batch" / "sizes.png")
        cp.over(language.sourcesDir / "batch" / "sizes.png", dir / "figures" / "batch" / language.id / "sizes.png")

    if (suite.individualBatchSources) {
        language.sourcesBatchNonEmpty.map { source =>
            mkdir! dir / "figures" / "batch" / language.id / source.id

            if (exists! language.sourcesDir / "batch" / source.id / "sizes.png")
                cp.over(language.sourcesDir / "batch" / source.id / "sizes.png", dir / "figures" / "batch" / language.id / source.id / "sizes.png")
        }
    }
}

val config = removeCommentedLines(read! suite.configPath).trim

def batchSourceTabContent(language: Language, source: Option[BatchSource]) = {
    val measurements                     = language.measurementsBatch(source, "standard")
    val optimizedParseForestMeasurements = language.measurementsBatch(source, "optimized-pf")
    val elkhoundMeasurements             = language.measurementsBatch(source, "elkhound")

    s"""|<div class="row">""" + 
        (if (suite.implode.fold(true)(_ == false)) 
            s"""|  <div class="col-sm"><img src="./figures/batch/internal-parse/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>"""
        else "") +
        (if (suite.implode.fold(true)(_ == true)) 
            s"""|  <div class="col-sm"><img src="./figures/batch/internal/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
                |  <div class="col-sm"><img src="./figures/batch/external/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>"""
        else "") +
    s"""|  <div class="col-sm"><img src="./figures/batch/${language.id}${source.fold("")("/" + _.id)}/sizes.png" /></p></div>""" +
    s"""|</div>
        |<div class="row">
        |  <div class="col-sm">
        |    <h3>Full parse forest</h3>
        |    <p><strong>Parse nodes context-free</strong>: ${measurements("parseNodesContextFree")}</p>
        |    <p><strong>Parse nodes lexical</strong>: ${measurements("parseNodesLexical")}</p>
        |    <p><strong>Parse nodes layout</strong>: ${measurements("parseNodesLayout")}</p>
        |  </div>
        |  <div class="col-sm">
        |    <h3>Optimized parse forest</h3>
        |    <p><strong>Parse nodes context-free</strong>: ${optimizedParseForestMeasurements("parseNodesContextFree")}</p>
        |    <p><strong>Parse nodes lexical</strong>: ${optimizedParseForestMeasurements("parseNodesLexical")}</p>
        |    <p><strong>Parse nodes layout</strong>: ${optimizedParseForestMeasurements("parseNodesLayout")}</p>
        |  </div>
        |  <div class="col-sm">
        |    <p><strong>Reductions LR</strong>: ${elkhoundMeasurements("doReductionsLR")}</p>
        |    <p><strong>Reductions GLR (deterministic)</strong>: ${elkhoundMeasurements("doReductionsDeterministicGLR")}</p>
        |    <p><strong>Reductions GLR (non-deterministic)</strong>: ${elkhoundMeasurements("doReductionsNonDeterministicGLR")}</p>
        |  </div>
        |</div>""".stripMargin
}

def batchLanguageContent(language: Language) = {
    val parseTableMeasurements = language.measurementsParseTable
    val sourcesTabs = (s"batch-${language.id}-all", "All", batchSourceTabContent(language, None)) +: (
        if (suite.individualBatchSources)
            language.sourcesBatchNonEmpty.map(source => (s"batch-${language.id}-${source.id}", source.getName, batchSourceTabContent(language, Some(source))))
        else 
            Nil)

    s"""|<div class="row">
        |  <div class="col-sm">
        |    <p><strong>Parse Table States</strong>: ${parseTableMeasurements("states")}</p>
        |  </div>
        |</div>
        |${withNav("<h3>Sources</h3>", sourcesTabs)}""".stripMargin
}

def batchTabs = suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty).map { language =>
    (s"batch-${language.id}", language.name, batchLanguageContent(language))
}

def batchContent = if (inScope("batch")) {
    s"""|<div class="row">""" +
        (if (suite.implode.fold(true)(_ == false))
            s"""|  <div class="col-sm"><img src="./figures/batch/internal-parse/throughput.png" /></div>"""
        else "") +
        (if (suite.implode.fold(true)(_ == true))
            s"""|  <div class="col-sm"><img src="./figures/batch/internal/throughput.png" /></div>
                |  <div class="col-sm"><img src="./figures/batch/external/throughput.png" /></div>"""
        else "") + (if (!suite.batchSamples.isDefined)
            s"""|  <div class="col-sm"><img src="./figures/batch-sampled/throughput.png" /></div>"""
        else "") +
    s"""|</div>
        |${withNav("<h2>Per Language</h2>", batchTabs)}""".stripMargin
} else ""

val incrementalContent = if (inScope("incremental")) {
    import IncrementalMeasurementsTableUtils._

    def tdWrapper(mapper: (String) => String) = (key: String) => s"""<td style="text-align: right;">${mapper(key).replace("\n", "<br />")}</td>"""

    def createIncrementalMeasurementsTable(
        header: String, ids: Seq[String],
        rows: Seq[Map[String, Long]], percs: Seq[Map[String, Double]],
        avgsLabel: String, avgs: Map[String, Long], avgPercs: Map[String, Double]
    ) = {
        val n = rows.length

        val measurementsAvgRow =
            s"""|<tr>
                |  <td>$avgsLabel</td>
                |  ${indent(2, measurementsCells.map(tdWrapper(cellMapper(avgs, avgPercs, header != "Version"))).mkString("\n"))}
                |</tr>""".stripMargin

        val measurementsRows = ids.zip(rows zip percs).map { case (label, (row, perc)) =>
            s"""|<tr>
                |  <td>$label</td>
                |  ${indent(2, measurementsCells.map(tdWrapper(cellMapper(row, perc, header != "Version"))).mkString("\n"))}
                |</tr>""".stripMargin
        }

        s"""|<table style="text-align: center;" border="1" cellpadding="2">
            |  <tr>
            |    <th rowspan="2">$header</th>
            |    <th colspan="3">Parse Nodes</th>
            |    <th colspan="1" rowspan="2">Character<br />Nodes<br />Count</th>
            |  </tr>
            |  <tr>
            |    <th>Count</th>
            |    <th>${withTooltip("Ambi&shy;guous", "Parse nodes that have multiple derivations")}</th>
            |    <th>${withTooltip("Irre&shy;usable", "Parse nodes that are marked as irreusable, i.e., they were created when the parser was parsing non-deterministically (i.e., had multiple active parse stacks)")}</th>
            |  </tr>
            |  ${indent(2, measurementsAvgRow)}
            |  ${if (header == "Version") s"""<tr><td colspan="7"><br /></td></tr>""" else ""}
            |  ${indent(2, measurementsRows.mkString("\n"))}
            |</table>""".stripMargin
    }

    def createIncrementalMeasurementsTableSkew(
        header: String, ids: Seq[String],
        rows: Seq[Map[String, Long]], percs: Seq[Map[String, Double]],
        avgsLabel: String, avgs: Map[String, Long], avgPercs: Map[String, Double]
    ) = {
        val n = rows.length

        val measurementsAvgRow =
            s"""|<tr>
                |  <td>$avgsLabel</td>
                |  ${indent(2, measurementsCellsSkew.map(tdWrapper(cellMapper(avgs, avgPercs, header != "Version"))).mkString("\n"))}
                |</tr>""".stripMargin

        val measurementsRows = ids.zip(rows zip percs).map { case (label, (row, perc)) =>
            if (row.getOrElse("breakDowns", -1) == row.getOrElse("breakDownTemporary", -1)) {
                s"""|<tr>
                    |  <td>$label</td>
                    |  <td>${row("createParseNode")}</td>
                    |  <td colspan="2"></td>
                    |  <td>${row("shiftParseNode")}</td>
                    |  <td>${row("shiftCharacterNode")}</td>
                    |  <td colspan="5"></td>
                    |</tr>""".stripMargin
            } else {
                s"""|<tr>
                    |  <td>$label</td>
                    |  ${indent(2, measurementsCellsSkew.map(tdWrapper(cellMapper(row, perc, header != "Version"))).mkString("\n"))}
                    |</tr>""".stripMargin
            }
        }

        s"""|<table style="text-align: center;" border="1" cellpadding="2">
            |  <tr>
            |    <th rowspan="2">$header</th>
            |    <th colspan="3">Parse Nodes</th>
            |    <th colspan="2">Shift</th>
            |    <th colspan="5">Breakdown</th>
            |  </tr>
            |  <tr>
            |    <th>Created</th>
            |    <th>${withTooltip("Reused", "Parse nodes that were reused from the previous parse forest")}</th>
            |    <th>${withTooltip("Rebuilt", "Parse nodes that were broken down during parsing, but recreated with the exact same children as in the previous version")}</th>
            |    <th>Parse Node</th>
            |    <th>Character Node</th>
            |    <th>Count</th>
            |    <th>${withTooltip("Contains Change", "Parse nodes that were broken down because they contain a change from the diff")}</th>
            |    <th>${withTooltip("Irre&shy;usable", "Parse nodes that were broken down because they are irreusable, i.e., they were created when the parser was parsing non-deterministically (i.e., had multiple active parse stacks)")}</th>
            |    <th>${withTooltip("No Actions", "Parse nodes that were broken down because no actions were found in the parse table")}</th>
            |    <th>${withTooltip("Wrong State", "Parse nodes that were broken down because their saved parse state does not match the current state of the parser")}</th>
            |  </tr>
            |  ${indent(2, measurementsAvgRow)}
            |  ${indent(2, measurementsRows.mkString("\n"))}
            |</table>""".stripMargin
    }

    def incrementalMeasurementsTables(table: String, skewTable: String) =
        s"""|<div class="row">
            |  <div class="col-md-4">
            |    ${indent(4, table)}
            |  </div>
            |  <div class="col-md-8">
            |    ${indent(4, skewTable)}
            |  </div>
            |</div>""".stripMargin

    def createIncrementalBenchmarksTable(header: String, ids: Seq[String], rows: Seq[Map[String, Double]], avgs: Map[String, Double]) = {
        def cellMapper(row: Map[String, Double])(key: String) = {
            if (row(key).isNaN) "&mdash;" else f"${row(key)}%3.3f"
        }

        val benchmarkCells = Seq("Standard", "Elkhound", "Recovery", "IncrementalNoCache", "Incremental", "TreeSitterIncrementalNoCache", "TreeSitterIncremental")
                                .filter(key => !avgs.getOrElse(key, Double.NaN).isNaN)

        val headers = Map(
            "Standard" -> "Standard",
            "Elkhound" -> "Elkhound",
            "Recovery" -> "Recovery",
            "IncrementalNoCache" -> "Incremental",
            "Incremental" -> "Incremental",
            "TreeSitterIncrementalNoCache" -> "Tree-sitter",
            "TreeSitterIncremental" -> "Tree-sitter"
        )

        val noAvgsNaN = avgs.map { case k -> v => k -> (if (header != "Version" && rows.exists(_(k).isNaN)) Double.NaN else v) }

        val benchmarkAvgRow =
            s"""|<tr>
                |  <td>Average</td>
                |  ${indent(2, benchmarkCells.map(tdWrapper(cellMapper(noAvgsNaN))).mkString("\n"))}
                |</tr>""".stripMargin

        val benchmarkRows = (ids zip rows).map { case (label, row) =>
            s"""|<tr>
                |  <td>$label</td>
                |  ${indent(2, benchmarkCells.map(tdWrapper(cellMapper(row))).mkString("\n"))}
                |</tr>""".stripMargin
        }

        s"""|<table style="text-align: center;" border="1" cellpadding="2">
            |  <tr>
            |    <th>$header</th>
            |    ${indent(4, benchmarkCells
                    .map(key => headers(key) + (if (key.contains("NoCache")) "<br />(no cache)" else ""))
                    .map(header => s"<th>$header</th>")
                    .mkString("\n"))}
            |  </tr>
            |  ${indent(2, benchmarkAvgRow)}
            |  ${indent(2, benchmarkRows.mkString("\n"))}
            |</table>""".stripMargin
    }

    import java.text.DecimalFormat
    val percFormat = new DecimalFormat("+0.00%;-0.00%")

    val languagesWithIncrementalSources = suite.languages.filter(_.sources.incremental.nonEmpty)
    val languageNames = languagesWithIncrementalSources.map(_.name)

    val plotFilenames = Seq(
        "report", "report-except-first",
        "report-time-vs-bytes", "report-time-vs-changes", "report-time-vs-changes-3D"
    )

    val timeData = Seq("parse", "parse+implode").map { parseImplode =>
        val timeRows = languagesWithIncrementalSources.map(_.benchmarksIncremental(parseImplode, None)).map(_._2)
        val avgs = timeRows.avgMaps
        (parseImplode, timeRows, avgs)
    }
    val timeSummaryTables = timeData.map { case (parseImplode, timeRows, avgs) => (
        createIncrementalBenchmarksTable("Language", languageNames, timeRows, avgs),

        f"""|<table style="text-align: center;" border="1" cellpadding="2">
            |  <tr>
            |    <th></th>
            |    <th>Standard</th>
            |    <th>Elkhound</th>
            |    <th>Incremental<br />(no cache)</th>
            |  </tr>
            |  <tr>
            |    <td>Slowdown of Incremental (no cache) w.r.t.:</td>
            |    <td>${percFormat.format(avgs("IncrementalNoCache") / avgs("Standard") - 1)}</td>
            |    <td>${percFormat.format(avgs("IncrementalNoCache") / avgs("Elkhound") - 1)}</td>
            |    <td>&mdash;</td>
            |  </tr>
            |  <tr>
            |    <td>Speedup of Incremental w.r.t.:</td>
            |    <td>${avgs("Standard") / avgs("Incremental")}%2.2f&times;</td>
            |    <td>${avgs("Elkhound") / avgs("Incremental")}%2.2f&times;</td>
            |    <td>${avgs("IncrementalNoCache") / avgs("Incremental")}%2.2f&times;</td>
            |  </tr>
            |</table>""".stripMargin,

        (if (parseImplode == "parse+implode")
        f"""|Speedup of Tree-sitter (incremental w.r.t. no cache): ${timeRows(0)("TreeSitterIncrementalNoCache") / timeRows(0)("TreeSitterIncremental")}%2.2f&times;
            |Speedup of Tree-sitter w.r.t. Incremental (no cache): ${timeRows(0)("IncrementalNoCache") / timeRows(0)("TreeSitterIncrementalNoCache")}%2.2f&times;
            |Speedup of Tree-sitter w.r.t. Incremental (incremental): ${timeRows(0)("Incremental") / timeRows(0)("TreeSitterIncremental")}%2.2f&times;
            |""".stripMargin.replaceAll("\n", "<br />\n").trim
        else "")
    )}
    val jsglr2parserVariants = timeData(0)._3.keys
        .filter(key => !Set("i", "Added", "Removed", "Changes", "Size (bytes)").contains(key) && !key.contains("Error") && !timeData(0)._3(key).isNaN)
    val timeSummary =
        s"""|<h2>Excluding imploding and tokenization</h2>
            |<h3>Average parse times for the entire evaluation corpus, per language, in milliseconds</h3>
            |${timeSummaryTables(0)._1}
            |<h3>Slowdown/speedup comparison</h3>
            |${timeSummaryTables(0)._2}
            |<br />
            |<h2>Including imploding and tokenization</h2>
            |<h3>Average parse times for the entire evaluation corpus, per language, in milliseconds</h3>
            |${timeSummaryTables(1)._1}
            |<h3>Slowdown/speedup comparison</h3>
            |${timeSummaryTables(1)._2}
            |<h3>Comparison with Tree-sitter</h3>
            |${timeSummaryTables(1)._3}
            |<br />
            |<h2>Fraction of parse time as part of the full JSGLR2 parsing pipeline</h2>
            |<table style="text-align: center;" border="1" cellpadding="2">
            |  <tr>
            |    ${indent(4, jsglr2parserVariants.map(parser => s"<th>${if (parser == "IncrementalNoCache") "Incremental<br />(no cache)" else parser}</th>").mkString("\n"))}
            |  </tr>
            |  <tr>
            |    ${indent(4, jsglr2parserVariants.map(parser => f"<td>${timeData(0)._3(parser) / timeData(1)._3(parser) * 100}%2.2f%%</td>").mkString("\n"))}
            |  </tr>
            |</table>
            |<br />""".stripMargin

    val benchmarksTabs =
        ("incremental-benchmarks-summary", "Summary", timeSummary) +:
        languagesWithIncrementalSources.map { language =>
            (s"incremental-benchmarks-${language.id}", language.name, withNav("<h3>Sources</h3>",
                language.sources.incremental.map { source =>
                    (s"incremental-benchmarks-${language.id}-${source.id}", source.getName,
                        plotFilenames.map { plot =>
                            s"""<p><img src="./figures/incremental/${language.id}/${source.id}-parse+implode/$plot.svg" /></p>"""
                        }.mkString("\n")
                    )
                }
            ))
        }

    val (rows, percs, skewRows, skewPercs) = getAllIncrementalMeasurements(languagesWithIncrementalSources)

    val measurementsTables = incrementalMeasurementsTables(
        createIncrementalMeasurementsTable("Language", languageNames, rows, percs, "Average", rows.avgMaps, percs.avgMaps),
        createIncrementalMeasurementsTableSkew("Language", languageNames, skewRows, skewPercs, "Average", skewRows.avgMaps, skewPercs.avgMaps))

    val measurementsTabs = languagesWithIncrementalSources.map { language =>
        val sourcesTabs = language.sources.incremental.map { source =>
            val (rows, percs, skewRows, skewPercs, avgs, avgPercs, skewAvgs, skewAvgPercs) = language.measurementsIncremental(Some(source))
            val n = rows.length

            val ids = rows.map(_("version").toString)
            val skewIds = s"&nbsp;&nbsp; -> ${rows(0)("version")}" +:
                rows.drop(1).map(_("version")).map(i => s"${"&nbsp;" * (i.toString.length - (i - 1).toString.length) * 2}${i - 1} -> ${i}")

            val avgsLabel = s"Average (${ids(0)}..${ids.last.toInt - 1})"
            val skewAvgsLabel = s"Average (${ids(1)}..${ids.last})"

            val measurementsTables = incrementalMeasurementsTables(
                createIncrementalMeasurementsTable("Version", ids, rows, percs, avgsLabel, avgs, avgPercs),
                createIncrementalMeasurementsTableSkew("Version", skewIds, skewRows, skewPercs, skewAvgsLabel, skewAvgs, skewAvgPercs))

            (s"incremental-measurements-${language.id}-${source.id}", source.getName, measurementsTables)
        }

        val (rows, percs, skewRows, skewPercs, avgs, avgPercs, skewAvgs, skewAvgPercs) = language.measurementsIncremental(None)
        val n = rows.length

        val ids = language.sources.incremental.map(_.getName)

        val measurementsTables = incrementalMeasurementsTables(
            createIncrementalMeasurementsTable("Source", ids, rows, percs, "Average", avgs, avgPercs),
            createIncrementalMeasurementsTableSkew("Source", ids, skewRows, skewPercs, "Average", skewAvgs, skewAvgPercs))

        (s"incremental-measurements-${language.id}", language.name,
            s"""|${measurementsTables}
                |${withNav("<h3>Sources</h3>", sourcesTabs)}""".stripMargin)
    }

    val measurementsContent =
        s"""|${measurementsTables}
            |${withNav("<h2>Per Language</h2>", measurementsTabs)}""".stripMargin

    withNav("", Seq(
        (s"incremental-benchmarks", "Benchmarks", withNav("<h2>Per Language</h2>", benchmarksTabs)),
        (s"incremental-measurements", "Measurements", measurementsContent),
    ))
} else ""

val memoryTabs = suite.languages.filter(l => exists! dir / "figures" / "memoryBenchmarks" / l.id).map { language =>
    (s"memory-${language.id}", language.name,
        s"""|<div class="row">
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-allocations-batch.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-cache-size-batch.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-allocations-incremental.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-cache-size-incremental.svg" /></div>
            |</div>""".stripMargin)
}

val memoryContent = if (memoryTabs.nonEmpty) {
    import java.text.DecimalFormat
    val percFormat = new DecimalFormat("+0.00%;-0.00%")

    val (memoryRowsInclGarbage, memoryRowsCacheOnly) = suite.languages.map(_.benchmarksMemory).unzip

    val avgUsage = memoryRowsInclGarbage.avgMaps
    val bestVariant = suite.jsglr2variants.minBy(variant => avgUsage(s"jsglr2-$variant"))
    val compareVariants = Seq(
        if (suite.variants.contains("standard")) Some(("standard", "Compared to the Standard variant")) else None,
        if (bestVariant != "standard") Some((bestVariant, s"Compared to the best variant (${bestVariant.capitalize})")) else None,
    ).flatten

    def createRow(usage: Map[String, Double]) = suite.jsglr2variants.map(variant => {
        val percs = compareVariants.map { case (compare, tooltip) =>
            if (compare == variant) "&emsp;&mdash;&emsp;"
            else withTooltip(percFormat.format(usage(s"jsglr2-$variant") / usage(s"jsglr2-$compare") - 1), tooltip)
        }.mkString(" / ")
        f"<td>${usage(s"jsglr2-$variant")}%1.0f ($percs)</td>"
    }).mkString("\n")

    val languageRowsIncl = (suite.languages zip memoryRowsInclGarbage).map { case (language, usage) =>
        s"""|<tr>
            |  <td>${language.name}</td>
            |  ${indent(2, createRow(usage))}
            |</tr>""".stripMargin
    }.mkString("\n")

    val cacheTable = if (suite.variants.contains("incremental")) {
        val languageRowsCache = (suite.languages zip memoryRowsCacheOnly).map { case (language, usage) =>
            f"""|<tr>
                |  <td>${language.name}</td>
                |  <td>${usage("jsglr2-incremental")}%1.0f</td>
                |</tr>""".stripMargin
        }.mkString("\n")

        f"""|
            |  <br />
            |  <b>Bytes of memory used for the cache, per character in the input</b><br />
            |  <table class="mx-auto" border="1" cellpadding="2">
            |    <tr>
            |      <th>Language</th>
            |      <th>Incremental</th>
            |    </tr>
            |    <tr>
            |      <td>Average</td>
            |      <td>${memoryRowsCacheOnly.avgMaps()("jsglr2-incremental")}%1.0f</td>
            |    </tr>
            |    ${indent(4, languageRowsCache)}
            |  </table>""".stripMargin
    } else ""

    s"""|<div class="text-center">
        |  <b>Bytes of memory used during parsing in batch mode, per character in the input</b><br />
        |  <table class="mx-auto" border="1" cellpadding="2">
        |    <tr>
        |      <th>Language</th>
        |      ${indent(6, suite.jsglr2variants.map(variant => s"<th>${variant.capitalize}</th>").mkString("\n"))}
        |    </tr>
        |    <tr>
        |      <td>Average</td>
        |      ${indent(6, createRow(avgUsage))}
        |    </tr>
        |    ${indent(4, languageRowsIncl)}
        |  </table>${cacheTable}
        |</div>
        |<br />
        |${withNav("<h2>Per Language</h2>", memoryTabs)}""".stripMargin
} else ""

val tabs = Seq(
    ("batch", "Batch", batchContent),
    ("recovery", "Recovery", ""),
    ("incremental", "Incremental", incrementalContent),
    ("memory", "Memory Benchmarks", memoryContent),
)

write.over(
    dir / "index.html",
    withTemplate(date, config,
        s"""|<p><strong>Iterations:</strong> ${suite.warmupIterations}/${suite.benchmarkIterations}</p>
            |<p>
            |  <strong>Spoofax version</strong>: ${sys.env.get("SPOOFAX_VERSION").getOrElse("master")}<br />
            |  <strong>JSGLR version</strong>: ${sys.env.get("JSGLR_VERSION").getOrElse("develop/jsglr2")}<br />
            |  <strong>SDF version</strong>: ${sys.env.get("SDF_VERSION").getOrElse("develop/jsglr2")}
            |</p>
            |${withNav("", tabs)}""".stripMargin
    )
)
