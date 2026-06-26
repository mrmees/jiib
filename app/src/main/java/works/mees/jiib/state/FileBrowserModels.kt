package works.mees.jiib.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class FileBrowserRowKind(val prefix: String) {
    Up("up"),
    Directory("dir"),
    File("file"),
}

data class FileBrowserDirectory(
    val directoryPath: String,
    val displayPath: String,
    val rows: List<FileBrowserRow>,
    val upRow: FileBrowserRow?,
)

data class FileBrowserRow(
    val kind: FileBrowserRowKind,
    val name: String,
    val stableId: String,
    val directoryPath: String? = null,
    val relativeFilename: String? = null,
    val rootPrefixedPath: String? = null,
    val sizeBytes: Long? = null,
    val modifiedEpochSeconds: Double? = null,
    val thumbnailRelPath: String? = null,
)

object FileBrowserPaths {
    fun directoryPath(relativeDirectory: String?): String {
        val relative = stripGcodesRoot(relativeDirectory)
        return if (relative.isBlank()) "gcodes" else "gcodes/$relative"
    }

    fun relativeFilename(path: String): String =
        stripGcodesRoot(path)

    fun rootPrefixedPath(relativeFilename: String): String =
        "gcodes/${stripGcodesRoot(relativeFilename)}"

    fun displayPath(directoryPath: String): String =
        stripGcodesRoot(directoryPath)

    fun parentDirectoryPath(directoryPath: String): String? {
        val relative = stripGcodesRoot(directoryPath)
        if (relative.isBlank()) return null
        val parent = relative.substringBeforeLast('/', "")
        return directoryPath(parent.ifBlank { null })
    }

    internal fun childRelative(directoryPath: String, name: String): String {
        val parent = stripGcodesRoot(directoryPath)
        val cleanName = stripSlashes(name)
        return if (parent.isBlank()) cleanName else "$parent/$cleanName"
    }

    private fun stripGcodesRoot(path: String?): String {
        val clean = stripSlashes(path.orEmpty())
        return when {
            clean == "gcodes" -> ""
            clean.startsWith("gcodes/") -> clean.removePrefix("gcodes/")
            else -> clean
        }
    }

    private fun stripSlashes(path: String): String =
        path.trim().trim('/')
}

fun parseFileBrowserDirectory(
    result: JsonObject,
    directoryPath: String = "gcodes",
): FileBrowserDirectory {
    val normalizedDirectoryPath = FileBrowserPaths.directoryPath(FileBrowserPaths.displayPath(directoryPath))
    val upRow = FileBrowserPaths.parentDirectoryPath(normalizedDirectoryPath)?.let { parent ->
        FileBrowserRow(
            kind = FileBrowserRowKind.Up,
            name = "Up",
            stableId = "up:$normalizedDirectoryPath",
            directoryPath = parent,
        )
    }
    val directories = result.arrayOrEmpty("dirs")
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.toDirectoryRow(normalizedDirectoryPath) }
        // Hide dotfile dirs (.thumbs, .git, etc.) — they're machinery, never something to browse into.
        .filterNot { it.name.startsWith(".") }
        .sortedBy { it.name.lowercase() }
    val files = result.arrayOrEmpty("files")
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.toFileRow(normalizedDirectoryPath) }
        .sortedWith(
            compareByDescending<FileBrowserRow> { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.name.lowercase() },
        )
    return FileBrowserDirectory(
        directoryPath = normalizedDirectoryPath,
        displayPath = FileBrowserPaths.displayPath(normalizedDirectoryPath),
        rows = directories + files,
        upRow = upRow,
    )
}

private fun JsonObject.toDirectoryRow(currentDirectoryPath: String): FileBrowserRow? {
    val raw = stringOrNull("dirname") ?: stringOrNull("name") ?: stringOrNull("path") ?: return null
    val relative = if (raw.contains('/')) {
        FileBrowserPaths.relativeFilename(raw)
    } else {
        FileBrowserPaths.childRelative(currentDirectoryPath, raw)
    }
    val name = relative.substringAfterLast('/')
    if (name.isBlank()) return null
    val path = FileBrowserPaths.directoryPath(relative)
    return FileBrowserRow(
        kind = FileBrowserRowKind.Directory,
        name = name,
        stableId = "dir:$path",
        directoryPath = path,
        modifiedEpochSeconds = doubleOrNullAt("modified"),
    )
}

private fun JsonObject.toFileRow(currentDirectoryPath: String): FileBrowserRow? {
    val raw = stringOrNull("filename") ?: stringOrNull("path") ?: stringOrNull("name") ?: return null
    val relative = if (raw.contains('/')) {
        FileBrowserPaths.relativeFilename(raw)
    } else {
        FileBrowserPaths.childRelative(currentDirectoryPath, raw)
    }
    val name = relative.substringAfterLast('/')
    if (name.isBlank()) return null
    return FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = name,
        stableId = "file:$relative",
        relativeFilename = relative,
        rootPrefixedPath = FileBrowserPaths.rootPrefixedPath(relative),
        sizeBytes = longOrNullAt("size"),
        modifiedEpochSeconds = doubleOrNullAt("modified"),
        thumbnailRelPath = smallestThumbRelPath(this),
    )
}

private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
    this[key] as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.stringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

private fun JsonObject.doubleOrNullAt(key: String): Double? =
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun JsonObject.longOrNullAt(key: String): Long? =
    runCatching { this[key]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull()
