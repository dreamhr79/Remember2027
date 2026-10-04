package dev.bikram.remember.calendar

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarPreferencesState(
    val agendaNotificationEnabled: Boolean = true,
    val selectedCalendarIds: Set<String> = emptySet(),
    val show5DaySummary: Boolean = true,
    val currentDayOffset: Int = 0,
)

private val Context.calendarDataStore by preferencesDataStore(name = "calendar_prefs")

@Singleton
class CalendarPrefs
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val AGENDA_ENABLED = booleanPreferencesKey("agenda_notification_enabled")
            val SELECTED_CALENDARS = stringSetPreferencesKey("selected_calendar_ids")
            val SHOW_5DAY_SUMMARY = booleanPreferencesKey("show_5day_summary")
            val CURRENT_DAY_OFFSET = intPreferencesKey("current_day_offset")
        }

        val state: Flow<CalendarPreferencesState> =
            context.calendarDataStore.data.map { prefs ->
                CalendarPreferencesState(
                    agendaNotificationEnabled = prefs[Keys.AGENDA_ENABLED] ?: true,
                    selectedCalendarIds = prefs[Keys.SELECTED_CALENDARS] ?: emptySet(),
                    show5DaySummary = prefs[Keys.SHOW_5DAY_SUMMARY] ?: true,
                    currentDayOffset = prefs[Keys.CURRENT_DAY_OFFSET] ?: 0,
                )
            }

        suspend fun snapshot(): CalendarPreferencesState = state.first()

        suspend fun setAgendaNotificationEnabled(enabled: Boolean) {
            context.calendarDataStore.edit { prefs ->
                prefs[Keys.AGENDA_ENABLED] = enabled
            }
        }

        suspend fun setSelectedCalendarIds(ids: Set<String>) {
            context.calendarDataStore.edit { prefs ->
                prefs[Keys.SELECTED_CALENDARS] = ids
            }
        }

        suspend fun setShow5DaySummary(show: Boolean) {
            context.calendarDataStore.edit { prefs ->
                prefs[Keys.SHOW_5DAY_SUMMARY] = show
            }
        }

        suspend fun setCurrentDayOffset(offset: Int) {
            context.calendarDataStore.edit { prefs ->
                prefs[Keys.CURRENT_DAY_OFFSET] = offset
            }
        }
    }
