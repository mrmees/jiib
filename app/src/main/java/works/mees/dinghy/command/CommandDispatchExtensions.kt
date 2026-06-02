package works.mees.dinghy.command

import kotlinx.serialization.json.JsonElement
import works.mees.dinghy.net.JsonRpcClient

fun <P> CommandDispatcher.dispatch(command: CommandSpec<P>, args: P) {
    val method = requireNotNull(command.method) {
        "Command ${command.catalogId} does not define a JSON-RPC method"
    }
    dispatch(command.dispatchKey(args), method, command.params(args))
}

suspend fun <P> JsonRpcClient.request(
    command: CommandSpec<P>,
    args: P,
    timeoutMs: Long = JsonRpcClient.DEFAULT_REQUEST_TIMEOUT_MS,
): JsonElement {
    require(command.transport == CommandTransport.JsonRpc) {
        "JsonRpcClient.request(command) requires a JSON-RPC command spec; ${command.catalogId} uses ${command.transport}"
    }
    val method = requireNotNull(command.method) {
        "Command ${command.catalogId} does not define a JSON-RPC method"
    }
    return request(method, command.params(args), timeoutMs)
}
