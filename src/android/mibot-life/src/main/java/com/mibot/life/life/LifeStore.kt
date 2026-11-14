package com.mibot.life.life

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 生活状态持久化（org.json 手动序列化，不引额外依赖）。
 * 关键：状态是「数据」，重启读盘恢复 —— 不依赖模型上下文（重启不忘）。
 */
class LifeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("mibot_life_state", Context.MODE_PRIVATE)

    fun load(): LifeStateData {
        val raw = prefs.getString(KEY, null) ?: return LifeStateData()
        return runCatching { parse(JSONObject(raw)) }.getOrElse { LifeStateData() }
    }

    fun save(state: LifeStateData) {
        prefs.edit().putString(KEY, dump(state).toString()).apply()
    }

    private fun dump(s: LifeStateData): JSONObject = JSONObject().apply {
        put("version", s.version)
        put("coin", s.coin)
        put("goals", JSONArray().apply { s.goals.forEach { put(goalJson(it)) } })
        put("curiosity", JSONArray().apply { s.curiosityGoals.forEach { put(curiosityJson(it)) } })
        put("works", JSONArray().apply { s.works.forEach { put(workJson(it)) } })
        put("episodes", JSONArray().apply { s.episodes.forEach { put(episodeJson(it)) } })
        put("projects", JSONArray().apply { s.projects.forEach { put(projectJson(it)) } })
        put("history", JSONArray().apply { s.history.takeLast(200).forEach { put(choiceJson(it)) } })
        put("interests", JSONArray().apply { s.interests.forEach { put(interestJson(it)) } })
        put("affect", JSONObject().apply {
            put("pleasure", s.affect.pleasure); put("energy", s.affect.energy)
            put("care", s.affect.care); put("frustration", s.affect.frustration)
            put("updatedAt", s.affect.updatedAt)
        })
        put("cares", JSONArray().apply { s.cares.forEach { put(careJson(it)) } })
        put("rhythm", JSONObject().apply {
            put("enabled", s.rhythm.enabled)
            put("morningStart", s.rhythm.morningStart)
            put("dayStart", s.rhythm.dayStart)
            put("eveningStart", s.rhythm.eveningStart)
            put("nightStart", s.rhythm.nightStart)
            put("writingOnlyInNight", s.rhythm.writingOnlyInNight)
        })
        put("contact", JSONObject().apply {
            put("dailyLimit", s.contact.dailyLimit)
            put("minIntervalMin", s.contact.minIntervalMin)
            put("lastContactAt", s.contact.lastContactAt)
            put("usedToday", s.contact.usedToday)
            put("unanswered", s.contact.unanswered)
        })
        put("xp", JSONObject().apply { s.levelXp.forEach { (k, v) -> put(k, v) } })
    }

    private fun parse(o: JSONObject): LifeStateData = LifeStateData(
        version = o.optInt("version", 1),
        coin = o.optInt("coin", 0),
        goals = o.optJSONArray("goals").mapArr(::goalFrom),
        curiosityGoals = o.optJSONArray("curiosity").mapArr(::curiosityFrom),
        works = o.optJSONArray("works").mapArr(::workFrom),
        episodes = o.optJSONArray("episodes").mapArr(::episodeFrom),
        projects = o.optJSONArray("projects").mapArr(::projectFrom),
        history = o.optJSONArray("history").mapArr(::choiceFrom),
        interests = o.optJSONArray("interests").mapArr(::interestFrom),
        affect = o.optJSONObject("affect")?.let {
            AffectState(
                pleasure = it.optDouble("pleasure", 0.0),
                energy = it.optDouble("energy", 0.5),
                care = it.optDouble("care", 0.0),
                frustration = it.optDouble("frustration", 0.0),
                updatedAt = it.optLong("updatedAt", System.currentTimeMillis()),
            )
        } ?: AffectState(),
        cares = o.optJSONArray("cares").mapArr(::careFrom),
        rhythm = o.optJSONObject("rhythm")?.let {
            RhythmConfig(
                enabled = it.optBoolean("enabled", false),
                morningStart = it.optInt("morningStart", 8),
                dayStart = it.optInt("dayStart", 10),
                eveningStart = it.optInt("eveningStart", 22),
                nightStart = it.optInt("nightStart", 23),
                writingOnlyInNight = it.optBoolean("writingOnlyInNight", true),
            )
        } ?: RhythmConfig(),
        contact = o.optJSONObject("contact")?.let {
            ContactBudget(
                dailyLimit = it.optInt("dailyLimit", 1),
                minIntervalMin = it.optInt("minIntervalMin", 120),
                lastContactAt = it.optLong("lastContactAt", 0),
                usedToday = it.optInt("usedToday", 0),
                unanswered = it.optInt("unanswered", 0),
            )
        } ?: ContactBudget(),
        levelXp = o.optJSONObject("xp")?.let { xp ->
            xp.keys().asSequence().associateWith { xp.optInt(it, 0) }
        } ?: emptyMap(),
    )

    private fun <T> JSONArray?.mapArr(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(f) }
    }

    private fun goalJson(g: LifeGoal) = JSONObject().apply {
        put("id", g.id); put("title", g.title); put("status", g.status.name)
        put("updatedAt", g.updatedAt); put("createdAt", g.createdAt)
        put("curiosity", g.curiosity)
    }

    private fun goalFrom(o: JSONObject) = LifeGoal(
        id = o.optString("id"), title = o.optString("title"),
        status = runCatching { GoalStatus.valueOf(o.optString("status")) }.getOrElse { GoalStatus.READY },
        updatedAt = o.optLong("updatedAt"), createdAt = o.optLong("createdAt"),
        curiosity = o.optBoolean("curiosity"),
    )

    private fun curiosityJson(g: CuriosityGoal) = JSONObject().apply {
        put("id", g.id); put("title", g.title); put("status", g.status.name)
        put("updatedAt", g.updatedAt); put("unfruitful", g.unfruitfulAttempts)
    }

    private fun curiosityFrom(o: JSONObject) = CuriosityGoal(
        id = o.optString("id"), title = o.optString("title"),
        status = runCatching { GoalStatus.valueOf(o.optString("status")) }.getOrElse { GoalStatus.ACTIVE },
        updatedAt = o.optLong("updatedAt"), unfruitfulAttempts = o.optInt("unfruitful"),
    )

    private fun workJson(w: LifeWork) = JSONObject().apply {
        put("id", w.id); put("goalId", w.goalId); put("title", w.title)
        put("phase", w.phase.name); put("content", w.content)
        put("version", w.version); put("updatedAt", w.updatedAt)
    }

    private fun workFrom(o: JSONObject) = LifeWork(
        id = o.optString("id"), goalId = o.optString("goalId"), title = o.optString("title"),
        phase = runCatching { WorkPhase.valueOf(o.optString("phase")) }.getOrElse { WorkPhase.WRITE },
        content = o.optString("content"), version = o.optInt("version", 1),
        updatedAt = o.optLong("updatedAt"),
    )

    private fun episodeJson(e: LifeEpisode) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("title", e.title)
        put("detail", e.detail); put("occurredAt", e.occurredAt)
    }

    private fun episodeFrom(o: JSONObject) = LifeEpisode(
        id = o.optString("id"),
        kind = runCatching { EpisodeKind.valueOf(o.optString("kind")) }.getOrElse { EpisodeKind.NOTE },
        title = o.optString("title"), detail = o.optString("detail"),
        occurredAt = o.optLong("occurredAt"),
    )

    private fun projectJson(p: LifeProject) = JSONObject().apply {
        put("id", p.id); put("title", p.title); put("goalId", p.goalId)
        put("stage", p.stage); put("maxStages", p.maxStages)
        put("lastReviewAt", p.lastReviewAt); put("insight", p.insight)
        put("limitation", p.limitation); put("nextQuestion", p.nextQuestion)
    }

    private fun projectFrom(o: JSONObject) = LifeProject(
        id = o.optString("id"), title = o.optString("title"), goalId = o.optString("goalId"),
        stage = o.optInt("stage", 1), maxStages = o.optInt("maxStages", 3),
        lastReviewAt = o.optLong("lastReviewAt"), insight = o.optString("insight"),
        limitation = o.optString("limitation"), nextQuestion = o.optString("nextQuestion"),
    )

    private fun choiceJson(c: LifeChoice) = JSONObject().apply {
        put("action", c.action.name); put("key", c.key); put("reason", c.reason)
        put("score", c.score); put("at", c.at); put("topic", c.topic)
        put("isCuriosity", c.isCuriosity)
        c.runStatus?.let { put("runStatus", it.name) }
    }

    private fun choiceFrom(o: JSONObject) = LifeChoice(
        action = runCatching { LifeAction.valueOf(o.optString("action")) }.getOrElse { LifeAction.REST },
        key = o.optString("key"), reason = o.optString("reason"),
        score = o.optDouble("score", 0.0), at = o.optLong("at"),
        topic = o.optString("topic"), isCuriosity = o.optBoolean("isCuriosity"),
        runStatus = o.optString("runStatus").takeIf { it.isNotEmpty() }?.let {
            runCatching { RunStatus.valueOf(it) }.getOrNull()
        },
    )

    private fun interestJson(i: Interest) = JSONObject().apply {
        put("topic", i.topic); put("domain", i.domain); put("strength", i.strength)
        put("count", i.count); put("lastAt", i.lastAt)
    }

    private fun interestFrom(o: JSONObject) = Interest(
        topic = o.optString("topic"), domain = o.optString("domain"),
        strength = o.optDouble("strength", 0.0), count = o.optInt("count", 0),
        lastAt = o.optLong("lastAt", 0),
    )

    private fun careJson(c: CareItem) = JSONObject().apply {
        put("id", c.id); put("text", c.text); put("source", c.source)
        put("createdAt", c.createdAt); put("dueAt", c.dueAt); put("resolved", c.resolved)
    }

    private fun careFrom(o: JSONObject) = CareItem(
        id = o.optString("id"), text = o.optString("text"),
        source = o.optString("source", "user"),
        createdAt = o.optLong("createdAt"), dueAt = o.optLong("dueAt"),
        resolved = o.optBoolean("resolved"),
    )

    companion object {
        private const val KEY = "state_json"
    }
}