package com.openminis.app.mibot.v7

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [destiny/zones] 功能架构四区 —— 按用户目标而非技术域组织导航。
 *
 * 四区：对话（主用面）/ 探索（看见世界）/ 能力（它能动手）/ 账户（底子）。
 * 每区是一组分组的入口行，行点开直达真实功能路由。这是功能层设计；
 * 视觉层（A 墨夜 / B 晨瓷 / C 素白）待选定后套 token，不影响本结构。
 */

/** 一个功能入口行。 */
data class ZoneEntry(
    val label: String,
    val hint: String,
    val route: String,
    val tag: String = "",
    val tone: Int = 0,
)

/** 一个分组（区下面的一组入口）。 */
data class ZoneGroup(val name: String, val entries: List<ZoneEntry>)

/** 一个区。 */
data class Zone(
    val id: String,
    val label: String,
    val subtitle: String,
    val groups: List<ZoneGroup>,
)

/** 四区的固定定义。 */
object Zones {
    // tab 顺序与 V7TabBar labels 一致：对话 / 探索 / 能力 / 账户
    val list = listOf(
        Zone(
            id = "chat",
            label = "对话",
            subtitle = "主用面 · 和 AI 说话",
            groups = emptyList(), // 对话 tab 走真实会话列表，不走分区行
        ),
        Zone(
            id = "explore",
            label = "探索",
            subtitle = "看见世界 · 有什么可做",
            groups = listOf(
                ZoneGroup("动态", listOf(
                    ZoneEntry("三态内容流", "在看 / 想到的 / 能领的钱", "zone_feed", "在看", 0),
                    ZoneEntry("活动记录", "它做过什么 · 何时", "zone_feed", "历史", 1),
                )),
                ZoneGroup("灵感", listOf(
                    ZoneEntry("点子 IDEAS", "想法收集与任务化", "zone_ideas", "真实", 2),
                    ZoneEntry("AI 邮箱", "邮件收发明细", "zone_mail", "真实", 3),
                    ZoneEntry("服务器", "巡检 · 指标", "zone_servers", "真实", 4),
                )),
            ),
        ),
        Zone(
            id = "capability",
            label = "能力",
            subtitle = "它能动手 · 替你做事",
            groups = listOf(
                ZoneGroup("主动", listOf(
                    ZoneEntry("主动能力", "激进程度 · 敏感推断 · 消息归纳", "zone_mika_active", "Mika", 0),
                    ZoneEntry("微信入口", "扫码绑定 · 微信里派活", "zone_mika_wechat", "Mika", 1),
                    ZoneEntry("AI 接打电话", "自动接听 · 帮打 · 任务单", "zone_mika_call", "Mika", 2),
                )),
                ZoneGroup("设备", listOf(
                    ZoneEntry("后台操控", "虚拟屏 · 无障碍 · 屏幕 · 点击", "zone_phone", "Mika", 0),
                    ZoneEntry("沙箱", "Linux 发行版 · 容器 · 终端", "zone_sandbox", "Mika", 1),
                    ZoneEntry("浏览器", "CDP 真实输入 · WebLogin（经能力总线）", "zone_browser", "Mika", 2),
                    ZoneEntry("语音", "转写 · 朗读 · 唤醒", "zone_voice", "内置", 3),
                )),
                ZoneGroup("运行", listOf(
                    ZoneEntry("插件", "DSH 插件宿主 · 市场", "zone_plugins", "内置", 4),
                    ZoneEntry("任务队列", "真实作业 · 执行中 · 排队", "zone_queue", "实时", 5),
                    ZoneEntry("应用访问", "白名单 · 禁用建议", "zone_app_access", "Mika", 6),
                    ZoneEntry("网关", "本地网关 · 中转站", "zone_gateway", "内置", 7),
                )),
            ),
        ),
        Zone(
            id = "account",
            label = "账户",
            subtitle = "底子 · 账号与系统",
            groups = listOf(
                ZoneGroup("模型", listOf(
                    ZoneEntry("模型服务商", "API Key · 账号 · 余额", "providers", "12 实例", 0),
                    ZoneEntry("模型组", "主模型 · 备用 · 路由", "model_groups", "150 条", 1),
                    ZoneEntry("用量统计", "token · 调用 · 花费", "usage_stats", "实时", 2),
                )),
                ZoneGroup("金库", listOf(
                    ZoneEntry("账号台账", "有账号 · 额度 · 签到", "mibot_vault", "有账号的才留", 3),
                    ZoneEntry("自动取 Key", "recipe 引擎 · 候选排序", "mibot_vault_discover", "v2", 4),
                    ZoneEntry("OAuth 账号中心", "统一账号链接", "mibot_vault", "真实", 5),
                )),
                ZoneGroup("能力扩展", listOf(
                    ZoneEntry("子 Agent", "派单花名册", "agents", "6 个", 6),
                    ZoneEntry("技能", "装在设备上的技能包", "skills", "真实", 7),
                    ZoneEntry("记忆", "长期记忆与文件", "memory", "真实", 0),
                    ZoneEntry("MCP 服务器", "外部工具接入", "mcp", "5 个", 1),
                    ZoneEntry("环境变量", "注入运行时", "env_vars", "真实", 2),
                )),
                ZoneGroup("存储与文件", listOf(
                    ZoneEntry("存储", "占用 · 清理", "storage", "", 3),
                    ZoneEntry("根文件系统", "容器镜像", "rootfs_management", "", 4),
                    ZoneEntry("挂载目录", "宿主机映射", "mounted_folders", "", 5),
                    ZoneEntry("文件浏览器", "容器内文件", "file_browser", "", 6),
                    ZoneEntry("备份", "导出 · 导入", "backup", "", 7),
                )),
                ZoneGroup("网络", listOf(
                    ZoneEntry("网络", "连通性 · DNS", "network", "", 0),
                    ZoneEntry("代理", "HTTP / SOCKS", "network_proxies", "", 1),
                    ZoneEntry("Tailscale", "组网与设备", "network_tailscale", "", 2),
                    ZoneEntry("容器 DNS", "容器内解析", "container_dns", "", 3),
                )),
                ZoneGroup("系统", listOf(
                    ZoneEntry("外观", "主题 · 字体 · 图标", "appearance", "", 4),
                    ZoneEntry("背景", "壁纸与模糊", "background", "", 5),
                    ZoneEntry("权限", "系统权限总览", "permissions", "", 6),
                    ZoneEntry("日志", "运行日志与诊断", "logs", "", 7),
                    ZoneEntry("定时任务", "Cron 与触发", "scheduled_tasks", "", 0),
                    ZoneEntry("关于", "版本 · 构建 · 许可", "about", "", 1),
                )),
            ),
        ),
    )

