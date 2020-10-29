import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._

println("Setting up languages...")

mkdir! languagesDir

suite.languages.foreach { language =>
    println(" " + language.name)

    language.parseTable match {
        case gitSpoofax @ GitSpoofax(repo: String, _, _) =>
            rm!    gitSpoofax.repoDir(language)
            mkdir! gitSpoofax.repoDir(language)
    
            timed("clone " + language.id) {
                println(s"  Cloning ${repo}...")
                %%("git", "clone", repo, ".")(gitSpoofax.repoDir(language))
            }

            val configPath = gitSpoofax.spoofaxProjectDir(language) / "metaborg.yaml"
    
            timed("build " + language.id) {
                println(s"  Building ${gitSpoofax.spoofaxProjectDir(language)}...")
                %%("mvn", "install", MAVEN_OPTS="-Xmx8G -Xss64M")(gitSpoofax.spoofaxProjectDir(language))
            }
        case LocalParseTable(_) =>
    }
}
