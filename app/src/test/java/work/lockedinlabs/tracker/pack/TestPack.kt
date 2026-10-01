package work.lockedinlabs.tracker.pack

import java.io.File

/** Installs the app's real data pack (app/src/main/assets/pack) for unit tests. */
object TestPack {
    val pack: DataPack by lazy { PackParser.parse { name -> File("src/main/assets/${PackParser.DIR}/$name").readText() } }

    fun install() = Pack.install(pack)
}
