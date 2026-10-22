'use strict';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

const state = {
    view: 'big',
    operator: localStorage.getItem('act-mcp-operator') || 'admin',
    options: [],
    editingBigId: null,
    editingProductId: null,
    pendingDelete: null,
    jsonText: '',
    big: {
        page: 1,
        size: 10,
        keyword: '',
        actType: '',
        audienceScope: '',
        items: [],
        total: 0,
        totalPages: 1
    },
    product: {
        page: 1,
        size: 10,
        keyword: '',
        actId: '',
        actType: '',
        items: [],
        total: 0,
        totalPages: 1
    }
};

const fieldMaps = {
    big: {
        name: 'bigName',
        actType: 'bigActType',
        startTime: 'bigStart',
        endTime: 'bigEnd',
        audienceScope: 'bigScope',
        budget: 'bigBudget',
        collectionDiscountJson: 'bigJson'
    },
    product: {
        actId: 'productActId',
        actType: 'productActType',
        poiId: 'productPoiId',
        skuId: 'productSkuId',
        upc: 'productUpc',
        productDiscountJson: 'productJson'
    }
};

/* ---------- Utilities ---------- */

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[char]);
}

function formatDateTime(value) {
    if (!value) return '—';
    return String(value).replace('T', ' ').slice(0, 16);
}

function toInputDateTime(value) {
    if (!value) return '';
    return String(value).replace(' ', 'T').slice(0, 16);
}

function toLocalInput(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatMoney(value) {
    const number = Number(value ?? 0);
    return `¥${number.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}`;
}

function formatCompactMoney(value) {
    const number = Number(value ?? 0);
    if (number >= 100000000) return `¥${(number / 100000000).toFixed(2)}亿`;
    if (number >= 10000) return `¥${(number / 10000).toFixed(2)}万`;
    return formatMoney(number);
}

function scopeLabel(scope) {
    return scope === 'NEW' ? '新客专享' : '所有用户';
}

function scopeClass(scope) {
    return scope === 'NEW' ? 'scope-new' : 'scope-all';
}

function jsonPreview(raw) {
    if (!raw) return '{}';
    const compact = String(raw).replace(/\s+/g, ' ');
    return compact.length > 40 ? `${compact.slice(0, 40)}…` : compact;
}

function isValidJson(raw) {
    const value = String(raw ?? '').trim();
    if (!value) return true;
    try {
        JSON.parse(value);
        return true;
    } catch {
        return false;
    }
}

function debounce(fn, delay) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}

async function api(path, options = {}) {
    const headers = { 'X-Operator': state.operator || 'admin' };
    const init = { method: options.method || 'GET', headers };

    if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(options.body);
    }

    let response;
    try {
        response = await fetch(path, init);
    } catch {
        throw new Error('网络请求失败，请确认服务是否正常运行');
    }

    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = { message: text };
        }
    }

    if (!response.ok) {
        const error = new Error(data?.message || `请求失败（HTTP ${response.status}）`);
        error.fieldErrors = data?.fieldErrors || null;
        error.status = response.status;
        throw error;
    }

    return data;
}

function toast(message, type = 'info') {
    const stack = $('#toastStack');
    const element = document.createElement('div');
    element.className = `toast ${type}`;
    element.textContent = message;
    stack.appendChild(element);

    setTimeout(() => {
        element.classList.add('leaving');
        element.addEventListener('animationend', () => element.remove(), { once: true });
        setTimeout(() => element.remove(), 260);
    }, 3200);
}

function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.hidden = false;
    document.body.classList.add('modal-open');
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.hidden = true;
    if (!$$('.modal-backdrop').some((item) => !item.hidden)) {
        document.body.classList.remove('modal-open');
    }
}

function setButtonLoading(button, loading, loadingText) {
    if (!button) return;
    if (loading) {
        button.dataset.originalText = button.textContent;
        button.disabled = true;
        button.textContent = loadingText;
    } else {
        button.disabled = false;
        button.textContent = button.dataset.originalText || button.textContent;
    }
}

/* ---------- Table helpers ---------- */

function tableLoading(view, colspan) {
    const tbody = document.getElementById(`${view}TableBody`);
    const table = $(`#${view}View table`);
    const empty = document.getElementById(`${view}Empty`);
    table.hidden = false;
    empty.hidden = true;
    tbody.innerHTML = `<tr class="loading-row"><td colspan="${colspan}"><span class="spinner"></span>加载中…</td></tr>`;
}

