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
  const options = {
    timeZone: BEIJING_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  };
  if (includeTime) {
    options.hour = "2-digit";
    options.minute = "2-digit";
    options.hour12 = false;
  }
  const formatted = new Intl.DateTimeFormat("zh-CN", options).format(date);
  return formatted.replace(/\//g, "-");
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

  const trendMetrics = ["weight_kg", "body_fat_pct", "bmi", "waist_cm"];
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

  const bestCorrelation = [...data.correlations].sort((a, b) => (Math.abs(b.correlation || 0) - Math.abs(a.correlation || 0)))[0];
  if (bestCorrelation && bestCorrelation.points.length) {
    makeChart("correlationChart", {
      tooltip: { trigger: "item" },
      xAxis: { type: "value", name: "体重 kg" },
      yAxis: { type: "value", name: metrics[bestCorrelation.metric_id]?.label || bestCorrelation.metric_id },
      series: [
        {
          type: "scatter",
          symbolSize: 12,
          data: bestCorrelation.points.map((point) => [point.x, point.y]),
          itemStyle: { color: metrics[bestCorrelation.metric_id]?.color || "#7c3aed" },
        },
      ],
      graphic: bestCorrelation.correlation === null ? [] : [
        {
          type: "text",
          left: "center",
          top: 10,
          style: {
            text: `相关系数 r = ${bestCorrelation.correlation}`,
            fill: "#60708b",
            font: "14px sans-serif",
          },
        },
      ],
    });
  } else {
    document.getElementById("correlationChart").outerHTML = `<div id="correlationChart" class="empty">至少需要两项可配对的数值指标记录，相关性图才会显示。</div>`;
  }

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
});
