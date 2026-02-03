/**
 * Network Management (WiFi, AP, Bluetooth)
 */

/* WiFi */
function loadWifiInfo() {
    const container = document.getElementById('wifiInfo');
    if (!container) return;

    fetch('/api/wifi')
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            const toggle = document.getElementById('wifiToggle');
            if (toggle) toggle.checked = data.enabled;

            if (!data.enabled) {
                container.innerHTML = '<div class="empty"><i class="fas fa-wifi"></i>WiFi đang tắt</div>';
                return;
            }

            container.innerHTML = `
                <div class="network-item" style="cursor: default;">
                    <div class="network-icon"><i class="fas fa-wifi"></i></div>
                    <div class="network-info">
                        <div class="network-name">${data.ssid || 'Không kết nối'}</div>
                        <div class="network-detail">${data.ipAddress || ''}</div>
                    </div>
                    ${data.ssid ? renderSignal(data.signalLevel || 0) : ''}
                </div>
            `;
        });
}

function scanWifi() {
    const container = document.getElementById('wifiNetworks');
    container.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Đang quét...</div>';

    fetch('/api/wifi/scan')
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            if (!data.networks || data.networks.length === 0) {
                container.innerHTML = '<div class="empty"><i class="fas fa-wifi"></i>Không tìm thấy mạng</div>';
                return;
            }

            // Remove duplicates and sort by signal
            const unique = {};
            data.networks.forEach(n => {
                if (!unique[n.ssid] || unique[n.ssid].level < n.level) {
                    unique[n.ssid] = n;
                }
            });

            const networks = Object.values(unique).sort((a, b) => b.level - a.level);

            container.innerHTML = networks.filter(n => n.ssid).map(n => `
                <div class="network-item" onclick="promptConnectWifi('${escapeHtml(n.ssid)}', ${n.secured})">
                    <div class="network-icon">
                        <i class="fas fa-wifi"></i>
                        ${n.secured ? '<i class="fas fa-lock" style="font-size: 10px; position: absolute; margin-left: -8px; margin-top: 10px;"></i>' : ''}
                    </div>
                    <div class="network-info">
                        <div class="network-name">${escapeHtml(n.ssid)}</div>
                        <div class="network-detail">${n.secured ? 'Bảo mật' : 'Mở'} • ${n.frequency}MHz</div>
                    </div>
                    ${renderSignal(n.signalLevel)}
                </div>
            `).join('');
        });
}

var pendingWifiSsid = null;

function promptConnectWifi(ssid, secured) {
    pendingWifiSsid = ssid;
    document.getElementById('wifiModalTitle').textContent = 'Kết nối: ' + ssid;
    document.getElementById('wifiPassword').value = '';

    if (secured) {
        document.getElementById('wifiModal').classList.add('show');
    } else {
        confirmConnectWifi();
    }
}

function confirmConnectWifi() {
    closeWifiModal();
    const password = document.getElementById('wifiPassword').value;
    const useStaticIp = document.getElementById('wifiStaticToggle').checked;

    const payload = {
        ssid: pendingWifiSsid,
        password: password
    };

    if (useStaticIp) {
        payload.useStaticIp = true;
        payload.ipAddress = document.getElementById('wifiIp').value.trim();
        payload.gateway = document.getElementById('wifiGateway').value.trim();
        payload.dns1 = document.getElementById('wifiDns1').value.trim();
        payload.dns2 = document.getElementById('wifiDns2').value.trim();

        if (!payload.ipAddress || !payload.gateway) {
            showStatus('Vui lòng nhập IP và Gateway', 'error');
            return;
        }
    }

    fetch('/api/wifi/connect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(r => r.json())
        .then(data => {
            showStatus(data.message, data.status, data.status === 'success' ? 'fa-check-circle' : 'fa-times-circle');
            setTimeout(loadWifiInfo, 3000);
        });
}

function toggleWifiStaticOptions() {
    const checked = document.getElementById('wifiStaticToggle').checked;
    document.getElementById('wifiStaticOptions').style.display = checked ? 'block' : 'none';
}

function closeWifiModal() {
    document.getElementById('wifiModal').classList.remove('show');
}

function toggleWifi() {
    const enabled = document.getElementById('wifiToggle').checked;
    fetch('/api/wifi/enable', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
    }).then(() => setTimeout(loadWifiInfo, 1000));
}

