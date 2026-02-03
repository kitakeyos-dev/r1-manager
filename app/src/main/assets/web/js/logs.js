// App Logs management
let logAutoRefresh = false;
let logRefreshInterval = null;
let allLogs = [];

function toggleAutoRefreshLogs() {
    logAutoRefresh = !logAutoRefresh;
    const btn = document.getElementById('log-auto-refresh-btn');

    if (logAutoRefresh) {
        btn.innerHTML = '<i class="fas fa-sync fa-spin"></i> Auto (ON)';
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-success');
        refreshLogs();
        logRefreshInterval = setInterval(refreshLogs, 3000);
    } else {
        btn.innerHTML = '<i class="fas fa-sync"></i> Auto (OFF)';
        btn.classList.remove('btn-success');
        btn.classList.add('btn-secondary');
        if (logRefreshInterval) {
            clearInterval(logRefreshInterval);
            logRefreshInterval = null;
        }
    }
}

function refreshLogs() {
    fetch('/api/logs/get?count=500')
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success' && data.data) {
                allLogs = data.data.logs;
                filterLogs();

                const now = new Date().toLocaleTimeString();
                document.getElementById('log-updated').innerText = now;
            }
        })
        .catch(err => console.error('Failed to fetch logs', err));
}

function filterLogs() {
    const levelFilter = document.getElementById('log-level-filter').value.toUpperCase();
    const tagFilter = document.getElementById('log-tag-filter').value.toLowerCase();
    const msgFilter = document.getElementById('log-msg-filter').value.toLowerCase();

    const filtered = allLogs.filter(log => {
        if (levelFilter && log.level !== levelFilter) return false;
        if (tagFilter && !log.tag.toLowerCase().includes(tagFilter)) return false;
        if (msgFilter && !log.message.toLowerCase().includes(msgFilter)) return false;
        return true;
    });

    renderLogs(filtered);
    document.getElementById('log-count').innerText = `${filtered.length} / ${allLogs.length} logs`;
}

function renderLogs(logs) {
    const container = document.getElementById('log-container');

    if (logs.length === 0) {
        container.innerHTML = '<div style="color: var(--gray-500); text-align: center; padding: 20px;">No logs match filters</div>';
        return;
    }

    const html = logs.map(log => {
        let levelColor = '#94a3b8'; // default gray
        let levelIcon = '●';

        if (log.level === 'ERROR') {
            levelColor = '#ef4444';
            levelIcon = '✖';
        } else if (log.level === 'WARN') {
            levelColor = '#f59e0b';
            levelIcon = '⚠';
        } else if (log.level === 'INFO') {
            levelColor = '#10b981';
            levelIcon = 'ℹ';
        } else if (log.level === 'DEBUG') {
            levelColor = '#60a5fa';
            levelIcon = '◉';
        } else if (log.level === 'VERBOSE') {
            levelColor = '#a78bfa';
            levelIcon = '○';
        }

        const tagColor = '#fbbf24';
        const timeColor = '#64748b';

        return `<div style="margin-bottom: 2px;">
            <span style="color: ${timeColor};">${log.time}</span>
            <span style="color: ${levelColor}; font-weight: bold;"> [${levelIcon} ${log.level}]</span>
            <span style="color: ${tagColor};"> ${log.tag}:</span>
            <span style="color: #e2e8f0;"> ${escapeHtml(log.message)}</span>
        </div>`;
    }).join('');

    container.innerHTML = html;
    container.scrollTop = container.scrollHeight;
}

function clearAppLogs() {
    if (!confirm('Clear all application logs?')) return;

    fetch('/api/logs/clear', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                allLogs = [];
                filterLogs();
                showStatus('Logs cleared', 'success');
            }
        })
        .catch(err => console.error('Failed to clear logs', err));
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
