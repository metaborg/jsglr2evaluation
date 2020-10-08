import $ivy.`com.lihaoyi::ammonite-ops:1.8.1`, ammonite.ops._

import $file.common, common._, Args._

def setupLanguages(implicit args: Args) = {
    println("Setting up languages...")
    
    mkdir! languagesDir

    args.languages.foreach { language =>
        println(" " + language.name)

        language.parseTable match {
            case gitSpoofax @ GitSpoofax(repo: String, _) =>
                rm!    gitSpoofax.repoDir(language)
                mkdir! gitSpoofax.repoDir(language)
        
                timed("clone " + language.id) {
                    println(s"  Cloning ${repo}...")
                    %%("git", "clone", repo, ".")(gitSpoofax.repoDir(language))
                }

                val configPath = gitSpoofax.spoofaxProjectDir(language) / "metaborg.yaml"

                write.over(configPath, read! configPath replaceFirst("sdf2table:\\s*[a-z]+", "sdf2table: java"))
        
                timed("build " + language.id) {
                    println(s"  Building ${gitSpoofax.spoofaxProjectDir(language)}...")
                    %%("mvn", "install", MAVEN_OPTS="-Xmx8G -Xss64M")(gitSpoofax.spoofaxProjectDir(language))
                }
            case LocalParseTable(_) =>
        }
    }
}

@main
def ini(args: String*) = withArgs(args :_ *)(setupLanguages(_))