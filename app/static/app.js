const api = {
  async get(url) {
    const response = await fetch(url);
    if (response.status === 401) {
      if (window.location.pathname !== "/login") window.location.href = "/login";
      throw new Error("authentication required");
    }
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  },
  async send(url, method, payload) {
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (response.status === 401) {
      if (window.location.pathname !== "/login") window.location.href = "/login";
      throw new Error("authentication required");
    }
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || "Request failed");
    }
    return response.json();
  },
};

function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return "--";
  return Number(value).toFixed(digits);
}

function parseError(error) {
  try {
    return JSON.parse(error.message).detail || error.message;
  } catch {
    return error.message;
  }
}

function updateStatus(elementId, message, isError = false) {
  const node = document.getElementById(elementId);
  if (!node) return;
  node.textContent = message;
  node.className = isError ? "status-pill" : "status-pill";
  node.style.color = isError ? "var(--danger)" : "var(--success)";
}

function navActive() {
  document.querySelectorAll(".nav a").forEach((link) => {
    if (link.pathname === window.location.pathname) {
      link.classList.add("active");
    }
  });
}

function initLayout() {
  navActive();
  document.querySelectorAll("[data-logout-link]").forEach((link) => {
    link.addEventListener("click", async (event) => {
      event.preventDefault();
      try {
        await api.send("/api/auth/logout", "POST", {});
      } catch {
        // ignore logout failures and still bounce to login
      }
      window.location.href = "/login";
    });
  });
}

function makeChart(elementId, option) {
  const dom = document.getElementById(elementId);
  if (!dom) return null;
  const chart = echarts.init(dom);
  chart.setOption(option);
  window.addEventListener("resize", () => chart.resize());
  return chart;
}

const BEIJING_TIMEZONE = "Asia/Shanghai";

function formatBeijingDateTime(value, includeTime = true) {
  if (!value) return "--";
  const date = new Date(value);
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: BEIJING_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || "";
  const datePart = `${get("year")}-${get("month")}-${get("day")}`;
  if (!includeTime) return datePart;
  return `${datePart} ${get("hour")}:${get("minute")}:${get("second")}`;
}

function toBeijingDateInputValue(date = new Date()) {
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: BEIJING_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || "";
  return `${get("year")}-${get("month")}-${get("day")}T${get("hour")}:${get("minute")}`;
}

function beijingDateLabel(value) {
  return formatBeijingDateTime(value, false);
}

function numericAxisRange(series) {
  const values = series.map((point) => point.value).filter((value) => typeof value === "number");
  if (!values.length) return { min: null, max: null };
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) {
    const padding = Math.max(Math.abs(min) * 0.05, 1);
    return { min: Number((min - padding).toFixed(3)), max: Number((max + padding).toFixed(3)) };
  }
  const span = max - min;
  const padding = Math.max(span * 0.12, 0.5);
  return {
    min: Number((min - padding).toFixed(3)),
    max: Number((max + padding).toFixed(3)),
  };
}

function trendLabel(delta) {
  if (delta === null || delta === undefined) return "";
  if (delta > 0) return `较上次 +${formatNumber(delta)} `;
  if (delta < 0) return `较上次 ${formatNumber(delta)} `;
  return "较上次 0";
}

async function initDashboardPage() {
  const page = document.body.dataset.page;
  if (page !== "dashboard") return;

  let currentRange = "year";
  const rangeButtons = document.querySelectorAll("[data-range]");

  async function load(range) {
    currentRange = range;
    rangeButtons.forEach((button) => {
      button.classList.toggle("active", button.dataset.range === range);
    });

    const data = await api.get(`/api/dashboard?range=${range}`);
    renderDashboard(data);
  }

  rangeButtons.forEach((button) => {
    button.addEventListener("click", () => load(button.dataset.range));
  });

  await load(currentRange);
}

function summaryCard(label, unit, summary) {
  const deltaClass = summary.delta > 0 ? "up" : "down";
  return `
    <div class="summary-card">
      <div class="label">${label}</div>
      <div class="value">${summary.latest === null ? "--" : `${formatNumber(summary.latest)} ${unit || ""}`}</div>
      <div class="delta ${summary.delta === null || summary.delta === undefined ? "" : deltaClass}">${trendLabel(summary.delta)}${unit || ""}</div>
    </div>
  `;
}

