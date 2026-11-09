package com.mibot.core.assembly

import com.mibot.core.cap.mcp.McpCapability
import com.mibot.core.cap.mcp.McpConnection
import com.mibot.core.cap.mcp.mcpAssemblyReason
import com.mibot.core.capability.Capability
import com.mibot.core.capability.CapabilityStatus
import com.mibot.core.capability.PromptEnv
import com.mibot.core.run.RunSpec
import com.mibot.core.tool.AgentTool

internal class McpClientProvider(private val connection: McpConnection) : CapabilityProvider {
    override val id: String = "mcp:${connection.endpoint.serverId}"
    override fun open(spec: RunSpec): Capability = try {
        McpCapability(connection, id)
    } catch (e: Exception) {
        McpFailedCapability(id, connection.endpoint.serverId, mcpAssemblyReason(e))
    }
}

/** 目录或能力构造失败只摘掉这个 server;错误留在提示里，不让它拖垮其他能力。 */
private class McpFailedCapability(
    override val id: String,
    private val serverId: String,
    private val reason: String,
) : Capability {
    override fun status() = CapabilityStatus.Unavailable("MCP server '$serverId' could not be assembled: $reason")
    override fun tools(): List<AgentTool> = emptyList()
    override fun sections(env: PromptEnv): List<String> = listOf(
        "MCP server '$serverId' could not be assembled: $reason. Its tools are unavailable; other capabilities remain available.",
    )
}
