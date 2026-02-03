/**
 * Utils and Helpers
 */

function showStatus(message, type, iconClass) {
    const status = document.getElementById('uploadStatus');
    if (!status) {
        console.warn('Status element missing:', message);
        return;
    }
    // Default icons if not provided
    if (!iconClass) {
        if (type === 'success') iconClass = 'fa-check-circle';
        else if (type === 'error') iconClass = 'fa-times-circle';
        else if (type === 'warning') iconClass = 'fa-exclamation-triangle';
        else iconClass = 'fa-info-circle';
    }

    status.innerHTML = `<i class="fas ${iconClass}"></i> ${message}`;
    status.className = 'status show ' + type;

    if (!iconClass.includes('spin')) {
        setTimeout(() => status.classList.remove('show'), 4000);
    }
}

function showStatusInElement(el, msg, type) {
    if (!el) return;
    const icons = { success: 'fa-check-circle', error: 'fa-times-circle', warning: 'fa-exclamation-triangle', info: 'fa-info-circle' };
    el.innerHTML = '<i class="fas ' + (icons[type] || 'fa-info-circle') + '"></i> ' + msg;
    el.className = 'status status-' + type;
}

function closeModal() {
    const modal = document.getElementById('confirmModal');
    if (modal) modal.classList.remove('show');
}

function showConfirm(title, message, onConfirm) {
    // Handle overload: showConfirm(message, onConfirm)
    if (typeof message === 'function') {
        onConfirm = message;
        message = title;
        title = "Xác nhận";
    }

    const modal = document.getElementById('confirmModal');
    const titleEl = document.getElementById('modalTitle');
    const msgEl = document.getElementById('modalMessage');
    const btn = document.getElementById('confirmBtn');

    if (modal && msgEl && btn) {
        if (titleEl) titleEl.innerText = title;
        msgEl.innerText = message;
        modal.classList.add('show');

        // nuclear listener clear
        const newBtn = btn.cloneNode(true);
        btn.parentNode.replaceChild(newBtn, btn);

        newBtn.onclick = () => {
            modal.classList.remove('show');
            if (onConfirm) onConfirm();
        };
    } else {
        if (confirm(message)) {
            if (onConfirm) onConfirm();
        }
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatSize(bytes) {
    return formatBytes(bytes);
}

function getProgressColor(percent) {
    if (percent < 60) return 'green';
    if (percent < 85) return 'yellow';
    return 'red';
}

function renderSignal(level) {
    const bars = [4, 8, 12, 16];
    return `<div class="network-signal">
        ${bars.map((h, i) => `<div class="signal-bar ${i < level ? 'active' : ''}" style="height: ${h}px;"></div>`).join('')}
    </div>`;
}

function postJson(url, data, successMsg = "Success") {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'ok' || data.status === 'success' || data.status === 'updated') {
                showStatus(successMsg, 'success');
                return data;
            } else {
                showStatus('Error: ' + (data.message || 'Unknown error'), 'error');
                throw new Error(data.message || 'Unknown error');
            }
        })
        .catch(err => {
            showStatus('Error: ' + err, 'error');
            throw err;
        });
}

function getFileIcon(name) {
    const ext = name.split('.').pop().toLowerCase();
    const icons = {
        'apk': 'fa-android', 'zip': 'fa-file-archive', 'rar': 'fa-file-archive', '7z': 'fa-file-archive',
        'jpg': 'fa-file-image', 'jpeg': 'fa-file-image', 'png': 'fa-file-image', 'gif': 'fa-file-image',
        'mp3': 'fa-file-audio', 'wav': 'fa-file-audio', 'ogg': 'fa-file-audio',
        'mp4': 'fa-file-video', 'avi': 'fa-file-video', 'mkv': 'fa-file-video',
        'pdf': 'fa-file-pdf', 'doc': 'fa-file-word', 'docx': 'fa-file-word',
        'xls': 'fa-file-excel', 'xlsx': 'fa-file-excel',
        'txt': 'fa-file-alt', 'log': 'fa-file-alt', 'json': 'fa-file-code',
        'js': 'fa-file-code', 'html': 'fa-file-code', 'css': 'fa-file-code', 'java': 'fa-file-code'
    };
    return icons[ext] || 'fa-file';
}
