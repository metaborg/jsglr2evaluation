import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $ivy.`org.jsoup:jsoup:1.7.2`, org.jsoup._
import $file.common, common._, Suite._
import java.io.File
import java.time._, java.time.format._

println("Adding to website...")

def indent(spaces: Int, str: String) = str.replaceAll("\n", s"\n${" " * spaces}")

def withTooltip(text: String, tooltip: String) =
    s"""<a onclick"javascript:void" data-toggle="tooltip" data-placement="top" title="$tooltip">$text</a>"""

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
        |        <h1>$id</h1>
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

val id = if (dev) "dev" else ZonedDateTime.now(ZoneId.of("Europe/Amsterdam")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

val dir = websiteDir / id

if (!dev) {
    val indexFile = websiteDir / "index.html"

    val index = Jsoup.parse(new File(indexFile.toString), "UTF-8")
    val ul = index.select("#runs").first

    val badges = suite.scopes.map(scope => s"""<span class="badge badge-primary badge-pill">$scope</span>""").mkString("\n")
    ul.prepend(
        s"""|<a href="./$id/index.html" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
            |  $id
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

    language.sourcesBatchNonEmpty.map { source =>
        mkdir! dir / "figures" / "batch" / language.id / source.id

        if (exists! language.sourcesDir / "batch" / source.id / "sizes.png")
            cp.over(language.sourcesDir / "batch" / source.id / "sizes.png", dir / "figures" / "batch" / language.id / source.id / "sizes.png")
    }
}

val config = removeCommentedLines(read! suite.configPath).trim

def batchSourceTabContent(language: Language, source: Option[BatchSource]) = {
    val measurements                     = language.measurementsBatch(source, "standard")
    val optimizedParseForestMeasurements = language.measurementsBatch(source, "optimized-pf")
    val elkhoundMeasurements             = language.measurementsBatch(source, "elkhound")

    s"""|<div class="row">
        |  <div class="col-sm"><img src="./figures/batch/internal-parse/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |  <div class="col-sm"><img src="./figures/batch/internal/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |  <div class="col-sm"><img src="./figures/batch/external/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |  <div class="col-sm"><img src="./figures/batch/${language.id}${source.fold("")("/" + _.id)}/sizes.png" /></p></div>
        |</div>
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
    val sourcesTabs = (s"batch-${language.id}-all", "All", batchSourceTabContent(language, None)) +:
        language.sourcesBatchNonEmpty.map { source =>
            (s"batch-${language.id}-${source.id}", source.getName, batchSourceTabContent(language, Some(source)))
        }

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

def batchContent =
    s"""|<div class="row">
        |  <div class="col-sm"><img src="./figures/batch/internal-parse/throughput.png" /></div>
        |  <div class="col-sm"><img src="./figures/batch/internal/throughput.png" /></div>
        |  <div class="col-sm"><img src="./figures/batch/external/throughput.png" /></div>
        |  <div class="col-sm"><img src="./figures/batch-sampled/throughput.png" /></div>
        |</div>
        |${withNav("<h2>Per Language</h2>", batchTabs)}""".stripMargin

def createMeasurementsTable(ids: Seq[String], rows: Seq[Map[String, Long]], header: String) = {
    import IncrementalMeasurementsTableUtils._

    val n = rows.length

    val measurementsAvgRow =
        if (header == "Version")
            s"""|<tr>
                |  <td>Average (0..${n - 2})</td>
                |  ${indent(2, measurementsCells.map(tdWrapper(cellMapper(rows.dropRight(1).avgMaps))).mkString("\n"))}
                |</tr>""".stripMargin
        else
            s"""|<tr>
                |  <td>Average</td>
                |  ${indent(2, measurementsCells.map(tdWrapper(cellMapperPercs(rows.avgMaps, rows.avgPercs(relativeTo)))).mkString("\n"))}
                |</tr>""".stripMargin

    val measurementsRows = ids.zip(rows).map { case (label, row) =>
        s"""|<tr>
            |  <td>$label</td>
            |  ${indent(2, measurementsCells.map(tdWrapper(cellMapper(row, header != "Version"))).mkString("\n"))}
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
        |    <th>${withTooltip("Ambiguous", "Parse nodes that have multiple derivations")}</th>
        |    <th>${withTooltip("Irreusable", "Parse nodes that are marked as irreusable, i.e., they were created when the parser was parsing non-deterministically (i.e., had multiple active parse stacks)")}</th>
        |  </tr>
        |  ${indent(2, measurementsAvgRow)}
        |  ${if (header == "Version") s"""<tr><td colspan="7"><br /></td></tr>""" else ""}
        |  ${indent(2, measurementsRows.mkString("\n"))}
        |</table>""".stripMargin
}

def createMeasurementsTableSkew(skewIds: Seq[String], skewRows: Seq[Map[String, Long]], header: String) = {
    import IncrementalMeasurementsTableUtils._

    val n = skewRows.length

    val measurementsAvgRow =
        if (header == "Version")
            s"""|<tr>
                |  <td>Average (1..${n - 1})</td>
                |  ${indent(2, measurementsCellsSkew.map(tdWrapper(cellMapper(skewRows.drop(1).avgMaps))).mkString("\n"))}
                |</tr>""".stripMargin
        else
            s"""|<tr>
                |  <td>Average</td>
                |  ${indent(2, measurementsCellsSkew.map(tdWrapper(cellMapperPercs(skewRows.avgMaps, skewRows.avgPercs(relativeTo)))).mkString("\n"))}
                |</tr>""".stripMargin

    val measurementsRows = skewIds.zip(skewRows).map { case (label, row) =>
        if (row.getOrElse("version", -1) == 0) {
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
                |  ${indent(2, measurementsCellsSkew.map(tdWrapper(cellMapper(row, header != "Version"))).mkString("\n"))}
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
        |    <th>${withTooltip("Irreusable", "Parse nodes that were broken down because they are irreusable, i.e., they were created when the parser was parsing non-deterministically (i.e., had multiple active parse stacks)")}</th>
        |    <th>${withTooltip("No Actions", "Parse nodes that were broken down because no actions were found in the parse table")}</th>
        |    <th>${withTooltip("Temporary", "Parse nodes that were broken down because they were created as temporary nodes while applying the text diff to the previous parse forest")}</th>
        |    <th>${withTooltip("Wrong State", "Parse nodes that were broken down because their saved parse state does not match the current state of the parser")}</th>
        |  </tr>
        |  ${indent(2, measurementsAvgRow)}
        |  ${indent(2, measurementsRows.mkString("\n"))}
        |</table>""".stripMargin
}

def incrementalMeasurementsTables(rowsTuple: (Seq[Map[String, Long]], Seq[Map[String, Long]]), header: String, optionalIds: Seq[String] = Seq.empty) = rowsTuple match {
    case (csvRows, skewRows) =>
        val n = csvRows.length
        val ids = if (optionalIds.length == n) optionalIds else (0 to n).map(_.toString)
        val skewIds = if (header == "Version") (0 to n).map(i => s"${if (i == 0) "&nbsp;&nbsp;" else i - 1} -> ${i}") else ids
        val skewRows2 = if (header == "Version") csvRows(0) +: skewRows else skewRows
        s"""|<div class="row">
            |  <div class="col-md-4">
            |    ${indent(4, createMeasurementsTable(ids, csvRows, header))}
            |  </div>
            |  <div class="col-md-8">
            |    ${indent(4, createMeasurementsTableSkew(skewIds, skewRows2, header))}
            |  </div>
            |</div>""".stripMargin
}

val languagesWithIncrementalSources = suite.languages.filter(_.sources.incremental.nonEmpty)

val incrementalTabs = languagesWithIncrementalSources.map { language =>
    val sourcesTabs = language.sources.incremental.map { source =>
        val measurementsTables = incrementalMeasurementsTables(language.measurementsIncremental(Some(source)), "Version")

        val plotFilenames = Seq(
            "report", "report-except-first",
            "report-time-vs-bytes", "report-time-vs-changes", "report-time-vs-changes-3D"
        )
        val plots = plotFilenames.map { plot =>
            s"""<p><img src="./figures/incremental/${language.id}/${source.id}-parse+implode/$plot.svg" /></p>"""
        }.mkString("\n")

        (s"incremental-${language.id}-${source.id}", source.getName, s"$measurementsTables\n$plots")
    }

    (s"incremental-${language.id}", language.name,
        s"""|${incrementalMeasurementsTables(language.measurementsIncremental(None), "Source", language.sources.incremental.map(_.getName))}
            |${withNav("<h3>Sources</h3>", sourcesTabs)}""".stripMargin)
}

val incrementalContent =
    s"""|${incrementalMeasurementsTables(
            languagesWithIncrementalSources.map(_.measurementsIncremental(None)).map { case (a, b) => (a.avgMaps, b.avgMaps) }.unzip,
            "Language",
            languagesWithIncrementalSources.map(_.name)
         )}
        |${withNav("<h2>Per Language</h2>", incrementalTabs)}""".stripMargin

val memoryTabs = suite.languages.filter(l => exists! dir / "figures" / "memoryBenchmarks" / l.id).map { language =>
    (s"memory-${language.id}", language.name,
        s"""|<div class="row">
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-allocations-batch.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-cache-size-batch.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-allocations-incremental.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-cache-size-incremental.svg" /></div>
            |</div>""".stripMargin)
}

val tabs = Seq(
    ("batch", "Batch", if (inScope("batch")) batchContent else ""),
    ("recovery", "Recovery", ""),
    ("incremental", "Incremental", if (inScope("incremental")) incrementalContent else ""),
    ("memory", "Memory Benchmarks", if (memoryTabs.nonEmpty) withNav("<h2>Per Language</h2>", memoryTabs) else ""),
)

write.over(
    dir / "index.html",
    withTemplate(id, config,
        s"""|<p><strong>Iterations:</strong> ${suite.warmupIterations}/${suite.benchmarkIterations}</p>
            |${withNav("", tabs)}""".stripMargin
    )
)