function tableError(view, colspan, message) {
    const tbody = document.getElementById(`${view}TableBody`);
    const table = $(`#${view}View table`);
    const empty = document.getElementById(`${view}Empty`);
    table.hidden = false;
    empty.hidden = true;
    tbody.innerHTML = `<tr class="table-message"><td colspan="${colspan}">${escapeHtml(message)}</td></tr>`;
}

function buildPageItems(current, total) {
    if (total <= 7) {
        return Array.from({ length: total }, (_, index) => index + 1);
    }
    const pages = new Set([1, total, current - 1, current, current + 1]);
    if (current <= 4) {
        [2, 3, 4, 5].forEach((page) => pages.add(page));
    }
    if (current >= total - 3) {
        [total - 4, total - 3, total - 2, total - 1].forEach((page) => pages.add(page));
    }
    const sorted = Array.from(pages)
        .filter((page) => page >= 1 && page <= total)
        .sort((a, b) => a - b);

    const output = [];
    sorted.forEach((page, index) => {
        if (index > 0 && page - sorted[index - 1] > 1) output.push('…');
        output.push(page);
    });
    return output;
}

function renderPagination(view, listState) {
    const summary = document.getElementById(`${view}PageSummary`);
    const buttons = document.getElementById(`${view}PageButtons`);
    const totalPages = listState.totalPages || 0;
    const current = listState.page || 1;

    summary.textContent = listState.total === 0
        ? '共 0 条'
        : `共 ${listState.total} 条 · 第 ${current} / ${totalPages} 页`;

    const parts = [];
    parts.push(`<button type="button" class="page-btn" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''} aria-label="上一页">‹</button>`);
    buildPageItems(current, totalPages).forEach((page) => {
        if (page === '…') {
            parts.push('<span class="page-ellipsis">…</span>');
        } else {
            parts.push(`<button type="button" class="page-btn ${page === current ? 'active' : ''}" data-page="${page}" ${page === current ? 'aria-current="page"' : ''}>${page}</button>`);
        }
    });
    parts.push(`<button type="button" class="page-btn" data-page="${current + 1}" ${current >= totalPages ? 'disabled' : ''} aria-label="下一页">›</button>`);
    buttons.innerHTML = parts.join('');
}

function updateTypeFilters() {
    const types = new Set();
    state.options.forEach((item) => item.actType && types.add(item.actType));
    state.big.items.forEach((item) => item.actType && types.add(item.actType));
    state.product.items.forEach((item) => item.actType && types.add(item.actType));

    if (state.big.actType) types.add(state.big.actType);
    if (state.product.actType) types.add(state.product.actType);

    const sorted = Array.from(types).sort((a, b) => a.localeCompare(b, 'zh-CN'));
    [
        ['bigTypeFilter', state.big.actType],
        ['productTypeFilter', state.product.actType]
    ].forEach(([id, selected]) => {
        const select = document.getElementById(id);
        select.innerHTML = ['<option value="">全部类型</option>']
            .concat(sorted.map((type) => `<option value="${escapeHtml(type)}">${escapeHtml(type)}</option>`))
            .join('');
        select.value = selected;
    });
}

function fillActivitySelects() {
    const filter = $('#productActFilter');
    const filterValue = state.product.actId;
    filter.innerHTML = ['<option value="">全部大活动</option>']
        .concat(state.options.map((item) => `<option value="${item.id}">${escapeHtml(item.name)}</option>`))
        .join('');
    filter.value = state.options.some((item) => String(item.id) === String(filterValue))
        ? filterValue
        : '';
    if (!filter.value) state.product.actId = '';

    const modalSelect = $('#productActId');
    const modalValue = modalSelect.value;
    modalSelect.innerHTML = ['<option value="">请选择大活动</option>']
        .concat(state.options.map((item) => `<option value="${item.id}">${escapeHtml(item.name)}</option>`))
        .join('');
    if (state.options.some((item) => String(item.id) === String(modalValue))) {
        modalSelect.value = modalValue;
    }
}

/* ---------- Data loading ---------- */

