import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._

println("Generating batch plots...")

Seq(Internal, External).map { comparison =>
    val subtitle = comparison match {
        case Internal => "without imploding"
        case External => "with imploding"
    }

    %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir, figuresDir / "batch" / comparison.dir, subtitle)(pwd)

    suite.languages.foreach { language =>
        println(" " + language.name)

        %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir / language.id, figuresDir / "batch" / comparison.dir / language.id, subtitle)(pwd)

        language.sourcesBatchNonEmpty.map { source =>
            %("Rscript", "batchPlots.R", batchResultsDir / comparison.dir / language.id / source.id, figuresDir / "batch" / comparison.dir / language.id / source.id, subtitle)(pwd)
        }
    }
}

%("Rscript", "batchSampledPlots.R", batchSampledResultsDir, figuresDir / "batch-sampled")(pwd)
