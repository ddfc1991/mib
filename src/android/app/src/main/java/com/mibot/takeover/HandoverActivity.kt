package com.mibot.takeover

/** 交回那一刻用户在主屏上做过的事:操作次数和停在哪个页面(取不到为 null)。 */
data class HandoverActivity(val inputCount: Int, val stoppedAt: String?)
