package com.mibot.task

import com.mibot.core.run.Channel
import com.mibot.core.run.ChannelInput
import com.mibot.core.run.ChannelInputSink
import com.mibot.core.run.ChannelPeer
import com.mibot.core.run.Delivery
import com.mibot.core.run.SendOutcome

/** 手机上的任务卡片和通知：任务服务仍绘制现有界面，此适配器承接送达账本。 */
class LocalChannel(private val deliverLocal: suspend (ChannelPeer, Delivery) -> SendOutcome) : Channel {
    override val id = ID
    override val confirms = true
    private var sink: ChannelInputSink? = null

    override fun start(sink: ChannelInputSink): AutoCloseable {
        this.sink = sink
        return AutoCloseable { if (this.sink === sink) this.sink = null }
    }

    override suspend fun send(peer: ChannelPeer, delivery: Delivery): SendOutcome =
        if (delivery is Delivery.Image) SendOutcome.Failed else deliverLocal(peer, delivery)
    suspend fun receive(input: ChannelInput) { sink?.receive(input) }

    companion object { const val ID = "local" }
}
