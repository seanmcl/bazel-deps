package com.github.johnynek.bazel_deps

import cats.data.{ NonEmptyList, Validated }
import cats.implicits._
import com.monovore.decline.{ Command => DCommand, _ }
import java.io.File
import java.nio.file.Path

sealed abstract class Command

object Command {
  case class Generate(repoRoot: Path, depsFile: String, shaFile: String) extends Command {
    def absDepsFile: File =
      new File(repoRoot.toFile, depsFile)

    def shaFilePath: String =
      new File(shaFile).toString
  }
  val generate = DCommand("generate", "generate transitive bazel targets") {
    val repoRoot = Opts.option[Path](
      "repo-root",
      short = "r",
      metavar = "reporoot",
      help = "the ABSOLUTE path to the root of the bazel repo")

    val depsFile = Opts.option[String](
      "deps",
      short = "d",
      metavar = "deps",
      help = "relative path to the dependencies yaml file")

    val shaFile = Opts.option[String](
      "sha-file",
      short = "s",
      metavar = "sha-file",
      help = "relative path to the sha lock file (usually called workspace.bzl).")

    (repoRoot |@| depsFile |@| shaFile).map(Generate(_, _, _))
  }

  case class FormatDeps(deps: Path, overwrite: Boolean) extends Command
  val format = DCommand("format-deps", "format the dependencies yaml file") {
    val depsFile = Opts.option[Path]("deps", short = "d", help = "the ABSOLUTE path to your dependencies yaml file")
    val overwrite = Opts.flag("overwrite", short = "o", help = "if set, we overwrite the file after we read it").orFalse

    (depsFile |@| overwrite).map(FormatDeps(_, _))
  }

  case class MergeDeps(deps: NonEmptyList[Path], output: Option[Path]) extends Command
  val mergeDeps = DCommand("merge-deps", "merge a series of dependencies yaml file") {
    val deps = Opts.options[Path]("deps", short = "d", help = "list of ABSOLUTE paths of files to merge")
    val out = Opts.option[Path]("output", short = "o", help = "merged output file").orNone

    (deps |@| out).map(MergeDeps(_, _))
  }

  implicit val langArg: Argument[Language] = new Argument[Language] {
    def defaultMetavar: String = "lang"
    def read(s: String) = s match {
      case "java" => Validated.valid(Language.Java)
      case "scala" => Validated.valid(Language.Scala.default)
      case other => Validated.invalidNel(s"unknown language: $other")
    }
  }

  implicit val mvnArg: Argument[MavenCoordinate] = new Argument[MavenCoordinate] {
    def defaultMetavar: String = "maven-coord"
    def read(s: String) = MavenCoordinate.parse(s)
  }

  case class AddDep(deps: Path, lang: Language, coords: NonEmptyList[MavenCoordinate]) extends Command
  val addDep = DCommand("add-dep", "add dependencies (of a single language) to the yaml file") {
    val p = Opts.option[Path]("deps", short = "d", help = "the YAML dependency file to add to")
    val lang = Opts.option[Language]("lang", short = "l", help = "the language of the given maven coordinate")
    val mcs = Opts.arguments[MavenCoordinate]("mvn-coord")

    (p |@| lang |@| mcs).map(AddDep(_, _, _))
  }

  val command: DCommand[Command] =
    DCommand(name = "bazel-deps", header = "a tool to manage transitive external Maven dependencies for bazel") {
      (Opts.help :: (List(generate, format, mergeDeps, addDep).map(Opts.subcommand(_))))
        .reduce(_.orElse(_))
    }
}
