(() => {
  "use strict";

  const PERSIST_KEY = "ragg-demo-state-v5";
  const COLLAPSE_BUDGET_MS = 5000;
  const EMBED_RATIO = 1.35;

  const CATALOG = [
    {
      id: "bge-small",
      name: "bge-small-en GGUF",
      role: "Embedding",
      size: "≈60 МБ",
      sizeMb: 60,
      group: "recommended",
      meta: "GGUF embed · download · обязателен для RAG · Fits",
      badge: "embedding",
      badgeClass: "badge--ok",
      defaultSelected: true,
      installed: false,
      active: false,
    },
    {
      id: "smol360",
      name: "SmolLM-360M Q4",
      role: "LLM",
      size: "≈80 МБ",
      sizeMb: 80,
      group: "recommended",
      meta: "Эталон · download · Fits",
      badge: "эталон",
      badgeClass: "badge--ok",
      defaultSelected: true,
      installed: false,
      active: false,
      isEtalon: true,
    },
    {
      id: "qwen05",
      name: "Qwen2.5-0.5B Q4_K_M",
      role: "LLM",
      size: "≈380 МБ",
      sizeMb: 380,
      group: "stronger",
      meta: "Сильнее · download · ~3.2 ток/с · Fits",
      badge: "медленнее",
      badgeClass: "badge--warn",
      defaultSelected: false,
      installed: false,
    },
    {
      id: "qwen15",
      name: "Qwen2.5-1.5B Q4_K_M",
      role: "LLM",
      size: "≈980 МБ",
      sizeMb: 980,
      group: "stronger",
      meta: "Сильнее · ~1.5 ток/с · медленнее на ~77%",
      badge: "медленнее",
      badgeClass: "badge--warn",
      defaultSelected: false,
      installed: false,
    },
    {
      id: "phi3",
      name: "Phi-3-mini Q4",
      role: "LLM",
      size: "2.2 ГБ",
      sizeMb: 2200,
      group: "skip",
      meta: "Insufficient RAM · Impractical",
      badge: "не стоит",
      badgeClass: "badge--danger",
      defaultSelected: false,
      installed: false,
      disabled: true,
    },
  ];

  const MOCK_DOCS = [
    { id: "d1", name: "handbook.txt", bytes: 128_000, active: true, vectorBytes: Math.round(128_000 * EMBED_RATIO) },
    { id: "d2", name: "faq-product.md", bytes: 42_000, active: true, vectorBytes: Math.round(42_000 * EMBED_RATIO) },
    { id: "d3", name: "notes-meeting.txt", bytes: 8_400, active: false, vectorBytes: 0 },
  ];

  const state = {
    screen: "home",
    onboardingDone: true,
    etalonTokPerSec: null,
    selected: new Set(CATALOG.filter((m) => m.defaultSelected).map((m) => m.id)),
    models: structuredClone(CATALOG),
    docs: structuredClone(MOCK_DOCS),
    /** черновик выбора: id документов, которые войдут в индекс после «Обновить» */
    draftActive: new Set(MOCK_DOCS.filter((d) => d.active).map((d) => d.id)),
    vectorizing: false,
    cancelVectorize: false,
    chats: [
      {
        id: "c1",
        title: "Политика возврата",
        updatedAt: Date.now() - 3600_000,
        messages: [
          { role: "user", text: "Какая политика возврата?" },
          {
            role: "assistant",
            text: "По handbook.txt возврат возможен в течение 14 дней при сохранении упаковки. Нужен чек или номер заказа.",
          },
        ],
      },
    ],
    activeChatId: "c1",
    streaming: false,
    collapsedAt: null,
    histQuery: "",
  };

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  const el = {
    app: $("#app"),
    orient: $("#orient-lock"),
    powerTitle: $("#power-title"),
    powerLead: $("#power-lead"),
    hwCpu: $("#hw-cpu"),
    hwRam: $("#hw-ram"),
    hwGpu: $("#hw-gpu"),
    hwTier: $("#hw-tier"),
    benchPhase: $("#bench-phase"),
    benchPct: $("#bench-pct"),
    benchBar: $("#bench-bar"),
    benchHint: $("#bench-hint"),
    anchorPill: $("#anchor-pill"),
    anchorTps: $("#anchor-tps"),
    listRecommended: $("#list-recommended"),
    listStronger: $("#list-stronger"),
    listSkip: $("#list-skip"),
    btnDownloadReco: $("#btn-download-reco"),
    dlLead: $("#dl-lead"),
    dlList: $("#dl-list"),
    dlBar: $("#dl-bar"),
    dlPct: $("#dl-pct"),
    messages: $("#messages"),
    chatTitle: $("#chat-title"),
    prompt: $("#prompt"),
    composer: $("#composer"),
    btnSend: $("#btn-send"),
    btnMenu: $("#btn-menu"),
    btnSaveChat: $("#btn-save-chat"),
    btnNewChat: $("#btn-new-chat"),
    drawer: $("#drawer"),
    drawerBackdrop: $("#drawer-backdrop"),
    histDrawer: $("#history-drawer"),
    histBackdrop: $("#hist-backdrop"),
    histList: $("#hist-list"),
    histSearch: $("#hist-search"),
    mmAnchor: $("#mm-anchor"),
    mmInstalled: $("#mm-installed"),
    mmCatalog: $("#mm-catalog"),
    storageStats: $("#storage-stats"),
    docList: $("#doc-list"),
    btnUpload: $("#btn-upload"),
    btnRefreshDocs: $("#btn-refresh-docs"),
    fileInput: $("#file-input"),
    vectorizeBlock: $("#vectorize-block"),
    vectorizePhase: $("#vectorize-phase"),
    vectorizePct: $("#vectorize-pct"),
    vectorizeBar: $("#vectorize-bar"),
    vectorizeHint: $("#vectorize-hint"),
    btnCancelVectorize: $("#btn-cancel-vectorize"),
    screenResources: $("#screen-resources"),
    toast: $("#toast"),
  };

  function setComposerBlocked(blocked, reason) {
    el.composer.classList.toggle("is-blocked", blocked);
    el.prompt.disabled = blocked || state.streaming;
    el.btnSend.disabled = blocked || state.streaming;
    if (blocked) {
      el.prompt.placeholder = reason || "Идёт индексация…";
    } else {
      el.prompt.placeholder = "Спросите по вашим документам…";
    }
  }

  /* ——— Persistence (survive collapse ≤5s) ——— */
  function serialize() {
    return {
      screen: state.screen,
      onboardingDone: state.onboardingDone,
      etalonTokPerSec: state.etalonTokPerSec,
      selected: [...state.selected],
      models: state.models,
      docs: state.docs,
      draftActive: [...state.draftActive],
      chats: state.chats,
      activeChatId: state.activeChatId,
      savedAt: Date.now(),
    };
  }

  function persist() {
    try {
      sessionStorage.setItem(PERSIST_KEY, JSON.stringify(serialize()));
    } catch (_) {
      /* ignore quota */
    }
  }

  function restoreIfFresh() {
    try {
      const raw = sessionStorage.getItem(PERSIST_KEY);
      if (!raw) return false;
      const data = JSON.parse(raw);
      const age = Date.now() - (data.savedAt || 0);
      if (age > COLLAPSE_BUDGET_MS + 500) {
        sessionStorage.removeItem(PERSIST_KEY);
        return false;
      }
      state.screen = data.screen || "power";
      state.onboardingDone = !!data.onboardingDone;
      state.etalonTokPerSec = data.etalonTokPerSec;
      state.selected = new Set(data.selected || []);
      state.models = data.models || structuredClone(CATALOG);
      state.docs = (data.docs || structuredClone(MOCK_DOCS)).map(normalizeDoc);
      state.draftActive = new Set(
        data.draftActive || state.docs.filter((d) => d.active).map((d) => d.id)
      );
      state.chats = data.chats || [];
      state.activeChatId = data.activeChatId;
      return { age };
    } catch (_) {
      return false;
    }
  }

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      state.collapsedAt = Date.now();
      persist();
    } else if (state.collapsedAt != null) {
      const away = Date.now() - state.collapsedAt;
      state.collapsedAt = null;
      if (away <= COLLAPSE_BUDGET_MS) {
        showToast(`Восстановлено после сворачивания (${(away / 1000).toFixed(1)} с)`);
      } else {
        showToast("Сессия свёрнута дольше 5 с — состояние могло устареть");
      }
      persist();
    }
  });

  window.addEventListener("pagehide", persist);
  window.addEventListener("beforeunload", persist);

  /* ——— Orientation lock ——— */
  function isPhoneLike() {
    return window.matchMedia("(max-width: 859px)").matches;
  }

  function isLandscape() {
    return window.matchMedia("(orientation: landscape)").matches;
  }

  async function tryLockPortrait() {
    try {
      const o = screen.orientation;
      if (o && typeof o.lock === "function" && isPhoneLike()) {
        await o.lock("portrait");
      }
    } catch (_) {
      /* browsers often deny without fullscreen */
    }
  }

  function updateOrientationGate() {
    const block = isPhoneLike() && isLandscape();
    el.orient.hidden = !block;
    document.documentElement.style.overflow = block ? "hidden" : "";
  }

  window.addEventListener("orientationchange", () => {
    updateOrientationGate();
    tryLockPortrait();
  });
  window.addEventListener("resize", updateOrientationGate);

  /* ——— Navigation ——— */
  function showScreen(name) {
    state.screen = name;
    $$(".screen").forEach((s) => {
      const id = s.id.replace("screen-", "");
      s.hidden = id !== name;
    });
    el.app.dataset.screen = name;
    closeDrawer();
    closeHistory();
    persist();

    if (name === "reco") renderReco();
    if (name === "home") renderChat();
    if (name === "models") renderModels();
    if (name === "resources") renderResources();
  }

  $$("[data-back]").forEach((btn) => {
    btn.addEventListener("click", () => showScreen(btn.dataset.back));
  });

  /* ——— Toast ——— */
  let toastTimer;
  function showToast(_text) {
    /* уведомления временно скрыты */
    el.toast.hidden = true;
    clearTimeout(toastTimer);
  }

  /* ——— Power check / bench ——— */
  function detectHardware() {
    const cores = navigator.hardwareConcurrency || 4;
    const mem = navigator.deviceMemory;
    const ramStr = mem ? `${mem} ГБ` : isPhoneLike() ? "~6 ГБ" : "~16 ГБ";
    const cpuStr = `${cores} ядер`;
    const gpuStr = isPhoneLike() ? "Mali-G76" : "Vulkan GPU";
    const tier = !isPhoneLike() || (mem && mem >= 12) ? "High" : mem && mem >= 6 ? "Mid" : "Mid";
    return { cpuStr, ramStr, gpuStr, tier };
  }

  function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
  }

  async function runPowerCheck(skipAnim = false) {
    const hw = detectHardware();
    el.hwCpu.textContent = "…";
    el.hwRam.textContent = "…";
    el.hwGpu.textContent = "…";
    el.hwTier.textContent = "…";
    el.powerTitle.textContent = "Проверка мощности";
    el.powerLead.textContent =
      "Снимаем профиль железа, скачиваем эталон и калибруем его на этом устройстве.";

    if (!skipAnim) await sleep(500);
    el.hwCpu.textContent = hw.cpuStr;
    el.hwRam.textContent = hw.ramStr;
    el.hwGpu.textContent = hw.gpuStr;
    el.hwTier.textContent = hw.tier;

    const phases = [
      { label: "Скачивание эталона…", until: 40 },
      { label: "Прогрев эталона…", until: 60 },
      { label: "Генерация…", until: 90 },
      { label: "Калибровка якоря…", until: 100 },
    ];

    let pct = 0;
    if (skipAnim) {
      pct = 100;
      el.benchBar.style.width = "100%";
      el.benchBar.parentElement.setAttribute("aria-valuenow", "100");
      el.benchPct.textContent = "100%";
      el.benchPhase.textContent = "Готово";
    } else {
      for (const phase of phases) {
        el.benchPhase.textContent = phase.label;
        while (pct < phase.until) {
          pct += 1 + Math.random() * 2.5;
          if (pct > phase.until) pct = phase.until;
          const v = Math.round(pct);
          el.benchBar.style.width = `${v}%`;
          el.benchBar.parentElement.setAttribute("aria-valuenow", String(v));
          el.benchPct.textContent = `${v}%`;
          await sleep(28);
        }
      }
    }

    const tps = +(5.2 + Math.random() * 2.6).toFixed(1);
    state.etalonTokPerSec = tps;
    const etalon = state.models.find((m) => m.isEtalon);
    if (etalon) {
      etalon.installed = true;
      etalon.active = true;
      etalon.meta = `Эталон · download · ~${tps} ток/с · Fits`;
    }
    el.benchPhase.textContent = `Результат: ${tps} ток/с`;
    el.benchHint.textContent = `Якорь сохранён · эталон скачан · SmolLM-360M Q4 · ${hw.tier}`;
    el.powerTitle.textContent = "Устройство откалибровано";
    el.powerLead.textContent =
      "Относительно эталона ранжируем каталог: слабее / эталон / сильнее.";
    persist();

    if (!skipAnim) await sleep(700);
    showScreen("reco");
  }

  /* ——— Recommendations ——— */
  const ICONS = {
    activate: `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3v9"/><path d="M8.5 7.5a6 6 0 1 0 7 0"/></svg>`,
    bench: `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8"/><path d="M12 12l4-2"/><path d="M12 8v1"/></svg>`,
    download: `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 4v10"/><path d="M8 10l4 4 4-4"/><path d="M5 18h14"/></svg>`,
    trash: `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M9 7V5h6v2M8 7l1 12h6l1-12"/></svg>`,
  };

  function modelCardHtml(m, { selectable, manager }) {
    const selected = state.selected.has(m.id);
    const disabled = !!m.disabled;
    const check = selectable
      ? `<span class="model-card__check" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12l5 5L20 7"/></svg>
        </span>`
      : "";

    let side = "";
    if (manager) {
      let actionBtns = "";
      if (m.installed) {
        actionBtns = `
          <button type="button" class="icon-btn icon-btn--sm ${m.active ? "is-active" : ""}" data-act="activate" data-id="${m.id}" ${m.active ? "disabled" : ""} aria-label="${m.active ? "Активна" : "Активировать"}" title="${m.active ? "Активна" : "Активировать"}">${ICONS.activate}</button>
          <button type="button" class="icon-btn icon-btn--sm" data-act="bench" data-id="${m.id}" aria-label="Прогнать вживую" title="Прогнать вживую">${ICONS.bench}</button>
          <button type="button" class="icon-btn icon-btn--sm icon-btn--danger" data-act="delete" data-id="${m.id}" aria-label="Удалить" title="Удалить">${ICONS.trash}</button>`;
      } else {
        actionBtns = `<button type="button" class="icon-btn icon-btn--sm" data-act="download" data-id="${m.id}" ${disabled ? "disabled" : ""} aria-label="Скачать" title="Скачать">${ICONS.download}</button>`;
      }
      side = `<div class="model-card__actions">${actionBtns}</div>`;
    }

    return `<article class="model-card ${manager ? "model-card--manager" : ""} ${selected && selectable ? "is-selected" : ""} ${disabled ? "is-disabled" : ""}" data-id="${m.id}" ${selectable && !disabled ? 'role="button" tabindex="0"' : ""}>
      ${check}
      <div class="model-card__body">
        <div class="model-card__title">
          <p class="model-card__name">${m.name}</p>
          <span class="badge ${m.badgeClass}">${m.badge}</span>
        </div>
        <p class="model-card__meta">${m.role} · ${m.size} · ${m.meta}</p>
      </div>
      ${side}
    </article>`;
  }

  function renderReco() {
    const tps = state.etalonTokPerSec ?? "—";
    el.anchorTps.textContent = typeof tps === "number" ? tps : tps;
    const by = (g) => state.models.filter((m) => m.group === g);
    el.listRecommended.innerHTML = by("recommended")
      .map((m) => modelCardHtml(m, { selectable: true }))
      .join("");
    el.listStronger.innerHTML = by("stronger")
      .map((m) => modelCardHtml(m, { selectable: true }))
      .join("");
    el.listSkip.innerHTML = by("skip")
      .map((m) => modelCardHtml(m, { selectable: true }))
      .join("");
  }

  const recoScreen = $("#screen-reco");
  recoScreen.addEventListener("click", (e) => {
    const card = e.target.closest(".model-card[data-id]");
    if (!card || card.classList.contains("is-disabled")) return;
    if (!card.closest("#list-recommended, #list-stronger, #list-skip")) return;
    const id = card.dataset.id;
    if (state.selected.has(id)) state.selected.delete(id);
    else state.selected.add(id);
    renderReco();
    persist();
  });
  recoScreen.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const card = e.target.closest(".model-card[data-id]");
    if (!card || card.classList.contains("is-disabled")) return;
    e.preventDefault();
    card.click();
  });

  el.btnDownloadReco.addEventListener("click", async () => {
    if (![...state.selected].some((id) => state.models.find((m) => m.id === id)?.role === "LLM")) {
      showToast("Выберите хотя бы одну LLM");
      return;
    }
    if (![...state.selected].some((id) => state.models.find((m) => m.id === id)?.role === "Embedding")) {
      showToast("Нужна embedding-модель");
      return;
    }
    await runDownload();
  });

  async function runDownload() {
    showScreen("download");
    const items = state.models.filter((m) => state.selected.has(m.id));
    el.dlList.innerHTML = items
      .map(
        (m) =>
          `<li data-id="${m.id}"><span>${m.name}</span><span class="dl-status">ожидание</span></li>`
      )
      .join("");
    el.dlBar.style.width = "0%";
    el.dlPct.textContent = "0%";

    let done = 0;
    for (const m of items) {
      const row = $(`li[data-id="${m.id}"] .dl-status`, el.dlList);
      el.dlLead.textContent = `Загрузка: ${m.name}`;
      for (let p = 0; p <= 100; p += 4 + Math.random() * 6) {
        const cur = Math.min(100, Math.round(p));
        row.textContent = `${cur}%`;
        const overall = Math.round(((done + cur / 100) / items.length) * 100);
        el.dlBar.style.width = `${overall}%`;
        el.dlPct.textContent = `${overall}%`;
        await sleep(40);
      }
      row.textContent = "готово";
      const model = state.models.find((x) => x.id === m.id);
      if (model) {
        model.installed = true;
        if (model.role === "LLM") {
          state.models.forEach((x) => {
            if (x.role === "LLM") x.active = x.id === model.id;
          });
        }
        if (model.role === "Embedding") model.active = true;
      }
      done += 1;
    }

    el.dlLead.textContent = "Онбординг завершён";
    state.onboardingDone = true;
    persist();
    await sleep(500);
    ensureActiveChat();
    showScreen("home");
    showToast("Добро пожаловать в чат");
  }

  /* ——— Chat ——— */
  function ensureActiveChat() {
    if (!state.activeChatId) {
      const chat = {
        id: `c-${Date.now()}`,
        title: "Новый чат",
        updatedAt: Date.now(),
        messages: [],
      };
      state.chats.unshift(chat);
      state.activeChatId = chat.id;
    }
  }

  function activeChat() {
    return state.chats.find((c) => c.id === state.activeChatId);
  }

  function renderChat() {
    ensureActiveChat();
    const chat = activeChat();
    el.chatTitle.textContent = chat?.title || "Новый чат";
    el.messages.innerHTML = "";

    if (!chat || chat.messages.length === 0) {
      el.messages.innerHTML = `<div class="empty-chat"><strong>Только чат</strong>Новый диалог или история. Модели и документы — в меню.</div>`;
      return;
    }

    for (const m of chat.messages) {
      const div = document.createElement("div");
      div.className = `msg msg--${m.role === "user" ? "user" : "assistant"}`;
      div.textContent = m.text;
      el.messages.appendChild(div);
    }
    el.messages.scrollTop = el.messages.scrollHeight;
  }

  function deleteChat(id) {
    state.chats = state.chats.filter((c) => c.id !== id);
    if (state.activeChatId === id) {
      state.activeChatId = state.chats[0]?.id || null;
    }
    if (!state.chats.length) {
      state.activeChatId = null;
      ensureActiveChat();
    }
    renderChat();
    renderHistory();
    persist();
  }

  function bindHistButtons(root) {
    $$("[data-chat]", root).forEach((btn) => {
      btn.addEventListener("click", () => {
        state.activeChatId = btn.dataset.chat;
        closeHistory();
        closeDrawer();
        showScreen("home");
        renderChat();
        persist();
      });
    });
    $$("[data-del-chat]", root).forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        deleteChat(btn.dataset.delChat);
      });
    });
  }

  function filteredChats() {
    const q = state.histQuery.trim().toLowerCase();
    const items = [...state.chats].sort((a, b) => b.updatedAt - a.updatedAt);
    if (!q) return items;
    return items.filter((c) => {
      if (c.title.toLowerCase().includes(q)) return true;
      return c.messages.some((m) => m.text.toLowerCase().includes(q));
    });
  }

  function histItemsHtml() {
    const items = filteredChats();
    if (!state.chats.length) {
      return "<li class='hist-empty'>Пока пусто</li>";
    }
    if (!items.length) {
      return "<li class='hist-empty'>Ничего не найдено</li>";
    }
    return items
      .map((c) => {
        const when = new Date(c.updatedAt).toLocaleString("ru-RU", {
          day: "2-digit",
          month: "short",
          hour: "2-digit",
          minute: "2-digit",
        });
        const active = c.id === state.activeChatId ? " is-active" : "";
        return `<li class="hist-item${active}">
          <button type="button" class="hist-item__open" data-chat="${c.id}">
            <strong>${escapeHtml(c.title)}</strong>
            <span>${when} · ${c.messages.length} сообщ.</span>
          </button>
          <button type="button" class="icon-btn icon-btn--sm icon-btn--danger" data-del-chat="${c.id}" aria-label="Удалить чат" title="Удалить">
            ${ICONS.trash}
          </button>
        </li>`;
      })
      .join("");
  }

  function onHistSearchInput(value) {
    state.histQuery = value;
    renderHistory();
  }

  function renderHistory() {
    if (el.histSearch && el.histSearch.value !== state.histQuery) {
      el.histSearch.value = state.histQuery;
    }
    el.histList.innerHTML = histItemsHtml();
    bindHistButtons(el.histList);
  }

  function autoResizePrompt() {
    const t = el.prompt;
    t.style.height = "auto";
    t.style.height = `${Math.min(t.scrollHeight, 120)}px`;
  }

  el.prompt.addEventListener("input", autoResizePrompt);

  el.composer.addEventListener("submit", async (e) => {
    e.preventDefault();
    const text = el.prompt.value.trim();
    if (!text || state.streaming || state.vectorizing) return;
    ensureActiveChat();
    const chat = activeChat();
    chat.messages.push({ role: "user", text });
    if (chat.title === "Новый чат") {
      chat.title = text.length > 36 ? `${text.slice(0, 36)}…` : text;
    }
    chat.updatedAt = Date.now();
    el.prompt.value = "";
    autoResizePrompt();
    renderChat();
    persist();

    state.streaming = true;
    el.btnSend.disabled = true;
    const bubble = document.createElement("div");
    bubble.className = "msg msg--assistant is-streaming";
    bubble.textContent = "";
    const empty = $(".empty-chat", el.messages);
    if (empty) empty.remove();
    el.messages.appendChild(bubble);

    const reply = mockReply(text);
    for (let i = 0; i < reply.length; i++) {
      bubble.textContent += reply[i];
      el.messages.scrollTop = el.messages.scrollHeight;
      await sleep(12 + Math.random() * 18);
    }
    bubble.classList.remove("is-streaming");
    chat.messages.push({ role: "assistant", text: reply });
    state.streaming = false;
    el.btnSend.disabled = false;
    persist();
  });

  function mockReply(q) {
    const lower = q.toLowerCase();
    if (lower.includes("возврат")) {
      return "По индексированным документам: возврат в течение 14 дней при сохранённой упаковке. Укажите номер заказа — подскажу следующий шаг.";
    }
    if (lower.includes("модел") || lower.includes("эталон")) {
      const tps = state.etalonTokPerSec;
      if (tps == null) {
        return "Моделей ещё нет. Меню → Модели → в блоке «Установленные» нажмите «Начать»: скачается эталон и пройдёт калибровка устройства.";
      }
      return `Якорь устройства: эталон SmolLM-360M Q4 (download) · ${tps} ток/с. Управление моделями — в меню «Модели».`;
    }
    return `Ответ по локальному контексту (mock): нашёл релевантные фрагменты в ваших документах. Вопрос «${q.slice(0, 80)}» обработан без облака. Добавьте файлы в Менеджере ресурсов для более точных ответов.`;
  }

  el.btnNewChat.addEventListener("click", () => {
    state.activeChatId = null;
    ensureActiveChat();
    renderChat();
    persist();
  });

  function safeFilename(name) {
    const base = String(name || "chat")
      .replace(/[\\/:*?"<>|]+/g, "_")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 60);
    return base || "chat";
  }

  function chatToTxt(chat) {
    const lines = [
      `RAGG · ${chat.title}`,
      `Сохранено: ${new Date().toLocaleString("ru-RU")}`,
      "",
      "—".repeat(40),
      "",
    ];
    for (const m of chat.messages) {
      const who = m.role === "user" ? "Вы" : "RAGG";
      lines.push(`[${who}]`);
      lines.push(m.text);
      lines.push("");
    }
    if (!chat.messages.length) {
      lines.push("(пусто)");
      lines.push("");
    }
    return lines.join("\n");
  }

  function saveActiveChatAsTxt() {
    ensureActiveChat();
    const chat = activeChat();
    if (!chat) return;
    const blob = new Blob([chatToTxt(chat)], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${safeFilename(chat.title)}.txt`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  el.btnSaveChat.addEventListener("click", saveActiveChatAsTxt);

  /* ——— Drawer / history ——— */
  function openDrawer() {
    closeHistory();
    el.drawer.hidden = false;
    el.drawerBackdrop.hidden = false;
  }
  function closeDrawer() {
    el.drawer.hidden = true;
    el.drawerBackdrop.hidden = true;
  }
  function openHistory() {
    closeDrawer();
    renderHistory();
    el.histDrawer.hidden = false;
    el.histBackdrop.hidden = false;
  }
  function closeHistory() {
    el.histDrawer.hidden = true;
    el.histBackdrop.hidden = true;
  }

  el.btnMenu.addEventListener("click", openDrawer);
  el.drawerBackdrop.addEventListener("click", closeDrawer);
  el.histBackdrop.addEventListener("click", closeHistory);

  el.histSearch?.addEventListener("input", () => onHistSearchInput(el.histSearch.value));

  $$("[data-nav]", el.drawer).forEach((btn) => {
    btn.addEventListener("click", () => {
      closeDrawer();
      closeHistory();
      showScreen(btn.dataset.nav);
    });
  });

  $$("[data-open]", el.drawer).forEach((btn) => {
    btn.addEventListener("click", () => {
      if (btn.dataset.open === "history") openHistory();
    });
  });

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /* ——— Model manager ——— */
  function renderMmAnchor() {
    if (state.etalonTokPerSec == null) {
      el.mmAnchor.hidden = true;
      el.mmAnchor.textContent = "";
    } else {
      el.mmAnchor.hidden = false;
      el.mmAnchor.textContent = `Ориентир: это устройство · эталон · ${state.etalonTokPerSec} ток/с`;
    }
  }

  function installedEmptyHtml() {
    return `<div class="mm-empty">
      <div class="mm-empty__head">
        <p class="mm-empty__title">Нет скачанных моделей.</p>
        <button type="button" class="text-btn" id="btn-setup-start">Начать</button>
      </div>
      <p class="mm-empty__text">Первичная настройка устройства: снимем профиль железа и скачаем эталонную GGUF-модель (~80&nbsp;МБ) — она нужна, чтобы измерить скорость именно на этом телефоне или ПК.</p>
      <p class="mm-empty__text">После короткого бенча появится якорь ток/с и рекомендации: какие модели слабее или сильнее эталона стоит ставить дальше.</p>
    </div>`;
  }

  async function startDeviceSetup() {
    const btn = $("#btn-setup-start");
    const etalon = state.models.find((m) => m.isEtalon);
    if (!etalon) return;
    if (btn) btn.disabled = true;
    try {
      showToast("Скачивание эталона…");
      await sleep(1400);
      etalon.installed = true;
      etalon.active = true;
      state.models.forEach((x) => {
        if (x.role === "LLM" && x.id !== etalon.id) x.active = false;
      });
      showToast(`Скачано: ${etalon.name}`);
      renderModels();

      showToast("Калибровка эталона…");
      await sleep(1200);
      const t = +(5.2 + Math.random() * 2.6).toFixed(1);
      state.etalonTokPerSec = t;
      etalon.meta = `Эталон · download · ~${t} ток/с · Fits`;
      showToast(`Якорь: ${t} ток/с`);
      persist();
      renderModels();
    } finally {
      const again = $("#btn-setup-start");
      if (again) again.disabled = false;
    }
  }

  function renderModels() {
    renderMmAnchor();
    const installed = state.models.filter((m) => m.installed);
    const catalog = state.models.filter((m) => !m.installed);
    el.mmInstalled.innerHTML = installed.length
      ? installed.map((m) => modelCardHtml(m, { manager: true })).join("")
      : installedEmptyHtml();
    el.mmCatalog.innerHTML = catalog.map((m) => modelCardHtml(m, { manager: true })).join("") || "<p class='screen-lead'>Каталог пуст</p>";

    $("#btn-setup-start")?.addEventListener("click", () => {
      startDeviceSetup();
    });

    $$("[data-act]", $("#screen-models")).forEach((btn) => {
      btn.addEventListener("click", async () => {
        const id = btn.dataset.id;
        const m = state.models.find((x) => x.id === id);
        if (!m) return;
        const act = btn.dataset.act;
        if (act === "activate") {
          state.models.forEach((x) => {
            if (x.role === m.role) x.active = x.id === id;
          });
          showToast(`Активна: ${m.name}`);
          renderModels();
          persist();
        } else if (act === "bench") {
          btn.disabled = true;
          btn.classList.add("is-busy");
          await sleep(1400);
          const t = +(2.5 + Math.random() * 4).toFixed(1);
          if (m.role === "LLM") {
            state.etalonTokPerSec = t;
            m.meta = m.isEtalon
              ? `Эталон · download · ~${t} ток/с · Fits`
              : `Живой прогон · ~${t} ток/с`;
          }
          showToast(`Оценка: ${t} ток/с`);
          renderModels();
          persist();
        } else if (act === "delete") {
          m.installed = false;
          m.active = false;
          if (m.isEtalon) {
            state.etalonTokPerSec = null;
            m.meta = "Эталон · download · Fits";
          }
          showToast(`Удалено: ${m.name}`);
          renderModels();
          persist();
        } else if (act === "download") {
          btn.disabled = true;
          btn.classList.add("is-busy");
          await sleep(1200);
          m.installed = true;
          if (m.isEtalon) {
            m.meta = "Эталон · download · скачан · замерьте якорь";
          }
          showToast(`Скачано: ${m.name}`);
          renderModels();
          persist();
        }
      });
    });
  }

  /* ——— Resources ——— */
  function normalizeDoc(d) {
    const active = !!d.active;
    const bytes = d.bytes || 0;
    return {
      id: d.id,
      name: d.name,
      bytes,
      active,
      vectorBytes: active ? d.vectorBytes ?? Math.round(bytes * EMBED_RATIO) : 0,
    };
  }

  function fmtMb(bytes) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
  }

  function docStatus(d) {
    const want = state.draftActive.has(d.id);
    if (d.active && want) return "В индексе";
    if (!d.active && want) return "Будет добавлен";
    if (d.active && !want) return "Будет убран";
    return "Не в индексе";
  }

  function sortedDocs() {
    return [...state.docs].sort((a, b) => {
      const aa = a.active ? 1 : 0;
      const ba = b.active ? 1 : 0;
      if (aa !== ba) return ba - aa;
      return a.name.localeCompare(b.name, "ru");
    });
  }

  function indexDirty() {
    const want = state.draftActive;
    const have = new Set(state.docs.filter((d) => d.active).map((d) => d.id));
    if (want.size !== have.size) return true;
    for (const id of want) if (!have.has(id)) return true;
    return false;
  }

  function renderStorageStats() {
    const activeDocs = state.docs.filter((d) => d.active);
    const sources = activeDocs.reduce((s, d) => s + d.bytes, 0);
    const db = activeDocs.reduce((s, d) => s + (d.vectorBytes || 0), 0);
    const total = sources + db;
    el.storageStats.innerHTML = `
      <div class="stat"><span class="stat__label">Исходники</span><span class="stat__value">${fmtMb(sources)}</span></div>
      <div class="stat"><span class="stat__label">БД / эмбеддинги</span><span class="stat__value">${fmtMb(db)}</span></div>
      <div class="stat"><span class="stat__label">Активных</span><span class="stat__value">${activeDocs.length}</span></div>
      <div class="stat"><span class="stat__label">Всего на диске</span><span class="stat__value">${fmtMb(total)}</span></div>
    `;
  }

  function renderResources() {
    renderStorageStats();

    el.docList.innerHTML = sortedDocs()
      .map((d) => {
        const checked = state.draftActive.has(d.id);
        const pendingAdd = checked && !d.active;
        const pendingOff = d.active && !checked;
        const classes = [
          "doc-card",
          d.active ? "is-active" : "",
          checked ? "is-checked" : "",
          pendingAdd ? "is-pending" : "",
          pendingOff ? "is-pending-off" : "",
        ]
          .filter(Boolean)
          .join(" ");
        return `<article class="${classes}" data-id="${d.id}" role="button" tabindex="0" aria-pressed="${checked}">
        <span class="doc-card__check" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12l5 5L20 7"/></svg>
        </span>
        <div>
          <p class="doc-card__name">${escapeHtml(d.name)}</p>
          <p class="doc-card__meta">${docStatus(d)} · ${fmtMb(d.bytes)}${d.active && d.vectorBytes ? ` · индекс ${fmtMb(d.vectorBytes)}` : ""}</p>
        </div>
        <div class="doc-card__actions">
          <button type="button" class="icon-btn icon-btn--sm icon-btn--danger" data-del="${d.id}" aria-label="Удалить" title="Удалить">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M9 7V5h6v2M8 7l1 12h6l1-12"/></svg>
          </button>
        </div>
      </article>`;
      })
      .join("");

    $$("[data-del]", el.docList).forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        if (state.vectorizing) return;
        const id = btn.dataset.del;
        state.docs = state.docs.filter((d) => d.id !== id);
        state.draftActive.delete(id);
        showToast("Ресурс удалён");
        renderResources();
        persist();
      });
    });
  }

  function toggleDocDraft(id) {
    if (state.vectorizing) return;
    if (state.draftActive.has(id)) state.draftActive.delete(id);
    else state.draftActive.add(id);
    renderResources();
    persist();
  }

  el.docList.addEventListener("click", (e) => {
    if (e.target.closest("[data-del]")) return;
    const card = e.target.closest(".doc-card[data-id]");
    if (!card) return;
    toggleDocDraft(card.dataset.id);
  });

  el.docList.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const card = e.target.closest(".doc-card[data-id]");
    if (!card) return;
    e.preventDefault();
    toggleDocDraft(card.dataset.id);
  });

  async function runVectorizeProgress(toAdd, toRemove) {
    const needEmbed = toAdd.length > 0;
    const phases = needEmbed
      ? [
          { label: "Выгрузка LLM…", until: 10, hint: "Освобождаем RAM перед embedding" },
          { label: "Загрузка embedding-модели…", until: 22, hint: "GGUF embed · mmap · кратко в RAM" },
          {
            label: `Векторизация: +${toAdd.length} / −${toRemove.length}…`,
            until: 78,
            hint: "Пишем Staging · Live пока не трогаем",
          },
          { label: "Выгрузка embedding…", until: 90, hint: "Модель выгружена" },
          { label: "Commit индекса…", until: 100, hint: "Staging → Live · атомарно" },
        ]
      : [
          {
            label:
              toRemove.length > 0
                ? `Удаление из индекса: −${toRemove.length}…`
                : "Синхронизация индекса…",
            until: 100,
            hint: "Без загрузки embedding · только Live",
          },
        ];

    el.vectorizeBlock.hidden = false;
    el.btnCancelVectorize.hidden = false;
    el.screenResources.classList.add("is-vectorizing");
    el.vectorizeBar.style.width = "0%";
    el.vectorizePct.textContent = "0%";
    el.vectorizeBar.parentElement.setAttribute("aria-valuenow", "0");

    let pct = 0;
    let cancelled = false;
    for (const phase of phases) {
      if (state.cancelVectorize) {
        cancelled = true;
        break;
      }
      el.vectorizePhase.textContent = phase.label;
      el.vectorizeHint.textContent = phase.hint;
      while (pct < phase.until) {
        if (state.cancelVectorize) {
          cancelled = true;
          break;
        }
        pct += 1.2 + Math.random() * 2.8;
        if (pct > phase.until) pct = phase.until;
        const v = Math.round(pct);
        el.vectorizeBar.style.width = `${v}%`;
        el.vectorizeBar.parentElement.setAttribute("aria-valuenow", String(v));
        el.vectorizePct.textContent = `${v}%`;
        await sleep(22);
      }
      if (cancelled) break;
    }

    if (cancelled) {
      el.vectorizePhase.textContent = "Отмена…";
      el.vectorizeHint.textContent = "Staging очищен · Live без изменений";
      el.btnCancelVectorize.hidden = true;
      await sleep(400);
      el.vectorizeBlock.hidden = true;
      el.screenResources.classList.remove("is-vectorizing");
      return false;
    }

    el.vectorizePhase.textContent = "Готово";
    el.vectorizeHint.textContent = needEmbed
      ? "Emb выгружена · индекс закоммичен"
      : "Индекс обновлён без embedding";
    el.btnCancelVectorize.hidden = true;
    await sleep(350);
    el.vectorizeBlock.hidden = true;
    el.screenResources.classList.remove("is-vectorizing");
    return true;
  }

  async function applyIndexUpdate() {
    if (state.vectorizing) return;
    if (!indexDirty()) {
      showToast("Изменений нет");
      return;
    }

    const want = state.draftActive;
    const toAdd = state.docs.filter((d) => want.has(d.id) && !d.active);
    const toRemove = state.docs.filter((d) => d.active && !want.has(d.id));

    state.vectorizing = true;
    state.cancelVectorize = false;
    el.btnRefreshDocs.disabled = true;
    el.btnRefreshDocs.classList.add("is-busy");
    setComposerBlocked(true, "Идёт индексация…");

    const committed = await runVectorizeProgress(toAdd, toRemove);

    if (committed) {
      for (const d of state.docs) {
        if (want.has(d.id)) {
          d.active = true;
          d.vectorBytes = Math.round(d.bytes * EMBED_RATIO);
        } else {
          d.active = false;
          d.vectorBytes = 0;
        }
      }
      showToast("Индекс обновлён");
    } else {
      showToast("Индексация отменена · Live цел");
    }

    state.vectorizing = false;
    state.cancelVectorize = false;
    el.btnRefreshDocs.disabled = false;
    el.btnRefreshDocs.classList.remove("is-busy");
    setComposerBlocked(false);
    renderResources();
    persist();
  }

  el.btnCancelVectorize.addEventListener("click", () => {
    if (!state.vectorizing) return;
    state.cancelVectorize = true;
  });

  el.btnUpload.addEventListener("click", () => {
    if (state.vectorizing) return;
    el.fileInput.click();
  });

  el.fileInput.addEventListener("change", () => {
    const files = [...el.fileInput.files];
    for (const f of files) {
      const id = `d-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
      state.docs.unshift({
        id,
        name: f.name,
        bytes: f.size || 4096,
        active: false,
        vectorBytes: 0,
      });
      state.draftActive.add(id);
    }
    el.fileInput.value = "";
    renderResources();
    persist();
    showToast(files.length ? `Добавлено: ${files.length} · нажмите обновить` : "Файл не выбран");
  });

  el.btnRefreshDocs.addEventListener("click", () => {
    applyIndexUpdate();
  });

  /* ——— Boot ——— */
  function boot() {
    updateOrientationGate();
    tryLockPortrait();

    const restored = restoreIfFresh();
    const skipOnboarding = new Set(["power", "reco", "download"]);
    if (restored) {
      const screen = skipOnboarding.has(state.screen) ? "home" : state.screen;
      state.onboardingDone = true;
      if (!state.activeChatId) state.activeChatId = state.chats[0]?.id || null;
      showScreen(screen);
      showToast(`Сессия восстановлена (${(restored.age / 1000).toFixed(1)} с)`);
      return;
    }

    showScreen("home");
  }

  boot();
})();
