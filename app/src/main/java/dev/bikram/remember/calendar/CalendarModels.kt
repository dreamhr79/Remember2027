package dev.bikram.remember.calendar

import androidx.compose.runtime.Immutable

@Immutable
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val ownerAccount: String,
    val color: Int,
    val isPrimary: Boolean,
)

@Immutable
data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String,
    val color: Int?,
)

data class DayAgendaItem(
    val isEvent: Boolean,
    val title: String,
    val subtitle: String = "",
    val timeLabel: String = "",
    val noteId: Long? = null,
    val isCompleted: Boolean = false,
)

data class DayAgenda(
    val dayOffset: Int,
    val dayStartMillis: Long,
    val dayEndMillis: Long,
    val dayTitle: String,
    val items: List<DayAgendaItem>,
    val eventCount: Int,
    val taskCount: Int,
    val totalFiveDayCount: Int,
    val fiveDayEventCount: Int,
    val fiveDayTaskCount: Int,
)