/* WiFi AP (Hotspot) */
function loadApInfo() {
    const container = document.getElementById('apInfo');
    if (!container) return;

    fetch('/api/wifi/ap')
        .then(r => r.json())
        .then(data => {
            document.getElementById('apToggle').checked = data.enabled;
            document.getElementById('apSsid').value = data.ssid || 'R1_Hotspot';

            if (!data.enabled) {
                container.innerHTML = '<div class="empty"><i class="fas fa-broadcast-tower"></i>Hotspot đang tắt</div>';
                return;
            }

            container.innerHTML = `
                <div class="info-grid">
                    <div class="info-item">
                        <div class="label">SSID</div>
                        <div class="value small">${data.ssid || 'N/A'}</div>
                    </div>
                    <div class="info-item">
                        <div class="label">Clients</div>
                        <div class="value">${data.clientCount || 0}</div>
                    </div>
                </div>
                ${data.clients && data.clients.length > 0 ? `
                    <div style="margin-top: 12px;">
                        ${data.clients.map(c => `
                            <div class="network-item" style="cursor: default;">
                                <div class="network-icon"><i class="fas fa-mobile-alt"></i></div>
                                <div class="network-info">
                                    <div class="network-name">${c.ip}</div>
                                    <div class="network-detail">${c.mac}</div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            `;
        });
}

function toggleAp() {
    const enabled = document.getElementById('apToggle').checked;
    const ssid = document.getElementById('apSsid').value || 'R1_Hotspot';
    const password = document.getElementById('apPassword').value;

    fetch('/api/wifi/ap', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled, ssid, password })
    }).then(() => setTimeout(loadApInfo, 2000));
}

function saveApConfig() {
    const enabled = document.getElementById('apToggle').checked;
    toggleAp();
    showStatus('Đã lưu cấu hình', 'success', 'fa-check-circle');
}

/* Bluetooth */
function loadBluetoothInfo() {
    const container = document.getElementById('btInfo');
    const pairedContainer = document.getElementById('btPaired');
    if (!container) return;

    fetch('/api/bluetooth')
        .then(r => r.json())
        .then(data => {
            if (!data.supported) {
                container.innerHTML = '<div class="empty"><i class="fab fa-bluetooth"></i>Bluetooth không hỗ trợ</div>';
                return;
            }

            document.getElementById('btToggle').checked = data.enabled;

            container.innerHTML = `
                <div class="info-grid">
                    <div class="info-item">
                        <div class="label">Tên</div>
                        <div class="value small">${data.name || 'N/A'}</div>
                    </div>
                    <div class="info-item">
                        <div class="label">Trạng thái</div>
                        <div class="value small">${data.enabled ? 'Bật' : 'Tắt'}</div>
                    </div>
                </div>
            `;

            if (data.pairedDevices && data.pairedDevices.length > 0) {
                pairedContainer.innerHTML = data.pairedDevices.map(d => `
                    <div class="network-item" style="cursor: default;">
                        <div class="network-icon"><fab class="fab fa-bluetooth"></fab></div>
                        <div class="network-info">
                            <div class="network-name">${d.name || 'Unknown'}</div>
                            <div class="network-detail">${d.address}</div>
                        </div>
                    </div>
                `).join('');
            } else {
                pairedContainer.innerHTML = '<div class="empty"><i class="fab fa-bluetooth"></i>Không có thiết bị</div>';
            }
        });
}

function toggleBluetooth() {
    const enabled = document.getElementById('btToggle').checked;
    fetch('/api/bluetooth/enable', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
    }).then(() => setTimeout(loadBluetoothInfo, 1000));
}

