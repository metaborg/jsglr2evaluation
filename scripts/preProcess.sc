import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._
import $file.parsers, parsers._
import org.spoofax.interpreter.terms.IStrategoTerm
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

println("Validate sources...")

object PreProcessing {

    def run = {
        suite.languages.foreach { language =>
            println(" " + language.name)

            val parsers = Parser.variants(language)

            timed("validate " + language.id) {
                (language.sourceFilesBatch() ++ language.sourceFilesIncremental).foreach { file =>
                    val input = read! file
                    val filename = file relativeTo language.sourcesDir

                    val results: Seq[(String, ParseResult)] = parsers.filter(!_.recovery).map { parser =>
                        val result = withTimeout(parser.parse(input), 30)(ParseFailure(Some("timeout"), Timeout))(e => ParseFailure(Some("failed: " + e.getMessage), Invalid))

                        (parser.id, result)
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
                                cp.over(file, sourcesDir / "ambiguous" / filename.last)
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

                language.sources.incremental.foreach { source =>
                    println(s"  Checking incremental correctness for ${source.id}")
                    val sourceDir = language.sourcesDir / "incremental" / source.id
                    val versions = (ls! sourceDir).map(path => (path relativeTo sourceDir).toString.toInt).sorted
                    versions.foreach { version =>
                        val versionDir = sourceDir / version.toString
                        (ls! versionDir).foreach { file =>
                            val previousFile = sourceDir / (version - 1).toString / (file relativeTo versionDir)
                            if (exists! previousFile) {
                                parsers.filter({
                                    case jsglr2Parser: JSGLR2Parser => jsglr2Parser.incremental && !jsglr2Parser.recovery
                                    case _ => false
                                }).asInstanceOf[Seq[JSGLR2Parser]].foreach { parser =>
                                    val batch = parser.parseMulti(read! file)(0)
                                    val incremental = parser.parseMulti(read! previousFile, read! file)(1)
                                    if (batch.toString() != incremental.toString()) {
                                        println("   AST of " + file + " differs between batch and incremental!")
                                        exit(1)
                                    }
                                }
                            }
                        }
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
        }
    }

}

PreProcessing.run

timed("persist dynamic parse tables") {
    persistDynamicParseTables
}
