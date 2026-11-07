/* 变电检修 · 安全规程刷题 —— 交互逻辑
 * 数据来自 js/questions.js（window.QUIZ_DATA / window.QUIZ_META）
 */
(function () {
  "use strict";

  var QUESTIONS = Array.isArray(window.QUIZ_DATA) ? window.QUIZ_DATA : [];
  var META = window.QUIZ_META || {};
  var LS_KEY = "substation-quiz-v1";
  var LETTERS = "ABCDEFGH";

  var CATEGORY_ORDER = ["单选题", "读图题", "问答题"];
  var FILTER_DEFS = {
    all: function () { return true; },
    single: function (q) { return q.category === "单选题"; },
    image: function (q) { return q.category === "读图题"; },
    essay: function (q) { return q.type === "essay"; },
    wrong: function (q, rec) { return rec && rec.correct === false; }
  };

  var QID = {};
  QUESTIONS.forEach(function (q) { QID[q.id] = q; });

  var state = loadState();
  var answerShown = false;
  var els = {};

  /* ---------------- 状态读写 ---------------- */
  function defaultState() {
    return {
      shuffle: true,
      filter: "all",
      index: 0,
      order: [],
      optionOrders: {},
      records: {},
      theme: "auto"
    };
  }

  function loadState() {
    var s = defaultState();
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (!raw) return s;
      var saved = JSON.parse(raw);
      if (!saved || typeof saved !== "object") return s;
      if (typeof saved.shuffle === "boolean") s.shuffle = saved.shuffle;
      if (typeof saved.filter === "string" && FILTER_DEFS[saved.filter]) s.filter = saved.filter;
      if (typeof saved.index === "number" && saved.index >= 0) s.index = saved.index;
      if (Array.isArray(saved.order)) s.order = saved.order.filter(function (id) { return !!QID[id]; });
      if (saved.optionOrders && typeof saved.optionOrders === "object") s.optionOrders = saved.optionOrders;
      if (saved.records && typeof saved.records === "object") s.records = sanitizeRecords(saved.records);
      if (saved.theme === "light" || saved.theme === "dark" || saved.theme === "auto") s.theme = saved.theme;
    } catch (e) { /* localStorage 不可用则用内存状态 */ }
    return s;
  }

  function sanitizeRecords(records) {
    var out = {};
    Object.keys(records).forEach(function (id) {
      if (!QID[id]) return;
      var r = records[id];
      if (!r || typeof r !== "object") return;
      out[id] = {
        selectedKey: typeof r.selectedKey === "string" ? r.selectedKey : null,
        revealed: !!r.revealed,
        correct: typeof r.correct === "boolean" ? r.correct : undefined,
        ts: typeof r.ts === "number" ? r.ts : 0
      };
    });
    return out;
  }

  function saveState() {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify({
        shuffle: state.shuffle,
        filter: state.filter,
        index: state.index,
        order: state.order,
        optionOrders: state.optionOrders,
        records: state.records,
        theme: state.theme
      }));
    } catch (e) { /* 忽略：隐私模式或空间不足 */ }
  }

  /* ---------------- 选题 / 乱序 ---------------- */
  function shuffleArr(arr) {
    for (var i = arr.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var t = arr[i]; arr[i] = arr[j]; arr[j] = t;
    }
    return arr;
  }

  function idsForFilter(filter) {
    return QUESTIONS.filter(function (q) {
      return FILTER_DEFS[filter] ? FILTER_DEFS[filter](q, state.records[q.id]) : true;
    }).map(function (q) { return q.id; });
  }

  function buildOrder(filter) {
    var ids = idsForFilter(filter);
    if (state.shuffle) shuffleArr(ids);
    return ids;
  }

  function ensureOrder() {
    // 校验已保存的 order 是否与当前筛选匹配，不匹配则重建
    var expected = idsForFilter(state.filter);
    if (!state.order.length) {
      state.order = state.shuffle ? shuffleArr(expected.slice()) : expected;
      state.index = 0;
    } else if (state.filter !== "wrong") {
      // 静态筛选（all/single/image/essay）：数量必须一致，否则数据已更新，重建
      if (state.order.length !== expected.length) {
        state.order = state.shuffle ? shuffleArr(expected.slice()) : expected;
        state.index = 0;
      }
    }
    if (state.index >= state.order.length) state.index = Math.max(0, state.order.length - 1);
    if (state.index < 0) state.index = 0;
  }

  function currentQuestion() {
    return QID[state.order[state.index]] || null;
  }

  function getRecord(id) {
    return state.records[id] || null;
  }

  function displayOptions(q) {
    var originals = q.options || [];
    if (!originals.length) return [];
    if (!state.shuffle) {
      return originals.map(function (o, i) {
        return { key: o.key, text: o.text, letter: LETTERS[i] };
      });
    }
    var keys = originals.map(function (o) { return o.key; });
    var saved = state.optionOrders[q.id];
    if (!saved || saved.length !== keys.length || saved.some(function (k) { return keys.indexOf(k) === -1; })) {
      saved = shuffleArr(keys.slice());
      state.optionOrders[q.id] = saved;
      saveState();
    }
    var byKey = {};
    originals.forEach(function (o) { byKey[o.key] = o; });
    return saved.map(function (k, i) {
      return { key: k, text: byKey[k].text, letter: LETTERS[i] };
    });
  }

  /* ---------------- 统计 ---------------- */
  function computeStats() {
    var stats = {
      total: QUESTIONS.length,
      answered: 0,
      correct: 0,
      wrong: 0,
      seen: 0,
      streak: 0,
      byCategory: {},
      answeredList: []
    };
    CATEGORY_ORDER.forEach(function (c) {
      stats.byCategory[c] = { total: 0, answered: 0, correct: 0, wrong: 0 };
    });
    QUESTIONS.forEach(function (q) {
      var cat = stats.byCategory[q.category];
      if (cat) cat.total++;
      var r = state.records[q.id];
      if (!r) return;
      if (r.revealed) stats.seen++;
      if (typeof r.correct === "boolean") {
        stats.answered++;
        stats.answeredList.push({ id: q.id, correct: r.correct, ts: r.ts || 0 });
        if (cat) { cat.answered++; if (r.correct) cat.correct++; else cat.wrong++; }
        if (r.correct) stats.correct++; else stats.wrong++;
      }
    });
    stats.accuracy = stats.answered ? Math.round((stats.correct / stats.answered) * 100) : null;
    // 连续答对：按最近一次作答时间倒序，遇到错误即中断
    stats.answeredList.sort(function (a, b) { return b.ts - a.ts; });
    for (var i = 0; i < stats.answeredList.length; i++) {
      if (stats.answeredList[i].correct) stats.streak++;
      else break;
    }
    return stats;
  }

  function pct(part, whole) {
    if (!whole) return "0%";
    return Math.round((part / whole) * 100) + "%";
  }

  function renderStats() {
    var s = computeStats();
    els.statAnswered.textContent = s.answered;
    els.statTotal.textContent = "/" + s.total;
    els.statSeen.textContent = "已查看答案 " + s.seen;
    els.statCorrect.textContent = s.correct;
    els.statWrong.textContent = s.wrong;
    els.statAccuracy.textContent = s.accuracy === null ? "正确率 —" : "正确率 " + s.accuracy + "%";
    els.statStreak.textContent = "连续答对 " + s.streak;
    var progress = s.total ? (s.answered / s.total) * 100 : 0;
    els.statProgressPct.textContent = Math.round(progress) + "%";
    els.progressFill.style.width = progress + "%";
    els.footTotal.textContent = s.total;

    // tabs 计数
    var counts = { all: s.total, single: 0, image: 0, essay: 0, wrong: s.wrong };
    QUESTIONS.forEach(function (q) {
      if (q.category === "单选题") counts.single++;
      else if (q.category === "读图题") counts.image++;
      else if (q.type === "essay") counts.essay++;
    });
    Object.keys(counts).forEach(function (k) {
      var el = els.tabCounts[k];
      if (el) el.textContent = counts[k];
    });

    // 统计弹窗
    els.dlgAnswered.textContent = s.answered;
    els.dlgTotal.textContent = s.total;
    els.dlgCorrect.textContent = s.correct;
    els.dlgWrong.textContent = s.wrong;
    els.dlgAccuracy.textContent = s.accuracy === null ? "—" : s.accuracy + "%";
    els.dlgSeen.textContent = s.seen;
    els.ringPct.textContent = s.total ? pct(s.answered, s.total) : "0%";
    els.ring.style.setProperty("--pct", s.total ? (s.answered / s.total) * 100 : 0);

    var html = "";
    CATEGORY_ORDER.forEach(function (c) {
      var row = s.byCategory[c] || { total: 0, answered: 0, correct: 0, wrong: 0 };
      html += "<tr><td>" + c + "</td><td class='num'>" + row.total + "</td><td class='num'>" + row.answered +
        "</td><td class='num ok'>" + row.correct + "</td><td class='num bad'>" + row.wrong +
        "</td><td class='num'>" + (row.answered ? pct(row.correct, row.answered) : "—") + "</td></tr>";
    });
    html += "<tr><td><b>合计</b></td><td class='num'><b>" + s.total + "</b></td><td class='num'><b>" +
      s.answered + "</b></td><td class='num ok'><b>" + s.correct + "</b></td><td class='num bad'><b>" +
      s.wrong + "</b></td><td class='num'><b>" + (s.accuracy === null ? "—" : s.accuracy + "%") + "</b></td></tr>";
    els.statsTableBody.innerHTML = html;
  }

  /* ---------------- 渲染 ---------------- */
  function renderTabs() {
    Array.prototype.forEach.call(els.tabs.querySelectorAll(".tab"), function (tab) {
      var active = tab.getAttribute("data-filter") === state.filter;
      tab.classList.toggle("active", active);
      tab.setAttribute("aria-pressed", active ? "true" : "false");
    });
    els.shuffleToggle.checked = state.shuffle;
  }

  function setBadge(el, text) {
    if (text) { el.textContent = text; el.hidden = false; }
    else { el.hidden = true; }
  }

  function renderImageArea(q) {
    var area = els.imageArea;
    area.innerHTML = "";
    if (!q.images || !q.images.length) { area.hidden = true; return; }
    area.hidden = false;

    var missing = document.createElement("div");
    missing.className = "image-missing";
    missing.innerHTML = "<strong>原题配图缺失</strong><span>题库文件未附带图片（" +
      q.images.join("、").replace(/</g, "&lt;") +
      "）。如有图片，请放入项目的 <code>images/</code> 目录并保持同名，刷新即可显示。</span>";
    area.appendChild(missing);

    var loaded = 0;
    q.images.forEach(function (name) {
      var img = document.createElement("img");
      img.alt = "题目配图：" + name;
      img.loading = "lazy";
      img.src = "images/" + name.split("/").map(encodeURIComponent).join("/");
      img.addEventListener("load", function () {
        loaded++;
        missing.hidden = true;
        area.appendChild(img);
      });
      img.addEventListener("error", function () { /* 文件不存在：保留占位提示 */ });
    });
  }

  function renderOptions(q, rec) {
    var list = els.options;
    list.innerHTML = "";
    if (q.type === "essay") { list.hidden = true; return; }
    list.hidden = false;

    var opts = displayOptions(q);
    var revealed = !!(rec && rec.revealed);
    var selectedKey = rec ? rec.selectedKey : null;
    var correctKey = q.answer;

    opts.forEach(function (o) {
      var li = document.createElement("li");
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "option";
      btn.setAttribute("data-key", o.key);
      btn.setAttribute("aria-label", "选项 " + o.letter + "：" + o.text);
      if (revealed) btn.disabled = true;

      var keySpan = document.createElement("span");
      keySpan.className = "opt-key";
      keySpan.textContent = o.letter;

      var textSpan = document.createElement("span");
      textSpan.className = "opt-text";
      textSpan.textContent = o.text;

      var mark = document.createElement("span");
      mark.className = "opt-mark";
      mark.setAttribute("aria-hidden", "true");

      if (revealed) {
        if (o.key === correctKey) {
          btn.classList.add("correct");
          mark.textContent = "✓";
        } else if (o.key === selectedKey) {
          btn.classList.add("wrong");
          mark.textContent = "✗";
        } else {
          btn.classList.add("dimmed");
        }
      } else if (o.key === selectedKey) {
        btn.classList.add("selected");
      }

      btn.appendChild(keySpan);
      btn.appendChild(textSpan);
      btn.appendChild(mark);
      btn.addEventListener("click", function () { selectOption(o.key); });
      li.appendChild(btn);
      list.appendChild(li);
    });
  }

  function correctDisplayLetter(q) {
    var opts = displayOptions(q);
    for (var i = 0; i < opts.length; i++) if (opts[i].key === q.answer) return opts[i].letter;
    return q.answer;
  }

  function renderAnswer(q, rec) {
    var panel = els.answerPanel;
    var revealed = !!(rec && rec.revealed) || q.type === "essay" && answerShown;
    if (!answerShown || !q) { panel.hidden = true; return; }
    panel.hidden = false;

    var isEssay = q.type === "essay";
    els.answerTitle.textContent = isEssay ? "参考答案" : "正确答案";
    els.answerPill.classList.toggle("essay", isEssay);
    els.answerPill.textContent = isEssay ? "参考" : correctDisplayLetter(q);

    var verdict = els.answerVerdict;
    verdict.className = "answer-verdict";
    if (isEssay) {
      if (rec && rec.correct === true) { verdict.textContent = "✓ 自评答对"; verdict.classList.add("ok"); }
      else if (rec && rec.correct === false) { verdict.textContent = "✗ 自评没答对"; verdict.classList.add("bad"); }
      else { verdict.textContent = "已看答案（未自评）"; verdict.classList.add("neutral"); }
    } else if (rec && rec.revealed) {
      if (rec.correct === true) { verdict.textContent = "✓ 回答正确"; verdict.classList.add("ok"); }
      else if (rec.correct === false) { verdict.textContent = "✗ 回答错误"; verdict.classList.add("bad"); }
      else { verdict.textContent = "已看答案（未作答）"; verdict.classList.add("neutral"); }
    } else {
      verdict.textContent = "记一记";
      verdict.classList.add("neutral");
    }

    if (isEssay) {
      els.answerText.hidden = false;
      els.answerText.textContent = (q.answerNote ? "（" + q.answerNote + "）\n" : "") + (q.answerText || "");
    } else {
      var opts = displayOptions(q);
      var correctOpt = null;
      opts.forEach(function (o) { if (o.key === q.answer) correctOpt = o; });
      els.answerText.hidden = false;
      els.answerText.textContent = correctOpt
        ? correctOpt.letter + ". " + correctOpt.text
        : "正确答案：" + (q.answer || "—");
    }

    if (q.analysis) {
      els.answerAnalysisBox.hidden = false;
      els.answerAnalysis.textContent = q.analysis;
    } else {
      els.answerAnalysisBox.hidden = true;
    }

    els.essayGrade.hidden = !isEssay;
    if (isEssay) {
      els.btnSelfCorrect.classList.toggle("active", !!(rec && rec.correct === true));
      els.btnSelfWrong.classList.toggle("active", !!(rec && rec.correct === false));
    }

    var src = [];
    if (q.sourceRef) src.push("题目来源：" + q.sourceRef.file + " · 第 " + q.sourceRef.row + " 行");
    if (q.id) src.push("题号：" + q.id);
    if (q.images && q.images.length) src.push("配图：" + q.images.join("、"));
    els.answerSource.textContent = src.join("　|　");
  }

  function renderEmpty() {
    els.qCategory.textContent = "提示";
    els.qSubcategory.hidden = true;
    els.qImageBadge.hidden = true;
    els.qCounter.textContent = "第 0 / 0 题";
    els.stem.textContent = state.filter === "wrong"
      ? "当前没有错题，继续保持！"
      : "当前筛选下暂无题目。";
    els.imageArea.hidden = true;
    els.options.innerHTML = "";
    els.options.hidden = true;
    els.essayHint.hidden = true;
    els.answerPanel.hidden = true;
    els.btnPrev.disabled = true;
    els.btnNext.disabled = true;
    els.btnReveal.disabled = true;
    els.btnReveal.textContent = "显示答案";
  }

  function renderQuestion() {
    ensureOrder();
    var q = currentQuestion();
    if (!q) { renderEmpty(); return; }

    var rec = getRecord(q.id);
    answerShown = !!(rec && rec.revealed) ? true : answerShown;
    // 换题时，若该题未看过答案则收起
    if (!(rec && rec.revealed)) answerShown = false;

    els.qCategory.textContent = q.category || (q.type === "essay" ? "问答题" : "单选题");
    setBadge(els.qSubcategory, q.subcategory || "");
    setBadge(els.qImageBadge, q.images && q.images.length ? "配图缺失" : "");

    els.stem.textContent = q.stem;
    els.qCounter.textContent = "第 " + (state.index + 1) + " / " + state.order.length + " 题";
    renderImageArea(q);
    renderOptions(q, rec);
    els.essayHint.hidden = q.type !== "essay";

    renderAnswer(q, rec);
    els.btnReveal.textContent = answerShown ? "隐藏答案" : "显示答案";
    els.btnReveal.disabled = false;
    els.btnPrev.disabled = state.index <= 0;
    els.btnNext.disabled = state.index >= state.order.length - 1;

    // 卡片动画：仅在换题时触发
    els.card.style.animation = "none";
    void els.card.offsetWidth;
    els.card.style.animation = "";
  }

  function render() {
    renderTabs();
    renderStats();
    renderQuestion();
  }

  /* ---------------- 交互 ---------------- */
  function selectOption(key) {
    var q = currentQuestion();
    if (!q) return;
    var rec = getRecord(q.id);
    if (rec && rec.revealed) return; // 已看答案后不可再改
    var next = { selectedKey: key, revealed: rec ? !!rec.revealed : false, correct: rec ? rec.correct : undefined, ts: Date.now() };
    state.records[q.id] = next;
    saveState();
    renderOptions(q, next);
    // 选中后不立即显示答案；点“显示答案”或“下一题”时才揭晓
  }

  /* “下一题”自动判分：揭晓答案、记录对错，约 1 秒后切到下一题 */
  var advanceTimer = null;
  var advancePending = false;

  function cancelPendingAdvance() {
    if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null; }
    advancePending = false;
  }

  /* 揭晓答案：若已有勾选且尚未揭晓，同时记录本题是否答对 */
  function revealAndRecord() {
    var q = currentQuestion();
    if (!q) return null;
    var rec = getRecord(q.id);
    answerShown = true;
    if (!(rec && rec.revealed)) {
      var next = {
        selectedKey: rec ? rec.selectedKey || null : null,
        revealed: true,
        correct: rec ? rec.correct : undefined,
        ts: Date.now()
      };
      if (q.type !== "essay" && next.selectedKey) {
        next.correct = next.selectedKey === q.answer;
      }
      state.records[q.id] = next;
      rec = next;
      saveState();
      renderStats();
    }
    renderOptions(q, rec);
    renderAnswer(q, rec);
    els.btnReveal.textContent = "隐藏答案";
    return q;
  }

  function toggleReveal() {
    var q = currentQuestion();
    if (!q) return;
    cancelPendingAdvance();
    if (answerShown) {
      answerShown = false;
      renderAnswer(q, getRecord(q.id));
      els.btnReveal.textContent = "显示答案";
      return;
    }
    revealAndRecord();
  }

  function gradeEssay(correct) {
    var q = currentQuestion();
    if (!q) return;
    var rec = getRecord(q.id) || { selectedKey: null, revealed: true, ts: Date.now() };
    state.records[q.id] = {
      selectedKey: null,
      revealed: true,
      correct: correct,
      ts: Date.now()
    };
    answerShown = true;
    saveState();
    renderStats();
    renderAnswer(q, state.records[q.id]);
    renderOptions(q, state.records[q.id]);
  }

  function go(delta) {
    var q = currentQuestion();
    if (!q) return;

    // 只勾选、未点过“显示答案”就直接点“下一题”：
    //   答对 → 显示正确与解析，约 1 秒后自动切到下一题
    //   答错 → 显示错误与解析，停留在本题，直到再点一次“下一题”
    if (delta > 0) {
      var rec = getRecord(q.id);
      if (q.type !== "essay" && rec && rec.selectedKey && !rec.revealed) {
        cancelPendingAdvance();
        var isCorrect = rec.selectedKey === q.answer;
        revealAndRecord();
        if (!isCorrect) {
          // 答错：显示错误与解析，停留在本题，等用户再点一次“下一题”
          return;
        }
        if (state.index >= state.order.length - 1) {
          toast("已是最后一题，答案已显示并记录");
          return;
        }
        // 答对：答案保持展示约 1 秒后自动切题（期间不改变按钮文案）
        advancePending = true;
        advanceTimer = setTimeout(function () {
          advanceTimer = null;
          advancePending = false;
          go(1);
        }, 1000);
        return;
      }
    }

    cancelPendingAdvance();
    var nextIndex = state.index + delta;
    if (nextIndex < 0 || nextIndex >= state.order.length) return;
    state.index = nextIndex;
    answerShown = false;
    saveState();
    render();
    els.card.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function goToIndex(i) {
    if (i < 0 || i >= state.order.length) return;
    state.index = i;
    answerShown = false;
    saveState();
    render();
  }

  function setFilter(filter) {
    if (!FILTER_DEFS[filter] || state.filter === filter) return;
    cancelPendingAdvance();
    state.filter = filter;
    state.order = buildOrder(filter);
    state.index = 0;
    answerShown = false;
    saveState();
    render();
  }

  function reshuffle() {
    cancelPendingAdvance();
    state.order = buildOrder(state.filter);
    state.optionOrders = {};
    state.index = 0;
    answerShown = false;
    saveState();
    render();
    toast("已重新打乱题目与选项");
  }

  function toggleShuffle(on) {
    cancelPendingAdvance();
    state.shuffle = !!on;
    state.order = buildOrder(state.filter);
    state.optionOrders = {};
    state.index = 0;
    answerShown = false;
    saveState();
    render();
    toast(state.shuffle ? "已开启乱序" : "已按原题顺序排列");
  }

  function resetProgress() {
    if (!window.confirm("确定清空所有做题记录吗？该操作不可恢复。")) return;
    cancelPendingAdvance();
    state.records = {};
    state.index = 0;
    answerShown = false;
    if (state.filter === "wrong") state.filter = "all";
    state.order = buildOrder(state.filter);
    saveState();
    render();
    toast("已清空做题记录");
  }

  /* ---------------- 主题 ---------------- */
  function applyTheme() {
    var theme = state.theme;
    var resolved = theme;
    if (theme === "auto") {
      resolved = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    }
    document.documentElement.setAttribute("data-theme", resolved);
    var iconMoon = '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';
    var iconSun = '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M19.1 4.9l-1.8 1.8M6.7 17.3l-1.8 1.8"/></svg>';
    els.btnTheme.innerHTML = theme === "light" ? iconSun : iconMoon;
    els.btnTheme.title = "主题：" + (theme === "light" ? "浅色" : theme === "dark" ? "深色" : "跟随系统") + "（点击切换）";
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", resolved === "dark" ? "#0b1220" : "#eef2f7");
  }

  function cycleTheme() {
    state.theme = state.theme === "auto" ? "light" : state.theme === "light" ? "dark" : "auto";
    saveState();
    applyTheme();
  }

  /* ---------------- Toast ---------------- */
  var toastTimer = null;
  function toast(msg) {
    els.toast.textContent = msg;
    els.toast.hidden = false;
    requestAnimationFrame(function () { els.toast.classList.add("show"); });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      els.toast.classList.remove("show");
      setTimeout(function () { els.toast.hidden = true; }, 220);
    }, 1800);
  }

  /* ---------------- 统计弹窗 ---------------- */
  function openStats() {
    cancelPendingAdvance();
    renderStats();
    if (typeof els.dialog.showModal === "function") els.dialog.showModal();
    else els.dialog.setAttribute("open", "");
  }
  function closeStats() {
    if (typeof els.dialog.close === "function") els.dialog.close();
    else els.dialog.removeAttribute("open");
  }

  /* ---------------- 初始化 ---------------- */
  function cacheEls() {
    els = {
      card: document.getElementById("card"),
      statAnswered: document.getElementById("stat-answered"),
      statTotal: document.getElementById("stat-total"),
      statSeen: document.getElementById("stat-seen"),
      statCorrect: document.getElementById("stat-correct"),
      statWrong: document.getElementById("stat-wrong"),
      statAccuracy: document.getElementById("stat-accuracy"),
      statStreak: document.getElementById("stat-streak"),
      statProgressPct: document.getElementById("stat-progress-pct"),
      progressFill: document.getElementById("progress-fill"),
      footTotal: document.getElementById("foot-total"),
      qCategory: document.getElementById("q-category"),
      qSubcategory: document.getElementById("q-subcategory"),
      qImageBadge: document.getElementById("q-image-badge"),
      qCounter: document.getElementById("q-counter"),
      stem: document.getElementById("q-stem"),
      imageArea: document.getElementById("q-image-area"),
      options: document.getElementById("q-options"),
      essayHint: document.getElementById("essay-hint"),
      answerPanel: document.getElementById("answer-panel"),
      answerTitle: document.getElementById("answer-title"),
      answerPill: document.getElementById("answer-pill"),
      answerVerdict: document.getElementById("answer-verdict"),
      answerText: document.getElementById("answer-text"),
      answerAnalysisBox: document.getElementById("answer-analysis-box"),
      answerAnalysis: document.getElementById("answer-analysis"),
      essayGrade: document.getElementById("essay-grade"),
      btnSelfCorrect: document.getElementById("btn-self-correct"),
      btnSelfWrong: document.getElementById("btn-self-wrong"),
      answerSource: document.getElementById("answer-source"),
      btnPrev: document.getElementById("btn-prev"),
      btnNext: document.getElementById("btn-next"),
      btnReveal: document.getElementById("btn-reveal"),
      tabs: document.getElementById("tabs"),
      tabCounts: {},
      shuffleToggle: document.getElementById("toggle-shuffle"),
      btnReshuffle: document.getElementById("btn-reshuffle"),
      btnReset: document.getElementById("btn-reset"),
      btnTheme: document.getElementById("btn-theme"),
      btnStats: document.getElementById("btn-stats"),
      dialog: document.getElementById("stats-dialog"),
      btnCloseStats: document.getElementById("btn-close-stats"),
      btnCloseStats2: document.getElementById("btn-close-stats-2"),
      btnReset2: document.getElementById("btn-reset-2"),
      dlgAnswered: document.getElementById("dlg-answered"),
      dlgTotal: document.getElementById("dlg-total"),
      dlgCorrect: document.getElementById("dlg-correct"),
      dlgWrong: document.getElementById("dlg-wrong"),
      dlgAccuracy: document.getElementById("dlg-accuracy"),
      dlgSeen: document.getElementById("dlg-seen"),
      statsTableBody: document.getElementById("stats-table-body"),
      ring: document.getElementById("ring"),
      ringPct: document.getElementById("ring-pct"),
      toast: document.getElementById("toast")
    };
    Array.prototype.forEach.call(document.querySelectorAll(".tab-count"), function (el) {
      els.tabCounts[el.getAttribute("data-count")] = el;
    });
  }

  function bindEvents() {
    els.btnPrev.addEventListener("click", function () { go(-1); });
    els.btnNext.addEventListener("click", function () { go(1); });
    els.btnReveal.addEventListener("click", toggleReveal);
    els.btnSelfCorrect.addEventListener("click", function () { gradeEssay(true); });
    els.btnSelfWrong.addEventListener("click", function () { gradeEssay(false); });

    els.tabs.addEventListener("click", function (e) {
      var tab = e.target.closest(".tab");
      if (!tab) return;
      setFilter(tab.getAttribute("data-filter"));
    });

    els.shuffleToggle.addEventListener("change", function (e) { toggleShuffle(e.target.checked); });
    els.btnReshuffle.addEventListener("click", reshuffle);
    els.btnReset.addEventListener("click", resetProgress);
    els.btnReset2.addEventListener("click", function () { closeStats(); resetProgress(); });
    els.btnTheme.addEventListener("click", cycleTheme);
    els.btnStats.addEventListener("click", openStats);
    els.btnCloseStats.addEventListener("click", closeStats);
    els.btnCloseStats2.addEventListener("click", closeStats);
    els.dialog.addEventListener("click", function (e) {
      if (e.target === els.dialog) closeStats();
    });

    document.addEventListener("keydown", function (e) {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (els.dialog.open) return;
      var tag = (e.target && e.target.tagName) || "";
      if (tag === "INPUT" || tag === "TEXTAREA") return;

      if (e.key === "ArrowLeft") { e.preventDefault(); go(-1); }
      else if (e.key === "ArrowRight") { e.preventDefault(); go(1); }
      else if (e.key === " " || e.key === "Enter") {
        if (tag === "BUTTON") return;
        e.preventDefault(); toggleReveal();
      } else if (/^[1-6]$/.test(e.key)) {
        var q = currentQuestion();
        if (!q || q.type === "essay") return;
        var opts = displayOptions(q);
        var idx = parseInt(e.key, 10) - 1;
        if (opts[idx]) selectOption(opts[idx].key);
      }
    });

    if (window.matchMedia) {
      var mq = window.matchMedia("(prefers-color-scheme: dark)");
      var onChange = function () { if (state.theme === "auto") applyTheme(); };
      if (mq.addEventListener) mq.addEventListener("change", onChange);
      else if (mq.addListener) mq.addListener(onChange);
    }
  }

  function boot() {
    cacheEls();
    if (!QUESTIONS.length) {
      els.stem.textContent = "题库加载失败：未找到 js/questions.js";
      applyTheme();
      return;
    }
    applyTheme();
    ensureOrder();
    bindEvents();
    render();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