async function loadStats() {
    try {
        const stats = await api('/api/stats');
        $('#statBig').textContent = Number(stats.bigActivityCount).toLocaleString('zh-CN');
        $('#statProduct').textContent = Number(stats.productActivityCount).toLocaleString('zh-CN');
        $('#statOngoing').textContent = Number(stats.ongoingCount).toLocaleString('zh-CN');
        $('#statBudget').textContent = formatCompactMoney(stats.totalBudget);
        $('#bigNavCount').textContent = stats.bigActivityCount;
        $('#productNavCount').textContent = stats.productActivityCount;
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function loadOptions() {
    try {
        state.options = await api('/api/big-activities/options');
        fillActivitySelects();
        updateTypeFilters();
    } catch (error) {
        toast(`加载大活动选项失败：${error.message}`, 'error');
    }
}

async function loadBigs(options = {}) {
    if (!options.silent) tableLoading('big', 8);
    const params = new URLSearchParams({ page: state.big.page, size: state.big.size });
    if (state.big.keyword) params.set('keyword', state.big.keyword);
    if (state.big.actType) params.set('actType', state.big.actType);
    if (state.big.audienceScope) params.set('audienceScope', state.big.audienceScope);

    try {
        const data = await api(`/api/big-activities?${params.toString()}`);
        state.big.items = data.items;
        state.big.total = data.total;
        state.big.totalPages = data.totalPages;
        state.big.page = data.page;
        renderBigs();
        updateTypeFilters();
    } catch (error) {
        tableError('big', 8, `加载失败：${error.message}`);
        $('#bigPageSummary').textContent = '加载失败';
        $('#bigPageButtons').innerHTML = '';
        if (!options.silent) toast(error.message, 'error');
    }
}

async function loadProducts(options = {}) {
    if (!options.silent) tableLoading('product', 6);
    const params = new URLSearchParams({ page: state.product.page, size: state.product.size });
    if (state.product.keyword) params.set('keyword', state.product.keyword);
    if (state.product.actType) params.set('actType', state.product.actType);
    if (state.product.actId) params.set('actId', state.product.actId);

    try {
        const data = await api(`/api/product-activities?${params.toString()}`);
        state.product.items = data.items;
        state.product.total = data.total;
        state.product.totalPages = data.totalPages;
        state.product.page = data.page;
        renderProducts();
        updateTypeFilters();
    } catch (error) {
        tableError('product', 6, `加载失败：${error.message}`);
        $('#productPageSummary').textContent = '加载失败';
        $('#productPageButtons').innerHTML = '';
        if (!options.silent) toast(error.message, 'error');
    }
}

async function refreshAll(showToast = false) {
    const results = await Promise.allSettled([
        loadStats(),
        loadOptions(),
        loadBigs({ silent: true }),
        loadProducts({ silent: true })
    ]);
    if (showToast) {
        const failed = results.some((result) => result.status === 'rejected');
        toast(failed ? '部分数据刷新失败，请稍后重试' : '数据已刷新', failed ? 'error' : 'success');
    }
}

/* ---------- Rendering ---------- */

function renderBigs() {
    const tbody = $('#bigTableBody');
    const table = $('#bigView table');
    const empty = $('#bigEmpty');

    if (state.big.items.length === 0) {
        tbody.innerHTML = '';
        table.hidden = true;
        empty.hidden = false;
    } else {
        table.hidden = false;
        empty.hidden = true;
        tbody.innerHTML = state.big.items.map((item) => `
            <tr>
                <td>
                    <div class="cell-title">${escapeHtml(item.name)}</div>
                    <div class="cell-sub">#${item.id} · 创建人 ${escapeHtml(item.createdBy || '—')}</div>
                </td>
                <td><span class="badge type">${escapeHtml(item.actType || '未设置')}</span></td>
                <td>
                    <div class="time-range">
                        <span>${escapeHtml(formatDateTime(item.startTime))}</span>
                        <span class="time-end">至 ${escapeHtml(formatDateTime(item.endTime))}</span>
                    </div>
                </td>
                <td><span class="badge ${scopeClass(item.audienceScope)}">${scopeLabel(item.audienceScope)}</span></td>
                <td class="num"><span class="budget-value">${formatMoney(item.budget)}</span></td>
                <td class="num">
                    <button type="button" class="count-link" data-action="view-products" data-id="${item.id}" title="查看该活动下的商品">
                        ${item.productCount}
                    </button>
                </td>
                <td>
                    <div class="timestamp">
                        <span>${escapeHtml(formatDateTime(item.updatedAt))}</span>
                        <span class="by">${escapeHtml(item.updatedBy || '—')}</span>
                    </div>
                </td>
                <td class="actions-col">
                    <div class="row-actions">
                        <button type="button" class="row-action" data-action="edit-big" data-id="${item.id}">编辑</button>
                        <button type="button" class="row-action" data-action="view-big-json" data-id="${item.id}">JSON</button>
                        <button type="button" class="row-action danger" data-action="delete-big" data-id="${item.id}">删除</button>
                    </div>
                </td>
            </tr>
        `).join('');
    }
    renderPagination('big', state.big);
}

function renderProducts() {
    const tbody = $('#productTableBody');
    const table = $('#productView table');
    const empty = $('#productEmpty');

    if (state.product.items.length === 0) {
        tbody.innerHTML = '';
        table.hidden = true;
        empty.hidden = false;
    } else {
        table.hidden = false;
        empty.hidden = true;
        tbody.innerHTML = state.product.items.map((item) => `
            <tr>
                <td>
                    <div class="cell-stack">
                        <div class="mono-line"><span class="key">POI</span>${escapeHtml(item.poiId)}</div>
                        <div class="mono-line"><span class="key">SKU</span>${escapeHtml(item.skuId || '—')}</div>
                        <div class="mono-line"><span class="key">UPC</span>${escapeHtml(item.upc || '—')}</div>
                    </div>
                </td>
                <td>
                    <div class="cell-title">${escapeHtml(item.actName || '—')}</div>
                    <div class="cell-sub">大活动 #${item.actId}</div>
                </td>
                <td><span class="badge type">${escapeHtml(item.actType || '未设置')}</span></td>
                <td>
                    <button type="button" class="json-pill" data-action="view-product-json" data-id="${item.id}" title="查看完整 JSON">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 4C6 4 5 5 5 7v3c0 1-.6 2-2 2 1.4 0 2 1 2 2v3c0 2 1 3 3 3M16 4c2 0 3 1 3 3v3c0 1 .6 2 2 2-1.4 0-2 1-2 2v3c0 2-1 3-3 3"/></svg>
                        ${escapeHtml(jsonPreview(item.productDiscountJson))}
                    </button>
                </td>
                <td>
                    <div class="timestamp">
                        <span>${escapeHtml(formatDateTime(item.updatedAt))}</span>
                        <span class="by">${escapeHtml(item.updatedBy || '—')}</span>
                    </div>
                </td>
                <td class="actions-col">
                    <div class="row-actions">
                        <button type="button" class="row-action" data-action="edit-product" data-id="${item.id}">编辑</button>
                        <button type="button" class="row-action" data-action="view-product-json" data-id="${item.id}">JSON</button>
                        <button type="button" class="row-action danger" data-action="delete-product" data-id="${item.id}">删除</button>
                    </div>
                </td>
            </tr>
        `).join('');
    }
    renderPagination('product', state.product);
}

/* ---------- View switching ---------- */

function switchView(view) {
    state.view = view;
    const hash = view === 'product' ? '#product' : '#big';
    if (location.hash !== hash) history.replaceState(null, '', hash);
    $$('.nav-item').forEach((item) => item.classList.toggle('active', item.dataset.view === view));
    $('#bigView').hidden = view !== 'big';
    $('#productView').hidden = view !== 'product';

    if (view === 'big') {
        $('#pageTitle').textContent = '大活动管理';
        $('#pageSubtitle').textContent = '维护活动主体信息与全局优惠配置';
        loadBigs({ silent: true });
    } else {
        $('#pageTitle').textContent = '商品活动管理';
        $('#pageSubtitle').textContent = '按商品维度配置 POI、SKU、UPC 与商品级优惠';
        loadProducts({ silent: true });
    }
}

/* ---------- Form helpers ---------- */

function clearFormErrors(formId) {
    const form = document.getElementById(formId);
    $$('.field.invalid', form).forEach((field) => field.classList.remove('invalid'));
    const errorBox = $('.form-error', form);
    errorBox.hidden = true;
    errorBox.textContent = '';
}

function showFormError(formId, message) {
    const errorBox = $(`#${formId} .form-error`);
    errorBox.textContent = message;
    errorBox.hidden = false;
}

function markInvalid(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const field = input.closest('.field');
    if (field) field.classList.add('invalid');
}

function focusFirstInvalid(formId) {
    const input = $(`#${formId} .field.invalid input, #${formId} .field.invalid select, #${formId} .field.invalid textarea`);
    if (input) input.focus();
}

function applyServerFieldErrors(kind, error) {
    if (!error.fieldErrors) return;
    Object.keys(error.fieldErrors).forEach((field) => {
        const inputId = fieldMaps[kind][field];
        if (inputId) markInvalid(inputId);
    });
    focusFirstInvalid(kind === 'big' ? 'bigForm' : 'productForm');
}

function formatJsonField(textarea) {
    const value = textarea.value.trim();
    if (!value) {
        textarea.value = '{}';
        return;
    }
    try {
        textarea.value = JSON.stringify(JSON.parse(value), null, 2);
    } catch {
        toast('当前内容不是合法 JSON，无法格式化', 'error');
    }
}

function resetBigForm() {
    $('#bigForm').reset();
    clearFormErrors('bigForm');
    state.editingBigId = null;
    const now = new Date();
    const end = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    $('#bigStart').value = toLocalInput(now);
    $('#bigEnd').value = toLocalInput(end);
    $('#bigBudget').value = '0';
    $('#bigScope').value = 'ALL';
    $('#bigJson').value = '{}';
    $('#bigModalTitle').textContent = '新建大活动';
    $('#bigSubmitBtn').textContent = '保存活动';
}

async function openBigModal(id = null) {
    resetBigForm();
    if (id !== null) {
        try {
            const item = await api(`/api/big-activities/${id}`);
            state.editingBigId = item.id;
            $('#bigModalTitle').textContent = '编辑大活动';
            $('#bigSubmitBtn').textContent = '保存修改';
            $('#bigName').value = item.name || '';
            $('#bigActType').value = item.actType || '';
            $('#bigScope').value = item.audienceScope || 'ALL';
            $('#bigStart').value = toInputDateTime(item.startTime);
            $('#bigEnd').value = toInputDateTime(item.endTime);
            $('#bigBudget').value = Number(item.budget ?? 0).toFixed(2);
            $('#bigJson').value = item.collectionDiscountJson || '{}';
        } catch (error) {
            toast(error.message, 'error');
            return;
        }
    }
    openModal('bigModal');
    setTimeout(() => $('#bigName').focus(), 50);
}

async function openProductModal(id = null, presetActId = null) {
    clearFormErrors('productForm');
    $('#productForm').reset();
    state.editingProductId = null;
    $('#productModalTitle').textContent = '新建商品活动';
    $('#productSubmitBtn').textContent = '保存商品活动';
    $('#productJson').value = '{}';
    fillActivitySelects();

    if (state.options.length === 0) {
        toast('请先创建一个大活动', 'info');
        openBigModal();
        return;
    }

    if (id !== null) {
        try {
            const item = await api(`/api/product-activities/${id}`);
            state.editingProductId = item.id;
            $('#productModalTitle').textContent = '编辑商品活动';
            $('#productSubmitBtn').textContent = '保存修改';
            $('#productActId').value = String(item.actId);
            $('#productActType').value = item.actType || '';
            $('#productPoiId').value = item.poiId || '';
            $('#productSkuId').value = item.skuId || '';
            $('#productUpc').value = item.upc || '';
            $('#productJson').value = item.productDiscountJson || '{}';
        } catch (error) {
            toast(error.message, 'error');
            return;
        }
    } else {
        const actId = presetActId || state.product.actId;
        if (actId && state.options.some((item) => String(item.id) === String(actId))) {
            $('#productActId').value = String(actId);
        }
        const selected = state.options.find((item) => String(item.id) === $('#productActId').value);
        $('#productActType').value = selected ? selected.actType : '';
    }

    openModal('productModal');
    setTimeout(() => $('#productActId').focus(), 50);
}

/* ---------- JSON viewer ---------- */

function openJsonModal(kind, item) {
    const raw = kind === 'big' ? item.collectionDiscountJson : item.productDiscountJson;
    let pretty = raw || '{}';
    try {
        pretty = JSON.stringify(JSON.parse(pretty), null, 2);
    } catch {
        // 保留原始内容，服务端已校验过，这里是防御性处理
    }
    state.jsonText = pretty;
    $('#jsonModalTitle').textContent = kind === 'big' ? '集合优惠 JSON' : '商品优惠 JSON';
    $('#jsonModalSubtitle').textContent = kind === 'big'
        ? `大活动 #${item.id} · ${item.name || ''}`
        : `商品活动 #${item.id} · ${item.actName || ''}`;
    $('#jsonViewContent').textContent = pretty;
    openModal('jsonModal');
}

async function copyJson() {
    try {
        await navigator.clipboard.writeText(state.jsonText);
        toast('JSON 已复制到剪贴板', 'success');
    } catch {
        const textarea = document.createElement('textarea');
        textarea.value = state.jsonText;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        const ok = document.execCommand('copy');
        textarea.remove();
        toast(ok ? 'JSON 已复制到剪贴板' : '复制失败，请手动选择文本', ok ? 'success' : 'error');
    }
}

/* ---------- Delete ---------- */

function askDelete(kind, item) {
    state.pendingDelete = { kind, id: item.id, name: kind === 'big' ? item.name : item.poiId };
    const title = $('#confirmTitle');
    const message = $('#confirmMessage');
    if (kind === 'big') {
        const extra = item.productCount > 0
            ? `该活动下还有 ${item.productCount} 个商品活动，删除后将一并移除。`
            : '该活动下暂无商品活动。';
        title.textContent = '删除大活动？';
        message.textContent = `即将删除「${item.name}」。${extra}此操作不可恢复。`;
    } else {
        title.textContent = '删除商品活动？';
        message.textContent = `即将删除 POI「${item.poiId}」对应的商品活动。此操作不可恢复。`;
    }
    openModal('confirmModal');
}

async function confirmDelete() {
    const pending = state.pendingDelete;
    if (!pending) return;
    const button = $('#confirmDeleteBtn');
    setButtonLoading(button, true, '删除中…');

    try {
        if (pending.kind === 'big') {
            await api(`/api/big-activities/${pending.id}`, { method: 'DELETE' });
            if (String(state.product.actId) === String(pending.id)) {
                state.product.actId = '';
                state.product.page = 1;
            }
            if (state.big.items.length === 1 && state.big.page > 1) state.big.page -= 1;
        } else {
            await api(`/api/product-activities/${pending.id}`, { method: 'DELETE' });
            if (state.product.items.length === 1 && state.product.page > 1) state.product.page -= 1;
        }

        closeModal('confirmModal');
        state.pendingDelete = null;
        toast('删除成功', 'success');
        await refreshAll();
        if (state.view === 'big') loadBigs({ silent: true });
        else loadProducts({ silent: true });
    } catch (error) {
        toast(error.message, 'error');
    } finally {
        setButtonLoading(button, false);
    }
}

/* ---------- Events ---------- */

function bindEvents() {
    $('#operatorInput').value = state.operator;
    $('#operatorInput').addEventListener('input', (event) => {
        state.operator = event.target.value.trim() || 'admin';
        localStorage.setItem('act-mcp-operator', state.operator);
    });

    $$('.nav-item').forEach((button) => {
        button.addEventListener('click', () => switchView(button.dataset.view));
    });

    $('#refreshBtn').addEventListener('click', async () => {
        const button = $('#refreshBtn');
        button.classList.add('spinning');
        await refreshAll(true);
        setTimeout(() => button.classList.remove('spinning'), 400);
    });

    $('#addBigBtn').addEventListener('click', () => openBigModal());
    $('#bigEmptyAddBtn').addEventListener('click', () => openBigModal());
    $('#addProductBtn').addEventListener('click', () => openProductModal());
    $('#productEmptyAddBtn').addEventListener('click', () => openProductModal());

    $('#bigKeyword').addEventListener('input', debounce((event) => {
        state.big.keyword = event.target.value.trim();
        state.big.page = 1;
        loadBigs();
    }, 300));

    $('#bigTypeFilter').addEventListener('change', (event) => {
        state.big.actType = event.target.value;
        state.big.page = 1;
        loadBigs();
    });

    $('#bigScopeFilter').addEventListener('change', (event) => {
        state.big.audienceScope = event.target.value;
        state.big.page = 1;
        loadBigs();
    });

    $('#bigResetBtn').addEventListener('click', () => {
        state.big.keyword = '';
        state.big.actType = '';
        state.big.audienceScope = '';
        state.big.page = 1;
        $('#bigKeyword').value = '';
        $('#bigTypeFilter').value = '';
        $('#bigScopeFilter').value = '';
        loadBigs();
    });

    $('#productKeyword').addEventListener('input', debounce((event) => {
        state.product.keyword = event.target.value.trim();
        state.product.page = 1;
        loadProducts();
    }, 300));

    $('#productActFilter').addEventListener('change', (event) => {
        state.product.actId = event.target.value;
        state.product.page = 1;
        loadProducts();
    });

    $('#productTypeFilter').addEventListener('change', (event) => {
        state.product.actType = event.target.value;
        state.product.page = 1;
        loadProducts();
    });

    $('#productResetBtn').addEventListener('click', () => {
        state.product.keyword = '';
        state.product.actId = '';
        state.product.actType = '';
        state.product.page = 1;
        $('#productKeyword').value = '';
        $('#productActFilter').value = '';
        $('#productTypeFilter').value = '';
        loadProducts();
    });

    $('#bigPageButtons').addEventListener('click', (event) => {
        const button = event.target.closest('.page-btn');
        if (!button || button.disabled) return;
        state.big.page = Number(button.dataset.page);
        loadBigs();
    });

    $('#productPageButtons').addEventListener('click', (event) => {
        const button = event.target.closest('.page-btn');
        if (!button || button.disabled) return;
        state.product.page = Number(button.dataset.page);
        loadProducts();
    });

    $('#bigTableBody').addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        const id = Number(button.dataset.id);
        const item = state.big.items.find((row) => row.id === id);
        if (!item) return;

        switch (button.dataset.action) {
            case 'edit-big':
                openBigModal(id);
                break;
            case 'delete-big':
                askDelete('big', item);
                break;
            case 'view-big-json':
                openJsonModal('big', item);
                break;
            case 'view-products':
                state.product.actId = String(id);
                state.product.page = 1;
                $('#productActFilter').value = String(id);
                switchView('product');
                break;
            default:
                break;
        }
    });

    $('#productTableBody').addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        const id = Number(button.dataset.id);
        const item = state.product.items.find((row) => row.id === id);
        if (!item) return;

        switch (button.dataset.action) {
            case 'edit-product':
                openProductModal(id);
                break;
            case 'delete-product':
                askDelete('product', item);
                break;
            case 'view-product-json':
                openJsonModal('product', item);
                break;
            default:
                break;
        }
    });

    $('#bigForm').addEventListener('submit', submitBigForm);
    $('#productForm').addEventListener('submit', submitProductForm);

    $$('#bigForm input, #bigForm select, #bigForm textarea').forEach((input) => {
        input.addEventListener('input', () => input.closest('.field')?.classList.remove('invalid'));
    });
    $$('#productForm input, #productForm select, #productForm textarea').forEach((input) => {
        input.addEventListener('input', () => input.closest('.field')?.classList.remove('invalid'));
    });

    $('#formatBigJson').addEventListener('click', () => formatJsonField($('#bigJson')));
    $('#formatProductJson').addEventListener('click', () => formatJsonField($('#productJson')));
    $('#copyJsonBtn').addEventListener('click', copyJson);
    $('#confirmDeleteBtn').addEventListener('click', confirmDelete);

    $('#productActId').addEventListener('change', (event) => {
        const selected = state.options.find((item) => String(item.id) === event.target.value);
        if (selected) $('#productActType').value = selected.actType || '';
    });

    $$('[data-close]').forEach((button) => {
        button.addEventListener('click', () => closeModal(button.dataset.close));
    });

    $$('.modal-backdrop').forEach((backdrop) => {
        backdrop.addEventListener('click', (event) => {
            if (event.target === backdrop) closeModal(backdrop.id);
        });
    });

    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        const visible = $$('.modal-backdrop').filter((modal) => !modal.hidden);
        if (visible.length > 0) closeModal(visible[visible.length - 1].id);
    });
}

