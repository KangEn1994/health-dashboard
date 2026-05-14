package com.healthdashboard.mobile.ui

import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.healthdashboard.mobile.data.DashboardDto
import com.healthdashboard.mobile.data.HealthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    repository: HealthRepository,
    onLogout: () -> Unit,
) {
    var dashboard by remember { mutableStateOf<DashboardDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repository.getDashboard("month") }
            .onSuccess {
                dashboard = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "加载失败"
                loading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图表查看") },
                actions = {
                    TextButton(onClick = onLogout) { Text("退出") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                dashboard != null -> {
                    Text("自动洞察", style = MaterialTheme.typography.titleMedium)
                    dashboard!!.insights.forEach { insight ->
                        Text(insight, modifier = Modifier.padding(top = 6.dp))
                    }

                    listOf("weight_kg", "body_fat_pct", "bmi").forEach { metricId ->
                        val series = dashboard!!.trends[metricId].orEmpty()
                        if (series.isEmpty()) return@forEach
                        val label = dashboard!!.metrics.firstOrNull { it.id == metricId }?.label
                            ?: if (metricId == "bmi") "BMI" else metricId
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        )
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            factory = { context ->
                                LineChart(context).apply {
                                    minimumHeight = 420
                                    description.isEnabled = false
                                    setTouchEnabled(true)
                                    setPinchZoom(true)
                                }
                            },
                            update = { chart ->
                                val lineEntries = series.mapIndexed { index, point ->
                                    Entry(index.toFloat(), point.value.toFloat())
                                }
                                val dataSet = LineDataSet(lineEntries, label).apply {
                                    color = Color.parseColor("#1d4ed8")
                                    lineWidth = 2.5f
                                    setCircleColor(Color.parseColor("#1d4ed8"))
                                    circleRadius = 3f
                                    valueTextSize = 10f
                                }
                                chart.data = LineData(dataSet)
                                chart.invalidate()
                            },
                        )
                    }
                }
            }
        }
    }
}
