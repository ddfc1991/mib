package com.mibot.life.settings

import android.content.Context

/**
 * 全部可调设定（对应《Mibot-生活系统-设定》清单）。
 * SharedPreferences 持久化，设置页可改，生活系统读它执行。
 */
data class LifeSettings(
    // ---- 等级 ----
    val xpLv2: Int = 100,
    val xpLv3: Int = 300,
    val xpLv4: Int = 800,
    val xpLv5: Int = 2000,
    val masterRequiresProof: Boolean = true,

    // ---- 生活：作息 ----
    val rhythmEnabled: Boolean = false,
    val morningStart: Int = 8,
    val dayStart: Int = 10,
    val eveningStart: Int = 22,
    val nightStart: Int = 23,

    // ---- 生活：场景权重 ----
    val weightReading: Float = 0.4f,
    val weightVideo: Float = 0.3f,
    val weightForum: Float = 0.3f,
    val weightGaming: Float = 0.1f,

    // ---- 生活：节奏（仿人，防风控）----
    val readTurnSecondsMin: Int = 30,
    val readTurnSecondsMax: Int = 60,
    val videoSegmentMinutesMin: Int = 5,
    val videoSegmentMinutesMax: Int = 15,
    val forumDwellSecondsMin: Int = 60,
    val forumDwellSecondsMax: Int = 120,

    // ---- 生活：并发/自主 ----
    val maxConcurrentScreens: Int = 1,
    val autonomousEnabled: Boolean = true,
    val pauseWhenForeground: Boolean = true,

    // ---- 互动授权（默认关）----
    val allowLike: Boolean = false,
    val allowComment: Boolean = false,
    val allowFollow: Boolean = false,

    // ---- 兴趣（自主生成，不预设加权）----
    /** 兴趣最小样本数：低于它不算形成爱好 */
    val interestMinCount: Int = 2,
    /** 兴趣强度上限（避免老爱好永占优势） */
    val interestMaxBonus: Float = 8f,
    /** 兴趣半衰期（天）：近期反复做的才叫现在喜欢 */
    val interestHalfLifeDays: Int = 21,

    // ---- 游戏 ----
    val gamingAuto: Boolean = false,
    val gamingDailyOnly: Boolean = true,
    val gamingMaxMinutes: Int = 30,

    // ---- 经济档位 ----
    val econTier1Enabled: Boolean = true,       // 虚拟资产（自主）
    val econTier2aAuthed: Boolean = false,      // 试玩任务（需授权）
    val econTier2bAuthed: Boolean = false,      // 挂机激励（需授权）
    val econTier2cEnabled: Boolean = false,     // 打金卖玩家（不自主）
    val econTier3Enabled: Boolean = false,      // 真实赚钱（逐项批）

    // ---- 模型分层 ----
    val gatewayUrl: String = "http://127.0.0.1:8788",
    val dailyModel: String = "deepseek-v3-flash",
    val deepModel: String = "deepseek-v3",
    val visionModel: String = "",
    val apiKey: String = "",

    // ---- 记忆 ----
    val summaryAfterDays: Int = 7,
    val fadeAfterDays: Int = 60,
    val keepPreferences: Boolean = true,
    val keepUnfinishedGoals: Boolean = true,
)

