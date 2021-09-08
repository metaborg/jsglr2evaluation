import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._
import $file.spoofax, spoofax._
import $ivy.`org.metaborg:org.spoofax.jsglr2.benchmark:2.6.0-SNAPSHOT`

import org.spoofax.interpreter.terms.IStrategoTerm
import org.spoofax.jsglr2._
import org.spoofax.jsglr2.imploder.ImploderVariant;
import org.spoofax.jsglr2.integration.IntegrationVariant
import org.spoofax.jsglr2.benchmark.jsglr2.util.JSGLR2MultiParser
import org.spoofax.jsglr2.parseforest.ParseForestConstruction;
import org.spoofax.jsglr2.parseforest.ParseForestRepresentation;
import org.spoofax.jsglr2.parser.ParserVariant;
import org.spoofax.jsglr2.reducing.Reducing;
import org.spoofax.jsglr2.stack.StackRepresentation;
import org.spoofax.jsglr2.stack.collections.ActiveStacksRepresentation;
import org.spoofax.jsglr2.stack.collections.ForActorStacksRepresentation;
import org.spoofax.jsglr2.tokens.TokenizerVariant;
import org.metaborg.parsetable.ParseTableVariant
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait Parser {
    def id: String
    def parse(input: String): ParseResult
    def recovery: Boolean
}

case class JSGLR2Parser(language: Language, name: String, jsglr2Variant: JSGLR2Variant, incremental: Boolean) extends Parser {
    val id = "jsglr2-" + name
    val variant = new IntegrationVariant(
        new ParseTableVariant(),
        jsglr2Variant
    )
    val recovery = variant.parser.recovery
    val jsglr2 = getJSGLR2(variant, language)
    def parse(input: String) = jsglr2.parseResult(input) match {
        case success: JSGLR2Success[IStrategoTerm] =>
            if (success.isAmbiguous)
                ParseFailure(Some("ambiguous"), Ambiguous)
            else
                ParseSuccess(Some(success.ast))
        case failure: JSGLR2Failure[_] => ParseFailure(Some(failure.parseFailure.failureCause.toMessage.toString), Invalid)
    }
    val jsglr2Multi = new JSGLR2MultiParser(jsglr2)
    def parseMulti(inputs: String*) = jsglr2Multi.parse(inputs:_*).asScala
}
object JSGLR2Parser {
    def apply(language: Language, jsglr2Preset: JSGLR2Variant.Preset, incremental: Boolean): JSGLR2Parser =
        new JSGLR2Parser(language, jsglr2Preset.name, jsglr2Preset.variant, incremental)
}

case class JSGLR1Parser(language: Language) extends Parser {
    val id = "jsglr1"
    val jsglr1 = getJSGLR1(language)
    def parse(input: String) = Try(jsglr1.parse(input, null, null)) match {
        case Success(_) => ParseSuccess(None)
        case Failure(_) => ParseFailure(None, Invalid)
    }
    val recovery = true
}

import $ivy.`org.antlr:antlr4-runtime:4.7.2`

import org.antlr.v4.runtime.{Lexer => ANTLR_Lexer, Parser => ANTLR_Parser, _}
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.spoofax.jsglr2.benchmark.antlr4.{Java8Lexer => ANTLR_Java8Lexer, Java8Parser => ANTLR_Java8Parser}
import org.spoofax.jsglr2.benchmark.antlr4.{JavaLexer => ANTLR_JavaLexer, JavaParser => ANTLR_JavaParser}

case class ANTLRParser[ANTLR__Lexer <: ANTLR_Lexer, ANTLR__Parser <: ANTLR_Parser](id: String, getLexer: CharStream => ANTLR__Lexer, getParser: TokenStream => ANTLR__Parser, doParse: ANTLR__Parser => Tree) extends Parser {
    def parse(input: String) = {
        try {
            val charStream = CharStreams.fromString(input)
            val lexer = getLexer(charStream)

            val tokens = new CommonTokenStream(lexer)
            val parser = getParser(tokens)

            parser.setErrorHandler(new BailErrorStrategy())
            
            doParse(parser)

            ParseSuccess(None)
        } catch {
            case e: ParseCancellationException => ParseFailure(None, Invalid)
        }
    }
    def recovery = false
}

trait ParseResult {
    def isValid: Boolean
    def isInvalid = !isValid
}
case class ParseSuccess(ast: Option[IStrategoTerm]) extends ParseResult {
    def isValid = true
}
case class ParseFailure(error: Option[String], reason: ParseFailureReason) extends ParseResult {
    def isValid = false
}

trait ParseFailureReason
object Invalid extends ParseFailureReason
object Ambiguous extends ParseFailureReason
object Timeout extends ParseFailureReason

object Parser {
    // Recovery without optimized parse forest; full parse forest is required for text reconstruction
    // TODO: use recovery variants _with_ optimized parse forest for benchmarking
    val recoveryJSGLR2 = new JSGLR2Variant(
        new ParserVariant(
            ActiveStacksRepresentation.standard(),
            ForActorStacksRepresentation.standard(),
            ParseForestRepresentation.standard(),
            ParseForestConstruction.Full,
            StackRepresentation.Hybrid,
            Reducing.Basic,
            true),
        ImploderVariant.standard(),
        TokenizerVariant.standard()
    )

    val recoveryElkhoundJSGLR2 = new JSGLR2Variant(
        new ParserVariant(
            ActiveStacksRepresentation.standard(),
            ForActorStacksRepresentation.standard(),
            ParseForestRepresentation.standard(),
            ParseForestConstruction.Full,
            StackRepresentation.HybridElkhound,
            Reducing.Elkhound,
            true),
        ImploderVariant.standard(),
        TokenizerVariant.standard()
    )

    def jsglr2variants(language: Language)(implicit suite: Suite): Seq[Parser] = suite.jsglr2variants.map(_ match {
        case "standard"            => JSGLR2Parser(language, JSGLR2Variant.Preset.standard, false)
        case "elkhound"            => JSGLR2Parser(language, JSGLR2Variant.Preset.elkhound, false)
        case "incremental"         => JSGLR2Parser(language, JSGLR2Variant.Preset.incremental, true)
        case "recovery"            => JSGLR2Parser(language, "recovery", recoveryJSGLR2, false)
        case "recoveryElkhound"    => JSGLR2Parser(language, "recoveryElkhound", recoveryElkhoundJSGLR2, false)
        case "recoveryIncremental" => JSGLR2Parser(language, JSGLR2Variant.Preset.recoveryIncremental, true)
    })

    def variants(language: Language)(implicit suite: Suite): Seq[Parser] =
        //JSGLR1Parser(language) +:
        jsglr2variants(language) ++
        language.antlrBenchmarks.map { benchmark =>
            benchmark.id match {
                case "antlr" =>
                    ANTLRParser[ANTLR_Java8Lexer, ANTLR_Java8Parser](benchmark.id, new ANTLR_Java8Lexer(_), new ANTLR_Java8Parser(_), _.compilationUnit)
                case "antlr-optimized" =>
                    ANTLRParser[ANTLR_JavaLexer, ANTLR_JavaParser](benchmark.id, new ANTLR_JavaLexer(_), new ANTLR_JavaParser(_), _.compilationUnit)
            }
        }
}
