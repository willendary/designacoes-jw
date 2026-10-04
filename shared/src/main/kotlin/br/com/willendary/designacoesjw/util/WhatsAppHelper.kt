package br.com.willendary.designacoesjw.util

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

object WhatsAppHelper {

    const val DEFAULT_SINGLE_TEMPLATE =
        "Olá, {nome}! Você foi designado para *{privilegio}* na reunião de *{data}* ({diaSemana}).\nPor favor, confirme o recebimento desta mensagem."

    const val DEFAULT_MEETING_TEMPLATE =
        "📋 *Designações — {tipo}*\n📅 Data: *{data}* ({diaSemana})\n\n{designacoes}\n\nPor favor, confirmem o recebimento."

    fun buildSingleMessage(
        template: String?,
        brother: Brother,
        privilege: Privilege,
        meeting: Meeting
    ): String {
        val tmpl = if (template.isNullOrBlank()) DEFAULT_SINGLE_TEMPLATE else template
        val date = AssignmentGenerator.parseDate(meeting.date)
        val weekday = if (date != LocalDate.MIN) {
            Datas.diaDaSemana(date)
        } else ""

        return tmpl
            .replace("{nome}", brother.name)
            .replace("{privilegio}", privilege.name)
            .replace("{data}", meeting.date)
            .replace("{diaSemana}", weekday)
            .replace("{tipo}", meeting.type)
    }

    fun buildMeetingBroadcastMessage(
        template: String?,
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        missingPrivileges: List<Privilege> = emptyList()
    ): String {
        val tmpl = if (template.isNullOrBlank()) DEFAULT_MEETING_TEMPLATE else template
        val date = AssignmentGenerator.parseDate(meeting.date)
        val weekday = if (date != LocalDate.MIN) {
            Datas.diaDaSemana(date)
        } else ""

        val assignmentsText = meeting.assignments.joinToString("\n") { a ->
            val p = privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
            val b = brothers.find { it.id == a.brotherId }?.name ?: "Irmão"
            "• *$p:* $b"
        }

        val missingText = if (missingPrivileges.isNotEmpty()) {
            "\n\n⚠ *Pendências:* " + missingPrivileges.joinToString { it.name }
        } else ""

        return tmpl
            .replace("{data}", meeting.date)
            .replace("{diaSemana}", weekday)
            .replace("{tipo}", meeting.type)
            .replace("{designacoes}", assignmentsText + missingText)
    }

    fun buildPublicTalkSpeakerMessage(
        talk: br.com.willendary.designacoesjw.data.PublicTalk,
        hospitalityBrotherName: String = "",
        hospitalityBrotherPhone: String = "",
        congregationName: String = ""
    ): String {
        val date = AssignmentGenerator.parseDate(talk.date)
        val weekday = if (date != LocalDate.MIN) {
            Datas.diaDaSemana(date)
        } else ""

        val congPart = if (congregationName.isNotBlank()) " da Congregação *$congregationName*" else ""
        val themePart = if (talk.themeNumber != null) "Nº ${talk.themeNumber} — \"${talk.themeTitle}\"" else "\"${talk.themeTitle}\""
        val hospPart = if (hospitalityBrotherName.isNotBlank()) {
            val phonePart = if (hospitalityBrotherPhone.isNotBlank()) " (WhatsApp: $hospitalityBrotherPhone)" else ""
            "\n🍽 *Hospitalidade/Refeição:* Irmão $hospitalityBrotherName$phonePart"
        } else ""
        val notesPart = if (talk.hospitalityNotes.isNotBlank()) "\n📝 *Obs:* ${talk.hospitalityNotes}" else ""

        return "🎤 *Discurso Público — Confirmação*\n\n" +
            "Olá, irmão *${talk.speakerName}*! Tudo bem?\n" +
            "Confirmamos com alegria a sua visita como orador público$congPart.\n\n" +
            "📅 *Data:* ${talk.date} ($weekday)\n" +
            "📖 *Tema:* $themePart$hospPart$notesPart\n\n" +
            "Por favor, confirme se está tudo certo para a data. Ficamos à disposição!"
    }

    fun buildCleaningScheduleMessage(
        schedule: br.com.willendary.designacoesjw.data.CleaningSchedule,
        group: br.com.willendary.designacoesjw.data.FieldServiceGroup?,
        overseerName: String = "",
        overseerPhone: String = ""
    ): String {
        val groupName = group?.name ?: "Grupo Designado"
        val overseerPart = if (overseerName.isNotBlank()) {
            val phonePart = if (overseerPhone.isNotBlank()) " ($overseerPhone)" else ""
            "\n👤 *Dirigente:* $overseerName$phonePart"
        } else ""
        val detailsPart = if (schedule.details.isNotBlank()) "\n📋 *Detalhes:* ${schedule.details}" else ""

        return "🧹 *Escala de Limpeza do Salão do Reino*\n\n" +
            "📅 *Semana:* ${schedule.weekDate}\n" +
            "👥 *Responsável:* *$groupName*$overseerPart$detailsPart\n\n" +
            "Agradecemos muito pelo amor e zelo de todos ao cuidarem da casa de Jeová!"
    }

    fun buildWebLink(phone: String, text: String): String {
        val cleanPhone = phone.filter { it.isDigit() }
        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        return if (cleanPhone.isNotBlank()) {
            "https://web.whatsapp.com/send?phone=$cleanPhone&text=$encodedText"
        } else {
            "https://web.whatsapp.com/send?text=$encodedText"
        }
    }

    fun buildUniversalLink(phone: String, text: String): String {
        val cleanPhone = phone.filter { it.isDigit() }
        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        return if (cleanPhone.isNotBlank()) {
            "https://wa.me/$cleanPhone?text=$encodedText"
        } else {
            "https://wa.me/?text=$encodedText"
        }
    }
}

