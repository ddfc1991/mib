package com.mibot.core.record

import com.mibot.core.agent.AgentLoop
import com.mibot.core.agent.SystemPrompt
import com.mibot.core.prompt.PromptVersion
import com.mibot.core.testing.fakeModel
import com.mibot.core.tool.FakeScreen
import com.mibot.core.tool.ToolDispatcher
import com.mibot.core.cap.web.FakeWebPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.mibot.core.assembly.toolDispatcher

/**
 * 运行记录里记下本任务提示词的版本:每组都展开时发给模型的系统提示词加工具定义的摘要,
 * 和这次实际展开了哪几组无关(只有手机时,展开了手机一组后看到的就是它)。
 */
class PromptVersionRecordTest {
    private suspend fun run(tools: ToolDispatcher): Pair<String, String> {
        val events = mutableListOf<RunEvent>()
        val m = fakeModel("main") { call("phone_observe"); answer("好了") }
        AgentLoop(m, tools, SystemPrompt.identity(), sink = object : RunSink {
            override fun event(e: RunEvent) { events += e }
        }).run("t")
        val r = m.requests.last()
        return events.filterIsInstance<RunEvent.PromptVersion>().single().hash to PromptVersion.of(r.system, r.tools)
    }

    @Test fun recordsHashOfWhatTheModelSaw() = runTest {
        val (phone, expected) = run(toolDispatcher(FakeScreen()))
        assertEquals(expected, phone)
        val (web, _) = run(toolDispatcher(FakeScreen(), web = FakeWebPort()))
        assertNotEquals(phone, web)
    }
}