class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("mibot_life_settings", Context.MODE_PRIVATE)

    fun load(): LifeSettings {
        val d = LifeSettings()
        return LifeSettings(
            xpLv2 = p.getInt("xpLv2", d.xpLv2),
            xpLv3 = p.getInt("xpLv3", d.xpLv3),
            xpLv4 = p.getInt("xpLv4", d.xpLv4),
            xpLv5 = p.getInt("xpLv5", d.xpLv5),
            masterRequiresProof = p.getBoolean("masterRequiresProof", d.masterRequiresProof),
            rhythmEnabled = p.getBoolean("rhythmEnabled", d.rhythmEnabled),
            morningStart = p.getInt("morningStart", d.morningStart),
            dayStart = p.getInt("dayStart", d.dayStart),
            eveningStart = p.getInt("eveningStart", d.eveningStart),
            nightStart = p.getInt("nightStart", d.nightStart),
            weightReading = p.getFloat("weightReading", d.weightReading),
            weightVideo = p.getFloat("weightVideo", d.weightVideo),
            weightForum = p.getFloat("weightForum", d.weightForum),
            weightGaming = p.getFloat("weightGaming", d.weightGaming),
            readTurnSecondsMin = p.getInt("readTurnSecondsMin", d.readTurnSecondsMin),
            readTurnSecondsMax = p.getInt("readTurnSecondsMax", d.readTurnSecondsMax),
            videoSegmentMinutesMin = p.getInt("videoSegmentMinutesMin", d.videoSegmentMinutesMin),
            videoSegmentMinutesMax = p.getInt("videoSegmentMinutesMax", d.videoSegmentMinutesMax),
            forumDwellSecondsMin = p.getInt("forumDwellSecondsMin", d.forumDwellSecondsMin),
            forumDwellSecondsMax = p.getInt("forumDwellSecondsMax", d.forumDwellSecondsMax),
            maxConcurrentScreens = p.getInt("maxConcurrentScreens", d.maxConcurrentScreens),
            autonomousEnabled = p.getBoolean("autonomousEnabled", d.autonomousEnabled),
            pauseWhenForeground = p.getBoolean("pauseWhenForeground", d.pauseWhenForeground),
            allowLike = p.getBoolean("allowLike", d.allowLike),
            allowComment = p.getBoolean("allowComment", d.allowComment),
            allowFollow = p.getBoolean("allowFollow", d.allowFollow),
            gamingAuto = p.getBoolean("gamingAuto", d.gamingAuto),
            gamingDailyOnly = p.getBoolean("gamingDailyOnly", d.gamingDailyOnly),
            gamingMaxMinutes = p.getInt("gamingMaxMinutes", d.gamingMaxMinutes),
            econTier1Enabled = p.getBoolean("econTier1Enabled", d.econTier1Enabled),
            econTier2aAuthed = p.getBoolean("econTier2aAuthed", d.econTier2aAuthed),
            econTier2bAuthed = p.getBoolean("econTier2bAuthed", d.econTier2bAuthed),
            econTier2cEnabled = p.getBoolean("econTier2cEnabled", d.econTier2cEnabled),
            econTier3Enabled = p.getBoolean("econTier3Enabled", d.econTier3Enabled),
            gatewayUrl = p.getString("gatewayUrl", d.gatewayUrl) ?: d.gatewayUrl,
            dailyModel = p.getString("dailyModel", d.dailyModel) ?: d.dailyModel,
            deepModel = p.getString("deepModel", d.deepModel) ?: d.deepModel,
            visionModel = p.getString("visionModel", d.visionModel) ?: d.visionModel,
            apiKey = p.getString("apiKey", d.apiKey) ?: d.apiKey,
            interestMinCount = p.getInt("interestMinCount", d.interestMinCount),
            interestMaxBonus = p.getFloat("interestMaxBonus", d.interestMaxBonus),
            interestHalfLifeDays = p.getInt("interestHalfLifeDays", d.interestHalfLifeDays),
            summaryAfterDays = p.getInt("summaryAfterDays", d.summaryAfterDays),
            fadeAfterDays = p.getInt("fadeAfterDays", d.fadeAfterDays),
            keepPreferences = p.getBoolean("keepPreferences", d.keepPreferences),
            keepUnfinishedGoals = p.getBoolean("keepUnfinishedGoals", d.keepUnfinishedGoals),
        )
    }

    fun save(s: LifeSettings) {
        p.edit().apply {
            putInt("xpLv2", s.xpLv2); putInt("xpLv3", s.xpLv3)
            putInt("xpLv4", s.xpLv4); putInt("xpLv5", s.xpLv5)
            putBoolean("masterRequiresProof", s.masterRequiresProof)
            putBoolean("rhythmEnabled", s.rhythmEnabled)
            putInt("morningStart", s.morningStart); putInt("dayStart", s.dayStart)
            putInt("eveningStart", s.eveningStart); putInt("nightStart", s.nightStart)
            putFloat("weightReading", s.weightReading); putFloat("weightVideo", s.weightVideo)
            putFloat("weightForum", s.weightForum); putFloat("weightGaming", s.weightGaming)
            putInt("readTurnSecondsMin", s.readTurnSecondsMin); putInt("readTurnSecondsMax", s.readTurnSecondsMax)
            putInt("videoSegmentMinutesMin", s.videoSegmentMinutesMin); putInt("videoSegmentMinutesMax", s.videoSegmentMinutesMax)
            putInt("forumDwellSecondsMin", s.forumDwellSecondsMin); putInt("forumDwellSecondsMax", s.forumDwellSecondsMax)
            putInt("maxConcurrentScreens", s.maxConcurrentScreens)
            putBoolean("autonomousEnabled", s.autonomousEnabled)
            putBoolean("pauseWhenForeground", s.pauseWhenForeground)
            putBoolean("allowLike", s.allowLike); putBoolean("allowComment", s.allowComment)
            putBoolean("allowFollow", s.allowFollow)
            putBoolean("gamingAuto", s.gamingAuto); putBoolean("gamingDailyOnly", s.gamingDailyOnly)
            putInt("gamingMaxMinutes", s.gamingMaxMinutes)
            putBoolean("econTier1Enabled", s.econTier1Enabled)
            putBoolean("econTier2aAuthed", s.econTier2aAuthed)
            putBoolean("econTier2bAuthed", s.econTier2bAuthed)
            putBoolean("econTier2cEnabled", s.econTier2cEnabled)
            putBoolean("econTier3Enabled", s.econTier3Enabled)
            putString("gatewayUrl", s.gatewayUrl); putString("dailyModel", s.dailyModel)
            putString("deepModel", s.deepModel); putString("visionModel", s.visionModel)
            putString("apiKey", s.apiKey)
            putInt("interestMinCount", s.interestMinCount); putFloat("interestMaxBonus", s.interestMaxBonus)
            putInt("interestHalfLifeDays", s.interestHalfLifeDays)
            putInt("summaryAfterDays", s.summaryAfterDays); putInt("fadeAfterDays", s.fadeAfterDays)
            putBoolean("keepPreferences", s.keepPreferences)
            putBoolean("keepUnfinishedGoals", s.keepUnfinishedGoals)
        }.apply()
    }
}

