import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Executing measurements...")

mkdir! Suite.measurementsDir

suite.languages.foreach { language =>
    println(" " + language.name)

    def batchMeasurements(source: Option[BatchSource]) = {
        val (sourcesDir, reportDir) = source match {
                case None => (
                    language.sourcesDir / "batch",
                    language.measurementsDir / "batch"
                )
                case Some(source) => (
                    language.sourcesDir / "batch" / source.id,
                    language.measurementsDir / "batch" / source.id
                )
            }

        timed("measure " + language.id + source.fold("")("/" + _.id) + " (batch)") {
            %%(
                "mvn",
                "-q",
                "exec:java@batch",
                "-Dexec.args=\""+
                    s"language=${language.id} " +
                    s"extension=${language.extension} " +
                    s"parseTablePath=${language.parseTableTermPath} " +
                    s"sourcePath=$sourcesDir " +
                    s"type=multiple" +
                "\"",
                "-DreportPath=" + reportDir,
                MAVEN_OPTS="-Xmx8G -Xss64M"
            )(suite.spoofaxDir / "jsglr" / "org.spoofax.jsglr2.measure")
        }
    }

    def incrementalMeasurements(source: IncrementalSource) = {
        timed("measure " + language.id + "/" + source.id + " (incremental)") {
            %%(
                "mvn",
                "-q",
                "exec:java@incremental",
                "-Dexec.args=\""+
                    s"language=${language.id} " +
                    s"extension=${language.extension} " +
                    s"parseTablePath=${language.parseTableTermPath} " +
                    s"sourcePath=${language.sourcesDir / "incremental" / source.id} " +
                "\"",
                s"-DreportPath=${language.measurementsDir / "incremental" / source.id}",
                MAVEN_OPTS="-Xmx8G -Xss64M"
            )(suite.spoofaxDir / "jsglr" / "org.spoofax.jsglr2.measure")
        }
    }

    if (language.sourcesBatchNonEmpty.nonEmpty) {
        batchMeasurements(None)

        language.sourcesBatchNonEmpty.map { source =>
            batchMeasurements(Some(source))
        }
    }

    language.sources.incremental.foreach { source =>
        incrementalMeasurements(source)
    }
}
