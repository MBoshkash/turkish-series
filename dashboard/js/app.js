/**
 * Turkish Series Dashboard - Main App
 */

// Global State
let appConfig = null;
let appConfigSha = null;
let seriesList = null;
let seriesListSha = null;
let scraperConfig = null;  // بيانات config.json (تحتوي على روابط المصادر للمسلسلات)
let scraperConfigSha = null;
let currentPage = 1;
const itemsPerPage = 20;

// ============================================
// Initialization
// ============================================

document.addEventListener('DOMContentLoaded', async () => {
    // تحميل إعدادات GitHub
    const hasSettings = githubAPI.loadSettings();
    updateGitHubFields();

    // Navigation
    setupNavigation();

    // Menu toggle for mobile
    document.getElementById('menuToggle').addEventListener('click', () => {
        document.getElementById('sidebar').classList.toggle('open');
    });

    // محاولة تحميل البيانات
    await loadInitialData();

    // لو عندنا Token محفوظ، نعمل sync تلقائي
    if (hasSettings && githubAPI.hasToken()) {
        console.log('Auto-syncing with saved token...');
        await syncWithGitHub();
    }
});

/**
 * إعداد التنقل بين الصفحات
 */
function setupNavigation() {
    const navItems = document.querySelectorAll('.nav-item');

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const page = item.dataset.page;

            // Update active nav
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');

            // Show page
            document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
            document.getElementById(`page-${page}`).classList.add('active');

            // Update title
            document.getElementById('pageTitle').textContent = item.querySelector('span').textContent;

            // Close sidebar on mobile
            document.getElementById('sidebar').classList.remove('open');
        });
    });
}

/**
 * تحديث حقول GitHub من الـ localStorage
 */
function updateGitHubFields() {
    document.getElementById('githubToken').value = githubAPI.token || '';
    document.getElementById('repoOwner').value = githubAPI.owner || 'MBoshkash';
    document.getElementById('repoName').value = githubAPI.repo || 'turkish-series';
}

// ============================================
// Data Loading
// ============================================

/**
 * تحميل البيانات الأولية
 */
async function loadInitialData() {
    try {
        // تحميل من GitHub Pages (قراءة فقط)
        const baseUrl = `https://${githubAPI.owner || 'mboshkash'}.github.io/${githubAPI.repo || 'turkish-series'}`;

        // تحميل app_config.json
        const configResponse = await fetch(`${baseUrl}/data/app_config.json`);
        if (configResponse.ok) {
            appConfig = await configResponse.json();
            updateDashboardStats();
            loadSourcesPage();
            loadMessagesPage();
            loadSettingsPage();
        }

        // تحميل series.json
        const seriesResponse = await fetch(`${baseUrl}/data/series.json`);
        if (seriesResponse.ok) {
            const data = await seriesResponse.json();
            seriesList = data.series || [];
            document.getElementById('totalSeries').textContent = seriesList.length;
            loadSeriesPage();
            populateSeriesSelect();
        }

        // تحديث حالة الاتصال
        updateConnectionStatus(true);

        showToast('success', 'تم التحميل', 'تم تحميل البيانات بنجاح');
    } catch (error) {
        console.error('Error loading data:', error);
        showToast('error', 'خطأ', 'فشل تحميل البيانات');
        updateConnectionStatus(false);
    }
}

/**
 * مزامنة مع GitHub (جلب أحدث البيانات مع SHA)
 */
