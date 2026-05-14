package com.healthdashboard.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthdashboard.mobile.data.EntryDto
import com.healthdashboard.mobile.data.EntryRequest
import com.healthdashboard.mobile.data.HealthRepository
import com.healthdashboard.mobile.data.MetricDto
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Composable
fun RecordsScreen(
    repository: HealthRepository,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val entries = remember { mutableStateListOf<EntryDto>() }
    val metrics = remember { mutableStateListOf<MetricDto>() }
    val values = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) {
        load()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("记录管理", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onLogout) { Text("退出") }
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            } else {
                metrics.forEach { metric ->
                    OutlinedTextField(
                        value = values[metric.id].orEmpty(),
                        onValueChange = { values[metric.id] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        label = { Text("${metric.label} (${metric.unit})") },
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    label = { Text("备注") },
                )

                Button(
                    onClick = {
                        scope.launch {
                            val payload = EntryRequest(
                                recorded_at = OffsetDateTime.now(ZoneOffset.ofHours(8))
                                    .withSecond(0)
                                    .withNano(0)
                                    .toString(),
                                values = values
                                    .filterValues { it.isNotBlank() }
                                    .mapValues { (_, value) -> value.toDoubleOrNull() ?: value },
                                note = note,
                                tags = emptyList(),
                            )
                            runCatching { repository.createEntry(payload) }
                                .onSuccess {
                                    values.clear()
                                    note = ""
                                    load()
                                    snackbarHostState.showSnackbar("记录已保存")
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(it.message ?: "保存失败")
                                }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                ) {
                    Text("新增记录")
                }

                LazyColumn(modifier = Modifier.padding(top = 20.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(entry.recorded_at)
                                Text(
                                    entry.values.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                if (entry.note.isNotBlank()) {
                                    Text(entry.note, modifier = Modifier.padding(top = 6.dp))
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { repository.deleteEntry(entry.id) }
                                                .onSuccess {
                                                    load()
                                                    snackbarHostState.showSnackbar("记录已删除")
                                                }
                                                .onFailure {
                                                    snackbarHostState.showSnackbar(it.message ?: "删除失败")
                                                }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
