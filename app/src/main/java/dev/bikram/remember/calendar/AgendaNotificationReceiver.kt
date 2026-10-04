package dev.bikram.remember.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgendaNotificationReceiver : BroadcastReceiver() {
    @Inject lateinit var agendaNotificationManager: AgendaNotificationManager
    @Inject lateinit var calendarPrefs: CalendarPrefs
    @Inject lateinit var noteRepository: NoteRepository

    @ApplicationScope
    @Inject lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        applicationScope.launch {
            try {
                when (action) {
                    AgendaNotificationManager.ACTION_PREV_DAY -> {
                        val current = calendarPrefs.snapshot().currentDayOffset
                        val newOffset = current - 1
                        calendarPrefs.setCurrentDayOffset(newOffset)
                        agendaNotificationManager.updateNotification(newOffset)
                    }
                    AgendaNotificationManager.ACTION_NEXT_DAY -> {
                        val current = calendarPrefs.snapshot().currentDayOffset
                        val newOffset = current + 1
                        calendarPrefs.setCurrentDayOffset(newOffset)
                        agendaNotificationManager.updateNotification(newOffset)
                    }
                    AgendaNotificationManager.ACTION_TODAY -> {
                        calendarPrefs.setCurrentDayOffset(0)
                        agendaNotificationManager.updateNotification(0)
                    }
                    AgendaNotificationManager.ACTION_COMPLETE_TASK -> {
                        val noteId = intent.getLongExtra(AgendaNotificationManager.EXTRA_NOTE_ID, -1L)
                        if (noteId > 0) {
                            noteRepository.markCompleted(noteId)
                        }
                        agendaNotificationManager.updateNotification()
                    }
                    AgendaNotificationManager.ACTION_REFRESH_AGENDA -> {
                        agendaNotificationManager.updateNotification()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
