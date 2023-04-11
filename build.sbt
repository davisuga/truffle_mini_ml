val scala3Version = "3.2.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "simple_truffle_lang",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.graalvm.truffle" % "truffle-api" % "22.3.1",
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "3.0.1",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )
