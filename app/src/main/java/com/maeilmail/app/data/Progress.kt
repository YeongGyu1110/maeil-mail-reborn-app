package com.maeilmail.app.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** 발송일은 한국 시간 기준이다 (웹 send_mail.py의 KST). 휴대폰 시간대가 달라도 같은 날짜를 쓴다 */
val KST: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * 웹 서비스 저장소의 progress.json (GitHub Actions의 send_mail.py가 쓴다).
 * sent: 보낸 질문 ID (보낸 순서), today: 가장 최근에 보낸 질문과 발송일,
 * next: 앞으로 보낼 질문 {발송일: ID}. 한 번 정한 질문은 보낼 때까지 바뀌지 않는다.
 */
data class Progress(
    val sent: List<String>,
    val today: Sent?,
    val next: Map<String, String>,
    /** next 필드가 있는 새 형식인지 (없으면 예전 형식) */
    val hasNext: Boolean,
) {
    data class Sent(val date: String, val id: String)

    /**
     * date에 받는(받은) 질문. 메일 발송 전이면 next에 정해 둔 질문, 발송 뒤면 today. 둘 다 아니면 쉬는 날이라 null.
     * 웹 index.html의 todayQuestionId와 같다 (예전 형식이면 마지막으로 보낸 질문).
     */
    fun questionOn(date: LocalDate): String? {
        val key = date.toString()
        next[key]?.let { return it }
        if (today?.date == key) return today.id
        if (!hasNext) return sent.lastOrNull()
        return null
    }

    /**
     * 메일로 받은 질문, 최근에 받은 것부터 (웹 index.html의 receivedIds와 같다).
     * todayId: 받은 질문에 넣을 오늘 질문. 앱은 알림 시각 전에는 오늘 질문을 보여주지 않으므로, 알림 시각이 지난 뒤에만 넘긴다.
     * known: 목차에 있는 질문 ID (목차에서 빠진 질문은 뺀다)
     */
    fun received(todayId: String?, known: Set<String>): List<String> {
        val ids = sent.toMutableList()
        if (todayId != null && todayId !in ids) ids.add(todayId)
        return ids.filter { it in known }.reversed().distinct()
    }

    /** date보다 뒤의 첫 발송일 (없으면 null) */
    fun nextDateAfter(date: LocalDate): LocalDate? =
        next.keys.map(LocalDate::parse).filter { it.isAfter(date) }.minOrNull()

    /** 알림을 걸 수 있는 날짜와 질문: next 전부와, 이미 보낸 today */
    fun scheduled(): Map<LocalDate, String> {
        val out = sortedMapOf<LocalDate, String>()
        today?.let { out[LocalDate.parse(it.date)] = it.id }
        next.forEach { (date, id) -> out[LocalDate.parse(date)] = id }
        return out
    }

    companion object {
        const val PATH = "progress.json"

        fun parse(json: String): Progress {
            val o = JSONObject(json)
            val sent = o.optJSONArray("sent")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val today = o.optJSONObject("today")?.let { Sent(it.getString("date"), it.getString("id")) }
            val nextObj = o.optJSONObject("next")
            val next = nextObj?.keys()?.asSequence()?.associateWith { nextObj.getString(it) }.orEmpty()
            return Progress(sent, today, next, hasNext = nextObj != null)
        }
    }
}
