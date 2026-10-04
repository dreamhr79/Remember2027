package dev.bikram.remember.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.notifications.canPostNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val calendarRepository: CalendarRepository,
        private val calendarPrefs: CalendarPrefs,
        private val noteRepository: NoteRepository,
    ) {
        companion object {
            const val NOTIFICATION_ID = 999901
            const val CHANNEL_ID = "channel_daily_agenda"

            const val ACTION_PREV_DAY = "dev.bikram.remember.calendar.ACTION_PREV_DAY"
            const val ACTION_NEXT_DAY = "dev.bikram.remember.calendar.ACTION_NEXT_DAY"
            const val ACTION_TODAY = "dev.bikram.remember.calendar.ACTION_TODAY"
            const val ACTION_COMPLETE_TASK = "dev.bikram.remember.calendar.ACTION_COMPLETE_TASK"
            const val ACTION_REFRESH_AGENDA = "dev.bikram.remember.calendar.ACTION_REFRESH_AGENDA"

            const val EXTRA_NOTE_ID = "extra_note_id"
        }

        init {
            createNotificationChannel()
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.agenda_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.agenda_channel_desc)
                        setShowBadge(false)
                        enableVibration(false)
                        enableLights(false)
                    }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        suspend fun updateNotification(targetOffset: Int? = null) {
            withContext(Dispatchers.IO) {
                val prefs = calendarPrefs.snapshot()
                if (!prefs.agendaNotificationEnabled) {
                    cancelNotification()
                    return@withContext
                }

                if (!canPostNotifications(context)) {
                    return@withContext
                }

                val offset = targetOffset ?: prefs.currentDayOffset
                if (targetOffset != null && targetOffset != prefs.currentDayOffset) {
                    calendarPrefs.setCurrentDayOffset(targetOffset)
                }

                val selectedCalIds = prefs.selectedCalendarIds.mapNotNull { it.toLongOrNull() }.toSet()

                // Calculate Day Range for offset
                val targetCal =
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                val dayStart = targetCal.timeInMillis
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
                val dayEnd = targetCal.timeInMillis - 1

                // 5-day horizon calculation
                val horizonCal =
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                val fiveDayStart = horizonCal.timeInMillis
                horizonCal.add(Calendar.DAY_OF_YEAR, 5)
                val fiveDayEnd = horizonCal.timeInMillis - 1

                // Fetch Events
                val dayEvents = calendarRepository.getEventsForRange(dayStart, dayEnd, selectedCalIds)
                val fiveDayEvents = calendarRepository.getEventsForRange(fiveDayStart, fiveDayEnd, selectedCalIds)

                // Fetch Notes/Tasks with reminders
                val activeNotes = noteRepository.activeReminderNotes().filter { !it.note.trashed && !it.note.archived }

                val dayTasks =
                    activeNotes.filter { noteWithItems ->
                        val reminder = noteWithItems.note.reminderAt
                        reminder != null && reminder in dayStart..dayEnd
                    }

                val fiveDayTasks =
                    activeNotes.filter { noteWithItems ->
                        val reminder = noteWithItems.note.reminderAt
                        reminder != null && reminder in fiveDayStart..fiveDayEnd && noteWithItems.note.completedAt == null
                    }

                // Incomplete task eligible for "Complete" quick action
                val firstIncompleteTask = dayTasks.firstOrNull { it.note.completedAt == null }

                // Build title
                val dayLabel =
                    when (offset) {
                        0 -> context.getString(R.string.agenda_notification_today)
                        1 -> context.getString(R.string.agenda_notification_tomorrow)
                        2 -> context.getString(R.string.agenda_notification_day_after)
                        -1 -> context.getString(R.string.agenda_notification_yesterday)
                        else -> {
                            val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                            sdf.format(c.time).replaceFirstChar { it.uppercase() }
                        }
                    }

                val dateFormatted =
                    SimpleDateFormat("d. MMMM", Locale.getDefault()).format(dayStart)
                val totalDayCount = dayEvents.size + dayTasks.size
                val notificationTitle = "📅 $dayLabel, $dateFormatted ($totalDayCount)"

                // Build Body Text
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val bodyLines = mutableListOf<String>()

                if (dayEvents.isNotEmpty()) {
                    for (event in dayEvents) {
                        val timeStr =
                            if (event.isAllDay) {
                                context.getString(R.string.agenda_all_day_prefix)
                            } else {
                                "${timeFormat.format(event.startMillis)} - ${timeFormat.format(event.endMillis)}"
                            }
                        bodyLines.add("🕒 $timeStr: ${event.title}")
                    }
                }

                if (dayTasks.isNotEmpty()) {
                    for (taskWithItems in dayTasks) {
                        val note = taskWithItems.note
                        val isDone = note.completedAt != null
                        val statusSymbol = if (isDone) "☑" else "☐"
                        val timeStr = note.reminderAt?.let { " (${timeFormat.format(it)})" } ?: ""
                        val title = note.title.ifBlank {
                            if (note.kind == NoteKind.LIST) "Popis zadataka" else "Zabilješka"
                        }
                        bodyLines.add("$statusSymbol $title$timeStr")
                    }
                }

                if (bodyLines.isEmpty()) {
                    bodyLines.add(context.getString(R.string.agenda_no_events_or_tasks))
                }

                if (prefs.show5DaySummary) {
                    val fiveDayTotal = fiveDayEvents.size + fiveDayTasks.size
                    bodyLines.add("")
                    bodyLines.add(
                        context.getString(
                            R.string.agenda_5day_summary_format,
                            fiveDayTotal,
                            fiveDayEvents.size,
                            fiveDayTasks.size,
                        ),
                    )
                }

                val fullText = bodyLines.joinToString("\n")
                val shortSummary =
                    if (totalDayCount > 0) {
                        "${dayEvents.size} događaja, ${dayTasks.size} zadataka"
                    } else {
                        context.getString(R.string.agenda_no_events_or_tasks)
                    }

                // Intents & Actions
                val openAppIntent =
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                val openAppPendingIntent =
                    PendingIntent.getActivity(
                        context,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val prevIntent =
                    Intent(context, AgendaNotificationReceiver::class.java).apply {
                        action = ACTION_PREV_DAY
                    }
                val prevPendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        1,
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val nextIntent =
                    Intent(context, AgendaNotificationReceiver::class.java).apply {
                        action = ACTION_NEXT_DAY
                    }
                val nextPendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        2,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val builder =
                    NotificationCompat
                        .Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_remember)
                        .setContentTitle(notificationTitle)
                        .setContentText(shortSummary)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
                        .setContentIntent(openAppPendingIntent)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setSilent(true)
                        .setShowWhen(false)
                        .setOnlyAlertOnce(true)

                // Add navigation actions
                builder.addAction(
                    0,
                    context.getString(R.string.agenda_notification_prev_day),
                    prevPendingIntent,
                )
                builder.addAction(
                    0,
                    context.getString(R.string.agenda_notification_next_day),
                    nextPendingIntent,
                )

                if (offset != 0) {
                    val todayIntent =
                        Intent(context, AgendaNotificationReceiver::class.java).apply {
                            action = ACTION_TODAY
                        }
                    val todayPendingIntent =
                        PendingIntent.getBroadcast(
                            context,
                            3,
                            todayIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    builder.addAction(
                        0,
                        context.getString(R.string.agenda_notification_action_today),
                        todayPendingIntent,
                    )
                }

                if (firstIncompleteTask != null) {
                    val completeIntent =
                        Intent(context, AgendaNotificationReceiver::class.java).apply {
                            action = ACTION_COMPLETE_TASK
                            putExtra(EXTRA_NOTE_ID, firstIncompleteTask.note.id)
                        }
                    val completePendingIntent =
                        PendingIntent.getBroadcast(
                            context,
                            4,
                            completeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    val taskTitle = firstIncompleteTask.note.title.take(15)
                    val actionLabel =
                        if (taskTitle.isNotBlank()) "✓ Riješi: $taskTitle" else context.getString(R.string.agenda_notification_complete_task)
                    builder.addAction(0, actionLabel, completePendingIntent)
                }

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            }
        }

        fun cancelNotification() {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
