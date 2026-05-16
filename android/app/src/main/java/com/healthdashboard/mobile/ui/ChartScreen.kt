package com.healthdashboard.mobile.ui

import android.app.DatePickerDialog
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.healthdashboard.mobile.data.DashboardDto
import com.healthdashboard.mobile.data.EntryDto
import com.healthdashboard.mobile.data.HealthRepository
import com.healthdashboard.mobile.data.TrendPointDto
import com.healthdashboard.mobile.data.WorkoutCalendarPointDto
import com.healthdashboard.mobile.data.WorkoutOverviewDto
import com.healthdashboard.mobile.data.WorkoutSessionDto
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private data class MobileTrendSpec(
    val metricId: String,
    val fallbackLabel: String,
    val fallbackUnit: String,
    val fallbackColor: String,
    val icon: ImageVector,
)

private const val BusinessDayStartHour = 6

private data class DataCalendarDay(
    val date: LocalDate,
    val entries: List<EntryDto>,
    val workoutSummary: WorkoutCalendarPointDto?,
)

private data class TrendSummary(val latest: Double?)

private val BeijingOffset: ZoneOffset = ZoneOffset.ofHours(8)
private val ChartDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val CalendarTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val DayDetailFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    repository: HealthRepository,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var dashboard by remember { mutableStateOf<DashboardDto?>(null) }
    var workoutOverview by remember { mutableStateOf<WorkoutOverviewDto?>(null) }
    var entries by remember { mutableStateOf<List<EntryDto>>(emptyList()) }
    var workoutCalendar by remember { mutableStateOf<List<WorkoutCalendarPointDto>>(emptyList()) }
    var selectedCalendarDay by remember { mutableStateOf<DataCalendarDay?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var endDate by remember { mutableStateOf(LocalDate.now(BeijingOffset)) }
    var startDate by remember { mutableStateOf(endDate.minusDays(13)) }
    var calendarYear by remember { mutableStateOf(endDate.year) }
    var calendarMonth by remember { mutableStateOf(endDate.monthValue) }

    suspend fun loadDashboard() {
        loading = true
        runCatching {
            dashboard = repository.getDashboard("year")
            workoutOverview = repository.getWorkoutOverview()
            entries = repository.getEntries()
            workoutCalendar = repository.getWorkoutCalendarSummary(400)
        }.onSuccess {
            error = null
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    fun openDatePicker(isStart: Boolean) {
        val target = if (isStart) startDate else endDate
        val calendar = Calendar.getInstance().apply {
            set(target.year, target.monthValue - 1, target.dayOfMonth)
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                if (isStart) {
                    startDate = if (picked.isAfter(endDate)) endDate else picked
                } else {
                    endDate = if (picked.isBefore(startDate)) startDate else picked
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    LaunchedEffect(Unit) {
        loadDashboard()
    }

    val calendarDays = remember(entries, workoutCalendar, calendarYear, calendarMonth) {
        buildMonthCalendar(entries, workoutCalendar, calendarYear, calendarMonth)
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppGradientHeader(
                    title = "数据总览",
                    subtitle = "趋势、日历、训练和洞察集中查看。",
                    action = {
                        TextButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Text("退出", modifier = Modifier.padding(start = 4.dp), color = ComposeColor.White)
                        }
                    },
                )
            }

            when {
                loading -> item {
                    SectionCard(title = "加载中") {
                        CircularProgressIndicator()
                    }
                }

                error != null -> item {
                    SectionCard(title = "加载失败") {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }

                dashboard != null -> {
                    val metricMap = dashboard!!.metrics.associateBy { it.id }
                    val filteredTrendMap = buildFilteredTrendMap(dashboard!!.trends, startDate, endDate)
                    val cardioDurationSeries = buildCardioDurationSeries(workoutOverview?.sessions.orEmpty(), startDate, endDate)
                    val weightSummary = summaryForSeries(filteredTrendMap["weight_kg"].orEmpty())
                    val bodyFatSummary = summaryForSeries(filteredTrendMap["body_fat_pct"].orEmpty())
                    val durationSummary = latestNonZeroSummary(cardioDurationSeries)
                    val bmiCurrent = dashboard!!.summaries["bmi"]?.latest
                    val trendSpecs = listOf(
                        MobileTrendSpec("weight_kg", "体重", "kg", "#155EEF", Icons.Rounded.MonitorWeight),
                        MobileTrendSpec("body_fat_pct", "体脂率", "%", "#DC2626", Icons.Rounded.Percent),
                        MobileTrendSpec("cardio_duration_min", "有氧时长", "分钟", "#0F766E", Icons.AutoMirrored.Rounded.DirectionsRun),
                    )

                    item {
                        SectionCard(
                            title = "日历总览",
                            subtitle = "左上蓝点代表体重数据，右上绿点代表训练数据。",
                        ) {
                            CalendarPickerRow(
                                selectedYear = calendarYear,
                                selectedMonth = calendarMonth,
                                availableYears = availableYears(entries, workoutCalendar),
                                onYearChange = { calendarYear = it },
                                onMonthChange = { calendarMonth = it },
                            )
                            DataCalendarView(days = calendarDays, onDayClick = { selectedCalendarDay = it })
                        }
                    }

                    item {
                        SectionCard(title = "自动洞察", subtitle = "健康趋势和训练建议合并展示。") {
                            val mergedInsights = buildList {
                                addAll(dashboard!!.insights)
                                addAll(workoutOverview?.recommendations.orEmpty())
                                if (weightSummary.latest != null && bmiCurrent != null) {
                                    add("当前体重 ${formatCompact(weightSummary.latest)}kg，BMI ${formatCompact(bmiCurrent)}")
                                }
                                durationSummary.latest?.let { add("当前区间最近一次有氧 ${formatCompact(it)} 分钟") }
                            }.distinct()
                            mergedInsights.forEach { InsightRow(text = it) }
                        }
                    }

                    items(trendSpecs) { spec ->
                        val series = if (spec.metricId == "cardio_duration_min") {
                            cardioDurationSeries
                        } else {
                            filteredTrendMap[spec.metricId].orEmpty()
                        }
                        val metric = metricMap[spec.metricId]
                        val title = if (spec.metricId == "weight_kg" && bmiCurrent != null) {
                            "${metric?.label ?: spec.fallbackLabel} · BMI ${formatCompact(bmiCurrent)}"
                        } else {
                            metric?.label ?: spec.fallbackLabel
                        }
                        val latestValue = when (spec.metricId) {
                            "weight_kg" -> weightSummary.latest
                            "body_fat_pct" -> bodyFatSummary.latest
                            "cardio_duration_min" -> durationSummary.latest
                            else -> null
                        }
                        TrendChartCard(
                            title = title,
                            subtitle = metric?.unit ?: spec.fallbackUnit,
                            series = series,
                            color = metric?.color ?: spec.fallbackColor,
                            icon = spec.icon,
                            latestValue = latestValue,
                            latestUnit = metric?.unit ?: spec.fallbackUnit,
                            startDate = startDate,
                            endDate = endDate,
                            onStartDateClick = { openDatePicker(true) },
                            onEndDateClick = { openDatePicker(false) },
                        )
                    }
                }
            }
        }
    }

    selectedCalendarDay?.let { day ->
        val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        AlertDialog(
            onDismissRequest = { selectedCalendarDay = null },
            confirmButton = { TextButton(onClick = { selectedCalendarDay = null }) { Text("关闭") } },
            title = {
                Column {
                    Text(day.date.format(DayDetailFormatter), style = MaterialTheme.typography.headlineSmall)
                    Text(weekday, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val weight = day.entries.firstNotNullOfOrNull { numericValue(it.values["weight_kg"]) }
                    val bodyFat = day.entries.firstNotNullOfOrNull { numericValue(it.values["body_fat_pct"]) }
                    if (weight != null || bodyFat != null) {
                        DetailPill(
                            background = ComposeColor(0xFFF5F8FF),
                            lines = buildList {
                                weight?.let { add("体重：${formatCompact(it)} kg") }
                                bodyFat?.let { add("体脂：${formatCompact(it)}%") }
                            },
                        )
                    }
                    day.workoutSummary?.let { workout ->
                        DetailPill(
                            background = ComposeColor(0xFFF4FBF7),
                            lines = listOf(
                                "${workoutTypeLabel(workout)}：训练 ${workout.session_count} 次，${workout.total_sets} 组，${workout.total_duration_minutes} 分钟",
                                "内容：${workout.parts.joinToString("、") { partLabel(it) }}",
                            ),
                        )
                    }
                    if (weight == null && bodyFat == null && day.workoutSummary == null) {
                        Text("当天没有可展示的数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPickerRow(
    selectedYear: Int,
    selectedMonth: Int,
    availableYears: List<Int>,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
) {
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExposedDropdownMenuBox(expanded = yearExpanded, onExpandedChange = { yearExpanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = "${selectedYear}年",
                onValueChange = {},
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                label = { Text("年份") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                availableYears.forEach { year ->
                    DropdownMenuItem(text = { Text("${year}年") }, onClick = {
                        yearExpanded = false
                        onYearChange(year)
                    })
                }
            }
        }
        ExposedDropdownMenuBox(expanded = monthExpanded, onExpandedChange = { monthExpanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = "${selectedMonth}月",
                onValueChange = {},
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                label = { Text("月份") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
            )
            ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                (1..12).forEach { month ->
                    DropdownMenuItem(text = { Text("${month}月") }, onClick = {
                        monthExpanded = false
                        onMonthChange(month)
                    })
                }
            }
        }
    }
}

@Composable
private fun DataCalendarView(days: List<DataCalendarDay>, onDayClick: (DataCalendarDay) -> Unit) {
    val weeks = days.chunked(7)
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = days.firstOrNull()?.date?.let { YearMonth.from(it).format(CalendarTitleFormatter) }.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekLabels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { day ->
                    DataCalendarDayCell(modifier = Modifier.weight(1f), day = day, onClick = { onDayClick(day) })
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun DataCalendarDayCell(modifier: Modifier = Modifier, day: DataCalendarDay, onClick: () -> Unit) {
    val hasEntry = day.entries.isNotEmpty()
    val hasWorkout = day.workoutSummary != null
    Card(
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFF8FAFC)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ComposeColor(0xFFFDFEFF), ComposeColor(0xFFF3F6FB))))
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Dot(visible = hasEntry, color = ComposeColor(0xFF155EEF))
                Dot(visible = hasWorkout, color = ComposeColor(0xFF0A7E5C))
            }
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Dot(visible: Boolean, color: ComposeColor) {
    Box(modifier = Modifier.size(8.dp).background(if (visible) color else ComposeColor.Transparent, CircleShape))
}

@Composable
private fun DetailPill(background: ComposeColor, lines: List<String>) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendChartCard(
    title: String,
    subtitle: String,
    series: List<TrendPointDto>,
    color: String,
    icon: ImageVector,
    latestValue: Double?,
    latestUnit: String,
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
) {
    SectionCard(title = title, subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartMetricHeader(icon = icon, latestValue = latestValue, latestUnit = latestUnit)
            ChartDateSelector(startDate = startDate, endDate = endDate, onStartDateClick = onStartDateClick, onEndDateClick = onEndDateClick)
            if (series.isEmpty()) {
                EmptyChartState()
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(248.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            description.isEnabled = false
                            setTouchEnabled(true)
                            setPinchZoom(true)
                            axisRight.isEnabled = false
                            legend.isEnabled = false
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            xAxis.granularity = 1f
                            xAxis.labelRotationAngle = -18f
                        }
                    },
                    update = { chart ->
                        val labels = series.map { formatChartDate(it.recorded_at) }
                        val parsedColor = runCatching { Color.parseColor(color) }.getOrDefault(Color.parseColor("#155EEF"))
                        val dataSet = LineDataSet(series.mapIndexed { index, point -> Entry(index.toFloat(), point.value.toFloat()) }, title).apply {
                            this.color = parsedColor
                            lineWidth = 3f
                            setCircleColor(parsedColor)
                            circleRadius = 3.5f
                            setDrawFilled(true)
                            fillAlpha = 35
                            fillColor = parsedColor
                            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                        }
                        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                        chart.axisLeft.axisMinimum = computeAxisMinimum(series)
                        chart.axisLeft.axisMaximum = computeAxisMaximum(series)
                        chart.data = LineData(dataSet).apply { setDrawValues(false) }
                        chart.marker = YOnlyMarker(chart.context) { index -> series.getOrNull(index)?.value?.let { formatCompact(it) } ?: "--" }
                        chart.invalidate()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChartMetricHeader(icon: ImageVector, latestValue: Double?, latestUnit: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("最新数据", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(
                text = latestValue?.let { "${formatCompact(it)}$latestUnit" } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChartDateSelector(startDate: LocalDate, endDate: LocalDate, onStartDateClick: () -> Unit, onEndDateClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("统计区间", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DatePill(label = startDate.format(ChartDateFormatter), onClick = onStartDateClick, modifier = Modifier.weight(1f))
            DatePill(label = endDate.format(ChartDateFormatter), onClick = onEndDateClick, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyChartState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp).background(ComposeColor(0xFFF8FAFC), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("当前区间暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DatePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ComposeColor(0xFFF3F6FB), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun buildFilteredTrendMap(trends: Map<String, List<TrendPointDto>>, startDate: LocalDate, endDate: LocalDate): Map<String, List<TrendPointDto>> {
    return trends.mapValues { (_, points) ->
        points.filter { point ->
            val date = businessDate(point.recorded_at)
            !date.isBefore(startDate) && !date.isAfter(endDate)
        }
    }
}

private fun buildCardioDurationSeries(
    sessions: List<WorkoutSessionDto>,
    startDate: LocalDate,
    endDate: LocalDate,
): List<TrendPointDto> {
    val durationByDate = sessions
        .mapNotNull { session ->
            val date = runCatching {
                businessDate(session.recorded_at)
            }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(startDate) || date.isAfter(endDate)) return@mapNotNull null
            val duration = session.exercises
                .filter { it.part_id == "cardio" }
                .sumOf { it.duration_minutes ?: 0 }
            date to duration
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, durations) -> durations.sum() }

    val totalDays = (endDate.toEpochDay() - startDate.toEpochDay()).coerceAtLeast(0)
    return (0..totalDays).map { offset ->
        val date = startDate.plusDays(offset)
            TrendPointDto(
                recorded_at = "${date}T06:00:00+08:00",
                value = (durationByDate[date] ?: 0).toDouble(),
            )
    }
}

private fun summaryForSeries(series: List<TrendPointDto>): TrendSummary = TrendSummary(latest = series.lastOrNull()?.value)

private fun latestNonZeroSummary(series: List<TrendPointDto>): TrendSummary {
    return TrendSummary(latest = series.lastOrNull { it.value != 0.0 }?.value)
}

private fun numericValue(value: Any?): Double? {
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun buildMonthCalendar(entries: List<EntryDto>, workouts: List<WorkoutCalendarPointDto>, year: Int, month: Int): List<DataCalendarDay> {
    val targetMonth = YearMonth.of(year, month)
    val entryMap = entries.groupBy { businessDate(it.recorded_at) }
    val workoutMap = workouts.associateBy { LocalDate.parse(it.date) }
    val firstDay = targetMonth.atDay(1)
    val start = firstDay.minusDays(((firstDay.dayOfWeek.value + 6) % 7).toLong())
    return (0 until 42).map { offset ->
        val date = start.plusDays(offset.toLong())
        DataCalendarDay(date = date, entries = entryMap[date].orEmpty(), workoutSummary = workoutMap[date])
    }
}

private fun availableYears(entries: List<EntryDto>, workouts: List<WorkoutCalendarPointDto>): List<Int> {
    val years = buildSet {
        entries.forEach { add(businessDate(it.recorded_at).year) }
        workouts.forEach { add(LocalDate.parse(it.date).year) }
    }.sortedDescending()
    return years.ifEmpty { listOf(LocalDate.now(BeijingOffset).year) }
}

private fun businessDate(recordedAt: String): LocalDate {
    return OffsetDateTime.parse(recordedAt)
        .withOffsetSameInstant(BeijingOffset)
        .minusHours(BusinessDayStartHour.toLong())
        .toLocalDate()
}

private fun workoutTypeLabel(workout: WorkoutCalendarPointDto): String {
    val hasCardio = workout.parts.any { it == "cardio" }
    val hasStrength = workout.total_sets > 0
    return when {
        hasCardio && hasStrength -> "训练"
        hasCardio -> "有氧"
        else -> "力量"
    }
}

private fun partLabel(partId: String): String = when (partId) {
    "cardio" -> "有氧"
    "chest" -> "胸"
    "back" -> "背"
    "shoulders" -> "肩"
    "arms" -> "手臂"
    "legs" -> "腿"
    "core" -> "核心"
    "glutes" -> "臀"
    else -> partId
}

private fun computeAxisMinimum(series: List<TrendPointDto>): Float {
    val min = series.minOfOrNull { it.value } ?: 0.0
    val max = series.maxOfOrNull { it.value } ?: min
    val padding = if (min == max) maxOf(abs(min) * 0.05, 1.0) else maxOf((max - min) * 0.12, 0.5)
    return (min - padding).toFloat()
}

private fun computeAxisMaximum(series: List<TrendPointDto>): Float {
    val min = series.minOfOrNull { it.value } ?: 0.0
    val max = series.maxOfOrNull { it.value } ?: min
    val padding = if (min == max) maxOf(abs(max) * 0.05, 1.0) else maxOf((max - min) * 0.12, 0.5)
    return (max + padding).toFloat()
}

private fun formatChartDate(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value).withOffsetSameInstant(BeijingOffset).format(DateTimeFormatter.ofPattern("MM-dd"))
    }.getOrDefault(value)
}

private fun formatCompact(value: Double): String {
    val rounded = round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private class YOnlyMarker(context: android.content.Context, private val valueProvider: (Int) -> String) : MarkerView(context, android.R.layout.simple_list_item_1) {
    private val labelView = findViewById<android.widget.TextView>(android.R.id.text1)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        labelView.text = valueProvider(e?.x?.toInt() ?: 0)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF = MPPointF((-width / 2).toFloat(), (-height).toFloat())
}
