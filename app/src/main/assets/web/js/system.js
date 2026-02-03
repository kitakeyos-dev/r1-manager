/**
 * System Info and Management
 */

function loadSystemInfo() {
    const container = document.getElementById('systemInfo');
    if (!container) return;
    container.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>';

    fetch('/api/system/info')
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            const ramPercent = data.ram?.usedPercent || 0;
            const storagePercent = data.storage?.usedPercent || 0;
            const cpuUsage = data.cpu?.usage || 0;

            container.innerHTML = `
                <div class="info-grid">
                    <div class="info-item">
                        <div class="label">RAM</div>
                        <div class="value">${ramPercent}%</div>
                        <div class="sub">${formatBytes(data.ram?.used || 0)} / ${formatBytes(data.ram?.total || 0)}</div>
                        <div class="progress-bar">
                            <div class="progress-fill ${getProgressColor(ramPercent)}" style="width: ${ramPercent}%"></div>
                        </div>
                    </div>
                    <div class="info-item">
                        <div class="label">Storage</div>
                        <div class="value">${storagePercent}%</div>
                        <div class="sub">${formatBytes(data.storage?.used || 0)} / ${formatBytes(data.storage?.total || 0)}</div>
                        <div class="progress-bar">
                            <div class="progress-fill ${getProgressColor(storagePercent)}" style="width: ${storagePercent}%"></div>
                        </div>
                    </div>
                    <div class="info-item">
                        <div class="label">CPU</div>
                        <div class="value">${cpuUsage}%</div>
                        <div class="sub">${data.cpu?.cores || 0} cores</div>
                        <div class="progress-bar">
                            <div class="progress-fill ${getProgressColor(cpuUsage)}" style="width: ${cpuUsage}%"></div>
                        </div>
                    </div>
                    <div class="info-item">
                        <div class="label">Device</div>
                        <div class="value small">${data.device?.model || 'N/A'}</div>
                        <div class="sub">Android ${data.device?.androidVersion || ''} (API ${data.device?.sdkLevel || ''})</div>
                    </div>
                </div>
            `;
        })
        .catch(err => {
            container.innerHTML = '<div class="empty"><i class="fas fa-exclamation-circle"></i>Lỗi tải thông tin</div>';
        });
}

function loadVolume() {
    const container = document.getElementById('volumeControl');
    if (!container) return;

    fetch('/api/volume')
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            container.innerHTML = `
                <div class="volume-item">
                    <div class="volume-icon"><i class="fas fa-music"></i></div>
                    <div class="volume-control">
                        <div class="volume-label">Nhạc</div>
                        <input type="range" class="volume-slider" min="0" max="100" value="${data.music?.percent || 0}" onchange="setSystemVolume('music', this.value)">
                    </div>
                    <div class="volume-value">${data.music?.percent || 0}%</div>
                </div>
                <div class="volume-item">
                    <div class="volume-icon"><i class="fas fa-bell"></i></div>
                    <div class="volume-control">
                        <div class="volume-label">Chuông</div>
                        <input type="range" class="volume-slider" min="0" max="100" value="${data.ring?.percent || 0}" onchange="setSystemVolume('ring', this.value)">
                    </div>
                    <div class="volume-value">${data.ring?.percent || 0}%</div>
                </div>
            `;

            // Update value display on input
            container.querySelectorAll('.volume-slider').forEach(slider => {
                slider.addEventListener('input', function () {
                    this.parentElement.nextElementSibling.textContent = this.value + '%';
                });
            });
        });
}

function setSystemVolume(type, value) {
    // Overloaded: (type, value) for System/Ring/Alarm, or (val) for hardware music volume via slider
    if (arguments.length === 1) {
        // Harware volume case - DEPRECATED or moved? Assuming system tab uses 2 args
        // If meant to use hardware volume, call specific function
        postJson('/api/hardware/volume', { level: parseInt(type) }, 'Volume set to ' + type);
        return;
    }

    fetch('/api/volume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, volume: parseInt(value) })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success' && data.data) {
                // Update slider to actual value
                const info = data.data;
                const container = document.getElementById('volumeControl');
                if (!container) return;

                // Helper
                const updateSlider = (t, percent) => {
                    const slider = container.querySelector(`input[onchange="setSystemVolume('${t}', this.value)"]`);
                    if (slider) {
                        slider.value = percent;
                        if (slider.parentElement && slider.parentElement.nextElementSibling) {
                            slider.parentElement.nextElementSibling.textContent = percent + '%';
                        }
                    }
                };

                if (info.music) updateSlider('music', info.music.percent);
                if (info.ring) updateSlider('ring', info.ring.percent);
            }
        });
}

function applySystemLanguage() {
    const lang = document.getElementById('language-select').value;
    setSystemLanguage(lang);
}

function setSystemLanguage(lang) {
    if (!confirm('This will change system language to ' + lang + ' and might require a reboot. Continue?')) return;
    fetch('/api/hardware/language', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lang: lang })
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'ok') {
                showStatus('Language set to ' + lang + '. Please reboot device.', 'success');
            } else {
                showStatus('Error setting language', 'error');
            }
        })
        .catch(err => showStatus('Error: ' + err, 'error'));
}

function rebootDevice() {
    if (!confirm('Bạn có chắc chắn muốn khởi động lại thiết bị?')) return;

    showStatus('Đang gửi lệnh khởi động lại...', 'warning', 'fa-power-off');
    fetch('/api/hardware/reboot', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            showStatus('Đã gửi lệnh khởi động lại. Vui lòng kết nối lại sau.', 'success', 'fa-check');
        });
}

function rebootService(service) {
    postJson('/api/hardware/service/reboot', { service: service }, 'Service ' + service + ' restarting...');
}

function executeShell() {
    const command = document.getElementById('shellCommand').value.trim();
    if (!command) return;

    const output = document.getElementById('shellOutput');
    output.textContent = 'Đang thực thi...';

    fetch('/api/system/shell', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command })
    })
        .then(r => r.json())
        .then(data => {
            let result = '';
            if (data.stdout) result += data.stdout;
            if (data.stderr) result += '\n[stderr]\n' + data.stderr;
            result += `\n[exit code: ${data.exitCode}]`;
            output.textContent = result.trim();
        })
        .catch(err => {
            output.textContent = 'Error: ' + err.message;
        });
}

function loadLogcat() {
    const lines = document.getElementById('logLines').value;
    const filter = document.getElementById('logFilter').value;
    const output = document.getElementById('logcatOutput');

    output.textContent = 'Đang tải...';

    let url = `/api/system/logcat?lines=${lines}`;
    if (filter) url += `&filter=${encodeURIComponent(filter)}`;

    fetch(url)
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            output.textContent = data.logs || 'Không có log';
        })
        .catch(err => {
            output.textContent = 'Error: ' + err.message;
        });
}

function clearLogcat() {
    fetch('/api/system/logcat/clear', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            document.getElementById('logcatOutput').textContent = 'Logcat đã được xóa';
        });
}
