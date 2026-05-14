package com.healthdashboard.mobile.ui

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.mikephil.charting.components.XAxis
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
    var selectedRange by remember { mutableStateOf("month") }

    suspend fun loadDashboard() {
        loading = true
        runCatching { repository.getDashboard(selectedRange) }
            .onSuccess {
                dashboard = it
                error = null
                loading = false
            }
            .onFailure {
                error = it.message ?: "加载失败"
                loading = false
            }
    }

    LaunchedEffect(selectedRange) {
        loadDashboard()
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
                    title = "图表查看",
                    subtitle = "浏览洞察、趋势和关键指标变化，可切换年月季度范围。",
                    action = {
                        TextButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Text("退出", modifier = Modifier.padding(start = 4.dp), color = androidx.compose.ui.graphics.Color.White)
                        }
                    },
                )
            }

            item {
                SectionCard(
                    title = "时间范围",
                    subtitle = "切换图表分析窗口。",
                ) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            "year" to "年",
                            "quarter" to "季度",
                            "month" to "月",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = selectedRange == value,
                                onClick = { selectedRange = value },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                }
            }

            when {
                loading -> item {
                    SectionCard(title = "加载中") {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }

                error != null -> item {
                    SectionCard(title = "加载失败") {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }

                dashboard != null -> {
                    item {
                        SectionCard(
                            title = "自动洞察",
                            subtitle = "来自服务端分析摘要，适合快速扫一眼近期变化。",
                        ) {
                            dashboard!!.insights.forEach { insight ->
                                Text(
                                    text = "• $insight",
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    val trendOrder = listOf("weight_kg", "body_fat_pct", "bmi")
                    items(trendOrder) { metricId ->
                        val series = dashboard!!.trends[metricId].orEmpty()
                        if (series.isEmpty()) return@items
                        val metric = dashboard!!.metrics.firstOrNull { it.id == metricId }
                        val label = metric?.label ?: if (metricId == "bmi") "BMI" else metricId

                        SectionCard(
                            title = label,
                            subtitle = "当前范围：${dashboard!!.range}",
                        ) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                factory = { context ->
                                    LineChart(context).apply {
                                        description.isEnabled = false
                                        setTouchEnabled(true)
                                        setPinchZoom(true)
                                        axisRight.isEnabled = false
                                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                                        legend.isEnabled = false
                                    }
                                },
                                update = { chart ->
                                    val lineEntries = series.mapIndexed { index, point ->
                                        Entry(index.toFloat(), point.value.toFloat())
                                    }
                                    val color = metric?.color?.let {
                                        runCatching { Color.parseColor(it) }.getOrDefault(Color.parseColor("#155EEF"))
                                    } ?: Color.parseColor("#155EEF")
                                    val dataSet = LineDataSet(lineEntries, label).apply {
                                        this.color = color
                                        lineWidth = 2.8f
                                        setCircleColor(color)
                                        circleRadius = 3.5f
                                        valueTextSize = 10f
                                        setDrawFilled(true)
                                        fillAlpha = 40
                                        fillColor = color
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
}
