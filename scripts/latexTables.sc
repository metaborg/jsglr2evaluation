import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("LateX reporting...")

trait BenchmarkType {
    def errorColumns: Seq[(String, String)]
}
case object Time extends BenchmarkType {
    def errorColumns = Seq("error" -> "Error")
}
case object Throughput extends BenchmarkType {
    def errorColumns = Seq("low" -> "Low", "high" -> "High")
}

def mean[B](xs: TraversableOnce[B])(implicit num: Integral[B]): Long = num.toLong(xs.sum) / xs.size

def thousandSeparator[T](num: Any) = {
    val str = num.toString
    val front = str.length() % 3
    val (head, toGroup) = str.splitAt(front)
    val tail = toGroup.grouped(3)
    (if (front == 0) tail else Iterator(head) ++ tail).mkString("\\,")
}

def latexTableTestSets(implicit suite: Suite) = {
    val languages = suite.languages.map { language =>
        val sources = language.sourcesBatchNonEmpty.map { source =>
            val files = language.sourceFilesBatch(Some(source))
            val lines = files | read.lines | (_.size) sum
            val size = files | stat | (_.size) sum

            s"  & ${source.getName} & ${files.size} & $lines & $size \\\\"
        }

        s"""|\\multirow{${language.sourcesBatchNonEmpty.size}}{*}{${language.name}}
            |${sources.mkString(" \\cline{2-5}\n")}""".stripMargin
    }

    s"""|\\begin{tabular}{|l|l|r|r|r|}
        |\\hline
        |Language & Source & Files & Lines & Size (bytes) \\\\ \\hline
        |${languages.mkString(" \\hline\n")} \\hline
        |\\end{tabular}
        |""".stripMargin
}

def latexTableTestSetsIncremental(implicit suite: Suite) = {
    val languages = suite.languages.map { language =>
        val sources = language.sources.incremental.map { source =>
            val sourceDir = language.sourcesDir / "incremental" / source.id
            val versions = (ls! sourceDir)
            val (files, lines, size) = versions.map(path => (path relativeTo sourceDir).toString.toInt).sorted.map { version =>
                val versionDir = sourceDir / version.toString
                val files = (ls! versionDir)
                (files.size.toLong, (files | read.lines | (_.size) sum).toLong, files | stat | (_.size) sum)
            }.unzip3
            val values = Iterator(files, lines, size, (size zip files) map { case (s, f) => s / f })
                .map(v => thousandSeparator(mean(v).toString))

            s"  & ${source.getName} & ${versions.size} & ${values.mkString(" & ")} \\\\"
        }

        s"""|\\multirow{${sources.size}}{*}{${language.name}}
            |${sources.mkString(" \\cline{2-7}\n")}""".stripMargin
    }

    s"""|\\begin{tabular}{|l|l|r|r|r|r|r|}
        |\\hline
        |Language & Source & Versions & Files & Lines & Size (B) & Mean file size (B) \\\\ \\hline
        |${languages.mkString(" \\hline\n")} \\hline
        |\\end{tabular}
        |""".stripMargin
}

def latexTableParseForest(implicit suite: Suite) = {
    val s = new StringBuilder()

    s.append("\\begin{tabular}{|l|l|r|r|}\n")
    s.append("\\hline\n")
    s.append("Language & Type & Full parse forest & Optimized parse forest \\\\\n")
    s.append("\\hline\n")

    suite.languages.foreach { language =>
        s.append("\\multirow{3}{*}{" + language.name + "}\n")

        val measurementsFull      = language.measurementsBatch(None, "standard")
        val measurementsOptimized = language.measurementsBatch(None, "optimized-pf")
        
        s.append("  & Context-free & " + measurementsFull("parseNodesContextFree") + " & " + measurementsOptimized("parseNodesContextFree") + " \\\\ \\cline{2-4}\n")
        s.append("  & Lexical      & " + measurementsFull("parseNodesLexical") +     " & " + measurementsOptimized("parseNodesLexical") +     " \\\\ \\cline{2-4}\n")
        s.append("  & Layout       & " + measurementsFull("parseNodesLayout") +      " & " + measurementsOptimized("parseNodesLayout") +      " \\\\ \\hline\n")
    }

    s.append("\\end{tabular}\n")

    s.toString
}

