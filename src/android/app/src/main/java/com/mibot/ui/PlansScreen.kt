package com.mibot.ui

import com.openminis.app.R
import com.mibot.i18n.tr
import com.mibot.i18n.trPlural
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mibot.chat.ConversationStore
import com.mibot.ui.components.MikaDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mibot.core.trigger.CONDITION_COOLDOWN
import com.mibot.core.trigger.CONDITION_NOT_MET
import com.mibot.core.trigger.CONDITION_PLAN_EDITED
import com.mibot.core.trigger.ConditionNotify
import com.mibot.core.trigger.TriggerDefinition
import com.mibot.core.trigger.TriggerSource
import com.mibot.core.trigger.TriggerSnapshot
import com.mibot.core.trigger.TriggerWindow
import com.mibot.core.trigger.TriggerWindowState
import com.mibot.core.trigger.canToggle
import com.mibot.core.trigger.toCopyPlanDraft
import com.mibot.task.ExactAlarmGuide
import com.mibot.task.TriggerRuntime
import com.mibot.ui.components.ActionSheet
import com.mibot.ui.components.BarIconButton
import com.mibot.ui.components.ButtonKind
import com.mibot.ui.components.ButtonSize
import com.mibot.ui.components.EmptyNote
import com.mibot.ui.components.FooterNote
import com.mibot.ui.components.GroupCard
import com.mibot.ui.components.GroupLabel
import com.mibot.ui.components.ListRow
import com.mibot.ui.components.MikaButton
import com.mibot.ui.components.MikaSwitch
import com.mibot.ui.components.SegmentedControl
import com.mibot.ui.components.SheetAction
import com.mibot.ui.components.StateCard
import com.mibot.ui.components.SubPage
import com.mibot.ui.components.TileTone
import com.mibot.ui.theme.MikaIcons
import com.mibot.ui.theme.MikaTheme
import com.mibot.ui.theme.MikaType
import java.time.Instant
import com.mibot.i18n.uiLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 已保存的定时任务:分「进行中」「已停用」两段,每条三行(做什么、什么时候与下次、上次结果),右边开关。
 * 点一行升起操作面板，可查看执行记录。右上角可手动新建；保存后让调度器重新布置系统闹钟。
 */
