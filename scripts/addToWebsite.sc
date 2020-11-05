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
cp.over(suite.dir / "archive.tar.gz", dir / "archive.tar.gz")
cp.over(reportsDir, dir / "reports")

suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty).map { language =>
    mkdir! dir / "reports" / "batch" / language.id

    if (exists! language.sourcesDir / "batch" / "sizes.png")
        cp.over(language.sourcesDir / "batch" / "sizes.png", dir / "reports" / "batch" / language.id / "sizes.png")

    language.sourcesBatchNonEmpty.map { source =>
        mkdir! dir / "reports" / "batch" / language.id / source.id

        if (exists! language.sourcesDir / "batch" / source.id / "sizes.png")
            cp.over(language.sourcesDir / "batch" / source.id / "sizes.png", dir / "reports" / "batch" / language.id / source.id / "sizes.png")
    }
}

val config = removeCommentedLines(read! suite.configPath).trim

def batchSourceTabContent(languageId: String, source: Option[BatchSource]) =
    s"""|<div class="row">
        |   <div class="col-sm">
        |       <img src="./reports/batch/${languageId}${source.fold("")("/" + _.id)}/throughput.png" /></p>
        |   </div>
        |   <div class="col-sm">
        |       <img src="./reports/batch/${languageId}${source.fold("")("/" + _.id)}/sizes.png" /></p>
        |   </div>
        |</div>""".stripMargin

def batchTabs = suite.languages.filter(_.sourcesBatchNonEmpty.nonEmpty).map { language =>
    (language.id, language.name, "<h3>Sources</h3>\n" + withNav(
        (s"${language.id}-all", "All", batchSourceTabContent(language.id, None)) +:
        language.sourcesBatchNonEmpty.map { source =>
            // TODO add field source.name?
            (source.id, source.id, batchSourceTabContent(language.id, Some(source)))
        }
    ))
}

val incrementalTabs = suite.languages.filter(_.sources.incremental.nonEmpty).map { language =>
    (language.id, language.name, withNav(language.sources.incremental.map { source => {
        val plots = Seq("report", "report-except-first", "report-time-vs-bytes", "report-time-vs-changes", "report-time-vs-changes-3D")
        // TODO add field source.name?
        (source.id, source.id, plots.map { plot =>
            s"""<p><img src="./reports/incremental/${language.id}/${source.id}-parse/$plot.svg" /></p>"""
        }.mkString("\n"))
    }}))
}

val tabs = Seq(
    ("batch", "Batch",
        if (exists! dir / "reports" / "batch" / "throughput.png" && batchTabs.nonEmpty)
            s"""|<p><img src="./reports/batch/throughput.png" /></p>
                |<h2>Per Language</h2>
                |${withNav(batchTabs)}""".stripMargin
        else ""),
    ("recovery", "Recovery", ""),
    ("incremental", "Incremental", if (incrementalTabs.nonEmpty) withNav(incrementalTabs) else "")
)

write.over(
    dir / "index.html",
    withTemplate(id, config,
        s"""|<p><strong>Iterations:</strong> ${suite.iterations}</p>
            |${withNav(tabs)}""".stripMargin
    )
)