async function syncWithGitHub() {
    if (!githubAPI.hasToken()) {
        showToast('warning', 'تنبيه', 'يجب إدخال GitHub Token أولاً');
        return;
    }

    const syncBtn = document.getElementById('syncBtn');
    syncBtn.disabled = true;
    syncBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> جاري المزامنة...';

    try {
        // جلب app_config.json مع SHA
        const configResult = await githubAPI.readJSON('data/app_config.json');
        if (configResult) {
            appConfig = configResult.data;
            appConfigSha = configResult.sha;
        }

        // جلب series.json مع SHA
        const seriesResult = await githubAPI.readJSON('data/series.json');
        if (seriesResult) {
            seriesList = seriesResult.data.series || [];
            seriesListSha = seriesResult.sha;
        }

        // جلب config.json (يحتوي على روابط المصادر للمسلسلات)
        const scraperResult = await githubAPI.readJSON('data/config.json');
        if (scraperResult) {
            scraperConfig = scraperResult.data;
            scraperConfigSha = scraperResult.sha;
            console.log('Loaded scraper config with', scraperConfig.series?.length || 0, 'series');
        }

        // تحديث الواجهة
        updateDashboardStats();
        loadSourcesPage();
        loadSeriesPage();
        loadMessagesPage();
        loadSettingsPage();
        populateSeriesSelect();

        showToast('success', 'تمت المزامنة', 'تم جلب أحدث البيانات من GitHub');
        updateConnectionStatus(true);
    } catch (error) {
        console.error('Sync error:', error);
        showToast('error', 'خطأ في المزامنة', error.message);
        updateConnectionStatus(false);
    } finally {
        syncBtn.disabled = false;
        syncBtn.innerHTML = '<i class="fas fa-sync"></i> مزامنة';
    }
}

// ============================================
// GitHub Settings
// ============================================

/**
 * حفظ إعدادات GitHub
 */
function saveGitHubSettings() {
    const token = document.getElementById('githubToken').value.trim();
    const owner = document.getElementById('repoOwner').value.trim();
    const repo = document.getElementById('repoName').value.trim();

    if (!token || !owner || !repo) {
        showToast('error', 'خطأ', 'يجب ملء جميع الحقول');
        return;
    }

    githubAPI.saveSettings(token, owner, repo);
    showToast('success', 'تم الحفظ', 'تم حفظ إعدادات GitHub');
}

/**
 * اختبار الاتصال بـ GitHub
 */
async function testConnection() {
    const token = document.getElementById('githubToken').value.trim();
    const owner = document.getElementById('repoOwner').value.trim();
    const repo = document.getElementById('repoName').value.trim();

    if (!token) {
        showToast('error', 'خطأ', 'يجب إدخال GitHub Token');
        return;
    }

    githubAPI.init(token, owner, repo);

    try {
        const repoInfo = await githubAPI.testConnection();
        showToast('success', 'نجح الاتصال', `متصل بـ ${repoInfo.full_name}`);
        updateConnectionStatus(true);

        // حفظ الإعدادات
        saveGitHubSettings();

        // مزامنة
        await syncWithGitHub();
    } catch (error) {
        showToast('error', 'فشل الاتصال', error.message);
        updateConnectionStatus(false);
    }
}

// ============================================
// Dashboard Stats
// ============================================

function updateDashboardStats() {
    if (appConfig) {
        const enabledSources = Object.values(appConfig.sources).filter(s => s.enabled).length;
        document.getElementById('activeSources').textContent = enabledSources;
        document.getElementById('lastUpdate').textContent = formatDate(appConfig.last_updated);
    }

    if (seriesList) {
        document.getElementById('totalSeries').textContent = seriesList.length;

        // حساب إجمالي الحلقات
        const totalEpisodes = seriesList.reduce((sum, s) => sum + (s.episodes_count || 0), 0);
        document.getElementById('totalEpisodes').textContent = totalEpisodes;
    }
}

// ============================================
// Sources Page
// ============================================

function loadSourcesPage() {
    if (!appConfig) return;

    const container = document.getElementById('sourcesList');
    container.innerHTML = '';

    Object.entries(appConfig.sources).forEach(([id, source]) => {
        const card = createSourceCard(id, source);
        container.appendChild(card);
    });
}

function createSourceCard(id, source) {
    const card = document.createElement('div');
    card.className = 'source-card';
    card.innerHTML = `
        <div class="source-card-header">
            <h3>
                <span class="status-dot ${source.enabled ? 'active' : ''}"></span>
                ${source.name}
            </h3>
            <label class="toggle-switch">
                <input type="checkbox" ${source.enabled ? 'checked' : ''}
                       onchange="toggleSource('${id}', this.checked)">
                <span class="toggle-slider"></span>
            </label>
        </div>
        <div class="source-info">
            <div class="source-info-item">
                <span class="label">الدومين الحالي:</span>
                <span>${source.current_domain}</span>
            </div>
            <div class="source-info-item">
                <span class="label">الأولوية:</span>
                <span>${source.priority}</span>
            </div>
            <div class="source-info-item">
                <span class="label">نوع الـ Resolver:</span>
                <span>${source.resolver?.type || 'غير محدد'}</span>
            </div>
        </div>
        <div class="source-actions">
            <button class="btn btn-secondary btn-sm" onclick="editSource('${id}')">
                <i class="fas fa-edit"></i>
                تعديل
            </button>
            <button class="btn btn-danger btn-sm" onclick="deleteSource('${id}')">
                <i class="fas fa-trash"></i>
            </button>
        </div>
    `;
    return card;
}

