import $ivy.`com.lihaoyi::ammonite-ops:1.8.1`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._
import $file.parsers, parsers._
import org.spoofax.interpreter.terms.IStrategoTerm
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

println("Validate sources...")

suite.languages.foreach { language =>
    println(" " + language.name)

    val parsers = Parser.variants(language)

    timed("validate " + language.id) {
        language.sourceFilesBatch().foreach { file =>
            val input = read! file
            val filename = file relativeTo language.sourcesDir

            val results: Seq[(String, ParseResult)] = parsers.map { parser =>
                try {
                    Await.result(Future.successful {
                        (parser.id, parser.parse(input))
                    }, 10 seconds)
                } catch {
                    case _: TimeoutException => (parser.id, ParseFailure(Some("timeout"), Timeout))
                }
            }

            val failures: Seq[(String, Option[String], ParseFailureReason)] = results.flatMap {
                case (parser, ParseFailure(error, reason)) => Some((parser, error, reason))
                case _ => None
            }

            val successASTs: Seq[IStrategoTerm] = results.flatMap {
                case (parser, ParseSuccess(ast)) => ast
                case _ => None
            }

            def consistentASTs(asts: Seq[IStrategoTerm]) = asts.map(_.toString()).distinct.size == 1

            val valid =
                if (failures.nonEmpty) {
                    println("   Invalid: " + filename)
                    failures.foreach { case (parser, error, _) =>
                        println("     " + parser + error.fold("")(" (" + _ + ")"))
                    }

                    val invalid = failures.exists(_._3 == Invalid)
                    val ambiguous = failures.exists(_._3 == Ambiguous)
                    
                    if (invalid) {
                        mkdir! sourcesDir / "invalid"
                        mv.over(file, sourcesDir / "invalid" / filename.last)
                    } else if (ambiguous) {
                        mkdir! sourcesDir / "ambiguous"
                        mv.over(file, sourcesDir / "ambiguous" / filename.last)
                    } else {
                        mkdir! sourcesDir / "timeout"
                        mv.over(file, sourcesDir / "timeout" / filename.last)
                    }

                    false
                } else if (!consistentASTs(successASTs)) {
                    println("   Inconsistent: " + filename)

                    mkdir! sourcesDir / "inconsistent"
                    mv.over(file, sourcesDir / "inconsistent" / filename.last)

                    false
                } else {
                    println("   Valid: " + filename)

                    true
                }
        }
    }

    val sizes =
        language.sources.batch.flatMap { source =>
            val sourceDir = language.sourcesDir / "batch" / source.id
            val files = ls! sourceDir |? (_.ext == language.extension)
            val sizes = files.map(_.size)
            
            if (sizes.nonEmpty) {
                write.over(language.sourcesDir / "batch" / source.id / "sizes.csv", sizes.mkString("\n") + "\n")            
                %("Rscript", "sourceSizes.R", sourceDir, source.id)(pwd)
            }

            sizes
        }
    
    if (sizes.nonEmpty) {
        write.over(language.sourcesDir / "batch" / "sizes.csv", sizes.mkString("\n") + "\n")
        %("Rscript", "sourceSizes.R", language.sourcesDir / "batch", language.name)(pwd)
    }

    timed("persist dynamic parse tables") {
        persistDynamicParseTables
    }
}
