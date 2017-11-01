
lazy val libdeps = Seq(
  "tradr" %% "tradr-common" % "0.0.1-SNAPSHOT",
  "com.datastax.oss" % "java-driver-core" % "4.0.0-alpha1",
  "com.datastax.oss" % "java-driver-parent" % "4.0.0-alpha1",
  "org.scala-lang.modules" % "scala-java8-compat_2.12" % "0.8.0"
)


lazy val root = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(Seq(
    name := "tradr-cassandra-connector",
    organization := "tradr",
    scalaVersion := "2.12.3",
    crossScalaVersions := Seq("2.11.11", "2.12.3"),
    libraryDependencies ++= libdeps,
    git.useGitDescribe := true
  ))

