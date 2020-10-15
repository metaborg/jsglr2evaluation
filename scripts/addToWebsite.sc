import $ivy.`com.lihaoyi::ammonite-ops:1.8.1`, ammonite.ops._
import $ivy.`org.jsoup:jsoup:1.7.2`, org.jsoup._
import $file.common, common._, Args._
import collection.JavaConverters._
import java.io.File

def addToWebsite(implicit args: Args) = {
    val id = java.time.LocalDateTime.now.toString
    
    val dir = websiteDir / id
    val indexFile = websiteDir / "index.html"
    
    val index = Jsoup.parse(new File("" + indexFile), "UTF-8")
    val ul = index.select("#runs").first

    ul.prepend("<li><a href=\"./" + id + "/index.html\">" + id + "</a></li>")

    write.over(indexFile, index.toString)

    mkdir! dir

    cp(pwd / "website-style.css", dir / "style.css")
    cp(args.dir / "archive.tar.gz", dir / "archive.tar.gz")
    cp.into(reportsDir, dir)

    val config = removeCommentedLines(read! args.configPath)

    val batchContent =
    s"""
       |<p><img src="./reports/benchmarks-batch-throughput.png" /></p>
       |<p><img src="./reports/benchmarks-perFile-throughput.png" /></p>
       |""".stripMargin

    val tabs = Seq(
        ("batch", "Batch", batchContent),
        ("recovery", "Recovery", ""),
        ("incremental", "Incremental", "")
    )

    write(
        dir / "index.html",
        withTemplate(id, s"""
           |<div class="row">
           |  <div class="col">
           |    <p><a href="../index.html">&larr; Back to index</a></p>
           |    <h1>$id</h1>
           |    <p><a href="./archive.tar.gz" class="btn btn-primary">Download Archive</a></p>
           |    <pre>$config</pre>
           |    ${withNav(tabs, "batch")}
           |  </div>
           |</div>""".stripMargin
        )
    )
}

def withNav(tabs: Seq[(String, String, String)], active: String) =
    s"""
    |<ul class="nav nav-tabs" role="tablist">
    |${tabs.map { case (id, name, _) =>
s"""|  <li class="nav-item" role="presentation">
    |    <a class="nav-link${if(id == active) " active" else ""}" id="$id-tab" data-toggle="tab" href="#$id" role="tab" aria-controls="$id" aria-selected="${if(id == active) "true" else "false"}">$name</a>
    |  </li>"""
     }.mkString("\n")}
    |</ul>
    |<div class="tab-content">
    |  ${tabs.map { case (id, _, content) =>
s"""|  <div class="tab-pane fade${if(id == active) " show active" else ""}" id="$id" role="tabpanel" aria-labelledby="$id-tab">
    |    $content
    |  </div>""".stripMargin
       }.mkString("\n")}
    |</div>
    |""".stripMargin

def withTemplate(title: String, content: String) =
    s"""<!doctype html>
    |<html lang="en">
    |<head>
    |  <meta charset="utf-8">
    |  <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/css/bootstrap.min.css" integrity="sha384-TX8t27EcRE3e/ihU7zmQxVncDAy5uIKz4rEkgIXeMed4M0jlfIDPvg6uqKI2xXr2" crossorigin="anonymous">
    |  <link rel="stylesheet" href="./style.css">
    |  <title>$title</title>
    |</head>
    |<body>
    |<div class="container">
    |$content
    |</div>
    |<script src="https://code.jquery.com/jquery-3.5.1.slim.min.js" integrity="sha384-DfXdz2htPH0lsSSs5nCTpuj/zy4C+OGpamoFVy38MVBnE+IbbVYUew+OrCXaRkfj" crossorigin="anonymous"></script>
    |<script src="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-ho+j7jyWK8fNQe+A12Hb8AhRq26LrZ/JpcUGGOn+Y7RsweNrtN/tE3MoK7ZeZDyx" crossorigin="anonymous"></script>
    |</body>
    |</html>""".stripMargin

def removeCommentedLines(text: String) = text.replaceAll("[ \t\r]*\n[ \t]*#[^\n]+", "")

@main
def ini(args: String*) = withArgs(args :_ *)(addToWebsite(_))