@Composable
fun PlansScreen(context: Context, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    BackHandler(onBack = onBack)
    var snapshot by remember { mutableStateOf(TriggerRuntime.snapshot(context)) }
    var creating by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TriggerDefinition?>(null) }
    var copying by remember { mutableStateOf<TriggerDefinition?>(null) }
    var deleting by remember { mutableStateOf<TriggerDefinition?>(null) }
    var acting by remember { mutableStateOf<TriggerDefinition?>(null) }
    var viewingHistory by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    // 从系统设置回来时重读:刚开了权限,提示就该消失
    var exact by remember { mutableStateOf(TriggerRuntime.canScheduleExactly(context)) }
    LifecycleResumeEffect(Unit) {
        exact = TriggerRuntime.canScheduleExactly(context)
        onPauseOrDispose {}
    }
    fun refresh() { snapshot = TriggerRuntime.snapshot(context) }
    fun act(block: () -> Unit = {}): Boolean =
        runCatching { block(); refresh(); error = null }
            .onFailure { error = it.message ?: tr(R.string.op_failed) }
            .isSuccess

    val (running, stopped) = snapshot.triggers.sortedBy { it.nextAtMs }.partition { it.enabled }
    val shown = if (segment == 0) running else stopped

    SubPage(
        tr(R.string.drawer_plans), onBack,
        right = {
            BarIconButton(MikaIcons.Plus, tr(R.string.plans_add), {
                creating = ConversationStore.shared(context).newConversation()
            })
        },
    ) {
        if (!exact) {
            StateCard(
                MikaIcons.Alarm, ExactAlarmGuide.TITLE, tr(R.string.plans_alarm_body),
                tone = TileTone.Warn,
            ) { MikaButton(tr(R.string.turn_on_go), { act { context.startActivity(TriggerRuntime.exactAlarmSettings(context)) } }, kind = ButtonKind.Primary, size = ButtonSize.Small) }
        }
        SegmentedControl(
            listOf(tr(R.string.plans_running_n, running.size), tr(R.string.plans_stopped_n, stopped.size)), segment, { segment = it },
            tags = listOf("plans-segment-running", "plans-segment-stopped"),
        )
        error?.let { Text(it, Modifier.padding(top = 8.dp), style = MikaType.Caption, color = MikaTheme.colors.bad) }
        if (shown.isEmpty()) {
            GroupLabel("")
            EmptyNote(
                if (snapshot.triggers.isEmpty()) tr(R.string.plans_empty)
                else if (segment == 0) tr(R.string.plans_none_running) else tr(R.string.plans_none_stopped),
            )
        } else {
            GroupLabel(trPlural(R.plurals.settings_count_tasks, shown.size))
            GroupCard {
                shown.forEachIndexed { i, plan ->
                    val last = snapshot.windows.filter { it.triggerId == plan.id }.maxByOrNull { it.dueAtMs }
                    PlanRow(plan, last, first = i == 0, canToggle = plan.canToggle(snapshot.windows),
                        onClick = { acting = plan }, onToggle = { on -> act { TriggerRuntime.setEnabled(context, plan.id, on) } })
                }
            }
        }
        FooterNote(tr(R.string.plans_footer))
    }

    acting?.let { plan ->
        ActionSheet(
            plan.reminderText ?: plan.goal,
            listOf(
                SheetAction(tr(R.string.plans_open_chat)) { onOpenConversation(plan.threadId) },
                SheetAction(tr(R.string.plans_run_history)) {
                    if (act()) viewingHistory = plan.id
                },
                SheetAction(tr(R.string.plans_run_now)) { act { TriggerRuntime.runNow(context, plan.id) } },
                SheetAction(tr(R.string.plans_edit_time)) { editing = plan },
                SheetAction(tr(R.string.plans_copy)) { copying = plan },
                SheetAction(tr(R.string.confirm_delete), destructive = true) { deleting = plan },
            ),
            onDismiss = { acting = null },
        )
    }

    viewingHistory?.let { id ->
        PlanRunHistoryDialog(snapshot, id, onDismiss = { viewingHistory = null })
    }

    creating?.let { threadId ->
        PlanEditorDialog(
            original = null,
            newThreadId = threadId,
            onDismiss = { creating = null },
            onSave = { plan ->
                if (act { TriggerRuntime.schedule(context, plan) }) creating = null
            },
        )
    }

    copying?.let { plan ->
        val draft = remember(plan.id) { plan.toCopyPlanDraft(System.currentTimeMillis()) }
        PlanEditorDialog(
            original = null,
            newThreadId = plan.threadId,
            onDismiss = { copying = null },
            onSave = { copy ->
                if (act { TriggerRuntime.schedule(context, copy) }) {
                    segment = if (copy.enabled) 0 else 1
                    copying = null
                }
            },
            initialDraft = draft,
        )
    }

    editing?.let { plan ->
        PlanEditorDialog(
            original = plan,
            newThreadId = plan.threadId,
            onDismiss = { editing = null },
            onSave = { updated ->
                if (act { TriggerRuntime.schedule(context, updated, replaceExisting = true) }) editing = null
            },
        )
    }
    deleting?.let { plan ->
        MikaDialog(onDismissRequest = { deleting = null }, title = { Text(tr(R.string.plans_delete_title)) },
            text = { Text(plan.goal) },
            confirmButton = { TextButton(onClick = { act { TriggerRuntime.remove(context, plan.id) }; deleting = null }) {
                Text(tr(R.string.confirm_delete), color = MikaTheme.colors.bad)
            } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr(R.string.cancel)) } })
    }
}

@Composable
private fun PlanRunHistoryDialog(snapshot: TriggerSnapshot, planId: String, onDismiss: () -> Unit) {
    val windows = remember(snapshot, planId) { snapshot.recentWindows(planId, 10) }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.plans_run_history)) },
        text = {
            if (windows.isEmpty()) {
                Text(tr(R.string.plans_never_run))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    windows.forEachIndexed { index, window ->
                        ListRow(
                            icon = null,
                            title = formatPlanTime(window.claimedAtMs),
                            subtitle = windowLabel(window),
                            first = index == 0,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr(R.string.ok_got_it)) } },
    )
}