/* ---------- Submit ---------- */

async function submitBigForm(event) {
    event.preventDefault();
    const form = $('#bigForm');
    clearFormErrors('bigForm');

    const name = $('#bigName').value.trim();
    const actType = $('#bigActType').value.trim();
    const startTime = $('#bigStart').value;
    const endTime = $('#bigEnd').value;
    const audienceScope = $('#bigScope').value;
    const budgetValue = $('#bigBudget').value;
    const collectionDiscountJson = $('#bigJson').value.trim();
    const errors = [];

    if (!name) {
        errors.push('请填写活动名称');
        markInvalid('bigName');
    }
    if (!actType) {
        errors.push('请填写活动类型');
        markInvalid('bigActType');
    }
    if (!startTime) {
        errors.push('请选择开始时间');
        markInvalid('bigStart');
    }
    if (!endTime) {
        errors.push('请选择结束时间');
        markInvalid('bigEnd');
    }
    if (startTime && endTime && new Date(endTime) <= new Date(startTime)) {
        errors.push('结束时间必须晚于开始时间');
        markInvalid('bigStart');
        markInvalid('bigEnd');
    }
    if (budgetValue === '' || Number.isNaN(Number(budgetValue)) || Number(budgetValue) < 0) {
        errors.push('预算必须为不小于 0 的数字');
        markInvalid('bigBudget');
    }
    if (!isValidJson(collectionDiscountJson)) {
        errors.push('集合优惠 JSON 格式不合法');
        markInvalid('bigJson');
    }

    if (errors.length > 0) {
        showFormError('bigForm', errors.join('\n'));
        focusFirstInvalid('bigForm');
        return;
    }

    const payload = {
        name,
        actType,
        startTime,
        endTime,
        audienceScope,
        budget: Number(budgetValue),
        collectionDiscountJson
    };
    const button = $('#bigSubmitBtn');
    setButtonLoading(button, true, '保存中…');

    try {
        if (state.editingBigId === null) {
            await api('/api/big-activities', { method: 'POST', body: payload });
            toast('大活动创建成功', 'success');
        } else {
            await api(`/api/big-activities/${state.editingBigId}`, { method: 'PUT', body: payload });
            toast('大活动更新成功', 'success');
        }
        closeModal('bigModal');
        await refreshAll();
    } catch (error) {
        showFormError('bigForm', error.fieldErrors
            ? Object.values(error.fieldErrors).join('\n')
            : error.message);
        applyServerFieldErrors('big', error);
    } finally {
        setButtonLoading(button, false);
    }
}

