package com.maeilmail.app.reminder

import com.maeilmail.app.data.Progress
import com.maeilmail.app.ui.Format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 첫 화면 맨 위와 위젯에 보일 오늘의 질문.
 * 알림 시각이 지났으면 오늘 질문, 아직이거나 쉬는 날이면 그 앞에 받은 질문과 오늘 질문이 없는 까닭(idle)을 함께 준다.
 */
data class Today(
    /** 보일 질문 ID (아직 받은 질문이 없으면 null) */
    val id: String?,
    /** 질문 위에 붙일 이름. 예: 오늘의 질문, 금요일에 받은 질문 */
    val label: String,
    /** id가 오늘 질문인지 (아니면 지난 질문) */
    val isToday: Boolean,
    /** 오늘 질문을 보일 수 없는 까닭 (보일 수 있으면 null) */
    val idle: Idle?,
) {
    /** rest: 쉬는 날이라 없는 것인지 (알림 시각 전이거나 발송 기록이 없는 것과 가른다) */
    data class Idle(val title: String, val body: String? = null, val rest: Boolean = false)

    companion object {
        const val LABEL = "오늘의 질문"

        /** progress: 받아 둔 발송 기록, now·time: 한국 시간, lastNotified: 마지막으로 알린 발송일 (순수 함수, 테스트 대상) */
        fun of(progress: Progress?, now: LocalDateTime, time: LocalTime, lastNotified: LocalDate?): Today {
            if (progress == null) {
                return Today(null, LABEL, false, Idle("오늘의 질문을 아직 받지 못했어요", "인터넷에 연결되면 받아 와요"))
            }
            val date = now.toLocalDate()
            val todayId = progress.questionOn(date)
            if (todayId != null && Reminders.isRevealed(date, now, time, lastNotified)) return Today(todayId, LABEL, true, null)

            val last = progress.lastBefore(date)
            val idle = when {
                todayId != null -> Idle("오늘의 질문은 ${Format.time(time)}에 열려요")
                // 앞으로 받을 질문을 모른다: 쉬는 날이 아니라 발송 기록을 오래 받지 못한 것이다
                progress.isStale(date) -> Idle("다음 질문을 아직 받지 못했어요", "목록을 아래로 당기면 새로 받아요")
                else -> Idle(
                    "오늘은 쉬는 날이에요",
                    progress.nextDateAfter(date)?.let { "다음 질문은 ${Format.day(it)} ${Format.time(time)}에 열려요" },
                    rest = true,
                )
            }
            return Today(last?.id, last?.date?.let { pastLabel(it, date) } ?: "지난 질문", false, idle)
        }

        /** 지난 질문의 이름: 일주일 안이면 요일, 더 앞이면 날짜 */
        private fun pastLabel(received: LocalDate, today: LocalDate): String =
            if (received.isAfter(today.minusDays(7))) "${Format.weekday(received)}에 받은 질문" else "${Format.day(received)}에 받은 질문"
    }
}
