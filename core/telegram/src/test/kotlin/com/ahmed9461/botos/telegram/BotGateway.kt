package com.ahmed9461.botos.telegram

import com.ahmed9461.botos.model.*

/** The live TDLib implementation belongs behind this boundary; never use bot tokens here. */
interface BotGateway {
    val isPreview: Boolean
    fun initial(chat: ChatKey): BotMessage
    fun activate(message: BotMessage, button: BotButton): BotMessage?
}

/** Synthetic content only. No network, login, personal data or claim of a real bot response. */
class PreviewGateway : BotGateway {
    override val isPreview = true
    override fun initial(chat: ChatKey) = home(chat, 1)
    override fun activate(message: BotMessage, button: BotButton): BotMessage? {
        if (message.chat.account != "preview") return null
        val payload = button.payload as? ActionPayload.Callback ?: return null
        val blocks = when (payload.opaqueData) {
            "preview:home" -> return home(message.chat, message.revision + 1)
            "preview:report" -> listOf(
                Block.Heading("title", "كل شيء، بوضوح"),
                Block.Paragraph("body", "هذا تقرير تجريبي لعرض الجداول داخل المساحة."),
                Block.Table("table", listOf("القسم", "العناصر"), listOf(listOf("مكتبتي", "12"), listOf("المفضلة", "8"), listOf("الأرشيف", "24"))),
                Block.Details("details", "تفاصيل أكثر", listOf(Block.Quote("quote", "مساحة أقل ازدحامًا، وتركيز أكبر على ما يهمك."), Block.Bullets("list", listOf("تبويبات بأسمائك", "مظهر يناسبك", "مكان واحد لبوتاتك")))),
                back(),
            )
            "preview:format" -> listOf(
                Block.Heading("title", "رسالة بأكثر من شكل"),
                Block.Quote("quote", "تصميم هادئ، ومحتوى واضح."),
                Block.Paragraph("body", "النص العربي يبقى مقروءًا، والمحتوى الإنجليزي يحتفظ باتجاهه."),
                Block.Code("code", "Hello, BotOS!\nA space for your bots."),
                Block.Details("fold", "افتح هذه المساحة", listOf(Block.Paragraph("nested", "يمكن فتح التفاصيل وإغلاقها دون مغادرة الرسالة."))),
                back(),
            )
            else -> return null
        }
        return message.copy(revision = message.revision + 1, blocks = blocks)
    }
    private fun home(chat: ChatKey, revision: Long): BotMessage {
        require(chat.account == "preview") { "Preview gateway requires a preview account" }
        return BotMessage(1, chat, revision, listOf(
            Block.Heading("title", "أهلًا في مساحتك"),
            Block.Paragraph("intro", "كل بوت له مكانه. جرّب الأزرار، وافتح التفاصيل، واستكشف المظهر الذي يناسبك."),
            Block.Buttons("actions", listOf(
                BotButton("report", "استعراض تقرير", ActionPayload.Callback("preview:report")),
                BotButton("format", "أشكال الرسائل", ActionPayload.Callback("preview:format")),
            )),
        ))
    }
    private fun back() = Block.Buttons("back-row", listOf(BotButton("back", "العودة", ActionPayload.Callback("preview:home"))))
}
