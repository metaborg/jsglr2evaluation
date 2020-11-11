import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Generating batch plots...")

%("Rscript", "batchPlots.R",        batchResultsDir,        figuresDir / "batch")(pwd)
%("Rscript", "batchSampledPlots.R", batchSampledResultsDir, figuresDir / "batch-sampled")(pwd)

suite.languages.foreach { language =>
    println(" " + language.name)

    %("Rscript", "batchPlots.R", batchResultsDir / language.id, figuresDir / "batch" / language.id)(pwd)

    language.sourcesBatchNonEmpty.map { source =>
        %("Rscript", "batchPlots.R", batchResultsDir / language.id / source.id, figuresDir / "batch" / language.id / source.id)(pwd)
    }
}
