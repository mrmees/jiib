package works.mees.jiib.ui.files

import kotlinx.serialization.json.JsonElement
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.FileDeleteArgs
import works.mees.jiib.command.FileDirectoryArgs
import works.mees.jiib.command.FileNameArgs
import works.mees.jiib.command.MetadataArgs
import works.mees.jiib.command.PrintStartArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.command.request
import works.mees.jiib.net.JsonRpcClient

interface FileBrowserClient {
    suspend fun getDirectory(path: String?, extended: Boolean = true): JsonElement? = null
    suspend fun getMetadata(filename: String): JsonElement? = null
    suspend fun getThumbnails(filename: String): JsonElement? = null
    fun deleteFile(rootPrefixedPath: String) = Unit
    fun startPrint(filename: String) = Unit
    fun pausePrint() = Unit
    fun resumePrint() = Unit
    fun cancelPrint() = Unit
}

class MoonrakerFileBrowserClient(
    private val rpc: JsonRpcClient,
    private val dispatcher: CommandDispatcher,
) : FileBrowserClient {
    override suspend fun getDirectory(path: String?, extended: Boolean): JsonElement? =
        runCatching {
            rpc.request(CommandRegistry.filesGetDirectory, FileDirectoryArgs(path = path, extended = extended))
        }.getOrNull()

    override suspend fun getMetadata(filename: String): JsonElement? =
        runCatching {
            rpc.request(CommandRegistry.filesMetadata, MetadataArgs(filename))
        }.getOrNull()

    override suspend fun getThumbnails(filename: String): JsonElement? =
        runCatching {
            rpc.request(CommandRegistry.filesThumbnails, FileNameArgs(filename))
        }.getOrNull()

    override fun deleteFile(rootPrefixedPath: String) {
        dispatcher.dispatch(CommandRegistry.filesDelete, FileDeleteArgs(rootPrefixedPath))
    }

    override fun startPrint(filename: String) {
        dispatcher.dispatch(CommandRegistry.printStart, PrintStartArgs(filename))
    }

    override fun pausePrint() {
        dispatcher.dispatch(CommandRegistry.printPause, Unit)
    }

    override fun resumePrint() {
        dispatcher.dispatch(CommandRegistry.printResume, Unit)
    }

    override fun cancelPrint() {
        dispatcher.dispatch(CommandRegistry.printCancel, Unit)
    }
}
