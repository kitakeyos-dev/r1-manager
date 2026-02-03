/**
 * Xiaozhi Bot Unified Logic (Operation & Management)
 */

let allBots = [];
let activeBotId = null;
let lastState = null;

// --- BOT DASHBOARD & MANAGEMENT ---

function loadBotDashboard() {
    loadGlobalConfig(); // Load global settings like Voice Bot Toggle

    fetch('/api/xiaozhi/bots')
        .then(res => res.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            allBots = data.profiles || [];
            activeBotId = data.active_id;
            renderBotDashboard();
            updateActiveBotDisplay();
        })
        .catch(err => showStatus('Error loading bots: ' + err.message, 'error'));
}

function loadGlobalConfig() {
    fetch('/api/xiaozhi/config')
        .then(res => res.json())
        .then(data => {
            const config = data.data || data;
            const toggle = document.getElementById('voiceBotToggle');
            if (toggle) {
                // If value is undefined (old config), default to true
                toggle.checked = (config.voice_bot_enabled !== 'false');
            }
        })
        .catch(console.error);
}

function toggleVoiceBot() {
    const toggle = document.getElementById('voiceBotToggle');
    const enabled = toggle.checked;

    postJson('/api/xiaozhi/config', { voice_bot_enabled: String(enabled) }, 'Updating Settings...')
        .then(() => {
            showStatus('Voice Bot ' + (enabled ? 'Enabled' : 'Disabled'), 'success');
        });
}

function renderBotDashboard() {
    const container = document.getElementById('bot-list-container');
    if (!container) return;

    if (allBots.length === 0) {
        container.innerHTML = '<div class="empty">No bots found. Click "Add Bot" to create one.</div>';
        return;
    }

    container.innerHTML = allBots.map(bot => `
        <div class="card bot-card ${bot.id === activeBotId ? 'active' : ''}" onclick="selectBot('${bot.id}')">
            <div class="bot-card-header">
                <i class="fas fa-robot bot-icon"></i>
                <div class="bot-info">
                    <div class="bot-name">${bot.name}</div>
                    <div class="bot-meta">${bot.macType === 'REAL' ? 'Physical MAC' : 'Fake MAC: ' + bot.customMac}</div>
                </div>
                ${bot.id === activeBotId ? '<span class="active-badge">Active</span>' : ''}
            </div>
            <div class="bot-card-actions" onclick="event.stopPropagation()">
                <button class="btn btn-small" onclick="editBot('${bot.id}')" title="Edit">
                    <i class="fas fa-edit"></i>
                </button>
                ${bot.id !== 'default' ? `
                <button class="btn btn-small btn-danger" onclick="confirmDeleteBot('${bot.id}')" title="Delete">
                    <i class="fas fa-trash"></i>
                </button>` : ''}
                ${bot.id !== activeBotId ? `
                <button class="btn btn-small btn-primary" onclick="switchBot('${bot.id}')" title="Switch to this Bot">
                    <i class="fas fa-exchange-alt"></i> Use
                </button>` : ''}
            </div>
        </div>
    `).join('');
}

function updateActiveBotDisplay() {
    const activeProfile = allBots.find(b => b.id === activeBotId);
    const statusCard = document.getElementById('active-bot-status-card');
    const nameSpan = document.getElementById('current-bot-name');

    if (activeProfile) {
        statusCard.style.display = 'block';
        nameSpan.innerText = activeProfile.name;
        updateXiaozhiStatus();
    } else {
        statusCard.style.display = 'none';
    }
}

function selectBot(botId) {
    // Optional highlight logic
}

function switchBot(botId) {
    postJson('/api/xiaozhi/bots/active', { id: botId }, 'Switching Bot...')
        .then(() => {
            activeBotId = botId;
            loadBotDashboard();
            addChatLog('System', `Switched to bot: ${allBots.find(b => b.id === botId).name}`);
        });
}

function confirmDeleteBot(botId) {
    if (botId === 'default') {
        showStatus('Cannot delete the default bot profile.', 'error');
        return;
    }
    const bot = allBots.find(b => b.id === botId);
    showConfirm(`Are you sure you want to delete bot "${bot.name}"?`, () => {
        postJson('/api/xiaozhi/bots/delete', { id: botId }, 'Deleting Bot...')
            .then(() => loadBotDashboard());
    });
}

// --- MODAL LOGIC ---

