package com.healthdashboard.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthdashboard.mobile.data.HealthRepository
import com.healthdashboard.mobile.data.WorkoutExerciseDto
import com.healthdashboard.mobile.data.WorkoutExerciseRequest
import com.healthdashboard.mobile.data.WorkoutOverviewDto
import com.healthdashboard.mobile.data.WorkoutPartDto
import com.healthdashboard.mobile.data.WorkoutPlanDto
import com.healthdashboard.mobile.data.WorkoutSessionDto
import com.healthdashboard.mobile.data.WorkoutSessionRequest
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val BeijingOffset: ZoneOffset = ZoneOffset.ofHours(8)

private data class WorkoutExerciseDraft(
    val id: Int,
    val partId: String = "",
    val exerciseId: String = "",
    val detail: String = "",
    val sets: String = "4",
    val reps: String = "10",
    val weightKg: String = "",
    val durationMinutes: String = "",
    val rpe: String = "",
)

private fun WorkoutExerciseDraft.normalizeForPart(partId: String): WorkoutExerciseDraft {
    return if (partId == "cardio") {
        copy(
            partId = partId,
            sets = "1",
            reps = "",
            weightKg = "",
            rpe = "",
        )
    } else {
        copy(
            partId = partId,
            sets = if (sets.isBlank()) "4" else sets,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(
    repository: HealthRepository,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var overview by remember { mutableStateOf<WorkoutOverviewDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var selectedDateTime by remember { mutableStateOf(LocalDateTime.now(BeijingOffset)) }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var energyLevel by remember { mutableStateOf("7") }
    val exerciseDrafts = remember { mutableStateListOf<WorkoutExerciseDraft>() }
    var draftSeed by remember { mutableStateOf(0) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val sessionFormatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }

    fun activeParts(source: WorkoutOverviewDto?): List<WorkoutPartDto> =
        source?.catalog?.parts?.filter { it.active }?.sortedBy { it.sort_order }.orEmpty()

    fun exercisesForPart(source: WorkoutOverviewDto?, partId: String): List<WorkoutExerciseDto> =
        source?.catalog?.exercises?.get(partId)?.filter { it.active }?.sortedBy { it.sort_order }.orEmpty()

    fun ensureDrafts(source: WorkoutOverviewDto?, preferredPlanId: String? = selectedPlanId) {
        if (exerciseDrafts.isNotEmpty()) return
        val plan = source?.plans?.firstOrNull { it.id == preferredPlanId && it.active }
        val parts = activeParts(source)
        val drafts = if (plan != null && plan.groups.isNotEmpty()) {
            plan.groups.flatMap { group ->
                group.exercise_ids.map { exerciseId ->
                    draftSeed += 1
                    WorkoutExerciseDraft(
                        id = draftSeed,
                        partId = group.part_id,
                        exerciseId = exerciseId,
                    )
                }
            }
        } else {
            val defaultPart = parts.firstOrNull()?.id.orEmpty()
            val defaultExercise = exercisesForPart(source, defaultPart).firstOrNull()?.id.orEmpty()
            draftSeed += 1
            listOf(
                WorkoutExerciseDraft(
                    id = draftSeed,
                    partId = defaultPart,
                    exerciseId = defaultExercise,
                ),
            )
        }
        exerciseDrafts.addAll(drafts)
    }

    suspend fun loadOverview() {
        loading = true
        runCatching { repository.getWorkoutOverview() }
            .onSuccess {
                overview = it
                ensureDrafts(it)
            }
            .onFailure {
                snackbarHostState.showSnackbar(it.message ?: "加载训练内容失败")
            }
        loading = false
    }

    fun applyPlan(plan: WorkoutPlanDto?) {
        selectedPlanId = plan?.id
        exerciseDrafts.clear()
        ensureDrafts(overview, plan?.id)
    }

    fun updateDraft(id: Int, transform: (WorkoutExerciseDraft) -> WorkoutExerciseDraft) {
        val index = exerciseDrafts.indexOfFirst { it.id == id }
        if (index >= 0) {
            exerciseDrafts[index] = transform(exerciseDrafts[index])
        }
    }

    fun addDraft() {
        val parts = activeParts(overview)
        val defaultPart = parts.firstOrNull()?.id.orEmpty()
        val defaultExercise = exercisesForPart(overview, defaultPart).firstOrNull()?.id.orEmpty()
        draftSeed += 1
        exerciseDrafts += WorkoutExerciseDraft(
            id = draftSeed,
            partId = defaultPart,
            exerciseId = defaultExercise,
        ).normalizeForPart(defaultPart)
    }

    fun resetForm() {
        selectedPlanId = null
        selectedDateTime = LocalDateTime.now(BeijingOffset)
        note = ""
        tags = ""
        energyLevel = "7"
        exerciseDrafts.clear()
        ensureDrafts(overview)
    }

    fun openDateTimePicker(context: android.content.Context) {
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

    LaunchedEffect(Unit) {
        loadOverview()
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppGradientHeader(
                    title = "训练工作台",
                    subtitle = "记录力量训练和有氧训练，直接查看最近建议，动作和计划仍在 Web 端配置。",
                    action = {
                        TextButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Text("退出", modifier = Modifier.padding(start = 4.dp), color = Color.White)
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

                overview == null -> item {
                    SectionCard(title = "训练数据不可用") {
                        Text("暂时无法获取训练数据，请检查接口或密码。", color = MaterialTheme.colorScheme.error)
                    }
                }

                else -> {
                    val currentOverview = overview!!
                    val plans = currentOverview.plans.filter { it.active }
                    val currentPlan = plans.firstOrNull { it.id == selectedPlanId }

                    item {
                        WorkoutSummaryStrip(overview = currentOverview)
                    }

                    item {
                        SectionCard(
                            title = "训练建议",
                            subtitle = "根据最近 14 天和 30 天训练情况生成，帮助你调整力量训练覆盖和有氧频率。",
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SummaryAssistChip(
                                    icon = Icons.Rounded.LocalFireDepartment,
                                    label = "14天 ${currentOverview.summary_14d.session_count} 次",
                                )
                                SummaryAssistChip(
                                    icon = Icons.Rounded.QueryStats,
                                    label = "14天 ${currentOverview.summary_14d.total_sets} 组",
                                )
                                SummaryAssistChip(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                    label = "30天 ${currentOverview.summary_30d.session_count} 次",
                                )
                            }
                            if (currentOverview.recommendations.isEmpty()) {
                                Text(
                                    text = "继续记录更多训练后，这里会给出更有针对性的建议。",
                                    modifier = Modifier.padding(top = 14.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                currentOverview.recommendations.forEach { insight ->
                                    InsightRow(text = insight)
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "新增训练记录",
                            subtitle = "先选计划或自由训练，再逐个补充力量动作或有氧项目细节。",
                        ) {
                            var planExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = planExpanded,
                                onExpandedChange = { planExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = currentPlan?.name ?: "自由训练",
                                    onValueChange = {},
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    label = { Text("训练计划") },
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                                )
                                ExposedDropdownMenu(
                                    expanded = planExpanded,
                                    onDismissRequest = { planExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("自由训练") },
                                        onClick = {
                                            planExpanded = false
                                            applyPlan(null)
                                        },
                                    )
                                    plans.forEach { plan ->
                                        DropdownMenuItem(
                                            text = { Text(plan.name) },
                                            onClick = {
                                                planExpanded = false
                                                applyPlan(plan)
                                            },
                                        )
                                    }
                                }
                            }
                            if (!currentPlan?.description.isNullOrBlank()) {
                                Text(
                                    text = currentPlan?.description.orEmpty(),
                                    modifier = Modifier.padding(top = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            OutlinedButton(
                                onClick = { openDateTimePicker(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                                Text(
                                    text = selectedDateTime.format(dateFormatter),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = energyLevel,
                                    onValueChange = { energyLevel = it.filter(Char::isDigit).take(2) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("体感强度") },
                                    placeholder = { Text("1-10") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = tags,
                                    onValueChange = { tags = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("标签") },
                                    placeholder = { Text("推、有氧、恢复") },
                                    singleLine = true,
                                )
                            }

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                label = { Text("训练备注") },
                                placeholder = { Text("例如今天力量训练后补了 20 分钟爬楼机，后段心率明显上来") },
                            )

                            Text(
                                text = "动作清单",
                                modifier = Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            exerciseDrafts.forEachIndexed { index, draft ->
                                val parts = activeParts(currentOverview)
                                val part = parts.firstOrNull { it.id == draft.partId } ?: parts.firstOrNull()
                                val exercises = exercisesForPart(currentOverview, part?.id.orEmpty())
                                val selectedExercise = exercises.firstOrNull { it.id == draft.exerciseId } ?: exercises.firstOrNull()
                                ExerciseDraftCard(
                                    index = index + 1,
                                    draft = draft,
                                    parts = parts,
                                    exercises = exercises,
                                    selectedPart = part,
                                    selectedExercise = selectedExercise,
                                    onDraftChange = { transform -> updateDraft(draft.id, transform) },
                                    onRemove = { exerciseDrafts.removeAll { it.id == draft.id } },
                                )
                            }

                            OutlinedButton(
                                onClick = { addDraft() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Text("继续添加动作", modifier = Modifier.padding(start = 6.dp))
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val payload = WorkoutSessionRequest(
                                            recorded_at = selectedDateTime.atOffset(BeijingOffset).toString(),
                                            plan_id = selectedPlanId,
                                            exercises = exerciseDrafts.mapNotNull { draft ->
                                                if (draft.partId.isBlank() || draft.exerciseId.isBlank()) {
                                                    return@mapNotNull null
                                                }
                                                WorkoutExerciseRequest(
                                                    part_id = draft.partId,
                                                    exercise_id = draft.exerciseId,
                                                    detail = draft.detail,
                                                    sets = draft.sets.toIntOrNull() ?: 1,
                                                    reps = draft.reps.toIntOrNull(),
                                                    weight_kg = draft.weightKg.toDoubleOrNull(),
                                                    duration_minutes = draft.durationMinutes.toIntOrNull(),
                                                    rpe = draft.rpe.toDoubleOrNull(),
                                                )
                                            },
                                            note = note,
                                            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                            energy_level = energyLevel.toIntOrNull(),
                                        )
                                        if (payload.exercises.isEmpty()) {
                                            snackbarHostState.showSnackbar("至少添加一个有效动作")
                                            return@launch
                                        }
                                        runCatching { repository.createWorkoutSession(payload) }
                                            .onSuccess {
                                                snackbarHostState.showSnackbar("训练记录已保存")
                                                resetForm()
                                                loadOverview()
                                            }
                                            .onFailure {
                                                snackbarHostState.showSnackbar(it.message ?: "训练记录保存失败")
                                            }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                            ) {
                                Text("保存训练记录")
                            }
                        }
                    }

                    item {
                        Text(
                            text = "最近训练",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(currentOverview.sessions.take(12), key = { it.id }) { session ->
                        WorkoutHistoryCard(
                            overview = currentOverview,
                            session = session,
                            sessionFormatter = sessionFormatter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutSummaryStrip(overview: WorkoutOverviewDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            title = "14天训练",
            value = overview.summary_14d.session_count.toString(),
            accent = Color(0xFF155EEF),
            note = "${overview.summary_14d.total_sets} 组",
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            title = "14天总组数",
            value = overview.summary_14d.total_sets.toString(),
            accent = Color(0xFF0A7E5C),
            note = null,
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            title = "30天有氧",
            value = overview.summary_30d.cardio_sessions.toString(),
            accent = Color(0xFFF79009),
            note = "${overview.summary_30d.cardio_duration_minutes} 分钟",
        )
    }
}

@Composable
private fun SummaryStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color,
    note: String?,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.White),
                    ),
                )
                .padding(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            note?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryAssistChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun InsightRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(Color(0xFFE7F0FF), RoundedCornerShape(999.dp))
                .padding(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF155EEF),
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDraftCard(
    index: Int,
    draft: WorkoutExerciseDraft,
    parts: List<WorkoutPartDto>,
    exercises: List<WorkoutExerciseDto>,
    selectedPart: WorkoutPartDto?,
    selectedExercise: WorkoutExerciseDto?,
    onDraftChange: ((WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val isCardio = selectedPart?.id == "cardio"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFF)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("动作 $index", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!selectedExercise?.description.isNullOrBlank()) {
                        Text(
                            text = selectedExercise?.description.orEmpty(),
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index > 1) {
                    OutlinedButton(onClick = onRemove) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null)
                        Text("移除", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            var partExpanded by remember(draft.id) { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = partExpanded,
                onExpandedChange = { partExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedPart?.label ?: "请选择部位",
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    readOnly = true,
                    label = { Text("训练部位") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = partExpanded,
                    onDismissRequest = { partExpanded = false },
                ) {
                    parts.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                partExpanded = false
                                onDraftChange {
                                    it.copy(exerciseId = "").normalizeForPart(option.id)
                                }
                            },
                        )
                    }
                }
            }

            var exerciseExpanded by remember(draft.id) { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = exerciseExpanded,
                onExpandedChange = { exerciseExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedExercise?.name ?: "请选择动作",
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    readOnly = true,
                    label = { Text("动作") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exerciseExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = exerciseExpanded,
                    onDismissRequest = { exerciseExpanded = false },
                ) {
                    exercises.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                exerciseExpanded = false
                                onDraftChange { it.copy(exerciseId = option.id) }
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.detail,
                onValueChange = { next -> onDraftChange { it.copy(detail = next) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text("动作细节") },
                placeholder = { Text(selectedExercise?.detail_placeholder ?: "例如阻力、坡度、配速、握距或器械设置") },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isCardio) {
                    OutlinedTextField(
                        value = draft.sets,
                        onValueChange = { next -> onDraftChange { it.copy(sets = next.filter { ch -> ch.isDigit() }.take(3)) } },
                        modifier = Modifier.weight(1f),
                        label = { Text("组数") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.reps,
                        onValueChange = { next -> onDraftChange { it.copy(reps = next.filter { ch -> ch.isDigit() }.take(3)) } },
                        modifier = Modifier.weight(1f),
                        label = { Text("次数") },
                        singleLine = true,
                    )
                }
            }

            if (!isCardio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = draft.weightKg,
                        onValueChange = { next -> onDraftChange { it.copy(weightKg = next.filter { ch -> ch.isDigit() || ch == '.' }.take(8)) } },
                        modifier = Modifier.weight(1f),
                        label = { Text("重量 kg") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.rpe,
                        onValueChange = { next -> onDraftChange { it.copy(rpe = next.filter { ch -> ch.isDigit() || ch == '.' }.take(4)) } },
                        modifier = Modifier.weight(1f),
                        label = { Text("RPE") },
                        singleLine = true,
                    )
                }
            }

            OutlinedTextField(
                value = draft.durationMinutes,
                onValueChange = { next -> onDraftChange { it.copy(durationMinutes = next.filter { ch -> ch.isDigit() }.take(4)) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text(if (isCardio) "时长（分钟）" else "动作时长（分钟，可选）") },
                singleLine = true,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WorkoutHistoryCard(
    overview: WorkoutOverviewDto,
    session: WorkoutSessionDto,
    sessionFormatter: DateTimeFormatter,
) {
    val planLabel = overview.plans.firstOrNull { it.id == session.plan_id }?.name ?: "自由训练"
    val recordedAt = runCatching {
        OffsetDateTime.parse(session.recorded_at).atZoneSameInstant(BeijingOffset).format(sessionFormatter)
    }.getOrDefault(session.recorded_at)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(recordedAt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(planLabel, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary)
                }
                session.energy_level?.let {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("强度 $it") },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.tags.forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
            }

            session.exercises.forEach { exercise ->
                val partLabel = overview.catalog.parts.firstOrNull { it.id == exercise.part_id }?.label ?: exercise.part_id
                val exerciseLabel = overview.catalog.exercises[exercise.part_id]
                    ?.firstOrNull { it.id == exercise.exercise_id }
                    ?.name ?: exercise.exercise_id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "$partLabel · $exerciseLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = buildString {
                                if (exercise.part_id != "cardio") {
                                    append("${exercise.sets} 组")
                                    exercise.reps?.let { append(" · ${it} 次") }
                                    exercise.weight_kg?.let { append(" · ${it} kg") }
                                }
                                exercise.duration_minutes?.let { append(" · ${it} 分钟") }
                                if (exercise.part_id != "cardio") {
                                    exercise.rpe?.let { append(" · RPE ${it}") }
                                }
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exercise.detail.isNotBlank()) {
                            Text(
                                text = exercise.detail,
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (session.note.isNotBlank()) {
                Text(
                    text = session.note,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