function renderDashboard(data) {
  const metrics = Object.fromEntries(data.metrics.map((metric) => [metric.id, metric]));
  metrics.workout_duration_min = {
    id: "workout_duration_min",
    label: "运动时长",
    unit: "分钟",
    color: "#0f766e",
  };
  const summaryNode = document.getElementById("summaryGrid");
  const cards = [
    ["weight_kg", "体重"],
    ["body_fat_pct", "体脂率"],
    ["bmi", "BMI"],
    ["waist_cm", "腰围"],
  ];

  summaryNode.innerHTML = cards
    .map(([metricId, fallback]) => {
      const metric = metrics[metricId] || { label: fallback, unit: metricId === "bmi" ? "" : "" };
      return summaryCard(metric.label || fallback, metric.unit || "", data.summaries[metricId] || {});
    })
    .join("");

  document.getElementById("continuityStats").innerHTML = `
    <div class="summary-card">
      <div class="label">记录天数</div>
      <div class="value">${data.continuity.record_days}</div>
      <div class="delta">最长连续 ${data.continuity.longest_streak} 天</div>
    </div>
    <div class="summary-card">
      <div class="label">最近记录缺口</div>
      <div class="value">${data.continuity.days_since_last_entry ?? "--"} 天</div>
      <div class="delta">总记录 ${data.entry_count} 条</div>
    </div>
    <div class="summary-card">
      <div class="label">体重周变化速度</div>
      <div class="value">${data.weight_velocity_per_week === null ? "--" : formatNumber(data.weight_velocity_per_week)} kg</div>
      <div class="delta">按周折算</div>
    </div>
  `;

  const trendMetrics = ["weight_kg", "body_fat_pct", "bmi", "waist_cm", "workout_duration_min"];
  const trendChartsGrid = document.getElementById("trendChartsGrid");
  trendChartsGrid.innerHTML = trendMetrics
    .map((metricId) => {
      const metric = metrics[metricId] || { label: metricId === "bmi" ? "BMI" : metricId, unit: "", color: "#111827" };
      return `
        <div class="mini-chart-card">
          <h4>${metric.label}</h4>
          <div class="mini-chart-meta">范围 ${data.range} · 单位 ${metric.unit || "无"} · 北京时间</div>
          <div id="chart-${metricId}" class="chart mini"></div>
        </div>
      `;
    })
    .join("");

  trendMetrics.forEach((metricId) => {
    const metric = metrics[metricId] || { label: metricId === "bmi" ? "BMI" : metricId, unit: "", color: "#111827" };
    const series = data.trends[metricId] || [];
    const axisRange = numericAxisRange(series);
    makeChart(`chart-${metricId}`, {
      tooltip: { trigger: "axis" },
      grid: { left: 18, right: 18, top: 20, bottom: 30, containLabel: true },
      xAxis: {
        type: "category",
        data: series.map((point) => beijingDateLabel(point.recorded_at)),
        axisLabel: { color: "#60708b" },
      },
      yAxis: {
        type: "value",
        min: axisRange.min,
        max: axisRange.max,
        axisLabel: { color: "#60708b" },
        splitLine: { lineStyle: { color: "rgba(96,112,139,0.12)" } },
      },
      series: [
        {
          name: metric.label,
          type: "line",
          smooth: true,
          symbolSize: 8,
          data: series.map((point) => point.value),
          lineStyle: { width: 3, color: metric.color || "#111827" },
          itemStyle: { color: metric.color || "#111827" },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: `${metric.color || "#111827"}55` },
              { offset: 1, color: `${metric.color || "#111827"}08` },
            ]),
          },
        },
      ],
    });
  });

  const comparisonMetrics = data.metrics.filter((metric) => metric.show_on_dashboard && metric.active && metric.type === "number");
  makeChart("comparisonChart", {
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    grid: { left: 16, right: 20, top: 40, bottom: 24, containLabel: true },
    xAxis: { type: "category", data: comparisonMetrics.map((metric) => metric.label) },
    yAxis: { type: "value" },
    series: [
      {
        name: "当前周期均值",
        type: "bar",
        data: comparisonMetrics.map((metric) => data.comparisons[metric.id]?.current),
        itemStyle: { color: "#0f62fe" },
      },
      {
        name: "上一周期均值",
        type: "bar",
        data: comparisonMetrics.map((metric) => data.comparisons[metric.id]?.previous),
        itemStyle: { color: "#9bbcf8" },
      },
    ],
  });

  const correlationGrid = document.getElementById("correlationGrid");
  const desiredCorrelations = ["body_fat_pct", "bmi", "workout_duration_min"];
  const correlationMap = Object.fromEntries((data.correlations || []).map((item) => [item.metric_id, item]));
  correlationGrid.innerHTML = desiredCorrelations
    .map((metricId) => {
      const metric = metrics[metricId] || { label: metricId, color: "#7c3aed" };
      return `
        <div class="mini-chart-card">
          <h4>体重 vs ${metric.label}</h4>
          <div class="mini-chart-meta">${metric.unit ? `单位 ${metric.unit} · ` : ""}北京时间</div>
          <div id="correlation-${metricId}" class="chart mini"></div>
        </div>
      `;
    })
    .join("");

  desiredCorrelations.forEach((metricId) => {
    const metric = metrics[metricId] || { label: metricId, color: "#7c3aed" };
    const correlation = correlationMap[metricId];
    if (!correlation || !correlation.points.length) {
      document.getElementById(`correlation-${metricId}`).outerHTML = `<div id="correlation-${metricId}" class="empty">至少需要体重和${metric.label}在同一天都有记录，相关性才会显示。</div>`;
      return;
    }
    makeChart(`correlation-${metricId}`, {
      tooltip: { trigger: "item" },
      grid: { left: 18, right: 18, top: 36, bottom: 26, containLabel: true },
      xAxis: { type: "value", name: "体重 kg" },
      yAxis: { type: "value", name: metric.label },
      series: [
        {
          type: "scatter",
          symbolSize: 12,
          data: correlation.points.map((point) => [point.x, point.y]),
          itemStyle: { color: metric.color || "#7c3aed" },
        },
      ],
      graphic: correlation.correlation === null ? [] : [
        {
          type: "text",
          left: "center",
          top: 10,
          style: {
            text: `相关系数 r = ${correlation.correlation}`,
            fill: "#60708b",
            font: "14px sans-serif",
          },
        },
      ],
    });
  });

  makeChart("heatmapChart", {
    tooltip: {},
    visualMap: {
      min: 0,
      max: Math.max(...data.calendar_heatmap.map((item) => item.count), 1),
      orient: "horizontal",
      left: "center",
      bottom: 0,
    },
    calendar: {
      top: 30,
      left: 20,
      right: 20,
      cellSize: ["auto", 18],
      range: data.calendar_heatmap.length ? data.calendar_heatmap[0].date.slice(0, 4) : String(new Date().getFullYear()),
      itemStyle: { borderWidth: 1, borderColor: "#fff" },
      yearLabel: { show: false },
    },
    series: {
      type: "heatmap",
      coordinateSystem: "calendar",
      data: data.calendar_heatmap.map((item) => [item.date, item.count]),
    },
  });

  document.getElementById("insightList").innerHTML = data.insights.map((text) => `<div class="insight-item">${text}</div>`).join("");

  Promise.resolve(api.get("/api/workouts/overview"))
    .then((workoutData) => {
      const summaryNode = document.getElementById("dashboardWorkoutSummary");
      const insightNode = document.getElementById("dashboardWorkoutInsights");
      if (!summaryNode || !insightNode) return;
      summaryNode.innerHTML = `
        <div class="summary-card">
          <div class="label">近 14 天训练次数</div>
          <div class="value">${workoutData.summary_14d.session_count}</div>
          <div class="delta">总组数 ${workoutData.summary_14d.total_sets}</div>
        </div>
        <div class="summary-card">
          <div class="label">近 30 天训练次数</div>
          <div class="value">${workoutData.summary_30d.session_count}</div>
          <div class="delta">计划种类 ${Object.keys(workoutData.summary_30d.plan_counts || {}).length}</div>
        </div>
        <div class="summary-card">
          <div class="label">覆盖部位数</div>
          <div class="value">${Object.keys(workoutData.summary_14d.part_counts || {}).length}</div>
          <div class="delta">最近两周统计</div>
        </div>
      `;
      insightNode.innerHTML = (workoutData.recommendations || [])
        .map((item) => `<div class="insight-item">${item}</div>`)
        .join("");
    })
    .catch(() => {
      const summaryNode = document.getElementById("dashboardWorkoutSummary");
      const insightNode = document.getElementById("dashboardWorkoutInsights");
      if (summaryNode) summaryNode.innerHTML = `<div class="empty">训练模块数据暂时不可用。</div>`;
      if (insightNode) insightNode.innerHTML = "";
    });
}

