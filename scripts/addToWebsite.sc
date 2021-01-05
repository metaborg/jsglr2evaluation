import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $ivy.`org.jsoup:jsoup:1.7.2`, org.jsoup._
import $file.common, common._, Suite._
import java.io.File
import java.time._, java.time.format._

println("Adding to website...")

def indent(spaces: Int, str: String) = str.replaceAll("\n", s"\n${" " * spaces}")

def withNav(tabs: Seq[(String, String, String)]) = {
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
    s"""|<ul class="nav nav-tabs" role="tablist">
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
        |        <pre>$config</pre>
        |        <br />
        |        ${indent(8, content)}
        |      </div>
        |    </div>
        |  </div>
        |  <script src="https://code.jquery.com/jquery-3.5.1.slim.min.js" integrity="sha384-DfXdz2htPH0lsSSs5nCTpuj/zy4C+OGpamoFVy38MVBnE+IbbVYUew+OrCXaRkfj" crossorigin="anonymous"></script>
        |  <script src="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-ho+j7jyWK8fNQe+A12Hb8AhRq26LrZ/JpcUGGOn+Y7RsweNrtN/tE3MoK7ZeZDyx" crossorigin="anonymous"></script>
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
            |</a>
            |""".stripMargin)

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
        |   <div class="col-sm"><img src="./figures/batch/internal-parse/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |   <div class="col-sm"><img src="./figures/batch/internal/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |   <div class="col-sm"><img src="./figures/batch/external/${language.id}${source.fold("")("/" + _.id)}/throughput.png" /></p></div>
        |   <div class="col-sm"><img src="./figures/batch/${language.id}${source.fold("")("/" + _.id)}/sizes.png" /></p></div>
        |</div>
        |<div class="row">
        |   <div class="col-sm">
        |       <h3>Full parse forest</h3>
        |       <p><strong>Parse nodes context-free</strong>: ${measurements("parseNodesContextFree")}</p>
        |       <p><strong>Parse nodes lexical</strong>: ${measurements("parseNodesLexical")}</p>
        |       <p><strong>Parse nodes layout</strong>: ${measurements("parseNodesLayout")}</p>
        |   </div>
        |   <div class="col-sm">
        |       <h3>Optimized parse forest</h3>
        |       <p><strong>Parse nodes context-free</strong>: ${optimizedParseForestMeasurements("parseNodesContextFree")}</p>
        |       <p><strong>Parse nodes lexical</strong>: ${optimizedParseForestMeasurements("parseNodesLexical")}</p>
        |       <p><strong>Parse nodes layout</strong>: ${optimizedParseForestMeasurements("parseNodesLayout")}</p>
        |   </div>
        |   <div class="col-sm">
        |       <p><strong>Reductions LR</strong>: ${elkhoundMeasurements("doReductionsLR")}</p>
        |       <p><strong>Reductions GLR (deterministic)</strong>: ${elkhoundMeasurements("doReductionsDeterministicGLR")}</p>
        |       <p><strong>Reductions GLR (non-deterministic)</strong>: ${elkhoundMeasurements("doReductionsNonDeterministicGLR")}</p>
        |   </div>
        |</div>""".stripMargin
}

def batchLanguageContent(language: Language) = {
    val parseTableMeasurements = language.measurementsParseTable

    s"""|<div class="row">
        |   <div class="col-sm">
        |       <p><strong>States</strong>: ${parseTableMeasurements("states")}</p>
        |   </div>
        |</div>
        |<h3>Sources</h3>""".stripMargin + withNav(
        (s"batch-${language.id}-all", "All", batchSourceTabContent(language, None)) +:
        language.sourcesBatchNonEmpty.map { source =>
            // TODO add field source.name?
            ("batch-${language.id}-${source.id}", source.id, batchSourceTabContent(language, Some(source)))
        }
    )
}

def batchTabs = suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty).map { language =>
    (s"batch-${language.id}", language.name, batchLanguageContent(language))
}

def batchContent =
    s"""|<div class="row">
        |   <div class="col-sm"><img src="./figures/batch/internal-parse/throughput.png" /></div>
        |   <div class="col-sm"><img src="./figures/batch/internal/throughput.png" /></div>
        |   <div class="col-sm"><img src="./figures/batch/external/throughput.png" /></div>
        |   <div class="col-sm"><img src="./figures/batch-sampled/throughput.png" /></div>
        |</div>
        |<h2>Per Language</h2>
        |${withNav(batchTabs)}""".stripMargin

val incrementalTabs = suite.languages.filter(_.sources.incremental.nonEmpty).map { language =>
    val sourcesTabs = withNav(language.sources.incremental.map { source => {
        val plots = Seq("report", "report-except-first", "report-time-vs-bytes", "report-time-vs-changes", "report-time-vs-changes-3D")
        // TODO add field source.name?
        (s"incremental-${language.id}-${source.id}", source.id, plots.map { plot =>
            s"""<p><img src="./figures/incremental/${language.id}/${source.id}-parse+implode/$plot.svg" /></p>"""
        }.mkString("\n"))
    }})
    (s"incremental-${language.id}", language.name,
        s"""|<div class="row">
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-full-garbage.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-cache-size.svg" /></div>
            |  <div class="col-lg-6"><img src="./figures/memoryBenchmarks/${language.id}/report-incremental.svg" /></div>
            |</div>
            |${sourcesTabs}""")
}

val tabs = Seq(
    ("batch", "Batch",
        if (exists! dir / "figures" / "batch" / "external" / "throughput.png" && batchTabs.nonEmpty) batchContent else ""),
    ("recovery", "Recovery", ""),
    ("incremental", "Incremental", if (incrementalTabs.nonEmpty) withNav(incrementalTabs) else "")
)

write.over(
    dir / "index.html",
    withTemplate(id, config,
        s"""|<p><strong>Iterations:</strong> ${suite.warmupIterations}/${suite.benchmarkIterations}</p>
            |${withNav(tabs)}""".stripMargin
    )
)
