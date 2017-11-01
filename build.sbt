lazy val libdeps = Seq(
  "tradr" %% "tradr-common" % "0.0.4",
  "com.datastax.oss" % "java-driver-core" % "4.0.0-alpha1",
  "com.datastax.oss" % "java-driver-parent" % "4.0.0-alpha1",
  "org.scala-lang.modules" % "scala-java8-compat_2.12" % "0.8.0"
//  "org.specs2" %% "specs2" % "3.7" % "test", "it"
)


lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Seq(
    name := "tradr-cassandra-connector",
    organization := "tradr",
    scalaVersion := "2.12.3",
    crossScalaVersions := Seq("2.11.11", "2.12.3"),
    libraryDependencies ++= libdeps,
    version := "0.0.2",
    assemblyJarName in assembly :=  s"${name.value}_${scalaVersion.value}-${version.value}.jar"
  ))

