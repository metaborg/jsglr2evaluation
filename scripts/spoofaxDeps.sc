import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import coursierapi.MavenRepository

interp.repositories.update(List(
    MavenRepository.of("file://" + %%("mvn", "help:evaluate", "-Dexpression=settings.localRepository", "-q", "-DforceStdout")(pwd).out.string),
    MavenRepository.of("https://repo1.maven.org/maven2")
))