def latexTableDeterminism(implicit suite: Suite) = {
    val s = new StringBuilder()

    s.append("\\begin{tabular}{|l|r|r|r|}\n")
    s.append("\\hline\n")
    s.append("Language & LR & GLR deterministic & GLR non-deterministic \\\\\n")
    s.append("\\hline\n")

    suite.languages.foreach { language =>
        val measurements = language.measurementsBatch(None, "elkhound")
        val lr = measurements("doReductionsLR").toInt
        val glrDeterministic = measurements("doReductionsDeterministicGLR").toInt
        val glrNonDeterministic = measurements("doReductionsNonDeterministicGLR").toInt
        val total = lr + glrDeterministic + glrNonDeterministic

        def percentage(i: Int) =
            Math.round(i.toFloat / total.toFloat * 100) + "\\%"

        s.append(language.name + " & " + lr + " (" + percentage(lr) + ") & " + glrDeterministic + " (" + percentage(glrDeterministic) + ") & " + glrNonDeterministic + " (" + percentage(glrNonDeterministic) + ") \\\\ \\hline\n")
    }

    s.append("\\end{tabular}\n")

    s.toString
}

def latexTableMeasurementsBatch(csv: CSV)(implicit suite: Suite) = {
    val measurements = csv.columns.filter(_ != "language").map { column =>
        val languages = suite.languages.map { language =>
            csv.rows.find(_("language") == language.id).get(column)
        }

        s"${column} & ${languages.mkString(" & ")}"
    }

    s"""|\\begin{tabular}{|l|${"r|" * suite.languages.size}}
        |\\hline
        |Measure & ${suite.languages.map(_.name).mkString(" & ")} \\\\ \\hline
        |${measurements.mkString(" \\\\ \\hline\n")} \\\\ \\hline
        |\\end{tabular}
        |""".stripMargin
}

def latexTableBenchmarks(benchmarksCSV: CSV, benchmarkType: BenchmarkType)(implicit suite: Suite) = {
    val s = new StringBuilder()

    s.append("\\begin{tabular}{|l|" + ("r|" * (suite.languages.size * (1 + benchmarkType.errorColumns.size))) + "}\n")
    s.append("\\hline\n")
    s.append("\\multirow{2}{*}{Variant}" + suite.languages.map(language => s" & \\multicolumn{${1 + benchmarkType.errorColumns.size}}{c|}{${language.name}}").mkString("") + " \\\\\n")
    s.append(s"\\cline{2-${1 + suite.languages.size * (1 + benchmarkType.errorColumns.size)}}\n")
    s.append(suite.languages.map(_ => " & Score" + benchmarkType.errorColumns.map(" & " + _._2).mkString).mkString + " \\\\\n")
    s.append("\\hline\n")

    val variants = benchmarksCSV.rows.map(_("variant")).distinct

    variants.foreach { variant =>
        s.append(variant)

        suite.languages.foreach { language =>
            def get(column: String) = benchmarksCSV.rows.find { row =>
                row("language") == language.id &&
                row("variant") == variant
            } match {
                case Some(row) => round(row(column))
                case None      => ""
            }

            s.append(" & " + get("score"))

            benchmarkType.errorColumns.foreach { case (errorColumn, _) =>
                s.append(" & " + get(errorColumn));
            }
        }

        s.append(" \\\\ \\hline\n");
    }

    s.append("\\end{tabular}\n")

    s.toString
}

mkdir! suite.figuresDir

write.over(suite.figuresDir / "testsets.tex", latexTableTestSets)
write.over(suite.figuresDir / "testsets-incremental.tex", latexTableTestSetsIncremental)
write.over(suite.figuresDir / "parseforest.tex", latexTableParseForest)
write.over(suite.figuresDir / "determinism.tex", latexTableDeterminism)

if(inScope("batch")) {
    write.over(suite.figuresDir / "measurements-parsetables.tex", latexTableMeasurementsBatch(CSV.parse(parseTableMeasurementsPath)))
    write.over(suite.figuresDir / "measurements-parsing.tex",     latexTableMeasurementsBatch(CSV.parse(parsingMeasurementsPath)))
    Seq(
        InternalParse,
        Internal,
        External
    ).map { comparison =>
        write.over(suite.figuresDir / s"benchmarks-${comparison.dir}-time.tex",          latexTableBenchmarks(CSV.parse(batchResultsDir / comparison.dir / "time.csv"),       Time))
        write.over(suite.figuresDir / s"benchmarks-${comparison.dir}-throughput.tex",    latexTableBenchmarks(CSV.parse(batchResultsDir / comparison.dir / "throughput.csv"), Throughput))
    }
}