async function initRecordsPage() {
  const page = document.body.dataset.page;
  if (page !== "records") return;

  const form = document.getElementById("recordForm");
  const filters = document.getElementById("filterForm");
  const metricFields = document.getElementById("metricFields");
  const listBody = document.getElementById("recordsBody");
  const cancelButton = document.getElementById("cancelEdit");
  let metrics = [];
  let editingId = null;

  function setDefaultRecordedAt() {
    form.recorded_at.value = toBeijingDateInputValue();
  }

  function fillDynamicFields(record = null) {
    metricFields.innerHTML = metrics
      .filter((metric) => metric.active)
      .map((metric) => `
        <label>
          ${metric.label} (${metric.unit || "无单位"})
          <input
            name="metric_${metric.id}"
            type="${metric.type === "number" ? "number" : "text"}"
            step="${metric.precision > 0 ? 1 / Math.pow(10, metric.precision) : 1}"
            value="${record?.values?.[metric.id] ?? ""}"
          />
        </label>
      `)
      .join("");
  }

  async function loadRecords() {
    const params = new URLSearchParams();
    const formData = new FormData(filters);
    for (const [key, value] of formData.entries()) {
      if (value) params.append(key, value.toString());
    }
    const [metricData, records] = await Promise.all([
      api.get("/api/metrics"),
      api.get(`/api/entries?${params.toString()}`),
    ]);
    metrics = metricData;
    if (!editingId) fillDynamicFields();
    renderRecords(records);
  }

  function renderRecords(records) {
    if (!records.length) {
      listBody.innerHTML = `<tr><td colspan="6"><div class="empty">还没有记录，先添加第一条数据。</div></td></tr>`;
      return;
    }
    const metricMap = Object.fromEntries(metrics.map((metric) => [metric.id, metric]));
    listBody.innerHTML = records
      .map((record) => {
        const values = Object.entries(record.values)
          .map(([metricId, value]) => `${metricMap[metricId]?.label || metricId}: ${value}${metricMap[metricId]?.unit || ""}`)
          .join("<br/>");
        const tags = (record.tags || []).map((tag) => `<span class="tag">${tag}</span>`).join("");
        return `
          <tr>
            <td>${formatBeijingDateTime(record.recorded_at)}</td>
            <td>${values || "无"}</td>
            <td>${record.note || "<span class='muted'>无备注</span>"}</td>
            <td>${tags ? `<div class="tag-list">${tags}</div>` : "<span class='muted'>无标签</span>"}</td>
            <td>${metrics.filter((metric) => metric.active && !(metric.id in record.values)).length} 项缺失</td>
            <td>
              <div class="actions">
                <button type="button" class="secondary" data-edit="${record.id}">编辑</button>
                <button type="button" class="danger" data-delete="${record.id}">删除</button>
              </div>
            </td>
          </tr>
        `;
      })
      .join("");

    records.forEach((record) => {
      const editButton = listBody.querySelector(`[data-edit="${record.id}"]`);
      if (editButton) {
        editButton.addEventListener("click", () => {
          editingId = record.id;
          form.recorded_at.value = toBeijingDateInputValue(new Date(record.recorded_at));
          form.note.value = record.note || "";
          form.tags.value = (record.tags || []).join(",");
          fillDynamicFields(record);
          cancelButton.hidden = false;
          window.scrollTo({ top: 0, behavior: "smooth" });
        });
      }

      const deleteButton = listBody.querySelector(`[data-delete="${record.id}"]`);
      if (deleteButton) {
        deleteButton.addEventListener("click", async () => {
          if (!window.confirm("确认删除这条记录？")) return;
          await api.send(`/api/entries/${record.id}`, "DELETE");
          updateStatus("recordStatus", "记录已删除");
          await loadRecords();
        });
      }
    });
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    const values = {};
    metrics.filter((metric) => metric.active).forEach((metric) => {
      const value = formData.get(`metric_${metric.id}`);
      if (value !== null && value !== "") values[metric.id] = value;
    });
    const payload = {
      recorded_at: `${formData.get("recorded_at")}:00+08:00`,
      values,
      note: formData.get("note") || "",
      tags: String(formData.get("tags") || "")
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    };
    try {
      if (editingId) {
        await api.send(`/api/entries/${editingId}`, "PUT", payload);
        updateStatus("recordStatus", "记录已更新");
      } else {
        await api.send("/api/entries", "POST", payload);
        updateStatus("recordStatus", "记录已新增");
      }
      form.reset();
      editingId = null;
      cancelButton.hidden = true;
      fillDynamicFields();
      setDefaultRecordedAt();
      await loadRecords();
    } catch (error) {
      updateStatus("recordStatus", parseError(error), true);
    }
  });

  cancelButton.addEventListener("click", () => {
    editingId = null;
    form.reset();
    fillDynamicFields();
    setDefaultRecordedAt();
    cancelButton.hidden = true;
  });

  filters.addEventListener("submit", async (event) => {
    event.preventDefault();
    await loadRecords();
  });

  setDefaultRecordedAt();
  await loadRecords();
}

