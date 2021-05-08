import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Generating batch plots...")

Seq(
    InternalParse
).map { comparison =>
    %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir, figuresDir / "batch" / comparison.dir, comparison.name)(pwd)

    suite.languages.foreach { language =>
        println(" " + language.name)

        %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir / language.id, figuresDir / "batch" / comparison.dir / language.id, comparison.name)(pwd)

        if (suite.individualBatchSources) {
            language.sourcesBatchNonEmpty.map { source =>
                %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir / language.id / source.id, figuresDir / "batch" / comparison.dir / language.id / source.id, comparison.name)(pwd)
            }
        }
    }
}

%("Rscript", "batchSampledPlots.R", batchSampledResultsDir, figuresDir / "batch-sampled")(pwd)
