package com.healthdashboard.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthdashboard.mobile.WeightWidgetProvider
import com.healthdashboard.mobile.data.EntryDto
import com.healthdashboard.mobile.data.EntryRequest
import com.healthdashboard.mobile.data.HealthRepository
import com.healthdashboard.mobile.data.MetricDto
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val BeijingZoneOffset: ZoneOffset = ZoneOffset.ofHours(8)

@Composable
fun RecordsScreen(
    repository: HealthRepository,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val entries = remember { mutableStateListOf<EntryDto>() }
    val metrics = remember { mutableStateListOf<MetricDto>() }
    val values = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var selectedDateTime by remember { mutableStateOf(LocalDateTime.now(BeijingZoneOffset)) }
    var editingEntryId by remember { mutableStateOf<String?>(null) }

    val dateLabelFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val historyFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }

    suspend fun load() {
        loading = true
        runCatching {
            metrics.clear()
            metrics.addAll(repository.getMetrics().filter { it.active && it.type == "number" })
            entries.clear()
            entries.addAll(repository.getEntries())
        }.onFailure {
            snackbarHostState.showSnackbar(it.message ?: "加载失败")
        }
        loading = false
    }

    fun resetForm() {
        editingEntryId = null
        values.clear()
        note = ""
        selectedDateTime = LocalDateTime.now(BeijingZoneOffset)
    }

    fun openDateTimePicker() {
        val calendar = Calendar.getInstance().apply {
            set(selectedDateTime.year, selectedDateTime.monthValue - 1, selectedDateTime.dayOfMonth, selectedDateTime.hour, selectedDateTime.minute)
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        selectedDateTime = LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun applyEntryToForm(entry: EntryDto) {
        editingEntryId = entry.id
        values.clear()
        entry.values.forEach { (key, value) -> values[key] = value.toString() }
        note = entry.note
        selectedDateTime = OffsetDateTime.parse(entry.recorded_at).toLocalDateTime()
    }

    LaunchedEffect(Unit) {
        load()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppGradientHeader(
                    title = "体重记录",
                    subtitle = "只做体重、体脂等健康数据录入与编辑，图表和日历总览统一在数据页查看。",
                    action = {
                        TextButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Text("退出", modifier = androidx.compose.ui.Modifier.padding(start = 4.dp), color = Color.White)
                        }
                    },
                )
            }

            item {
                SectionCard(
                    title = if (editingEntryId == null) "新增体重记录" else "编辑体重记录",
                    subtitle = "默认当前时间，也可以手动改成历史时间。",
                ) {
                    OutlinedButton(
                        onClick = { openDateTimePicker() },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Text(
                            text = selectedDateTime.format(dateLabelFormatter),
                            modifier = androidx.compose.ui.Modifier.padding(start = 8.dp),
                        )
                    }
                    metrics.forEach { metric ->
                        OutlinedTextField(
                            value = values[metric.id].orEmpty(),
                            onValueChange = { values[metric.id] = it },
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            label = { Text("${metric.label} (${metric.unit})") },
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        label = { Text("备注") },
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val isNew = editingEntryId == null
                                val entryId = editingEntryId
                                val payload = EntryRequest(
                                    recorded_at = selectedDateTime.atOffset(BeijingZoneOffset).toString(),
                                    values = values
                                        .filterValues { it.isNotBlank() }
                                        .mapValues { (_, value) -> value.toDoubleOrNull() ?: value },
                                    note = note,
                                    tags = emptyList(),
                                )
                                val operation = entryId?.let { id ->
                                    runCatching { repository.updateEntry(id, payload) }
                                } ?: runCatching {
                                    repository.createEntry(payload)
                                }
                                operation
                                    .onSuccess {
                                        resetForm()
                                        load()
                                        WeightWidgetProvider.updateWidgets(
                                            context,
                                            android.appwidget.AppWidgetManager.getInstance(context),
                                            android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
                                                android.content.ComponentName(context, WeightWidgetProvider::class.java),
                                            ),
                                        )
                                        snackbarHostState.showSnackbar(if (isNew) "记录已保存" else "记录已更新")
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar(it.message ?: "保存失败")
                                    }
                            }
                        },
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                    ) {
                        Text(if (editingEntryId == null) "保存记录" else "更新记录")
                    }
                    if (editingEntryId != null) {
                        OutlinedButton(
                            onClick = { resetForm() },
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        ) {
                            Text("取消编辑")
                        }
                    }
                }
            }

            if (loading) {
                item {
                    SectionCard(title = "加载中") {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Text(
                        text = "历史记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    RecordHistoryCard(
                        entry = entry,
                        metrics = metrics,
                        formatter = historyFormatter,
                        onEdit = { applyEntryToForm(entry) },
                        onDelete = {
                            scope.launch {
                                runCatching { repository.deleteEntry(entry.id) }
                                    .onSuccess {
                                        if (editingEntryId == entry.id) resetForm()
                                        load()
                                        WeightWidgetProvider.updateWidgets(
                                            context,
                                            android.appwidget.AppWidgetManager.getInstance(context),
                                            android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
                                                android.content.ComponentName(context, WeightWidgetProvider::class.java),
                                            ),
                                        )
                                        snackbarHostState.showSnackbar("记录已删除")
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar(it.message ?: "删除失败")
                                    }
                            }
                        },
                    )
                }
            }

            item {
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecordHistoryCard(
    entry: EntryDto,
    metrics: List<MetricDto>,
    formatter: DateTimeFormatter,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val metricMap = metrics.associateBy { it.id }
    Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = androidx.compose.ui.Modifier.padding(18.dp)) {
            Text(formatEntryDateTime(entry.recorded_at, formatter), style = MaterialTheme.typography.titleMedium)
            Text(
                entry.values.entries.joinToString(" · ") {
                    val metric = metricMap[it.key]
                    val label = metric?.label ?: it.key
                    val unit = metric?.unit.orEmpty()
                    "$label: ${it.value}$unit"
                },
                modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.layout.Row(modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Text("编辑", modifier = androidx.compose.ui.Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text("删除", modifier = androidx.compose.ui.Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun formatEntryDateTime(
    recordedAt: String,
    formatter: DateTimeFormatter,
): String {
    return runCatching {
        OffsetDateTime.parse(recordedAt).withOffsetSameInstant(BeijingZoneOffset).format(formatter)
    }.getOrDefault(recordedAt)
}
