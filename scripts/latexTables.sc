import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._

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

def mean[B](xs: TraversableOnce[B])(implicit num: Integral[B]): Long =
    num.toLong(xs.sum) / xs.size +
        (if (2 * (num.toLong(xs.sum) % xs.size) >= xs.size) 1L else 0L) // Round up when fractional part would be >= 0.5

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
            }.filter(_._1 != 0).unzip3
            val values = Iterator(files, lines, size, (size zip files) map { case (s, f) => if (f == 0) 0 else s / f })
                .map(v => thousandSeparator(mean(v).toString))

            s"  & ${source.getName} & ${files.size} & ${values.mkString(" & ")} \\\\"
        }

        s"""|\\multirow{${sources.size}}{*}{${language.name}}
            |${sources.mkString("\n")}""".stripMargin
    }

    s"""|\\begin{tabular}{l|l|r|r|r|r|r}
        |Language & Source & Versions & Files & Lines & Size (B) & Mean file size (B) \\\\ \\hline
        |${languages.mkString(" \\hline\n")}
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


val texWrapper = (mapper: (String) => String) => (key: String) => {
    val res = mapper(key).replace("%", "\\%")
    val res2 = if (res.contains("\n")) s"\\makecell[r]{${res.replace("\n", "\\\\")}}" else res
    "([0-9]{4,})".r.replaceSomeIn(res2, m => Some(thousandSeparator(m.group(1)).replace("\\", "\\\\")))
}

def createMeasurementsTableSummary(ids: Seq[String], rows: Seq[Map[String, Long]], percs: Seq[Map[String, Double]]) = {
    import IncrementalMeasurementsTableUtils._

    val n = rows.length

    val measurementsAvgRow =
        s"Average & ${measurementsCellsSummary.map(texWrapper(cellMapper(rows.avgMaps, percs.avgMaps, true))).mkString(" & ")}"

    val measurementsRows = ids.zip(rows zip percs).map { case (label, (row, perc)) =>
        s"$label & ${measurementsCellsSummary.map(texWrapper(cellMapper(row, perc, true))).mkString(" & ")}"
    }

    // The resizebox is because the table is 1.05pt too wide otherwise. Hardly noticable, but I want to fix all warnings
    s"""|\\begin{tabular}[t]{c *{${measurementsCellsSummary.size}}{|r}}
        |  \\multirowcell{3}{Language} & \\multicolumn{4}{c|}{Parse nodes (\\% of total nodes)} & \\multicolumn{4}{c}{\\resizebox{0.4\\textwidth}{!}{Breakdowns (\\% of total breakdowns)}} \\\\
        |       & \\multirowcell{2}{Irre-\\\\usable} & \\multirowcell{2}{Reused} & \\multirowcell{2}{Broken\\\\down} & \\multirowcell{2}{Rebuilt}
        |       & \\multirowcell{2}{Irre-\\\\usable} & \\multirowcell{2}{No\\\\actions} & \\multirowcell{2}{Tempo-\\\\rary} & \\multirowcell{2}{Wrong\\\\state} \\\\
        |  & & & & & & & & \\\\ \\hline
        |  ${measurementsAvgRow} \\\\ \\hline
        |  ${measurementsRows.mkString(" \\\\\n  ")}
        |\\end{tabular}
        |""".stripMargin
}

def createMeasurementsTable(
    header: String, ids: Seq[String],
    rows: Seq[Map[String, Long]], percs: Seq[Map[String, Double]],
    avgsLabel: String, avgs: Map[String, Long], avgPercs: Map[String, Double]
) = {
    import IncrementalMeasurementsTableUtils._

    val n = rows.length

    val measurementsAvgRow = s"$avgsLabel & ${measurementsCells.map(texWrapper(cellMapper(avgs, avgPercs, header != "Version"))).mkString(" & ")}"

    val measurementsRows = ids.zip(rows zip percs).map { case (label, (row, perc)) =>
        s"$label & ${measurementsCells.map(texWrapper(cellMapper(row, perc, header != "Version"))).mkString(" & ")}"
    }

    s"""|\\begin{tabular}[t]{c *{${measurementsCells.size}}{|r}}
        |  \\multirowcell{3}{$header} & \\multicolumn{3}{c|}{Parse Nodes}   & \\multirowcell{3}{Character\\\\Nodes\\\\Count} \\\\
        |                             & \\multirowcell{2}{Count}
        |                             & \\multirowcell{2}{Ambi-\\\\guous}
        |                             & \\multirowcell{2}{Irre-\\\\usable}     \\\\
        |  & & & \\\\ \\hline
        |  ${measurementsAvgRow} \\\\ \\hline
        |  ${if (header == "Version") " & & & & \\\\" else ""}
        |  ${measurementsRows.mkString(" \\\\\n  ")}
        |\\end{tabular}
        |""".stripMargin
}

def createMeasurementsTableSkew(
    header: String, ids: Seq[String],
    rows: Seq[Map[String, Long]], percs: Seq[Map[String, Double]],
    avgsLabel: String, avgs: Map[String, Long], avgPercs: Map[String, Double]
) = {
    import IncrementalMeasurementsTableUtils._

    val n = rows.length

    val measurementsAvgRow = s"$avgsLabel & ${measurementsCellsSkew.map(texWrapper(cellMapper(avgs, avgPercs, header != "Version"))).mkString(" & ")}"

    val measurementsRows = ids.zip(rows zip percs).map { case (label, (row, perc)) =>
        if (row.getOrElse("breakDowns", -1) == row.getOrElse("breakDownTemporary", -1))
            s"$label & ${thousandSeparator(row("createParseNode"))} & & & ${thousandSeparator(row("shiftParseNode"))} & ${thousandSeparator(row("shiftCharacterNode"))} & & & & &"
        else
            s"$label & ${measurementsCellsSkew.map(texWrapper(cellMapper(row, perc, header != "Version"))).mkString(" & ")}"
    }

    s"""|\\begin{tabular}[t]{c *{${measurementsCellsSkew.size}}{|r}}
        |  \\multirowcell{3}{$header} & \\multicolumn{3}{c|}{Parse Nodes} &                \\multicolumn{2}{c|}{Shift}                & \\multicolumn{5}{c}{Breakdown} \\\\
        |                             &  Created  &  Reused  &  Rebuilt   & \\makecell{Parse\\\\Node} & \\makecell{Character\\\\Node} & Count & \\makecell{Irre-\\\\usable} & \\makecell{No\\\\Actions} & \\makecell{Tempo-\\\\rary} & \\makecell{Wrong\\\\State} \\\\ \\hline
        |  ${measurementsAvgRow} \\\\ \\hline
        |  ${measurementsRows.mkString(" \\\\\n  ")}
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