/**
 * تفعيل/تعطيل مصدر
 */
async function toggleSource(id, enabled) {
    if (!appConfig) return;

    appConfig.sources[id].enabled = enabled;

    // تحديث الواجهة
    loadSourcesPage();
    updateDashboardStats();

    showToast('info', 'تم التحديث', `${enabled ? 'تم تفعيل' : 'تم تعطيل'} ${appConfig.sources[id].name}`);

    // لو عندنا token، نحفظ في GitHub
    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

/**
 * تعديل مصدر
 */
function editSource(id) {
    const source = appConfig.sources[id];
    if (!source) return;

    const resolverType = source.resolver?.type || 'iframe';

    document.getElementById('modalTitle').textContent = `تعديل ${source.name}`;
    document.getElementById('modalBody').innerHTML = `
        <div class="form-group">
            <label>الاسم العربي:</label>
            <input type="text" id="editSourceName" class="form-control" value="${source.name}">
        </div>
        <div class="form-group">
            <label>الاسم الإنجليزي:</label>
            <input type="text" id="editSourceNameEn" class="form-control" value="${source.name_en || ''}">
        </div>
        <div class="form-group">
            <label>الدومين الحالي:</label>
            <input type="text" id="editSourceDomain" class="form-control" value="${source.current_domain}">
        </div>
        <div class="form-group">
            <label>الدومينات البديلة (مفصولة بفاصلة):</label>
            <input type="text" id="editSourceDomains" class="form-control" value="${source.domains.join(', ')}">
        </div>
        <div class="form-group">
            <label>الأولوية:</label>
            <input type="number" id="editSourcePriority" class="form-control" value="${source.priority}">
        </div>
        <div class="form-group">
            <label>نوع الـ Resolver:</label>
            <select id="editSourceResolverType" class="form-control">
                <option value="redirect" ${resolverType === 'redirect' ? 'selected' : ''}>redirect - الموقع يعمل redirect لرابط مباشر</option>
                <option value="iframe" ${resolverType === 'iframe' ? 'selected' : ''}>iframe - الفيديو في iframe محتاج extraction</option>
                <option value="webview" ${resolverType === 'webview' ? 'selected' : ''}>webview - محتاج WebView كامل (حماية قوية)</option>
                <option value="direct" ${resolverType === 'direct' ? 'selected' : ''}>direct - رابط مباشر للفيديو</option>
                <option value="3isk" ${resolverType === '3isk' ? 'selected' : ''}>3isk - قصة عشق (يجلب iframe من embed)</option>
                <option value="akwam" ${resolverType === 'akwam' ? 'selected' : ''}>akwam - أكوام (يجلب رابط مباشر من redirect)</option>
            </select>
            <small>redirect: مثل أكوام | iframe: مثل عرب سيد | 3isk: قصة عشق | akwam: أكوام</small>
        </div>
        <div class="form-group">
            <label>
                <input type="checkbox" id="editSourceNeedsWebview" ${source.resolver?.needs_webview ? 'checked' : ''}>
                يحتاج WebView
            </label>
        </div>
    `;

    document.getElementById('modalSaveBtn').onclick = () => saveSourceEdit(id);
    openModal();
}

async function saveSourceEdit(id) {
    const source = appConfig.sources[id];

    source.name = document.getElementById('editSourceName').value;
    source.name_en = document.getElementById('editSourceNameEn').value;
    source.current_domain = document.getElementById('editSourceDomain').value;
    source.domains = document.getElementById('editSourceDomains').value.split(',').map(d => d.trim());
    source.priority = parseInt(document.getElementById('editSourcePriority').value);

    // تحديث الـ Resolver
    if (!source.resolver) {
        source.resolver = { version: 1 };
    }
    source.resolver.type = document.getElementById('editSourceResolverType').value;
    source.resolver.needs_webview = document.getElementById('editSourceNeedsWebview').checked;

    closeModal();
    loadSourcesPage();
    showToast('success', 'تم الحفظ', 'تم تحديث المصدر');

    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

/**
 * إضافة مصدر جديد
 */
function addNewSource() {
    document.getElementById('modalTitle').textContent = 'إضافة مصدر جديد';
    document.getElementById('modalBody').innerHTML = `
        <div class="form-group">
            <label>المعرف (ID):</label>
            <input type="text" id="newSourceId" class="form-control" placeholder="مثال: arabseed">
        </div>
        <div class="form-group">
            <label>الاسم العربي:</label>
            <input type="text" id="newSourceName" class="form-control" placeholder="مثال: عرب سيد">
        </div>
        <div class="form-group">
            <label>الاسم الإنجليزي:</label>
            <input type="text" id="newSourceNameEn" class="form-control" placeholder="مثال: ArabSeed">
        </div>
        <div class="form-group">
            <label>الدومين:</label>
            <input type="text" id="newSourceDomain" class="form-control" placeholder="مثال: arabseed.ink">
        </div>
        <div class="form-group">
            <label>الأولوية:</label>
            <input type="number" id="newSourcePriority" class="form-control" value="5">
        </div>
        <div class="form-group">
            <label>نوع الـ Resolver:</label>
            <select id="newSourceResolverType" class="form-control">
                <option value="redirect">redirect - الموقع يعمل redirect لرابط مباشر</option>
                <option value="iframe" selected>iframe - الفيديو في iframe محتاج extraction</option>
                <option value="webview">webview - محتاج WebView كامل (حماية قوية)</option>
                <option value="direct">direct - رابط مباشر للفيديو</option>
                <option value="3isk">3isk - قصة عشق (يجلب iframe من embed)</option>
                <option value="akwam">akwam - أكوام (يجلب رابط مباشر من redirect)</option>
            </select>
        </div>
        <div class="form-group">
            <label>
                <input type="checkbox" id="newSourceNeedsWebview" checked>
                يحتاج WebView
            </label>
        </div>
    `;

    document.getElementById('modalSaveBtn').onclick = saveNewSource;
    openModal();
}

async function saveNewSource() {
    const id = document.getElementById('newSourceId').value.trim().toLowerCase();
    const name = document.getElementById('newSourceName').value.trim();
    const nameEn = document.getElementById('newSourceNameEn').value.trim();
    const domain = document.getElementById('newSourceDomain').value.trim();
    const priority = parseInt(document.getElementById('newSourcePriority').value);
    const resolverType = document.getElementById('newSourceResolverType').value;
    const needsWebview = document.getElementById('newSourceNeedsWebview').checked;

    if (!id || !name || !domain) {
        showToast('error', 'خطأ', 'يجب ملء جميع الحقول المطلوبة');
        return;
    }

    if (appConfig.sources[id]) {
        showToast('error', 'خطأ', 'هذا المعرف موجود مسبقاً');
        return;
    }

    appConfig.sources[id] = {
        id: id,
        name: name,
        name_en: nameEn,
        enabled: false,
        priority: priority,
        icon: '',
        domains: [domain],
        current_domain: domain,
        patterns: {
            base: `https://{domain}`,
            series: '/series/{slug}',
            episode: '/episode/{slug}-ep-{ep}'
        },
        headers: {},
        resolver: {
            version: 1,
            type: resolverType,
            needs_webview: needsWebview
        },
        qualities: ['1080p', '720p', '480p'],
        default_quality: '720p',
        supports_download: true,
        supports_watch: true
    };

    closeModal();
    loadSourcesPage();
    updateDashboardStats();
    showToast('success', 'تمت الإضافة', 'تم إضافة المصدر الجديد');

    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

/**
 * حذف مصدر
 */
async function deleteSource(id) {
    if (!confirm(`هل أنت متأكد من حذف ${appConfig.sources[id].name}؟`)) {
        return;
    }

    delete appConfig.sources[id];
    loadSourcesPage();
    updateDashboardStats();
    showToast('success', 'تم الحذف', 'تم حذف المصدر');

    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

// ============================================
// Series Page
// ============================================

function loadSeriesPage() {
    if (!seriesList) return;

    const tbody = document.getElementById('seriesTableBody');
    tbody.innerHTML = '';

    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    const pageData = seriesList.slice(startIndex, endIndex);

    pageData.forEach(series => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${series.id}</td>
            <td>${series.title}</td>
            <td>${series.episodes_count || 0}</td>
            <td>
                <span class="badge badge-success">أكوام</span>
            </td>
            <td class="actions">
                <button class="btn btn-secondary btn-sm" onclick="editSeriesSources('${series.id}')">
                    <i class="fas fa-plus"></i>
                    إضافة مصدر
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    // Pagination
    renderPagination();
}

function renderPagination() {
    if (!seriesList) return;

    const totalPages = Math.ceil(seriesList.length / itemsPerPage);
    const container = document.getElementById('seriesPagination');
    container.innerHTML = '';

    // Previous button
    const prevBtn = document.createElement('button');
    prevBtn.innerHTML = '<i class="fas fa-chevron-right"></i>';
    prevBtn.disabled = currentPage === 1;
    prevBtn.onclick = () => { currentPage--; loadSeriesPage(); };
    container.appendChild(prevBtn);

    // Page numbers
    for (let i = 1; i <= Math.min(totalPages, 5); i++) {
        const pageNum = document.createElement('button');
        pageNum.textContent = i;
        pageNum.className = i === currentPage ? 'active' : '';
        pageNum.onclick = () => { currentPage = i; loadSeriesPage(); };
        container.appendChild(pageNum);
    }

    // Next button
    const nextBtn = document.createElement('button');
    nextBtn.innerHTML = '<i class="fas fa-chevron-left"></i>';
    nextBtn.disabled = currentPage === totalPages;
    nextBtn.onclick = () => { currentPage++; loadSeriesPage(); };
    container.appendChild(nextBtn);
}

function filterSeries() {
    const query = document.getElementById('seriesSearch').value.toLowerCase();
    const tbody = document.getElementById('seriesTableBody');

    const filtered = seriesList.filter(s =>
        s.title.toLowerCase().includes(query) ||
        s.id.toString().includes(query)
    );

    tbody.innerHTML = '';
    filtered.slice(0, itemsPerPage).forEach(series => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${series.id}</td>
            <td>${series.title}</td>
            <td>${series.episodes_count || 0}</td>
            <td><span class="badge badge-success">أكوام</span></td>
            <td class="actions">
                <button class="btn btn-secondary btn-sm" onclick="editSeriesSources('${series.id}')">
                    <i class="fas fa-plus"></i>
                    إضافة مصدر
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

/**
 * تعديل مصادر مسلسل معين
 */
function editSeriesSources(seriesId) {
    const series = seriesList.find(s => s.id === seriesId);
    if (!series) return;

    // البحث عن المسلسل في scraperConfig للحصول على الروابط الحالية
    const scraperSeries = scraperConfig?.series?.find(s => s.id === seriesId || s.id === String(seriesId));
    const existingSources = scraperSeries?.sources || {};

    document.getElementById('modalTitle').textContent = `مصادر: ${series.title}`;
    document.getElementById('modalBody').innerHTML = `
        <p class="text-muted mb-4">أضف روابط المسلسل من المصادر المختلفة:</p>
        ${Object.entries(appConfig.sources).map(([id, source]) => {
            // جلب الرابط الحالي إن وجد
            const currentUrl = existingSources[id]?.url || '';
            return `
            <div class="form-group">
                <label>${source.name}:</label>
                <input type="text" id="source_${id}" class="form-control"
                       value="${currentUrl}"
                       placeholder="رابط المسلسل في ${source.name}">
                ${currentUrl ? '<small class="text-success">رابط موجود</small>' : '<small class="text-muted">لم يتم إضافة رابط بعد</small>'}
            </div>
        `}).join('')}
    `;

    document.getElementById('modalSaveBtn').onclick = () => saveSeriesSources(seriesId);
    openModal();
}

async function saveSeriesSources(seriesId) {
    if (!scraperConfig) {
        showToast('error', 'خطأ', 'لم يتم تحميل بيانات المصادر');
        return;
    }

    // البحث عن المسلسل في scraperConfig
    let scraperSeries = scraperConfig.series?.find(s => s.id === seriesId || s.id === String(seriesId));

    // لو المسلسل غير موجود، نضيفه
    if (!scraperSeries) {
        const seriesInfo = seriesList.find(s => s.id === seriesId);
        scraperSeries = {
            id: String(seriesId),
            name: seriesInfo?.title || '',
            original_name: '',
            enabled: true,
            sources: {}
        };
        if (!scraperConfig.series) scraperConfig.series = [];
        scraperConfig.series.push(scraperSeries);
    }

    // تحديث روابط المصادر
    Object.keys(appConfig.sources).forEach(sourceId => {
        const url = document.getElementById(`source_${sourceId}`).value.trim();
        if (url) {
            scraperSeries.sources[sourceId] = {
                url: url,
                fetch: ['info', 'poster', 'episodes', 'download', 'watch']
            };
        } else if (scraperSeries.sources[sourceId]) {
            // حذف المصدر لو تم إفراغ الرابط
            delete scraperSeries.sources[sourceId];
        }
    });

    closeModal();
    showToast('success', 'تم الحفظ', 'تم تحديث روابط المصادر');

    // حفظ في GitHub
    if (githubAPI.hasToken() && scraperConfigSha) {
        await saveScraperConfigToGitHub();
    }
}

// ============================================
// Episodes Page
// ============================================

function populateSeriesSelect() {
    if (!seriesList) return;

    const select = document.getElementById('episodeSeriesSelect');
    select.innerHTML = '<option value="">-- اختر مسلسل --</option>';

    seriesList.forEach(series => {
        const option = document.createElement('option');
        option.value = series.id;
        option.textContent = series.title;
        select.appendChild(option);
    });
}

async function loadEpisodes() {
    const seriesId = document.getElementById('episodeSeriesSelect').value;
    if (!seriesId) return;

    const container = document.getElementById('episodesList');
    container.innerHTML = '<div class="spinner"></div>';

    try {
        const baseUrl = `https://${githubAPI.owner || 'mboshkash'}.github.io/${githubAPI.repo || 'turkish-series'}`;
        const response = await fetch(`${baseUrl}/data/series/${seriesId}.json`);

        if (!response.ok) {
            throw new Error('لم يتم العثور على بيانات المسلسل');
        }

        const seriesData = await response.json();
        const episodes = seriesData.episodes || [];

        container.innerHTML = '';

        if (episodes.length === 0) {
            container.innerHTML = '<div class="empty-state"><i class="fas fa-film"></i><p>لا توجد حلقات</p></div>';
            return;
        }

        episodes.forEach(ep => {
            const card = document.createElement('div');
            card.className = 'episode-card';
            card.innerHTML = `
                <div class="episode-card-header">
                    <span class="episode-number">الحلقة ${ep.number}</span>
                    <button class="btn btn-secondary btn-sm" onclick="editEpisodeSources('${seriesId}', ${ep.number})">
                        <i class="fas fa-edit"></i>
                    </button>
                </div>
                <div class="episode-sources">
                    <div class="episode-source">
                        <span class="source-name"><i class="fas fa-check-circle"></i> أكوام</span>
                        <span class="badge badge-success">متاح</span>
                    </div>
                </div>
            `;
            container.appendChild(card);
        });
    } catch (error) {
        container.innerHTML = `<div class="empty-state"><i class="fas fa-exclamation-triangle"></i><p>${error.message}</p></div>`;
    }
}

async function editEpisodeSources(seriesId, episodeNumber) {
    // تحميل بيانات الحلقة من GitHub
    const paddedEp = String(episodeNumber).padStart(2, '0');
    const episodeFile = `data/episodes/${seriesId}_${paddedEp}.json`;

    let episodeData = null;
    let episodeSha = null;

    // عرض loading
    document.getElementById('modalTitle').textContent = `الحلقة ${episodeNumber}`;
    document.getElementById('modalBody').innerHTML = '<div class="spinner"></div>';
    openModal();

    try {
        // محاولة جلب بيانات الحلقة
        if (githubAPI.hasToken()) {
            const result = await githubAPI.readJSON(episodeFile);
            if (result) {
                episodeData = result.data;
                episodeSha = result.sha;
            }
        } else {
            // جلب من GitHub Pages
            const baseUrl = `https://${githubAPI.owner || 'mboshkash'}.github.io/${githubAPI.repo || 'turkish-series'}`;
            const response = await fetch(`${baseUrl}/${episodeFile}`);
            if (response.ok) {
                episodeData = await response.json();
            }
        }
    } catch (error) {
        console.log('Episode file not found:', error);
    }

    // استخراج الروابط الحالية من servers.watch
    const existingUrls = {};
    if (episodeData?.servers?.watch) {
        episodeData.servers.watch.forEach(server => {
            if (server.source) {
                existingUrls[server.source] = server.url;
            }
        });
    }

    document.getElementById('modalBody').innerHTML = `
        <p class="text-muted mb-4">أضف روابط الحلقة من المصادر المختلفة:</p>
        ${Object.entries(appConfig.sources).map(([id, source]) => {
            const currentUrl = existingUrls[id] || '';
            return `
            <div class="form-group">
                <label>${source.name}:</label>
                <input type="text" id="ep_source_${id}" class="form-control"
                       value="${currentUrl}"
                       placeholder="رابط الحلقة في ${source.name}">
                ${currentUrl ? '<small class="text-success">رابط موجود</small>' : '<small class="text-muted">لم يتم إضافة رابط بعد</small>'}
            </div>
        `}).join('')}
    `;

    // حفظ SHA للاستخدام عند الحفظ
    window.currentEpisodeSha = episodeSha;
    window.currentEpisodeData = episodeData;

    document.getElementById('modalSaveBtn').onclick = () => saveEpisodeSources(seriesId, episodeNumber);
}

async function saveEpisodeSources(seriesId, episodeNumber) {
    if (!githubAPI.hasToken()) {
        showToast('error', 'خطأ', 'يجب الاتصال بـ GitHub أولاً');
        return;
    }

    const paddedEp = String(episodeNumber).padStart(2, '0');
    const episodeFile = `data/episodes/${seriesId}_${paddedEp}.json`;

    // إنشاء أو تحديث بيانات الحلقة
    let episodeData = window.currentEpisodeData || {
        series_id: String(seriesId),
        series_title: seriesList.find(s => s.id === seriesId)?.title || '',
        episode_number: episodeNumber,
        title: `الحلقة ${episodeNumber}`,
        date_added: new Date().toLocaleDateString('ar-EG'),
        last_updated: new Date().toISOString(),
        servers: { watch: [], download: [] }
    };

    // تحديث السيرفرات
    const newWatchServers = [];
    const newDownloadServers = [];

    Object.entries(appConfig.sources).forEach(([sourceId, source]) => {
        const url = document.getElementById(`ep_source_${sourceId}`).value.trim();
        if (url) {
            newWatchServers.push({
                name: source.name,
                type: sourceId,
                url: url,
                quality: source.default_quality || '720p',
                source: sourceId
            });
            newDownloadServers.push({
                name: source.name,
                url: url,
                quality: source.default_quality || '720p',
                size: '',
                source: sourceId
            });
        }
    });

    episodeData.servers.watch = newWatchServers;
    episodeData.servers.download = newDownloadServers;
    episodeData.last_updated = new Date().toISOString();

    // حفظ في GitHub
    try {
        const result = await githubAPI.writeJSON(
            episodeFile,
            episodeData,
            `🔧 Update episode ${seriesId}_${paddedEp} sources from Dashboard`,
            window.currentEpisodeSha
        );

        closeModal();
        showToast('success', 'تم الحفظ', 'تم تحديث روابط الحلقة');

        // تحديث عرض الحلقات
        loadEpisodes();
    } catch (error) {
        console.error('Save episode error:', error);
        showToast('error', 'خطأ', 'فشل حفظ الحلقة: ' + error.message);
    }
}

// ============================================
// Messages Page
// ============================================

function loadMessagesPage() {
    if (!appConfig) return;

    document.getElementById('maintenanceMsg').value = appConfig.messages.maintenance || '';
    document.getElementById('announcementMsg').value = appConfig.messages.announcement || '';
}

async function saveMessages() {
    if (!appConfig) return;

    appConfig.messages.maintenance = document.getElementById('maintenanceMsg').value.trim() || null;
    appConfig.messages.announcement = document.getElementById('announcementMsg').value.trim() || null;

    showToast('success', 'تم الحفظ', 'تم تحديث الرسائل');

    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

// ============================================
// Settings Page
// ============================================

function loadSettingsPage() {
    if (!appConfig) return;

    document.getElementById('defaultSource').value = appConfig.settings.default_source || 'akwam';
    document.getElementById('minVersionCode').value = appConfig.app.min_version_code || 1;
    document.getElementById('latestVersionCode').value = appConfig.app.latest_version_code || 1;
    document.getElementById('latestVersionName').value = appConfig.app.latest_version_name || '1.0.0';
    document.getElementById('forceUpdate').checked = appConfig.app.force_update || false;
}

async function saveSettings() {
    if (!appConfig) return;

    appConfig.settings.default_source = document.getElementById('defaultSource').value;
    appConfig.app.min_version_code = parseInt(document.getElementById('minVersionCode').value);
    appConfig.app.latest_version_code = parseInt(document.getElementById('latestVersionCode').value);
    appConfig.app.latest_version_name = document.getElementById('latestVersionName').value;
    appConfig.app.force_update = document.getElementById('forceUpdate').checked;

    showToast('success', 'تم الحفظ', 'تم تحديث الإعدادات');

    if (githubAPI.hasToken() && appConfigSha) {
        await saveConfigToGitHub();
    }
}

// ============================================
// Save to GitHub
// ============================================

async function saveConfigToGitHub() {
    try {
        appConfig.last_updated = new Date().toISOString();

        const result = await githubAPI.writeJSON(
            'data/app_config.json',
            appConfig,
            '🔧 Update app_config.json from Dashboard',
            appConfigSha
        );

        appConfigSha = result.content.sha;
        showToast('success', 'تم الحفظ', 'تم حفظ التغييرات في GitHub');
    } catch (error) {
        console.error('Save error:', error);
        showToast('error', 'خطأ', 'فشل الحفظ في GitHub: ' + error.message);
    }
}

/**
 * حفظ config.json (روابط المصادر للمسلسلات)
 */
async function saveScraperConfigToGitHub() {
    try {
        const result = await githubAPI.writeJSON(
            'data/config.json',
            scraperConfig,
            '🔧 Update config.json from Dashboard',
            scraperConfigSha
        );

        scraperConfigSha = result.content.sha;
        showToast('success', 'تم الحفظ', 'تم حفظ روابط المصادر في GitHub');
    } catch (error) {
        console.error('Save scraper config error:', error);
        showToast('error', 'خطأ', 'فشل حفظ روابط المصادر: ' + error.message);
    }
}

// ============================================
// UI Helpers
// ============================================

function openModal() {
    document.getElementById('modal').classList.add('active');
}

function closeModal() {
    document.getElementById('modal').classList.remove('active');
}

function showToast(type, title, message) {
    const container = document.getElementById('toastContainer');

    const icons = {
        success: 'fa-check-circle',
        error: 'fa-times-circle',
        warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle'
    };

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <i class="fas ${icons[type]}"></i>
        <div class="toast-content">
            <h4>${title}</h4>
            <p>${message}</p>
        </div>
    `;

    container.appendChild(toast);

    // إزالة بعد 4 ثواني
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(-20px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function updateConnectionStatus(connected) {
    const badge = document.getElementById('connectionStatus');
    if (connected) {
        badge.className = 'status-badge connected';
        badge.innerHTML = '<i class="fas fa-circle"></i> متصل';
    } else {
        badge.className = 'status-badge disconnected';
        badge.innerHTML = '<i class="fas fa-circle"></i> غير متصل';
    }
}

function formatDate(dateString) {
    if (!dateString) return '-';
    try {
        const date = new Date(dateString);
        return date.toLocaleDateString('ar-EG', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    } catch {
        return dateString;
    }
}
