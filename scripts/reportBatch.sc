import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Reporting batch results...")

%("Rscript", "batchBenchmarks.R",        batchResultsDir,   reportsDir / "batch")(pwd)
%("Rscript", "batchBenchmarksPerFile.R", perFileResultsDir, reportsDir / "perFile")(pwd)

suite.languages.foreach { language =>
    println(" " + language.name)

    %("Rscript", "batchBenchmarks.R", batchResultsDir / language.id, reportsDir / "batch" / language.id)(pwd)

    language.sourcesBatchNonEmpty.map { source =>
        %("Rscript", "batchBenchmarks.R", batchResultsDir / language.id / source.id, reportsDir / "batch" / language.id / source.id)(pwd)
    }
}
