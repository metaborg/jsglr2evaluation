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
        (language.sourceFilesBatch() ++ language.sourceFilesIncremental).foreach { file =>
            val input = read! file
            val filename = file relativeTo language.sourcesDir

            val results: Seq[(String, ParseResult)] = parsers.map { parser =>
                try {
                    Await.result(Future.successful {
                        (parser.id, parser.parse(input))
                    }, 10 seconds)
                } catch {
                    case _: TimeoutException => (parser.id, ParseFailure(Some("timeout")))
                }
            }

            val failures: Seq[(String, Option[String])] = results.flatMap {
                case (parser, ParseFailure(error)) => Some((parser, error))
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
                    failures.foreach { case (parser, error) =>
                        println("     " + parser + error.fold("")(" (" + _ + ")"))
                    }

                    false
                } else if (!consistentASTs(successASTs)) {
                    println("   Inconsistent: " + filename)

                    false
                } else {
                    println("   Valid: " + filename)

                    true
                }

            if (!valid) {
                mkdir! sourcesDir / "invalid"
                mv.over(file, sourcesDir / "invalid" / filename.last)
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
                            case JSGLR2Parser(_, _, incremental) => incremental
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

    timed("persist dynamic parse tables") {
        persistDynamicParseTables
    }
}