function showAddBotModal() {
    document.getElementById('xzModalTitle').innerText = 'Add New Bot';
    document.getElementById('xz-bot-id').value = '';
    document.getElementById('xz-bot-name').value = 'My New Bot';
    document.getElementById('xz-ws-url').value = 'wss://api.tenclass.net/xiaozhi/v1/';
    document.getElementById('xz-qta-url').value = 'https://api.tenclass.net/xiaozhi/ota/';
    document.getElementById('xz-custom-mac').value = '';
    document.getElementById('xz-mcp-url').value = '';
    document.getElementById('xz-mcp-token').value = '';

    const newUuid = ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, c =>
        (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
    );
    document.getElementById('xz-uuid').innerText = newUuid;
    document.getElementById('xz-otp-status').innerText = 'Ready';

    toggleMacInput('new');
    document.getElementById('xiaozhiSettingsModal').style.display = 'flex';
}

function editBot(botId) {
    const bot = allBots.find(b => b.id === botId);
    if (!bot) return;

    document.getElementById('xzModalTitle').innerText = 'Edit Bot: ' + bot.name;
    document.getElementById('xz-bot-id').value = bot.id;
    document.getElementById('xz-bot-name').value = bot.name;
    document.getElementById('xz-ws-url').value = bot.wsUrl;
    document.getElementById('xz-qta-url').value = bot.qtaUrl;

    let botUuid = bot.uuid;
    if (!botUuid || botUuid.length < 30) {
        botUuid = ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, c =>
            (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
        );
    }
    document.getElementById('xz-uuid').innerText = botUuid;
    document.getElementById('xz-custom-mac').value = (bot.customMac || '').toLowerCase();
    document.getElementById('xz-mcp-url').value = bot.mcpUrl || '';
    document.getElementById('xz-mcp-token').value = bot.mcpToken || '';
    document.getElementById('xz-otp-status').innerText = 'Ready';

    toggleMacInput(bot.id);
    document.getElementById('xiaozhiSettingsModal').style.display = 'flex';
}

function saveBotConfig() {
    const botId = document.getElementById('xz-bot-id').value;
    const bot = {
        id: botId,
        name: document.getElementById('xz-bot-name').value,
        wsUrl: document.getElementById('xz-ws-url').value,
        qtaUrl: document.getElementById('xz-qta-url').value,
        macType: (botId === 'default' ? 'REAL' : 'FAKE'),
        customMac: document.getElementById('xz-custom-mac').value.toLowerCase().trim(),
        uuid: document.getElementById('xz-uuid').innerText.trim(),
        mcpUrl: document.getElementById('xz-mcp-url').value.trim(),
        mcpToken: document.getElementById('xz-mcp-token').value.trim()
    };

    postJson('/api/xiaozhi/bots', bot, 'Saving Bot...')
        .then(() => {
            closeXiaozhiSettings();
            loadBotDashboard();
        });
}

function closeXiaozhiSettings() {
    document.getElementById('xiaozhiSettingsModal').style.display = 'none';
}

// --- OPERATION LOGIC ---

function startXiaozhi() {
    const statusDiv = document.getElementById('xiaozhi-status');
    if (statusDiv) statusDiv.innerText = 'Status: Sending command...';

    fetch('/api/xiaozhi/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                updateXiaozhiStatus();
            } else {
                if (statusDiv) statusDiv.innerHTML = 'Status: <span style="color:#ef4444;">' + (data.message || 'Error') + '</span>';
            }
        })
        .catch(err => {
            console.error('Start failed', err);
            if (statusDiv) statusDiv.innerHTML = 'Status: <span style="color:#ef4444;">Network Error</span>';
        });
}

function stopXiaozhi() {
    const statusDiv = document.getElementById('xiaozhi-status');
    if (statusDiv) statusDiv.innerText = 'Status: Stopping...';
    postJson('/api/xiaozhi/stop', {}, 'Conversation Stopped');
}

function updateXiaozhiStatus() {
    fetch('/api/xiaozhi/status')
        .then(response => response.json())
        .then(data => {
            const statusBadge = document.getElementById('active-bot-state');
            if (statusBadge) {
                statusBadge.innerText = data.status;
                statusBadge.classList.remove('status-listening', 'status-speaking', 'status-error');

                if (data.status.startsWith('Error')) {
                    statusBadge.classList.add('status-error');
                } else if (data.status === 'Listening') {
                    statusBadge.classList.add('status-listening');
                } else if (data.status === 'Speaking') {
                    statusBadge.classList.add('status-speaking');
                }
            }

            const oldStatusDiv = document.getElementById('xiaozhi-status');
            if (oldStatusDiv) oldStatusDiv.innerText = 'Status: ' + data.status;

            if (lastState !== data.status) {
                addChatLog('R1', `State: ${data.status}`);
                lastState = data.status;
            }
        })
        .catch(err => console.error('Status check failed', err));
}

