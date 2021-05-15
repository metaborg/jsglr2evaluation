import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._
import $file.parsers, parsers._
import org.spoofax.interpreter.terms.IStrategoTerm
import org.spoofax.jsglr2.JSGLR2Variant
import org.spoofax.jsglr2.integration.IntegrationVariant
import org.spoofax.jsglr2.recovery.Reconstruction
import org.spoofax.jsglr2.parser.result.{ParseSuccess => JSGLR2ParseSuccess}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

println("Validate sources...")

object PreProcessing {

    val timeout = 30

    def run = {
        suite.languages.foreach { language =>
            println(" " + language.name)

            val parsers = Parser.variants(language)

            timed("validate " + language.id) {
                (language.sourceFilesBatch() ++ language.sourceFilesIncremental).foreach { file =>
                    val input = read! file
                    val filename = file relativeTo language.sourcesDir

                    val results: Seq[(String, ParseResult)] = parsers.filter(!_.recovery).map { parser =>
                        val result = withTimeout(parser.parse(input), timeout)(ParseFailure(Some("timeout"), Timeout))(e => ParseFailure(Some("failed: " + e.getMessage), Invalid))

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

                    val verdict =
                        if (failures.nonEmpty) {
                            println("   Invalid: " + filename)
                            failures.foreach { case (parser, error, _) =>
                                println("     " + parser + error.fold("")(" (" + _ + ")"))
                            }

                            val invalid = failures.exists(_._3 == Invalid)
                            val ambiguous = failures.exists(_._3 == Ambiguous)

                            Some(if (invalid) "invalid" else if (ambiguous) "ambiguous" else "timeout")
                        } else if (!consistentASTs(successASTs)) {
                            println("   Inconsistent: " + filename)

                            Some("inconsistent")
                        } else {
                            println("   Valid: " + filename)

                            None
                        }

                    verdict match {
                        case Some(folder) =>
                            val destinationFile = sourcesDir / folder / file.relativeTo(sourcesDir)
                            mkdir! destinationFile / up
                            if (verdict == "ambiguous")
                                // We still want to benchmark ambiguous files, but also want to be able to inspect them
                                cp.over(file, destinationFile)
                            else
                                mv.over(file, destinationFile)
                        case None =>
                    }
                }

                // TODO at some point, the recoveryIncremental parser should also work
                val incrementalParsers = parsers.filter({
                    case jsglr2Parser: JSGLR2Parser => jsglr2Parser.incremental && !jsglr2Parser.recovery
                    case _ => false
                }).asInstanceOf[Seq[JSGLR2Parser]]
                if (incrementalParsers.nonEmpty) {
                    language.sources.incremental.foreach { source =>
                        println(s"  Checking incremental correctness for ${source.id}")
                        val sourceDir = language.sourcesDir / "incremental" / source.id
                        val versions = (ls! sourceDir).map(path => (path relativeTo sourceDir).toString.toInt).sorted
                        versions.foreach { version =>
                            val versionDir = sourceDir / version.toString
                            (ls! versionDir).foreach { file =>
                                val previousFile = sourceDir / (version - 1).toString / (file relativeTo versionDir)
                                if (exists! previousFile) {
                                    incrementalParsers.foreach { parser =>
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

                if (suite.variants.contains("recovery")) {
                    val recoveringJSGLR2 = parsers.find {
                        case parser: JSGLR2Parser => parser.name == "recovery"
                        case _ => false
                    }.get.asInstanceOf[JSGLR2Parser]
                    val recoveringParser = recoveringJSGLR2.jsglr2.parser()
                    val nonRecoveringJSGLR2 = parsers.find {
                        case parser: JSGLR2Parser => parser.name == "standard"
                        case _ => false
                    }.get.asInstanceOf[JSGLR2Parser]

                    def verdictWithTimeout(id: String, timeout: Long)(body: => Option[String]): Option[String] =
                        withTimeout(body, timeout)(Some(s"timeout-$id"))(e => Some("failed"))

                    language.sources.recovery.foreach { source =>
                        println(s"  Checking recovery reconstruction for ${source.id}")

                        val sourceDir = language.sourcesDir / "recovery" / source.id

                        language.sourceFilesRecovery().foreach { file =>
                            val input = read! file
                            val filename = file relativeTo language.sourcesDir

                            val verdict: Option[String] =
                                verdictWithTimeout("non-recovery", timeout)(nonRecoveringJSGLR2.parse(input) match {
                                    case ParseFailure(error, reason) =>
                                        None
                                    case ParseSuccess(_) =>
                                        Some("valid") // Non-recovering parsing should fail
                                }).orElse(verdictWithTimeout("recovery", 2 * timeout) {
                                    recoveringParser.parse(input) match {
                                        case success: JSGLR2ParseSuccess[_] =>
                                            val reconstructed = Reconstruction.reconstruct(recoveringParser, success)

                                            nonRecoveringJSGLR2.parse(reconstructed.inputString) match {
                                                case ParseSuccess(_) => None
                                                case ParseFailure(_, _) => Some("reconstruction-broken")
                                            }
                                        case _ =>
                                            Some("unrecovered")
                                    }
                                })

                            verdict match {
                                case Some(verdict) =>
                                    println(s"   Invalid ($verdict): " + filename)

                                    mkdir! sourcesDir / "recovery-invalid" / verdict
                                    mv.over(file, sourcesDir / "recovery-invalid" / verdict / filename.last)
                                case None =>
                                    println("   Valid: " + filename)
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
