import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._
import $file.spoofaxDeps
import $ivy.`com.google.guava:guava-testlib:30.0-jre`
import $ivy.`com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:HEAD-SNAPSHOT`
import $ivy.`org.openjdk.jol:jol-core:0.14`

import $file.common, common._, Suite._
import $file.spoofax, spoofax._
import $file.parsers, parsers._

import java.lang.management.ManagementFactory
import com.google.monitoring.runtime.instrumentation.AllocationSizeMeasurer
import org.openjdk.jol.info.GraphLayout

import org.spoofax.jsglr2.incremental.EditorUpdate
import org.spoofax.jsglr2.incremental.diff.{IStringDiff,JGitHistogramDiff}

import scala.collection.JavaConverters._

val diff: IStringDiff = new JGitHistogramDiff();

val allocationSizeMeasurer = new AllocationSizeMeasurer();

println("Executing memory benchmarks...")

suite.languages.foreach { language =>
    println(" " + language.name)

    val warmupIterations = suite.warmupIterations
    val benchmarkIterations = suite.benchmarkIterations

    mkdir! language.memoryBenchmarksDir
    val resultCsvBatch = language.memoryBenchmarksDir / "batch.csv"
    val resultCsvIncremental = language.memoryBenchmarksDir / "incremental.csv"

    write.over(resultCsvBatch,       """"Parser","File","Size","Memory (incl. garbage)","Error incl.","Memory (excl. garbage)","Error excl."""" + "\n")
    write.over(resultCsvIncremental, """"Parser","File","Previous","Size","Added","Removed","Changes","Memory (incl. garbage)","Error incl.","Memory (excl. garbage)","Error excl."""" + "\n")

    val incrementalSources = language.sources.incremental.flatMap { source =>
        val sourceDir = language.sourcesDir / "incremental" / source.id
        val versions = (ls! sourceDir).map(path => (path relativeTo sourceDir).toString.toInt).sorted
        versions.flatMap { version =>
            val versionDir = sourceDir / version.toString
            (ls! versionDir).map { file =>
                val previousFile = sourceDir / (version - 1).toString / (file relativeTo versionDir)
                (previousFile, file)
            }.filter(exists! _._1).map { case (file1, file2) =>
                val diffList: java.util.List[EditorUpdate] = diff.diff(read! file1, read! file2)
                val deleted = diffList.asScala.map(_.deletedLength).sum
                val inserted = diffList.asScala.map(_.insertedLength).sum
                val numChanges = diffList.size

                (file1, file2, deleted, inserted, numChanges)
            }.filter(_._5 > 0)
        }
    }

    val numSamples = 100 // TODO this could be configurable
    val sampledFilesBatch = scala.util.Random.shuffle(language.sourceFilesBatch() ++ language.sourceFilesIncremental).take(numSamples)
    val sampledFilesIncremental = scala.util.Random.shuffle(incrementalSources).take(numSamples)

    Parser.jsglr2variants(language).foreach { parser =>
        timed("memoryBenchmarks " + language.id + " " + parser.id) {
            val jsglr2parser = parser.asInstanceOf[JSGLR2Parser]

            sampledFilesBatch.foreach { file =>
                val input = read! file
                val filename = file relativeTo language.sourcesDir

                val results: (Seq[Long], Seq[Long]) = (1 to (warmupIterations + benchmarkIterations)).map { i =>
                    java.lang.System.gc()

                    val parserSizeBefore = GraphLayout.parseInstance(jsglr2parser.jsglr2).totalSize()
                    allocationSizeMeasurer.startMeasure()

                    jsglr2parser.jsglr2.parseResult(input, "some random name to trigger caching", null)

                    val memoryIncl = allocationSizeMeasurer.endMeasure()
                    val parserSizeAfter = GraphLayout.parseInstance(jsglr2parser.jsglr2).totalSize()

                    jsglr2parser.jsglr2 match {
                        case j: org.spoofax.jsglr2.JSGLR2ImplementationWithCache[_, _, _, _, _, _] => j.clearCache()
                        case _ => ()
                    }

                    Some((memoryIncl, parserSizeAfter - parserSizeBefore))
                }.drop(warmupIterations).flatten.unzip

                write.append(resultCsvBatch, s""""${parser.id}","${filename}",${file.size},""")
                if (results._1.length == 0) {
                    write.append(resultCsvBatch, ",,,\n")
                } else {
                    val memoryIncl = results._1.sum / results._1.length
                    val memoryExcl = results._2.sum / results._2.length
                    val memoryInclError = results._1.map(v => (v - memoryIncl).abs).max
                    val memoryExclError = results._2.map(v => (v - memoryExcl).abs).max

                    write.append(resultCsvBatch, s"""${memoryIncl},${memoryInclError},${memoryExcl},${memoryExclError}""" + "\n")
                }
            }

            sampledFilesIncremental.foreach { case (file1, file2, deleted, inserted, numChanges) =>
                val input1 = read! file1
                val input2 = read! file2
                val filename = file2 relativeTo language.sourcesDir

                val results: (Seq[Long], Seq[Long]) = (1 to (warmupIterations + benchmarkIterations)).map { i =>
                    java.lang.System.gc()

                    jsglr2parser.jsglr2.parseResult(input1, "some random name to trigger caching", null)

                    val parserSizeBefore = GraphLayout.parseInstance(jsglr2parser.jsglr2).totalSize()
                    allocationSizeMeasurer.startMeasure()

                    jsglr2parser.jsglr2.parseResult(input2, "some random name to trigger caching", null)

                    val memoryIncl = allocationSizeMeasurer.endMeasure()
                    val parserSizeAfter = GraphLayout.parseInstance(jsglr2parser.jsglr2).totalSize()

                    jsglr2parser.jsglr2 match {
                        case j: org.spoofax.jsglr2.JSGLR2ImplementationWithCache[_, _, _, _, _, _] => j.clearCache()
                        case _ => ()
                    }

                    Some((memoryIncl, parserSizeAfter - parserSizeBefore))
                }.drop(warmupIterations).flatten.unzip

                write.append(resultCsvIncremental, s""""${parser.id}","${filename}",${file1.size},${file2.size},${deleted},${inserted},${numChanges},""")
                if (results._1.length == 0) {
                    write.append(resultCsvIncremental, ",,,\n")
                } else {
                    val memoryIncl = results._1.sum / results._1.length
                    val memoryExcl = results._2.sum / results._2.length
                    val memoryInclError = results._1.map(v => (v - memoryIncl).abs).max
                    val memoryExclError = results._2.map(v => (v - memoryExcl).abs).max

                    write.append(resultCsvIncremental, s"""${memoryIncl},${memoryInclError},${memoryExcl},${memoryExclError}""" + "\n")
                }
            }
        }
    }
}
