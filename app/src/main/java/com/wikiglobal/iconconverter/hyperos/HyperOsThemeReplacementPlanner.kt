package com.wikiglobal.iconconverter.hyperos

import java.io.File

data class RenderedThemeActivityIcon(
    val packageName: String,
    val launcherActivity: String,
    val png: ByteArray
)

/** Builds only exact HyperOS entries present in the baseline archive. */
object HyperOsThemeReplacementPlanner {
    fun plan(base: File, rendered: List<RenderedThemeActivityIcon>): List<HyperOs3IconReplacement> = rendered
        .groupBy { it.packageName }
        .flatMap { (packageName, icons) ->
            val replacements = linkedMapOf<String, HyperOs3IconReplacement>()
            val baseEntry = HyperOsThemeEntryResolver.baseEntry(packageName)
            // HyperOS always consults the package-level entry, including a package
            // with one launcher activity and a pre-existing activity alias.
            replacements[baseEntry] = HyperOs3IconReplacement(packageName, icons.first().png, exactEntryName = baseEntry)
            icons.forEach { icon ->
                HyperOsThemeEntryResolver.entriesFor(base, packageName, icon.launcherActivity)
                    .filter { it != baseEntry }
                    .forEach { exact -> replacements[exact] = HyperOs3IconReplacement(packageName, icon.png, exactEntryName = exact) }
            }
            replacements.values
        }
}