async function initMetricsPage() {
  const page = document.body.dataset.page;
  if (page !== "metrics") return;

  const profileForm = document.getElementById("profileForm");
  const metricForm = document.getElementById("metricForm");
  const metricsList = document.getElementById("metricsList");
  const cancelButton = document.getElementById("cancelMetricEdit");
  let editingMetricId = null;

  async function loadPage() {
    const [profile, metrics] = await Promise.all([api.get("/api/profile"), api.get("/api/metrics")]);
    profileForm.height_cm.value = profile.height_cm;
    profileForm.timezone.value = profile.timezone;
    renderMetrics(metrics);
  }

  function applyMetricToForm(metric = null) {
    metricForm.metric_id.value = metric?.id || "";
    metricForm.label.value = metric?.label || "";
    metricForm.unit.value = metric?.unit || "";
    metricForm.type.value = metric?.type || "number";
    metricForm.color.value = metric?.color || "#0f62fe";
    metricForm.chart_type.value = metric?.chart_type || "line";
    metricForm.precision.value = metric?.precision ?? 1;
    metricForm.goal_direction.value = metric?.goal_direction || "neutral";
    metricForm.show_on_dashboard.checked = Boolean(metric?.show_on_dashboard ?? true);
    metricForm.active.checked = Boolean(metric?.active ?? true);
    metricForm.sort_order.value = metric?.sort_order ?? 100;
  }

  function renderMetrics(metrics) {
    if (!metrics.length) {
      metricsList.innerHTML = `<div class="empty">暂无指标配置。</div>`;
      return;
    }

    metricsList.innerHTML = metrics
      .map((metric) => `
        <div class="panel">
          <div class="actions" style="justify-content: space-between; align-items: center;">
            <div>
              <h4>${metric.label} <small>${metric.id}</small></h4>
              <p class="muted">${metric.type} · ${metric.unit || "无单位"} · ${metric.chart_type} · ${metric.active ? "启用" : "已归档"}</p>
            </div>
            <div class="tag" style="background:${metric.color}22;color:${metric.color};">${metric.goal_direction}</div>
          </div>
          <div class="actions">
            <button type="button" class="secondary" data-edit="${metric.id}">编辑</button>
            <button type="button" class="danger" data-archive="${metric.id}">归档</button>
          </div>
        </div>
      `)
      .join("");

    metrics.forEach((metric) => {
      const editButton = metricsList.querySelector(`[data-edit="${metric.id}"]`);
      if (editButton) {
        editButton.addEventListener("click", () => {
          editingMetricId = metric.id;
          applyMetricToForm(metric);
          metricForm.metric_id.disabled = true;
          cancelButton.hidden = false;
          window.scrollTo({ top: 0, behavior: "smooth" });
        });
      }

      const archiveButton = metricsList.querySelector(`[data-archive="${metric.id}"]`);
      if (archiveButton) {
        archiveButton.addEventListener("click", async () => {
          if (!window.confirm(`确认归档指标 ${metric.label}？`)) return;
          await api.send(`/api/metrics/${metric.id}`, "DELETE");
          updateStatus("metricStatus", "指标已归档");
          await loadPage();
        });
      }
    });
  }

  profileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api.send("/api/profile", "PUT", {
        height_cm: Number(profileForm.height_cm.value),
        timezone: profileForm.timezone.value.trim(),
      });
      updateStatus("profileStatus", "基础资料已保存");
    } catch (error) {
      updateStatus("profileStatus", parseError(error), true);
    }
  });

  metricForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
      id: metricForm.metric_id.value.trim(),
      label: metricForm.label.value.trim(),
      unit: metricForm.unit.value.trim(),
      type: metricForm.type.value,
      color: metricForm.color.value,
      chart_type: metricForm.chart_type.value,
      precision: Number(metricForm.precision.value),
      goal_direction: metricForm.goal_direction.value,
      active: metricForm.active.checked,
      show_on_dashboard: metricForm.show_on_dashboard.checked,
      sort_order: Number(metricForm.sort_order.value),
    };

    try {
      if (editingMetricId) {
        const { id, ...updatePayload } = payload;
        await api.send(`/api/metrics/${editingMetricId}`, "PUT", updatePayload);
        updateStatus("metricStatus", "指标已更新");
      } else {
        await api.send("/api/metrics", "POST", payload);
        updateStatus("metricStatus", "指标已新增");
      }
      editingMetricId = null;
      metricForm.reset();
      applyMetricToForm();
      metricForm.metric_id.disabled = false;
      cancelButton.hidden = true;
      await loadPage();
    } catch (error) {
      updateStatus("metricStatus", parseError(error), true);
    }
  });

  cancelButton.addEventListener("click", () => {
    editingMetricId = null;
    metricForm.reset();
    applyMetricToForm();
    metricForm.metric_id.disabled = false;
    cancelButton.hidden = true;
  });

  applyMetricToForm();
  await loadPage();
}

function createWorkoutHelpers() {
  let overview = null;
  let exerciseRowSeed = 0;

  function activeParts() {
    return (overview?.catalog?.parts || []).filter((part) => part.active);
  }

  function exercisesForPart(partId) {
    return overview?.catalog?.exercises?.[partId] || [];
  }

  function fillSelectOptions(select, items, placeholder, valueKey = "id", labelKey = "label") {
    if (!select) return;
    const options = placeholder ? [`<option value="">${placeholder}</option>`] : [];
    items.forEach((item) => {
      options.push(`<option value="${item[valueKey]}">${item[labelKey]}</option>`);
    });
    select.innerHTML = options.join("");
  }

  function fillSessionExerciseSelect(select, detailInput, partId) {
    const exercises = exercisesForPart(partId);
    select.innerHTML = exercises
      .filter((exercise) => exercise.active)
      .map((exercise) => `<option value="${exercise.id}">${exercise.name}</option>`)
      .join("");
    const selectedExercise = exercises.find((exercise) => exercise.id === select.value) || exercises[0];
    if (selectedExercise) {
      detailInput.placeholder = selectedExercise.detail_placeholder || "阻力、坡度、配速、握距或器械细节等";
      select.value = selectedExercise.id;
    }
  }

  function setOverview(data) {
    overview = data;
  }

  function nextExerciseRowId() {
    exerciseRowSeed += 1;
    return exerciseRowSeed;
  }

  function resetExerciseRowSeed() {
    exerciseRowSeed = 0;
  }

  return {
    activeParts,
    exercisesForPart,
    fillSelectOptions,
    fillSessionExerciseSelect,
    setOverview,
    nextExerciseRowId,
    resetExerciseRowSeed,
    getOverview: () => overview,
  };
}

