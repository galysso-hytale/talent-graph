plugins {
    id("com.azuredoom.hytale-workspace") version "1.+"
}

hytaleWorkspace {
    // Only `core` is a loadable Hytale plugin; `api` is a plain library module.
    modProjects = listOf(":core")
    hostProject = ":core"

    // Shared defaults inherited by every `hytaleTools` project.
    manifestGroup = property("manifest_group").toString()
    hytaleVersion = property("hytale_version").toString()
    patchline = property("patchline").toString()
}