// Poll status periodically if active tab is xiaozhi
setInterval(() => {
    const tab = document.getElementById('xiaozhi-tab');
    if (tab && tab.classList.contains('active')) {
        updateXiaozhiStatus();
    }
}, 5000);

// --- UTILITIES ---

function toggleMacInput(botId) {
    if (!botId) {
        botId = document.getElementById('xz-bot-id').value;
    }
    const isFake = (botId !== 'default');
    document.getElementById('xz-custom-mac-group').style.display = (isFake ? 'block' : 'none');
}

function checkXiaozhiOtp() {
    const statusEl = document.getElementById('xz-otp-status');
    statusEl.innerText = 'Checking...';

    const qtaUrl = document.getElementById('xz-qta-url').value;
    const botId = document.getElementById('xz-bot-id').value;
    const macType = (botId === 'default' ? 'REAL' : 'FAKE');
    const customMac = document.getElementById('xz-custom-mac').value.trim();

    if (macType === 'FAKE' && !customMac) {
        statusEl.innerHTML = '<span style="color:#ef4444;">Error: Please enter or generate a MAC address</span>';
        return;
    }

    const uuidText = document.getElementById('xz-uuid').innerText.trim();
    const uuid = (uuidText && uuidText.length > 30) ? uuidText : null;
    const deviceId = (macType === 'FAKE' ? customMac.toLowerCase() : null);

    const payload = {
        qta_url: qtaUrl,
        device_id: deviceId,
        uuid: uuid
    };

    fetch('/api/xiaozhi/check-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(response => response.json())
        .then(data => {
            if (data.activation) {
                statusEl.innerHTML =
                    '<span style="color:#ffcc00; font-size:1.2em;">Code: ' + data.activation.code + '</span><br>' +
                    '<small>' + data.activation.message.replace(/\n/g, '<br>') + '</small>';
            } else if (data.mqtt || data.mqttConfig) {
                statusEl.innerHTML = '<span style="color:#00C851;">Activated! MQTT Configured.</span>';
                loadBotDashboard();
            } else if (data.status === 'error' || (data.code && data.code !== 0 && data.code !== 200)) {
                statusEl.innerHTML = '<span style="color:red;">Error: ' + (data.message || 'Unknown Error') + '</span>';
            } else {
                statusEl.innerText = 'Response received (Unknown state). Check Console.';
                showStatus('Unknown response: ' + JSON.stringify(data), 'warning');
            }
        })
        .catch(err => {
            statusEl.innerText = 'Error: ' + err.message;
        });
}

function addChatLog(sender, message) {
    const log = document.getElementById('chat-log');
    if (!log) return;

    const entry = document.createElement('div');
    entry.style.marginBottom = '4px';
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    let color = '#94a3b8';
    if (sender === 'System') color = '#fbbf24';
    if (sender === 'R1') color = '#60a5fa';
    if (sender === 'Bot') color = '#10b981';

    entry.innerHTML = `<span style="color:#64748b; font-size:11px;">[${time}]</span> <span style="color:${color}; font-weight:bold;">${sender}:</span> ${message}`;

    if (log.querySelector('.text-muted')) {
        log.innerHTML = '';
    }

    log.appendChild(entry);
    log.scrollTop = log.scrollHeight;
}

function clearChatLog() {
    const log = document.getElementById('chat-log');
    if (log) log.innerHTML = '<div class="text-muted">No activity yet...</div>';
}

function generateRandomMac() {
    const hex = '0123456789ABCDEF';
    let mac = '';
    for (let i = 0; i < 6; i++) {
        let part = '';
        if (i === 0) {
            const firstDigit = hex[Math.floor(Math.random() * 16)];
            const secondDigit = hex[Math.floor(Math.random() * 8) * 2];
            part = firstDigit + secondDigit;
        } else {
            part = hex[Math.floor(Math.random() * 16)] + hex[Math.floor(Math.random() * 16)];
        }
        mac += part + (i === 5 ? '' : ':');
    }
    document.getElementById('xz-custom-mac').value = mac.toLowerCase();
}

function regenerateBotUuid() {
    const newUuid = ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, c =>
        (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
    );
    document.getElementById('xz-uuid').innerText = newUuid;
}
