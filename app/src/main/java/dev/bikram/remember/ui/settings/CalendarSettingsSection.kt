package dev.bikram.remember.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.calendar.AgendaNotificationManager
import dev.bikram.remember.calendar.CalendarInfo
import dev.bikram.remember.calendar.CalendarPrefs
import dev.bikram.remember.calendar.CalendarRepository
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.appClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun CalendarSettingsSection(
    calendarPrefs: CalendarPrefs,
    calendarRepository: CalendarRepository,
    agendaNotificationManager: AgendaNotificationManager,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefState by calendarPrefs.state.collectAsStateWithLifecycle(
        initialValue = dev.bikram.remember.calendar.CalendarPreferencesState(),
    )
    var hasPermission by remember { mutableStateOf(calendarRepository.hasCalendarPermission()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                scope.launch {
                    calendars = calendarRepository.getCalendars()
                    agendaNotificationManager.updateNotification()
                }
            }
        }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            calendars = calendarRepository.getCalendars()
            // If user hasn't explicitly saved a calendar selection yet, default to all calendars selected
            if (prefState.selectedCalendarIds.isEmpty() && calendars.isNotEmpty()) {
                val allIds = calendars.map { it.id.toString() }.toSet()
                calendarPrefs.setSelectedCalendarIds(allIds)
            }
        }
    }

    GroupedListColumn(modifier = modifier) {
        // Toggle 1: Persistent Agenda Notification
        GroupedListItem(position = GroupPosition.FIRST) {
            SettingsToggleRow(
                materialSymbolName = "event",
                title = stringResource(R.string.settings_agenda_notification_toggle),
                subtitle = stringResource(R.string.settings_agenda_notification_toggle_desc),
                checked = prefState.agendaNotificationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        calendarPrefs.setAgendaNotificationEnabled(enabled)
                        if (enabled) {
                            agendaNotificationManager.updateNotification()
                        } else {
                            agendaNotificationManager.cancelNotification()
                        }
                    }
                },
            )
        }

        // Toggle 2: 5-Day summary
        GroupedListItem(position = if (!hasPermission || calendars.isEmpty()) GroupPosition.LAST else GroupPosition.MIDDLE) {
            SettingsToggleRow(
                materialSymbolName = "calendar_clock",
                title = stringResource(R.string.settings_agenda_5day_summary),
                subtitle = stringResource(R.string.settings_agenda_5day_summary_desc),
                checked = prefState.show5DaySummary,
                onCheckedChange = { show ->
                    scope.launch {
                        calendarPrefs.setShow5DaySummary(show)
                        agendaNotificationManager.updateNotification()
                    }
                },
            )
        }

        // Permission Card (if not granted)
        if (!hasPermission) {
            GroupedListItem(position = GroupPosition.LAST) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "event_busy",
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_calendar_permission_required),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.settings_calendar_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        RememberOutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                        ) {
                            Text(stringResource(R.string.settings_grant_permission))
                        }
                    }
                }
            }
        } else if (calendars.isEmpty()) {
            GroupedListItem(position = GroupPosition.LAST) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "event",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.settings_no_calendars_found),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "Ako koristite emulator ili uređaj bez prijavljenog Google računa, dodajte račun u postavkama uređaja za sinkronizaciju kalendara. Vaši zadaci iz aplikacije i dalje se redovito prikazuju u obavijesti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RememberOutlinedButton(
                        onClick = {
                            scope.launch {
                                calendars = calendarRepository.getCalendars()
                                agendaNotificationManager.updateNotification()
                            }
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Osvježi kalendare")
                    }
                }
            }
        } else {
            // Calendar picker list
            calendars.forEachIndexed { index, calendar ->
                val isSelected = calendar.id.toString() in prefState.selectedCalendarIds
                val position =
                    if (index == calendars.lastIndex) GroupPosition.LAST else GroupPosition.MIDDLE

                GroupedListItem(position = position) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .appClickable {
                                    scope.launch {
                                        val currentIds = prefState.selectedCalendarIds.toMutableSet()
                                        val calIdStr = calendar.id.toString()
                                        if (calIdStr in currentIds) {
                                            currentIds.remove(calIdStr)
                                        } else {
                                            currentIds.add(calIdStr)
                                        }
                                        calendarPrefs.setSelectedCalendarIds(currentIds)
                                        agendaNotificationManager.updateNotification()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(calendar.color)),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                calendar.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (calendar.accountName.isNotBlank() && calendar.accountName != calendar.displayName) {
                                Text(
                                    calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        RememberSwitch(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val currentIds = prefState.selectedCalendarIds.toMutableSet()
                                    val calIdStr = calendar.id.toString()
                                    if (checked) {
                                        currentIds.add(calIdStr)
                                    } else {
                                        currentIds.remove(calIdStr)
                                    }
                                    calendarPrefs.setSelectedCalendarIds(currentIds)
                                    agendaNotificationManager.updateNotification()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