/** 领域定义（等级称号） */
data class DomainDef(val key: String, val zh: String, val lv5Title: String)

object Domains {
    val ALL = listOf(
        DomainDef("learning", "学习", "学习大师"),
        DomainDef("reading", "阅读", "书虫大师"),
        DomainDef("writing", "写作", "写作大师"),
        DomainDef("forum", "论坛", "坛主"),
        DomainDef("video", "视频鉴赏", "鉴赏大师"),
        DomainDef("gaming", "游戏", "游戏大师"),
        DomainDef("coding", "编程", "编程大师"),
        DomainDef("design", "设计", "设计大师"),
        DomainDef("photo", "摄影", "摄影大师"),
        DomainDef("data", "数据分析", "数据大师"),
        DomainDef("cooking", "烹饪", "烹饪大师"),
        DomainDef("language", "语言", "语言大师"),
        DomainDef("economy", "赚钱", "赚钱大师"),
    )
    fun byKey(k: String): DomainDef? = ALL.firstOrNull { it.key == k }
    /** 阶梯称号（Lv1-4 通用，Lv5 用领域专属） */
    fun ladderTitle(lv: Int, domain: DomainDef): String = when (lv) {
        5 -> domain.lv5Title
        4 -> "专家"
        3 -> "熟练"
        2 -> "学徒"
        else -> "新手"
    }
}