import sbt._
import Keys._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import xerial.sbt.Sonatype._
import com.typesafe.sbt.pgp.PgpKeys
import scalaprops.ScalapropsPlugin.autoImport._

object SmartCheckBuild {
  import Dependencies._

  private def gitHash: String = scala.util.Try(
    sys.process.Process("git rev-parse HEAD").lines_!.head
  ).getOrElse("master")

  private[this] val unusedWarnings = (
    "-Ywarn-unused" ::
    "-Ywarn-unused-import" ::
    Nil
  )

  lazy val exampleSettings = Seq(
    sonatypeSettings
  ).flatten ++ Seq(
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.10.6", scalaVersion.value),
    resolvers += Opts.resolver.sonatypeReleases,
    scalacOptions ++= (
      "-deprecation" ::
      "-unchecked" ::
      "-Xlint" ::
      "-feature" ::
      "-language:existentials" ::
      "-language:higherKinds" ::
      "-language:implicitConversions" ::
      "-language:reflectiveCalls" ::
      Nil
    )
  )

  lazy val buildSettings = Seq(
    sonatypeSettings,
    scalapropsWithScalazlaws
  ).flatten ++ Seq(
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.10.6", scalaVersion.value),
    resolvers += Opts.resolver.sonatypeReleases,
    scalacOptions ++= (
      "-deprecation" ::
      "-unchecked" ::
      "-Xlint" ::
      "-feature" ::
      "-language:existentials" ::
      "-language:higherKinds" ::
      "-language:implicitConversions" ::
      "-language:reflectiveCalls" ::
      Nil
    ),
    scalacOptions ++= {
      if(scalaVersion.value.startsWith("2.11"))
        unusedWarnings
      else
        Nil
    },
    scalapropsVersion := Version.scalaprops,
    libraryDependencies ++= Seq(
      scalaprops,
      shapeless
    ),
    libraryDependencies ++= {
     if (scalaBinaryVersion.value startsWith "2.10")
       Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
     else
       Nil
    },
    fullResolvers ~= {_.filterNot(_.name == "jcenter")},
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      ReleaseStep(
        action = state => Project.extract(state).runTask(PgpKeys.publishSigned, state)._1,
        enableCrossBuild = true
      ),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    credentials ++= PartialFunction.condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASS")){
      case (Some(user), Some(pass)) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    }.toList,
    organization := "com.github.pocketberserker",
    homepage := Some(url("https://github.com/pocketberserker/scala-smartcheck")),
    licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
    pomExtra :=
      <developers>
        <developer>
          <id>pocketberserker</id>
          <name>Yuki Nakayama</name>
          <url>https://github.com/pocketberserker</url>
        </developer>
      </developers>
      <scm>
        <url>git@github.com:pocketberserker/scala-smartcheck.git</url>
        <connection>scm:git:git@github.com:pocketberserker/scala-smartcheck.git</connection>
        <tag>{if(isSnapshot.value) gitHash else { "v" + version.value }}</tag>
      </scm>
    ,
    description := "A Smarter QuickCheck ",
    pomPostProcess := { node =>
      import scala.xml._
      import scala.xml.transform._
      def stripIf(f: Node => Boolean) = new RewriteRule {
        override def transform(n: Node) =
          if (f(n)) NodeSeq.Empty else n
      }
      val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
      new RuleTransformer(stripTestScope).transform(node)(0)
    }
  ) ++ Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) ~= {_.filterNot(unusedWarnings.toSet)}
  )

  object Dependencies {

    object Version {
      val scalaprops = "0.3.4"
      val shapeless = "2.3.2"
    }

    val scalaprops = "com.github.scalaprops" %% "scalaprops-core" % Version.scalaprops
    val shapeless = "com.chuusai" %% "shapeless" % Version.shapeless
  }
}