function renderWorkoutCatalogSummary(helpers, presetOverviewGrid, catalogPanels, planPanels) {
  const overview = helpers.getOverview();
  if (!overview) return;
  const plans = overview.plans || [];
  const exerciseCount = Object.values(overview.catalog?.exercises || {}).reduce((sum, items) => sum + items.length, 0);

  if (presetOverviewGrid) {
    presetOverviewGrid.innerHTML = `
      <div class="summary-card">
        <div class="label">内置训练部位</div>
        <div class="value">${helpers.activeParts().length}</div>
        <div class="delta">已启用部位</div>
      </div>
      <div class="summary-card">
        <div class="label">内置动作模板</div>
        <div class="value">${exerciseCount}</div>
        <div class="delta">可直接选用</div>
      </div>
      <div class="summary-card">
        <div class="label">内置分组计划</div>
        <div class="value">${plans.filter((plan) => plan.active).length}</div>
        <div class="delta">支持直接套用</div>
      </div>
    `;
  }

  if (catalogPanels) {
    catalogPanels.innerHTML = helpers.activeParts()
      .map((part) => {
        const exercises = helpers.exercisesForPart(part.id);
        return `
          <div class="panel">
            <div class="actions" style="justify-content: space-between; align-items: center;">
              <div class="actions" style="gap:10px; align-items:center;">
                <h4>${part.label}</h4>
                <span class="tag" style="background:${part.color}22;color:${part.color};">${exercises.length} 个动作</span>
              </div>
              <button type="button" class="action-ghost-danger" data-delete-part="${part.id}">删除部位</button>
            </div>
            <div class="list-grid compact-list">
              ${exercises
                .map(
                  (exercise) => `
                    <div class="compact-item">
                      <div class="compact-item-head">
                        <strong>${exercise.name}</strong>
                        <button type="button" class="action-ghost-danger" data-delete-exercise="${part.id}:${exercise.id}">删除动作</button>
                      </div>
                      <div class="muted">${exercise.description || "无描述"}</div>
                      <div class="tiny-note">${exercise.detail_placeholder || "可自由填写动作细节"}</div>
                    </div>
                  `
                )
                .join("")}
            </div>
          </div>
        `;
      })
      .join("");
  }

  if (planPanels) {
    planPanels.innerHTML = plans.length
      ? plans
          .map(
            (plan) => `
              <div class="panel">
                <div class="actions" style="justify-content: space-between; align-items: center;">
                  <div class="actions" style="gap:10px; align-items:center;">
                    <h4>${plan.name}</h4>
                    <span class="tag">${plan.groups.length} 个组</span>
                  </div>
                  <button type="button" class="action-ghost-danger" data-delete-plan="${plan.id}">删除计划</button>
                </div>
                <div class="muted" style="margin-bottom: 12px;">${plan.description || "无计划说明"}</div>
                <div class="list-grid compact-list">
                  ${plan.groups
                    .map((group) => {
                      const part = helpers.activeParts().find((item) => item.id === group.part_id);
                      const exerciseNames = group.exercise_ids
                        .map((exerciseId) => helpers.exercisesForPart(group.part_id).find((item) => item.id === exerciseId)?.name || exerciseId)
                        .join("、");
                      return `
                        <div class="compact-item">
                          <strong>${group.name}</strong>
                          <div class="muted">${part?.label || group.part_id} · ${exerciseNames}</div>
                          <div class="tiny-note">${group.notes || "无组说明"}</div>
                        </div>
                      `;
                    })
                    .join("")}
                </div>
              </div>
            `
          )
          .join("")
      : `<div class="empty">还没有训练计划。</div>`;
  }
}

function renderWorkoutRecommendations(overview, summaryGrid, recommendationList) {
  if (!summaryGrid || !recommendationList) return;
  summaryGrid.innerHTML = `
    <div class="summary-card">
      <div class="label">近 14 天训练</div>
      <div class="value">${overview.summary_14d.session_count}</div>
      <div class="delta">总组数 ${overview.summary_14d.total_sets}</div>
    </div>
    <div class="summary-card">
      <div class="label">近 30 天有氧</div>
      <div class="value">${overview.summary_30d.cardio_sessions || 0}</div>
      <div class="delta">累计 ${overview.summary_30d.cardio_duration_minutes || 0} 分钟</div>
    </div>
    <div class="summary-card">
      <div class="label">活跃部位覆盖</div>
      <div class="value">${Object.keys(overview.summary_14d.part_counts || {}).length}</div>
      <div class="delta">最近 14 天</div>
    </div>
  `;
  recommendationList.innerHTML = (overview.recommendations || [])
    .map((text) => `<div class="insight-item">${text}</div>`)
    .join("");
}

