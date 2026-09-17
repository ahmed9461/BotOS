package com.ahmed9461.botos.model

object CoreChecks {
    fun run(): Int {
        var count = 0
        fun expect(condition: Boolean) { check(condition) { "Check ${count + 1} failed" }; count++ }
        fun rejects(action: () -> Unit) = runCatching(action).isFailure
        expect(BotNames.normalize(" @Sample_Bot ") == "sample_bot")
        expect(BotNames.normalize("https://t.me/Sample_Bot") == "sample_bot")
        expect(BotNames.normalize("https://evil.example/Sample_Bot") == null)
        expect(BotNames.normalize("https://t.me@evil.example/Sample_Bot") == null)
        expect(BotNames.normalize("https://t.me/Sample_Bot?start=secret") == null)
        expect(BotNames.normalize("javascript:alert(1)") == null)
        expect(BotNames.normalize("../../passwd") == null)
        expect(BotNames.normalize("@a") == null)
        expect(BotNames.normalize("@" + "a".repeat(33)) == null)
        expect(BotNames.normalize("@حساب") == null)
        expect(!BotNames.validTitle(" \n "))
        expect(!BotNames.validTitle("a".repeat(41)))
        expect(BotNames.validTitle("مكتبتي"))
        val bot = SavedBot("id-1", "sample_bot", "مكتبتي | My library")
        expect(WorkspaceCodec.decodeBots(WorkspaceCodec.encodeBots(listOf(bot))) == listOf(bot))
        expect(WorkspaceCodec.decodeBots(WorkspaceCodec.encodeBots(emptyList())).isEmpty())
        expect(BotNames.isDuplicate(listOf(bot), "SAMPLE_BOT"))
        expect(!BotNames.isDuplicate(listOf(bot), "sample_bot", bot.id))
        expect(rejects { WorkspaceCodec.encodeBots(listOf(bot, bot)) })
        expect(rejects { WorkspaceCodec.decodeBots("2\n") })
        expect(rejects { WorkspaceCodec.decodeBots("1\nmalformed") })
        val a = ChatKey("account-a", "bot-a")
        val b = ChatKey("account-a", "bot-b")
        val button = BotButton("open", "افتح", ActionPayload.Callback("opaque"))
        val message = BotMessage(1, a, 1, listOf(Block.Buttons("actions", listOf(button))))
        val first = MessageTimeline(a).upsert(message)
        val ticket = ActionTicket(a, 1, 1, "open")
        expect(first.messages.size == 1)
        expect(first.resolve(ticket) == button)
        expect(first.upsert(message).messages.size == 1)
        expect(first.upsert(message.copy(chat = b)) == first)
        expect(first.resolve(ticket.copy(chat = b)) == null)
        expect(first.resolve(ticket.copy(chat = ChatKey("account-b", "bot-a"))) == null)
        val edited = first.upsert(message.copy(revision = 2, blocks = listOf(Block.Paragraph("body", "تغيرت الرسالة"))))
        expect(edited.messages.size == 1)
        expect(edited.resolve(ticket) == null)
        expect(edited.upsert(message) == edited)
        expect(first.delete(1).resolve(ticket) == null)
        expect(first.resolve(ticket.copy(buttonId = "missing")) == null)
        val disabled = MessageTimeline(a).upsert(message.copy(blocks = listOf(Block.Buttons("a", listOf(button.copy(enabled = false))))))
        expect(disabled.resolve(ticket) == null)
        val duplicate = MessageTimeline(a).upsert(message.copy(blocks = listOf(Block.Buttons("a", listOf(button, button)))))
        expect(duplicate.resolve(ticket) == null)
        val nested = MessageTimeline(a).upsert(message.copy(blocks = listOf(Block.Details("d", "details", message.blocks))))
        expect(nested.resolve(ticket) == button)
        var deep = message.blocks
        repeat(20) { deep = listOf(Block.Details("d$it", "details", deep)) }
        expect(MessageTimeline(a).upsert(message.copy(blocks = deep)).resolve(ticket) == null)
        var bounded = MessageTimeline(a)
        repeat(250) { bounded = bounded.upsert(message.copy(id = it.toLong())) }
        expect(bounded.messages.size == ContentLimits.MAX_MESSAGES)
        expect(bounded.resolve(ticket) == null)
        return count
    }
}
fun main() { println("${CoreChecks.run()} core checks passed") }