@Composable
private fun PlanRow(plan: TriggerDefinition, last: TriggerWindow?, first: Boolean, canToggle: Boolean,
    onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val status = when {
        plan.enabled -> tr(R.string.drawer_running)
        last?.state == TriggerWindowState.Delivered -> tr(R.string.plans_done)
        last?.state == TriggerWindowState.Skipped -> tr(R.string.plans_checked)
        last?.state == TriggerWindowState.Failed -> tr(R.string.step_failed)
        last?.state == TriggerWindowState.Unknown -> tr(R.string.plans_result_pending)
        else -> tr(R.string.chat_paused)
    }
    val repeat = when {
        plan.calendarTime != null -> {
            val days = if (plan.calendarDays.isEmpty()) tr(R.string.plans_every_day) else plan.calendarDays.joinToString(tr(R.string.list_sep)) {
                if (it in 1..7) java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, uiLocale()) else "?"
            }
            "$days ${plan.calendarClocks().joinToString(tr(R.string.list_sep))} (${plan.calendarZone})"
        }
        plan.intervalMs != null -> trPlural(R.plurals.plans_every_minutes, (plan.intervalMs!! / 60_000).toInt())
        else -> tr(R.string.plans_once)
    }
    val when_ = if (plan.enabled) tr(R.string.plans_next, repeat, formatPlanTime(plan.nextAtMs)) else "$repeat · $status"
    val subtitle = plan.conditionGoal?.let {
        tr(if (plan.notifyWhen == ConditionNotify.EachTimeMet) R.string.plans_condition_each else R.string.plans_condition, it, when_)
    } ?: when_
    val sourceSubtitle = "${planSourceLabel(plan.source)} · $subtitle"
    val c = MikaTheme.colors
    val delivered = last?.state == TriggerWindowState.Delivered
    ListRow(
        null, plan.reminderText ?: plan.goal, sourceSubtitle, first = first,
        detail = last?.let { lastResult(plan, it) } ?: tr(R.string.plans_never_run),
        detailColor = if (delivered) c.ok else c.ink3,
        onClick = onClick,
    ) { MikaSwitch(plan.enabled, onToggle, enabled = canToggle) }
}

private val PLAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private fun formatPlanTime(ms: Long): String = PLAN_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** 计划是谁建的,列表里标出来源好追溯。 */
private fun planSourceLabel(source: TriggerSource): String = when (source) {
    TriggerSource.User -> tr(R.string.plans_source_user)
    TriggerSource.Model -> tr(R.string.plans_source_model)
    TriggerSource.External -> tr(R.string.plans_source_external)
}
/** 上次结果:时间、结果;那次检查记下了值时一起显示(检查失败、结果未知的那一次没有值,不拿更早的充数)。 */
private fun lastResult(plan: TriggerDefinition, w: TriggerWindow): String {
    val value = plan.lastValue?.takeIf { w.state in setOf(TriggerWindowState.Skipped, TriggerWindowState.ActionDispatched, TriggerWindowState.Delivered) }
    return if (value != null) tr(R.string.plans_last_value, formatPlanTime(w.dueAtMs), windowLabel(w), value)
    else tr(R.string.plans_last, formatPlanTime(w.dueAtMs), windowLabel(w))
}

private fun windowLabel(w: TriggerWindow): String = when (w.state) {
    TriggerWindowState.Delivered -> tr(R.string.plans_delivered)
    TriggerWindowState.Skipped -> when (w.reason) {
        CONDITION_NOT_MET -> tr(R.string.plans_not_met)
        CONDITION_COOLDOWN -> tr(R.string.plans_cooldown)
        CONDITION_PLAN_EDITED -> tr(R.string.trace_cancelled)
        else -> tr(R.string.plans_unchanged)
    }
    TriggerWindowState.Failed -> tr(R.string.step_failed)
    TriggerWindowState.Unknown -> tr(R.string.plans_result_pending)
    TriggerWindowState.Claimed, TriggerWindowState.Dispatched, TriggerWindowState.ActionDispatched -> tr(R.string.plans_running_now)
}