async function initWorkoutsPage() {
  const page = document.body.dataset.page;
  if (page !== "workouts") return;
  window.scrollTo({ top: 0, behavior: "auto" });

  const sessionForm = document.getElementById("workoutSessionForm");
  const sessionsBody = document.getElementById("workoutSessionsBody");
  const summaryGrid = document.getElementById("workoutSummaryGrid");
  const recommendationList = document.getElementById("workoutRecommendationList");
  const exerciseRowsContainer = document.getElementById("workoutExerciseRows");
  const addExerciseRowButton = document.getElementById("addWorkoutExerciseRow");
  const submitButton = document.getElementById("workoutSessionSubmit");
  const cancelEditButton = document.getElementById("cancelWorkoutEdit");
  const helpers = createWorkoutHelpers();

  function setWorkoutDefaultRecordedAt() {
    sessionForm.recorded_at.value = toBeijingDateInputValue();
  }

  function resetWorkoutForm() {
    sessionForm.reset();
    sessionForm.session_id.value = "";
    submitButton.textContent = "保存训练记录";
    cancelEditButton.style.display = "none";
    setWorkoutDefaultRecordedAt();
    resetExerciseRows();
  }

  function applySessionToForm(session) {
    sessionForm.session_id.value = session.id;
    sessionForm.recorded_at.value = toBeijingDateInputValue(session.recorded_at);
    sessionForm.plan_id.value = session.plan_id || "";
    sessionForm.energy_level.value = session.energy_level ?? "";
    sessionForm.note.value = session.note || "";
    sessionForm.tags.value = (session.tags || []).join(", ");
    const presets = (session.exercises || []).map((exercise) => ({
      part_id: exercise.part_id,
      exercise_id: exercise.exercise_id,
      detail: exercise.detail || "",
      sets: exercise.sets,
      reps: exercise.reps,
      weight_kg: exercise.weight_kg,
      duration_minutes: exercise.duration_minutes,
      rpe: exercise.rpe,
    }));
    resetExerciseRows(presets);
    submitButton.textContent = "更新训练记录";
    cancelEditButton.style.display = "";
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function deleteSession(sessionId) {
    await api.send(`/api/workouts/sessions/${sessionId}`, "DELETE");
    if (sessionForm.session_id.value === sessionId) {
      resetWorkoutForm();
    }
    updateStatus("workoutSessionStatus", "训练记录已删除")
    await loadOverview();
  }

  function workoutExerciseRowTemplate(rowId) {
    return `
      <div class="workout-exercise-row" data-row-id="${rowId}">
        <div class="actions" style="justify-content: space-between; align-items: center;">
          <h4>动作 ${rowId}</h4>
          <button type="button" class="secondary workout-row-remove">移除</button>
        </div>
        <div class="form-grid">
          <label>
            训练部位
            <select name="part_id" required></select>
          </label>
          <label>
            动作
            <select name="exercise_id" required></select>
          </label>
          <label>
            细节描述
            <input type="text" name="detail" placeholder="握距、节奏、器械细节等" />
          </label>
        </div>
        <div class="form-grid workout-strength-fields">
          <label class="field-sets">
            组数
            <input type="number" name="sets" min="1" max="50" value="4" />
          </label>
          <label class="field-reps">
            次数
            <input type="number" name="reps" min="1" max="500" value="10" />
          </label>
          <label class="field-weight">
            重量（kg）
            <input type="number" name="weight_kg" min="0" step="0.5" />
          </label>
          <label class="field-duration">
            时长（分钟）
            <input type="number" name="duration_minutes" min="0" />
          </label>
          <label class="field-rpe">
            RPE
            <input type="number" name="rpe" min="0" max="10" step="0.5" />
          </label>
        </div>
      </div>
    `;
  }

  function syncWorkoutRowMode(rowNode) {
    const partSelect = rowNode.querySelector('select[name="part_id"]');
    const setsInput = rowNode.querySelector('input[name="sets"]');
    const repsInput = rowNode.querySelector('input[name="reps"]');
    const weightInput = rowNode.querySelector('input[name="weight_kg"]');
    const durationInput = rowNode.querySelector('input[name="duration_minutes"]');
    const detailInput = rowNode.querySelector('input[name="detail"]');
    const setsField = rowNode.querySelector(".field-sets");
    const repsField = rowNode.querySelector(".field-reps");
    const weightField = rowNode.querySelector(".field-weight");
    const rpeField = rowNode.querySelector(".field-rpe");
    const isCardio = partSelect.value === "cardio";

    if (isCardio) {
      setsInput.value = "1";
      repsInput.value = "";
      weightInput.value = "";
      rowNode.querySelector('input[name="rpe"]').value = "";
      durationInput.required = true;
      durationInput.placeholder = "建议填写时长";
      detailInput.placeholder = detailInput.placeholder || "阻力、坡度、配速、心率等";
      if (setsField) setsField.style.display = "none";
      if (repsField) repsField.style.display = "none";
      if (weightField) weightField.style.display = "none";
      if (rpeField) rpeField.style.display = "none";
    } else {
      if (!setsInput.value) setsInput.value = "4";
      durationInput.required = false;
      durationInput.placeholder = "";
      if (setsField) setsField.style.display = "";
      if (repsField) repsField.style.display = "";
      if (weightField) weightField.style.display = "";
      if (rpeField) rpeField.style.display = "";
    }
  }

  function bindExerciseRow(rowNode, preset = {}) {
    const partSelect = rowNode.querySelector('select[name="part_id"]');
    const exerciseSelect = rowNode.querySelector('select[name="exercise_id"]');
    const detailInput = rowNode.querySelector('input[name="detail"]');
    const removeButton = rowNode.querySelector(".workout-row-remove");

    helpers.fillSelectOptions(partSelect, helpers.activeParts(), "", "id", "label");
    partSelect.value = preset.part_id || helpers.activeParts()[0]?.id || "";
    helpers.fillSessionExerciseSelect(exerciseSelect, detailInput, partSelect.value);
    if (preset.exercise_id) {
      exerciseSelect.value = preset.exercise_id;
      helpers.fillSessionExerciseSelect(exerciseSelect, detailInput, partSelect.value);
    }
    if (preset.detail) detailInput.value = preset.detail;
    if (preset.sets) rowNode.querySelector('input[name="sets"]').value = preset.sets;
    if (preset.reps) rowNode.querySelector('input[name="reps"]').value = preset.reps;
    if (preset.weight_kg !== undefined && preset.weight_kg !== null) rowNode.querySelector('input[name="weight_kg"]').value = preset.weight_kg;
    if (preset.duration_minutes !== undefined && preset.duration_minutes !== null) rowNode.querySelector('input[name="duration_minutes"]').value = preset.duration_minutes;
    if (preset.rpe !== undefined && preset.rpe !== null) rowNode.querySelector('input[name="rpe"]').value = preset.rpe;
    syncWorkoutRowMode(rowNode);

    partSelect.addEventListener("change", () => {
      helpers.fillSessionExerciseSelect(exerciseSelect, detailInput, partSelect.value);
      syncWorkoutRowMode(rowNode);
    });
    exerciseSelect.addEventListener("change", () => {
      helpers.fillSessionExerciseSelect(exerciseSelect, detailInput, partSelect.value);
    });
    removeButton.addEventListener("click", () => {
      rowNode.remove();
      if (!exerciseRowsContainer.children.length) addExerciseRow();
    });
  }

  function addExerciseRow(preset = {}) {
    const rowId = helpers.nextExerciseRowId();
    exerciseRowsContainer.insertAdjacentHTML("beforeend", workoutExerciseRowTemplate(rowId));
    const rowNode = exerciseRowsContainer.lastElementChild;
    bindExerciseRow(rowNode, preset);
  }

  function resetExerciseRows(presets = []) {
    exerciseRowsContainer.innerHTML = "";
    helpers.resetExerciseRowSeed();
    if (!presets.length) {
      addExerciseRow();
      return;
    }
    presets.forEach((preset) => addExerciseRow(preset));
  }

  function renderOverview() {
    const overview = helpers.getOverview();
    if (!overview) return;
    const plans = overview.plans || [];
    const planMap = Object.fromEntries(plans.map((plan) => [plan.id, plan]));
    renderWorkoutRecommendations(overview, summaryGrid, recommendationList);
    helpers.fillSelectOptions(
      sessionForm.plan_id,
      [{ id: "", name: "不使用计划" }, ...plans.filter((plan) => plan.active).map((plan) => ({ id: plan.id, name: plan.name }))],
      "",
      "id",
      "name"
    );
    if (!exerciseRowsContainer.children.length) {
      resetExerciseRows();
    } else {
      Array.from(exerciseRowsContainer.children).forEach((rowNode) => {
        const partSelect = rowNode.querySelector('select[name="part_id"]');
        const exerciseSelect = rowNode.querySelector('select[name="exercise_id"]');
        const detailInput = rowNode.querySelector('input[name="detail"]');
        helpers.fillSelectOptions(partSelect, helpers.activeParts(), "", "id", "label");
        partSelect.value = helpers.activeParts().find((part) => part.id === partSelect.value)?.id || helpers.activeParts()[0]?.id || "";
        helpers.fillSessionExerciseSelect(exerciseSelect, detailInput, partSelect.value);
        syncWorkoutRowMode(rowNode);
      });
    }

    if (!overview.sessions.length) {
      sessionsBody.innerHTML = `<tr><td colspan="5"><div class="empty">还没有训练记录，先录一组动作。</div></td></tr>`;
    } else {
      sessionsBody.innerHTML = overview.sessions
        .map((session) => {
          const exercises = (session.exercises || [])
            .map((exercise) => {
              const part = helpers.activeParts().find((item) => item.id === exercise.part_id);
              const exerciseDef = helpers.exercisesForPart(exercise.part_id).find((item) => item.id === exercise.exercise_id);
              const isCardio = exercise.part_id === "cardio";
              const metricBits = isCardio ? [] : [`${exercise.sets}组`];
              if (!isCardio && exercise.reps) metricBits.push(`x ${exercise.reps}次`);
              if (!isCardio && exercise.weight_kg) metricBits.push(`${exercise.weight_kg}kg`);
              if (exercise.duration_minutes) metricBits.push(`${exercise.duration_minutes}分钟`);
              if (!isCardio && exercise.rpe !== null && exercise.rpe !== undefined) metricBits.push(`RPE ${exercise.rpe}`);
              return `${part?.label || exercise.part_id} / ${exerciseDef?.name || exercise.exercise_id} · ${metricBits.join(" · ")}${exercise.detail ? `<br/><span class="muted">${exercise.detail}</span>` : ""}`;
            })
            .join("<br/><br/>");
          const tags = (session.tags || []).map((tag) => `<span class="tag">${tag}</span>`).join("");
          return `
            <tr>
              <td>${formatBeijingDateTime(session.recorded_at)}</td>
              <td>${planMap[session.plan_id]?.name || "<span class='muted'>未使用计划</span>"}</td>
              <td>${exercises}</td>
              <td>${session.note || "<span class='muted'>无备注</span>"}</td>
              <td>${tags || "<span class='muted'>无标签</span>"}</td>
              <td>
                <div class="actions">
                  <button type="button" class="secondary" data-edit-session="${session.id}">编辑</button>
                  <button type="button" class="action-ghost-danger" data-delete-session="${session.id}">删除</button>
                </div>
              </td>
            </tr>
          `;
        })
        .join("");
      sessionsBody.querySelectorAll("[data-edit-session]").forEach((button) => {
        button.addEventListener("click", () => {
          const session = overview.sessions.find((item) => item.id === button.dataset.editSession);
          if (session) applySessionToForm(session);
        });
      });
      sessionsBody.querySelectorAll("[data-delete-session]").forEach((button) => {
        button.addEventListener("click", async () => {
          if (!window.confirm("确认删除这条训练记录？")) return;
          try {
            await deleteSession(button.dataset.deleteSession);
          } catch (error) {
            updateStatus("workoutSessionStatus", parseError(error), true);
          }
        });
      });
    }
  }

  async function loadOverview() {
    helpers.setOverview(await api.get("/api/workouts/overview"));
    renderOverview();
  }

  addExerciseRowButton.addEventListener("click", () => addExerciseRow());
  cancelEditButton.addEventListener("click", () => resetWorkoutForm());
  sessionForm.plan_id.addEventListener("change", () => {
    if (sessionForm.session_id.value) return;
    const selectedPlan = (helpers.getOverview()?.plans || []).find((plan) => plan.id === sessionForm.plan_id.value);
    if (!selectedPlan) return;
    const presets = selectedPlan.groups.flatMap((group) =>
      group.exercise_ids.map((exerciseId) => ({
        part_id: group.part_id,
        exercise_id: exerciseId,
      }))
    );
    resetExerciseRows(presets);
  });

  sessionForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const exercises = Array.from(exerciseRowsContainer.querySelectorAll(".workout-exercise-row")).map((rowNode) => ({
        part_id: rowNode.querySelector('select[name="part_id"]').value,
        exercise_id: rowNode.querySelector('select[name="exercise_id"]').value,
        detail: rowNode.querySelector('input[name="detail"]').value.trim(),
        sets: Number(rowNode.querySelector('input[name="sets"]').value || 1),
        reps: rowNode.querySelector('input[name="reps"]').value ? Number(rowNode.querySelector('input[name="reps"]').value) : null,
        weight_kg: rowNode.querySelector('input[name="weight_kg"]').value ? Number(rowNode.querySelector('input[name="weight_kg"]').value) : null,
        duration_minutes: rowNode.querySelector('input[name="duration_minutes"]').value ? Number(rowNode.querySelector('input[name="duration_minutes"]').value) : null,
        rpe: rowNode.querySelector('input[name="rpe"]').value ? Number(rowNode.querySelector('input[name="rpe"]').value) : null,
        note: "",
      })).map((exercise) => exercise.part_id === "cardio"
        ? {
            ...exercise,
            sets: 1,
            reps: null,
            weight_kg: null,
            rpe: null,
          }
        : exercise);
      const payload = {
        recorded_at: `${sessionForm.recorded_at.value}:00+08:00`,
        plan_id: sessionForm.plan_id.value || null,
        energy_level: sessionForm.energy_level.value ? Number(sessionForm.energy_level.value) : null,
        note: sessionForm.note.value.trim(),
        tags: sessionForm.tags.value
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
        exercises,
      };
      if (sessionForm.session_id.value) {
        await api.send(`/api/workouts/sessions/${sessionForm.session_id.value}`, "PUT", payload);
        updateStatus("workoutSessionStatus", "训练记录已更新");
      } else {
        await api.send("/api/workouts/sessions", "POST", payload);
        updateStatus("workoutSessionStatus", "训练记录已保存");
      }
      resetWorkoutForm();
      await loadOverview();
    } catch (error) {
      updateStatus("workoutSessionStatus", parseError(error), true);
    }
  });

  resetWorkoutForm();
  await loadOverview();
}

