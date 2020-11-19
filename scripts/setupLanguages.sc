import $ivy.`com.lihaoyi::ammonite-ops:2.2.0`, ammonite.ops._

import $file.common, common._, Suite._

println("Setting up languages...")

mkdir! languagesDir

suite.languages.foreach { language =>
    println(" " + language.name)

    language.parseTable match {
        case gitSpoofax @ GitSpoofax(repo: String, _, version: String, _) =>
            val repoDir = gitSpoofax.repoDir(language)
            rm! repoDir
            mkdir! repoDir
    
            timed("clone " + language.id) {
                println(s"  Cloning ${repo} (version: ${version})...")
                %%("git", "clone", repo, ".")(repoDir)
                %%("git", "checkout", version)(repoDir)
            }

            val configPath = gitSpoofax.spoofaxProjectDir(language) / "metaborg.yaml"
    
            timed("build " + language.id) {
                println(s"  Building ${gitSpoofax.spoofaxProjectDir(language)}...")
                %%("mvn", "install", MAVEN_OPTS="-Xmx8G -Xss64M")(gitSpoofax.spoofaxProjectDir(language))
            }
        case LocalParseTable(_) =>
    }
}