if (inScope("batch")) {
    write.over(suite.figuresDir / "parseforest.tex", latexTableParseForest)
    write.over(suite.figuresDir / "determinism.tex", latexTableDeterminism)

    write.over(suite.figuresDir / "measurements-parsetables.tex", latexTableMeasurementsBatch(CSV.parse(parseTableMeasurementsPath)))
    write.over(suite.figuresDir / "measurements-parsing.tex",     latexTableMeasurementsBatch(CSV.parse(parsingMeasurementsPath)))

    Seq(
        InternalParse,
        Internal,
        External
    ).filter(comparison => suite.implode.fold(true)(_ == comparison.implode)).map { comparison =>
        write.over(suite.figuresDir / s"benchmarks-${comparison.dir}-time.tex",          latexTableBenchmarks(CSV.parse(batchResultsDir / comparison.dir / "time.csv"),       Time))
        write.over(suite.figuresDir / s"benchmarks-${comparison.dir}-throughput.tex",    latexTableBenchmarks(CSV.parse(batchResultsDir / comparison.dir / "throughput.csv"), Throughput))
    }
}

if (inScope("incremental")) {
    import IncrementalMeasurementsTableUtils._

    val languagesWithIncrementalSources = suite.languages.filter(_.sources.incremental.nonEmpty)

    mkdir! suite.figuresDir / "incremental"
    languagesWithIncrementalSources.foreach { language =>
        mkdir! suite.figuresDir / "incremental" / language.id
        language.sources.incremental.foreach { source =>
            mkdir! suite.figuresDir / "incremental" / language.id / s"${source.id}-parse"

            val (rows, percs, skewRows, skewPercs, avgs, avgPercs, skewAvgs, skewAvgPercs) = language.measurementsIncremental(Some(source))
            val n = rows.length

            val ids = rows.map(_("version").toString)
            val skewIds = s"~~ \\textrightarrow\\ ${rows(0)("version")}" +:
                rows.drop(1).map(_("version")).map(i => s"${"~" * (i.toString.length - (i - 1).toString.length) * 2}${i - 1} \\textrightarrow\\ ${i}")

            val avgsLabel = s"\\makecell{Average\\\\(${ids(0)}..${ids.last.toInt - 1})}"
            val skewAvgsLabel = s"\\makecell{Average\\\\(${ids(1)}..${ids.last})}"


            write.over(
                suite.figuresDir / "incremental" / language.id / s"${source.id}-parse" / "measurements-parsing-incremental.tex",
                createMeasurementsTable("Version", ids, rows, percs, avgsLabel, avgs, avgPercs))
            write.over(
                suite.figuresDir / "incremental" / language.id / s"${source.id}-parse" / "measurements-parsing-incremental-skew.tex",
                createMeasurementsTableSkew("Version", skewIds, skewRows, skewPercs, skewAvgsLabel, skewAvgs, skewAvgPercs))
        }

        val (rows, percs, skewRows, skewPercs, avgs, avgPercs, skewAvgs, skewAvgPercs) = language.measurementsIncremental(None)
        val n = rows.length

        val ids = language.sources.incremental.map(_.getName)

        write.over(
            suite.figuresDir / "incremental" / language.id / "measurements-parsing-incremental.tex",
            createMeasurementsTable("Source", ids, rows, percs, "Average", avgs, avgPercs))
        write.over(
            suite.figuresDir / "incremental" / language.id / "measurements-parsing-incremental-skew.tex",
            createMeasurementsTableSkew("Source", ids, skewRows, skewPercs, "Average", skewAvgs, skewAvgPercs))
    }

    val languageNames = languagesWithIncrementalSources.map(_.name)
    val (rows, percs, skewRows, skewPercs) = getAllMeasurements(languagesWithIncrementalSources)

    write.over(
        suite.figuresDir / "incremental" / "measurements-parsing-incremental.tex",
        createMeasurementsTable("Language", languageNames, rows, percs, "Average", rows.avgMaps, percs.avgMaps))
    write.over(
        suite.figuresDir / "incremental" / "measurements-parsing-incremental-skew.tex",
        createMeasurementsTableSkew("Language", languageNames, skewRows, skewPercs, "Average", skewRows.avgMaps, skewPercs.avgMaps))
    write.over(
        suite.figuresDir / "incremental" / "measurements-parsing-incremental-summary.tex",
        createMeasurementsTableSummary(languageNames,
            rows.zip(skewRows).map(t => t._2 ++ t._1.filterKeys(_ == "parseNodesIrreusable")),
            percs.zip(skewPercs).map(t => t._2 ++ t._1.filterKeys(_ == "parseNodesIrreusable"))))

    val appendix =
        s"""|\\begin{table}[ht]
            |    \\centering
            |    \\legend{Incremental parsing measurements for all languages.}
            |    \\label{tbl:incremental-measurements-all}
            |    \\maxsizebox*{\\linewidth}{\\textheight-2.2em}{%
            |        \\input{\\generated/figures/incremental/measurements-parsing-incremental}\\hspace{0.5em}%
            |        \\input{\\generated/figures/incremental/measurements-parsing-incremental-skew}%
            |    }
            |\\end{table}
            |
            |
            |${languagesWithIncrementalSources.map { language =>
                s"""|\\section{${language.name}}
                    |
                    |\\begin{table}[ht]
                    |    \\centering
                    |    \\legend{Incremental parsing measurements for the ${language.name} language.}
                    |    \\label{tbl:incremental-measurements-${language.id}}
                    |    \\maxsizebox*{\\linewidth}{\\textheight-2.2em}{%
                    |        \\input{\\generated/figures/incremental/${language.id}/measurements-parsing-incremental}\\hspace{0.5em}%
                    |        \\input{\\generated/figures/incremental/${language.id}/measurements-parsing-incremental-skew}%
                    |    }
                    |\\end{table}
                    |
                    |${language.sources.incremental.map { source =>
                        s"""|\\begin{table}[ht]
                            |    \\centering
                            |    \\legend{Incremental parsing measurements for ${language.name} source ${source.getName}.}
                            |    \\label{tbl:incremental-measurements-${language.id}-${source.id}}
                            |    \\maxsizebox*{\\linewidth}{\\textheight-2.2em}{%
                            |        \\input{\\generated/figures/incremental/${language.id}/${source.id}-parse/measurements-parsing-incremental}\\hspace{0.5em}%
                            |        \\input{\\generated/figures/incremental/${language.id}/${source.id}-parse/measurements-parsing-incremental-skew}%
                            |    }
                            |\\end{table}""".stripMargin
                     }.mkString("\n\n")}""".stripMargin
             }.mkString("\n\n\n\\clearpage\n\n")}
            |""".stripMargin
    write.over(suite.figuresDir / "incremental" / "measurements-parsing-incremental-appendix.tex", appendix)
}
