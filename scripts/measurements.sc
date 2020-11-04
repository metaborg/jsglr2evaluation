import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Executing measurements...")

mkdir! Suite.measurementsDir

suite.languages.foreach { language =>
    println(" " + language.name)

    if (language.sourcesBatchNonEmpty.nonEmpty)
        timed("measure " + language.id) {
            %%(
                "mvn",
                "exec:java",
                "-Dexec.args=\""+
                    s"language=${language.id} " +
                    s"extension=${language.extension} " +
                    s"parseTablePath=${language.parseTableTermPath} " +
                    s"sourcePath=${language.sourcesDir / "batch"} " +
                    s"type=multiple" +
                "\"",
                "-DreportPath=" + language.measurementsDir,
                MAVEN_OPTS="-Xmx8G -Xss64M"
            )(suite.spoofaxDir / "jsglr" / "org.spoofax.jsglr2.measure")
        }
}
