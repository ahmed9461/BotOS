package com.ahmed9461.botos.model

/** A displayed attachment is authorized by a particular message revision, not just a file ID. */
data class MediaReference(val chat: ChatKey, val messageId: Long, val revision: Long, val blockId: String) {
    override fun toString() = "MediaReference([redacted])"
}

fun messageMediaIndex(timeline: MessageTimeline?): Map<MediaReference, MediaInfo> {
    timeline ?: return emptyMap()
    val result = linkedMapOf<MediaReference, MediaInfo>()
    val duplicates = mutableSetOf<MediaReference>()
    var remaining = 2048
    timeline.messages.takeLast(ContentLimits.MAX_MESSAGES).forEach { message ->
        if (message.chat != timeline.chat || message.id == Long.MIN_VALUE) return@forEach
        fun visit(blocks: List<Block>, depth: Int) {
            if (depth >= ContentLimits.MAX_DEPTH) return
            for (block in blocks) {
                if (remaining-- <= 0) return
                when (block) {
                    is Block.Media -> {
                        val ref = MediaReference(message.chat, message.id, message.revision, block.id)
                        if (result.put(ref, block.info) != null) duplicates.add(ref)
                    }
                    is Block.Details -> visit(block.children, depth + 1)
                    is Block.RichDetails -> visit(block.children, depth + 1)
                    is Block.RichQuote -> visit(block.children, depth + 1)
                    is Block.Gallery -> visit(block.children, depth + 1)
                    is Block.RichList -> block.items.take(100).forEach { visit(it.blocks, depth + 1) }
                    else -> Unit
                }
            }
        }
        visit(message.blocks, 0)
    }
    return result - duplicates
}