async function submitProductForm(event) {
    event.preventDefault();
    const form = $('#productForm');
    clearFormErrors('productForm');

    const actId = $('#productActId').value;
    const actType = $('#productActType').value.trim();
    const poiId = $('#productPoiId').value.trim();
    const skuId = $('#productSkuId').value.trim();
    const upc = $('#productUpc').value.trim();
    const productDiscountJson = $('#productJson').value.trim();
    const errors = [];

    if (!actId) {
        errors.push('请选择所属大活动');
        markInvalid('productActId');
    }
    if (!actType) {
        errors.push('请填写活动类型');
        markInvalid('productActType');
    }
    if (!poiId) {
        errors.push('请填写 POI ID');
        markInvalid('productPoiId');
    }
    if (!isValidJson(productDiscountJson)) {
        errors.push('商品优惠 JSON 格式不合法');
        markInvalid('productJson');
    }

    if (errors.length > 0) {
        showFormError('productForm', errors.join('\n'));
        focusFirstInvalid('productForm');
        return;
    }

    const payload = {
        actId: Number(actId),
        actType,
        poiId,
        skuId,
        upc,
        productDiscountJson
    };
    const button = $('#productSubmitBtn');
    setButtonLoading(button, true, '保存中…');

    try {
        if (state.editingProductId === null) {
            await api('/api/product-activities', { method: 'POST', body: payload });
            toast('商品活动创建成功', 'success');
        } else {
            await api(`/api/product-activities/${state.editingProductId}`, { method: 'PUT', body: payload });
            toast('商品活动更新成功', 'success');
        }
        closeModal('productModal');
        await refreshAll();
    } catch (error) {
        showFormError('productForm', error.fieldErrors
            ? Object.values(error.fieldErrors).join('\n')
            : error.message);
        applyServerFieldErrors('product', error);
    } finally {
        setButtonLoading(button, false);
    }
}

/* ---------- Bootstrap ---------- */

async function bootstrap() {
    bindEvents();
    if (location.hash === '#product') {
        state.view = 'product';
        $$('.nav-item').forEach((item) => item.classList.toggle('active', item.dataset.view === 'product'));
        $('#bigView').hidden = true;
        $('#productView').hidden = false;
        $('#pageTitle').textContent = '商品活动管理';
        $('#pageSubtitle').textContent = '按商品维度配置 POI、SKU、UPC 与商品级优惠';
    }
    await loadOptions();
    await Promise.all([loadStats(), loadBigs(), loadProducts()]);
}

document.addEventListener('DOMContentLoaded', bootstrap);