async function initWorkoutSettingsPage() {
  const page = document.body.dataset.page;
  if (page !== "workout-settings") return;
  window.scrollTo({ top: 0, behavior: "auto" });

  const partForm = document.getElementById("workoutPartForm");
  const exerciseForm = document.getElementById("workoutExerciseForm");
  const planForm = document.getElementById("workoutPlanForm");
  const catalogPanels = document.getElementById("workoutCatalogPanels");
  const planPanels = document.getElementById("workoutPlanPanels");
  const presetOverviewGrid = document.getElementById("presetOverviewGrid");
  const helpers = createWorkoutHelpers();

  function bindDeleteActions() {
    catalogPanels.querySelectorAll("[data-delete-part]").forEach((button) => {
      button.addEventListener("click", async () => {
        const partId = button.dataset.deletePart;
        if (!window.confirm(`确认删除训练部位 ${partId}？若已被计划或记录使用，将无法删除。`)) return;
        try {
          await api.send(`/api/workouts/parts/${partId}`, "DELETE");
          updateStatus("workoutPartStatus", "训练部位已删除");
          await loadOverview();
        } catch (error) {
          updateStatus("workoutPartStatus", parseError(error), true);
        }
      });
    });

    catalogPanels.querySelectorAll("[data-delete-exercise]").forEach((button) => {
      button.addEventListener("click", async () => {
        const [partId, exerciseId] = String(button.dataset.deleteExercise || "").split(":");
        if (!partId || !exerciseId) return;
        if (!window.confirm(`确认删除动作 ${exerciseId}？若已被计划或记录使用，将无法删除。`)) return;
        try {
          await api.send(`/api/workouts/parts/${partId}/exercises/${exerciseId}`, "DELETE");
          updateStatus("workoutExerciseStatus", "训练动作已删除");
          await loadOverview();
        } catch (error) {
          updateStatus("workoutExerciseStatus", parseError(error), true);
        }
      });
    });

    planPanels.querySelectorAll("[data-delete-plan]").forEach((button) => {
      button.addEventListener("click", async () => {
        const planId = button.dataset.deletePlan;
        if (!window.confirm(`确认删除训练计划 ${planId}？`)) return;
        try {
          await api.send(`/api/workouts/plans/${planId}`, "DELETE");
          updateStatus("workoutPlanStatus", "训练计划已删除");
          await loadOverview();
        } catch (error) {
          updateStatus("workoutPlanStatus", parseError(error), true);
        }
      });
    });
  }

  function renderOverview() {
    renderWorkoutCatalogSummary(helpers, presetOverviewGrid, catalogPanels, planPanels);
    bindDeleteActions();
    const parts = helpers.activeParts();
    const partOptions = parts.map((part) => `<option value="${part.id}">${part.label}</option>`).join("");
    helpers.fillSelectOptions(exerciseForm.part_id, parts, "请选择部位", "id", "label");
    helpers.fillSelectOptions(planForm.group_part_id, parts, "请选择部位", "id", "label");
    if (parts.length) {
      const exercisePartValue = parts.some((part) => part.id === exerciseForm.part_id.value) ? exerciseForm.part_id.value : parts[0].id;
      const planPartValue = parts.some((part) => part.id === planForm.group_part_id.value) ? planForm.group_part_id.value : parts[0].id;
      exerciseForm.part_id.innerHTML = partOptions;
      planForm.group_part_id.innerHTML = partOptions;
      exerciseForm.part_id.value = exercisePartValue;
      planForm.group_part_id.value = planPartValue;
      fillExerciseMultiSelectForSettings(planPartValue);
    } else {
      exerciseForm.part_id.innerHTML = `<option value="">暂无部位，请先新增训练部位</option>`;
      planForm.group_part_id.innerHTML = `<option value="">暂无部位，请先新增训练部位</option>`;
      planForm.group_exercise_ids.innerHTML = "";
    }
  }

  async function loadOverview() {
    helpers.setOverview(await api.get("/api/workouts/overview"));
    renderOverview();
  }

  function fillExerciseMultiSelectForSettings(partId) {
    const select = planForm.group_exercise_ids;
    const exercises = helpers.exercisesForPart(partId);
    select.innerHTML = exercises
      .filter((exercise) => exercise.active)
      .map((exercise) => `<option value="${exercise.id}">${exercise.name}</option>`)
      .join("");
    if (!select.innerHTML) {
      select.innerHTML = `<option value="" disabled>该部位下暂无动作</option>`;
    }
  }

  planForm.group_part_id.addEventListener("change", () => fillExerciseMultiSelectForSettings(planForm.group_part_id.value));

  partForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const partId = partForm.part_id.value.trim();
      await api.send(`/api/workouts/parts/${partId}`, "POST", {
        label: partForm.label.value.trim(),
        color: partForm.color.value,
        sort_order: Number(partForm.sort_order.value || 100),
        active: partForm.active.checked,
      });
      updateStatus("workoutPartStatus", "训练部位已保存");
      partForm.reset();
      partForm.color.value = "#ef4444";
      await loadOverview();
    } catch (error) {
      updateStatus("workoutPartStatus", parseError(error), true);
    }
  });

  exerciseForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const partId = exerciseForm.part_id.value;
      const exerciseId = exerciseForm.exercise_id.value.trim();
      await api.send(`/api/workouts/parts/${partId}/exercises/${exerciseId}`, "POST", {
        name: exerciseForm.name.value.trim(),
        description: exerciseForm.description.value.trim(),
        detail_placeholder: exerciseForm.detail_placeholder.value.trim(),
        active: true,
        sort_order: 100,
      });
      updateStatus("workoutExerciseStatus", "训练动作已保存");
      exerciseForm.reset();
      await loadOverview();
    } catch (error) {
      updateStatus("workoutExerciseStatus", parseError(error), true);
    }
  });

  planForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const selectedExerciseIds = Array.from(planForm.group_exercise_ids.selectedOptions).map((option) => option.value);
      await api.send("/api/workouts/plans", "POST", {
        name: planForm.name.value.trim(),
        description: planForm.description.value.trim(),
        active: true,
        groups: [
          {
            name: planForm.group_name.value.trim(),
            part_id: planForm.group_part_id.value,
            exercise_ids: selectedExerciseIds,
            notes: planForm.group_notes.value.trim(),
            sort_order: 100,
          },
        ],
      });
      updateStatus("workoutPlanStatus", "训练计划已保存");
      planForm.reset();
      await loadOverview();
    } catch (error) {
      updateStatus("workoutPlanStatus", parseError(error), true);
    }
  });

  await loadOverview();
}

async function initLoginPage() {
  const page = document.body.dataset.page;
  if (page !== "login") return;
  const form = document.getElementById("loginForm");
  try {
    await api.get("/api/auth/me");
    window.location.href = "/";
    return;
  } catch {
    // stay on login page
  }
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    try {
      await api.send("/api/auth/login", "POST", {
        password: String(formData.get("password") || ""),
      });
      window.location.href = "/";
    } catch (error) {
      updateStatus("loginStatus", parseError(error), true);
    }
  });
}

document.addEventListener("DOMContentLoaded", async () => {
  initLayout();
  await initLoginPage();
  await initDashboardPage();
  await initRecordsPage();
  await initMetricsPage();
  await initWorkoutsPage();
  await initWorkoutSettingsPage();
});