    fun byId(id: String): Zone? = list.firstOrNull { it.id == id }
}

/**
 * 把分区行路由翻译成真实导航。分区行用 zone_ 前缀的占位路由，
 * 这里统一映射到 V7Actions 的真实回调（AppNavigation 已在 V7Actions
 * 里接好 onNavigate / onVoice / onServers / onPhoneControl / ...）。
 */
fun ZoneEntry.navigate(actions: V7Actions) {
    when (route) {
        "zone_feed" -> actions.onFeed()
        "zone_ideas" -> actions.onNavigate("glass_ideas")
        "zone_mail" -> actions.onNavigate("mibot_mail")
        "zone_servers" -> actions.onServers()
        "zone_phone" -> actions.onPhoneControl()
        "zone_sandbox" -> actions.onSandbox()
        "zone_browser" -> actions.onNavigate("mibot_browser")
        "zone_voice" -> actions.onVoice()
        "zone_plugins" -> actions.onNavigate("mibot_plugins")
        "zone_queue" -> actions.onNavigate("destiny_queue")
        "zone_app_access" -> actions.onAppAccess()
        "zone_gateway" -> actions.onNavigate("mibot_gateway")
        "zone_mika_active" -> actions.onNavigate("mika_active")
        "zone_mika_wechat" -> actions.onNavigate("mika_wechat")
        "zone_mika_call" -> actions.onNavigate("mika_call")
        else -> actions.onNavigate(route)
    }
}

/**
 * 一个分区页：大标题 + 副标题 + 分组入口行。
 * 复用 V7 规格（V7Section 分组头、平铺行、发丝线、单色强调）。
 */
@Composable
fun DestinyZonePage(
    zone: Zone,
    actions: V7Actions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                zone.subtitle,
                fontSize = 12.sp,
                color = V7.Tx2,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        zone.groups.forEach { group ->
            item { V7Section(group.name, "${group.entries.size} 项") }
            items(group.entries, key = { "z-${zone.id}-${group.name}-${it.label}" }) { entry ->
                ZoneRow(entry, actions) { entry.navigate(actions) }
            }
        }
    }
}

@Composable
private fun ZoneRow(entry: ZoneEntry, actions: V7Actions, onClick: () -> Unit) {
    ZoneQuickRow(entry.label, entry.hint, entry.tag, onClick)
}

/** 通用入口行（公开，供其它页面插入单个入口，如对话区的群聊）。 */
@Composable
fun ZoneQuickRow(
    label: String,
    hint: String = "",
    tag: String = "",
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = V7.Tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    fontSize = 11.5.sp,
                    color = V7.Tx2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tag.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(V7.Sf2)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(tag, fontSize = 10.5.sp, color = V7.Tx2) }
        }
        Spacer(Modifier.width(8.dp))
        Text("\u203A", fontSize = 17.sp, color = V7.Tx3)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}
