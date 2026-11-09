package com.mibot.core.general

import com.mibot.core.observation.ObsKind
import com.mibot.core.observation.Observation
import com.mibot.core.observation.Place
import com.mibot.core.observation.Trust
import com.mibot.core.observation.Untrusted
import com.mibot.core.prompt.Prompts
import com.mibot.core.search.WebSearch
import com.mibot.core.search.WebSearchResult
import com.mibot.core.tool.AgentTool
import com.mibot.core.tool.Args
import com.mibot.core.tool.Concurrency
import com.mibot.core.tool.Risk
import com.mibot.core.tool.ToolContext
import com.mibot.core.tool.ToolDef
import com.mibot.core.tool.ToolExecution
import com.mibot.core.tool.Verb
import com.mibot.core.tool.int
import com.mibot.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 经用户配置的搜索端点直接查网页;失败时明确让模型退回浏览器,不暗中换服务。 */
internal object WebSearchTool : AgentTool {
    override val verb = Verb.WebSearch
    override val label = "搜索网页"
    override val risk = Risk.Read
    override val offScreen = true
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(
        "web_search", Prompts.tool("web_search", "maxCount" to WebSearch.MAX_COUNT),
        mapOf(
            "query" to str("Words to search for, 1-500 characters."),
            "count" to int("Optional result count, 1-${WebSearch.MAX_COUNT} (default 5).", 1, WebSearch.MAX_COUNT),
        ),
        listOf("query"),
    )

    override fun available(ctx: ToolContext) = ctx.config.webSearch != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val query = a.str("query", max = 500)!!
        val count = (a.int("count", 1, WebSearch.MAX_COUNT) ?: 5).coerceIn(1, WebSearch.MAX_COUNT)
        return when (val result = checkNotNull(ctx.config.webSearch).search(query, count)) {
            is WebSearchResult.Success -> {
                ToolExecution(
                    buildJsonObject {
                        put("ok", true)
                        put("results", JsonArray(result.hits.map { hit ->
                            buildJsonObject {
                                put("title", Untrusted.wrap(hit.title))
                                put("url", Untrusted.wrap(hit.url))
                                put("snippet", Untrusted.wrap(hit.snippet))
                            }
                        }))
                        if (result.hits.isEmpty()) put("hint", "No results. Try a different query or use the browser tools to search.")
                    },
                    observations = result.hits.map { hit ->
                        Observation(
                            ObsKind.Read, "${hit.title}\n${hit.snippet}", source = "web_search",
                            place = Place.page(hit.url), title = hit.title, trust = Trust.Untrusted,
                        )
                    }.takeIf { it.isNotEmpty() } ?: listOf(
                        Observation(ObsKind.Read, "No web search results for: $query", source = "web_search", trust = Trust.Trusted),
                    ),
                )
            }
            is WebSearchResult.Failure -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", result.code)
                put("message", result.message)
                put("fallback", "Use the browser tools to perform this search instead.")
                result.retryAfterSeconds?.let { put("retryAfterSeconds", it) }
            })
        }
    }
}