/* Wake on LAN */
/* Wake on LAN */
function sendWol(mac) {
    const statusDiv = document.getElementById('wolStatus');
    const targetMac = mac || document.getElementById('wolMac').value.trim();

    if (!targetMac) {
        statusDiv.innerHTML = '<span class="error">Vui lòng nhập hoặc chọn MAC Address</span>';
        return;
    }

    // Validate format
    const macRegex = /^([0-9A-Fa-f]{2}[:-])*([0-9A-Fa-f]{2})$/;
    if (!macRegex.test(targetMac)) {
        statusDiv.innerHTML = '<span class="error">Định dạng MAC không hợp lệ</span>';
        return;
    }

    statusDiv.innerHTML = `<span class="info"><i class="fas fa-spinner fa-spin"></i> Đang gửi tín hiệu tới ${targetMac}...</span>`;

    fetch('/api/wol/wake', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ mac: targetMac })
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success' || data.code === 200) {
                statusDiv.innerHTML = `<span class="success"><i class="fas fa-check"></i> ${data.data || data.message}</span>`;
            } else {
                statusDiv.innerHTML = `<span class="error"><i class="fas fa-times"></i> ${data.message}</span>`;
            }
        })
        .catch(error => {
            statusDiv.innerHTML = `<span class="error"><i class="fas fa-exclamation-triangle"></i> Lỗi kết nối</span>`;
        });
}

function addWolDevice() {
    const nameInput = document.getElementById('wolName');
    const macInput = document.getElementById('wolMac');
    const name = nameInput.value.trim();
    const mac = macInput.value.trim();
    const statusDiv = document.getElementById('wolStatus');

    if (!mac) {
        statusDiv.innerHTML = '<span class="error">Vui lòng nhập MAC Address để thêm</span>';
        return;
    }

    const macRegex = /^([0-9A-Fa-f]{2}[:-])*([0-9A-Fa-f]{2})$/;
    if (!macRegex.test(mac)) {
        statusDiv.innerHTML = '<span class="error">Định dạng MAC không hợp lệ</span>';
        return;
    }

    statusDiv.innerHTML = '<span class="info"><i class="fas fa-spinner fa-spin"></i> Đang lưu...</span>';

    fetch('/api/wol/save', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name: name || 'Thiết bị không tên', mac: mac })
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success' || data.code === 200) {
                statusDiv.innerHTML = '<span class="success"><i class="fas fa-check"></i> Đã lưu thiết bị!</span>';
                nameInput.value = '';
                macInput.value = '';
                renderWolDevices();
            } else {
                statusDiv.innerHTML = `<span class="error"><i class="fas fa-times"></i> ${data.message}</span>`;
            }
        })
        .catch(error => {
            statusDiv.innerHTML = '<span class="error"><i class="fas fa-exclamation-triangle"></i> Lỗi kết nối</span>';
        });
}


function deleteWolDevice(mac) {
    if (!confirm('Xóa thiết bị này?')) return;
    fetch('/api/wol/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mac: mac })
    }).then(() => renderWolDevices());
}

function renderWolDevices() {
    const listDiv = document.getElementById('wolHistory');
    if (!listDiv) return;

    fetch('/api/wol/list')
        .then(r => r.json())
        .then(devices => {
            // Handle ApiResponse format (devices might be in data field)
            const list = Array.isArray(devices) ? devices : (devices.data || []);

            if (list.length === 0) {
                listDiv.innerHTML = '<div class="empty" style="font-size: 12px; opacity: 0.6;">Chưa có thiết bị nào được lưu</div>';
                return;
            }

            listDiv.innerHTML = list.map(d => `
                <div class="network-item" style="padding: 8px; margin: 0; background: var(--gray-800); border-radius: 6px; align-items: center;">
                    <div class="network-icon" style="font-size: 14px;"><i class="fas fa-desktop"></i></div>
                    <div class="network-info" onclick="document.getElementById('wolMac').value='${d.mac}'; document.getElementById('wolName').value='${d.name}'" style="cursor: pointer;">
                        <div class="network-name" style="font-size: 13px;">${escapeHtml(d.name)}</div>
                        <div class="network-detail" style="font-size: 11px;">${d.mac}</div>
                    </div>
                    <div style="display: flex; gap: 8px;">
                        <button class="btn btn-primary btn-small" onclick="sendWol('${d.mac}')" title="Bật">
                            <i class="fas fa-bolt"></i> Wake
                        </button>
                        <button class="btn btn-secondary btn-small" onclick="deleteWolDevice('${d.mac}')" title="Xóa">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </div>
            `).join('');
        });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Render on page load
document.addEventListener('DOMContentLoaded', renderWolDevices);
