package dev.bikram.remember.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun hasCalendarPermission(): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED

        suspend fun getCalendars(): List<CalendarInfo> =
            withContext(Dispatchers.IO) {
                if (!hasCalendarPermission()) return@withContext emptyList()

                val projection =
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.OWNER_ACCOUNT,
                        CalendarContract.Calendars.CALENDAR_COLOR,
                        CalendarContract.Calendars.IS_PRIMARY,
                    )

                val uri = CalendarContract.Calendars.CONTENT_URI
                val calendars = mutableListOf<CalendarInfo>()

                try {
                    val cursor: Cursor? =
                        context.contentResolver.query(
                            uri,
                            projection,
                            null,
                            null,
                            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
                        )

                    cursor?.use {
                        val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                        val nameIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        val accountIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                        val ownerIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)
                        val colorIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                        val primaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                        while (it.moveToNext()) {
                            val id = it.getLong(idIdx)
                            val name = it.getString(nameIdx) ?: "Calendar $id"
                            val account = it.getString(accountIdx) ?: ""
                            val owner = it.getString(ownerIdx) ?: account
                            val color = it.getInt(colorIdx)
                            val isPrimary = if (primaryIdx >= 0) it.getInt(primaryIdx) == 1 else false

                            calendars.add(
                                CalendarInfo(
                                    id = id,
                                    displayName = name,
                                    accountName = account,
                                    ownerAccount = owner,
                                    color = color,
                                    isPrimary = isPrimary,
                                ),
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    return@withContext emptyList()
                } catch (_: Throwable) {
                    return@withContext emptyList()
                }

                calendars
            }

        suspend fun getEventsForRange(
            startMillis: Long,
            endMillis: Long,
            calendarIds: Set<Long>,
        ): List<CalendarEvent> =
            withContext(Dispatchers.IO) {
                if (!hasCalendarPermission()) return@withContext emptyList()

                val projection =
                    arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.CALENDAR_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.DESCRIPTION,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                        CalendarContract.Instances.EVENT_LOCATION,
                        CalendarContract.Instances.DISPLAY_COLOR,
                    )

                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, startMillis)
                ContentUris.appendId(builder, endMillis)
                val uri = builder.build()

                val selection: String?
                val selectionArgs: Array<String>?
                if (calendarIds.isNotEmpty()) {
                    val placeholders = calendarIds.joinToString(",") { "?" }
                    selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
                    selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
                } else {
                    selection = null
                    selectionArgs = null
                }

                val events = mutableListOf<CalendarEvent>()
                try {
                    val cursor =
                        context.contentResolver.query(
                            uri,
                            projection,
                            selection,
                            selectionArgs,
                            "${CalendarContract.Instances.BEGIN} ASC",
                        )

                    cursor?.use {
                        val eventIdIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                        val calIdIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                        val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                        val descIdx = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                        val beginIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                        val endIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
                        val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                        val locIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                        val colorIdx = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

                        while (it.moveToNext()) {
                            val eventId = it.getLong(eventIdIdx)
                            val calId = it.getLong(calIdIdx)
                            val title = it.getString(titleIdx) ?: "(Bez naslova)"
                            val desc = if (descIdx >= 0) it.getString(descIdx) ?: "" else ""
                            val begin = it.getLong(beginIdx)
                            val end = it.getLong(endIdx)
                            val allDay = it.getInt(allDayIdx) != 0
                            val loc = if (locIdx >= 0) it.getString(locIdx) ?: "" else ""
                            val color = if (colorIdx >= 0 && !it.isNull(colorIdx)) it.getInt(colorIdx) else null

                            events.add(
                                CalendarEvent(
                                    id = eventId,
                                    calendarId = calId,
                                    title = title,
                                    description = desc,
                                    startMillis = begin,
                                    endMillis = end,
                                    isAllDay = allDay,
                                    location = loc,
                                    color = color,
                                ),
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    return@withContext emptyList()
                } catch (_: Throwable) {
                    return@withContext emptyList()
                }

                events
            }
    }
