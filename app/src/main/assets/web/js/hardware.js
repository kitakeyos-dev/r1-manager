/**
 * Hardware Control (Light, Voice, LED sync)
 */

/* Hardware Status Polling */
function updateHardwareStatus() {
    // Only update if hardware tab is active
    const viewHardware = document.getElementById('view-hardware');
    if (!viewHardware || viewHardware.style.display === 'none') {
        const viewLights = document.getElementById('view-lights');
        // Also update if lights tab is active (shared data?)
        // For now, let's keep original logic: only if hardware tab active?
        // Actually original code checked id 'view-hardware'
        if (!viewLights || viewLights.style.display === 'none') return;
    }

    fetch('/api/hardware/info')
        .then(r => r.json())
        .then(data => {
            if (!data || data.status === 'waiting') return;

            // Update Volume
            if (data.vol !== undefined) {
                const volRange = document.querySelector('input[onchange="setVolume(this.value)"]');
                if (volRange && document.activeElement !== volRange) volRange.value = data.vol;
            }

            // Update Toggle Switches - Connectivity
            const toggleDlna = document.getElementById('toggle-dlna');
            if (toggleDlna && data.dlna_open !== undefined) {
                toggleDlna.checked = data.dlna_open;
            }

            const toggleAirplay = document.getElementById('toggle-airplay');
            if (toggleAirplay && data.airplay_open !== undefined) {
                toggleAirplay.checked = data.airplay_open;
            }

            // Bluetooth: device_state 3 means BT is on (based on r1_control observations)
            const toggleBluetooth = document.getElementById('toggle-bluetooth');
            if (toggleBluetooth && data.device_state !== undefined) {
                toggleBluetooth.checked = (data.device_state === 3);
            }

            // Voice - Device Name
            if (data.device_name) {
                const nameInput = document.getElementById('device-name-input');
                if (nameInput && document.activeElement !== nameInput) nameInput.placeholder = data.device_name;
            }

            // Light toggle - music_light_enable
            const toggleLight = document.getElementById('toggle-light');
            if (toggleLight && data.music_light_enable !== undefined) {
                toggleLight.checked = data.music_light_enable;
            }
        })
        .catch(e => console.error(e));
}

// Poll every 5 seconds
setInterval(updateHardwareStatus, 5000);

/* Status LED & Light Ring */
function controlLight(state) {
    showStatus('Đang gửi lệnh...', 'info', 'fa-spinner fa-spin');

    fetch('/api/hardware/light', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ state: state })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus(data.message, 'success', 'fa-check-circle');
            } else {
                showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
            }
        })
        .catch(err => {
            showStatus('Lỗi kết nối: ' + err.message, 'error', 'fa-times-circle');
        });
}

function setLightEffect(mode) {
    postJson('/api/hardware/light/effect', { mode: mode }, 'Light effect changed');
}

function setLightBrightness(val) {
    postJson('/api/hardware/light/brightness', { value: parseInt(val) }, 'Brightness set to ' + val);
}

function setLightSpeed(val) {
    const value = parseInt(val);
    document.getElementById('speed-value').innerText = value + '%';
    postJson('/api/hardware/light/speed', { value: value }, 'Speed set to ' + value);
}

function requestMusic() {
    const song = document.getElementById('music-request-input').value;
    if (!song) return alert('Please enter song name');
    postJson('/api/hardware/music/request', { song: song }, 'Requesting music...');
}

function requestRadio() {
    const name = document.getElementById('radio-request-input').value;
    if (!name) return alert('Please enter radio station name');
    postJson('/api/hardware/music/radio', { name: name }, 'Requesting radio...');
}

function sendTTS() {
    const text = document.getElementById('tts-input').value;
    if (!text) return alert('Please enter text');
    postJson('/api/hardware/tts', { text: text }, 'TTS sent');
}

// Connectivity setters
function setBluetooth(enable) {
    postJson('/api/hardware/bluetooth/set', { enable: enable }, 'Bluetooth ' + (enable ? 'Enabled' : 'Disabled'));
}

function setDlna(enable) {
    postJson('/api/hardware/dlna/set', { enable: enable }, 'DLNA ' + (enable ? 'Enabled' : 'Disabled'));
}

function setAirPlay(enable) {
    postJson('/api/hardware/airplay/set', { enable: enable }, 'AirPlay ' + (enable ? 'Enabled' : 'Disabled'));
}

// Voice setters
function setDeviceName() {
    const name = document.getElementById('device-name-input').value;
    if (!name) return alert('Please enter name');
    postJson('/api/hardware/voice/name', { name: name }, 'Device name set (reboot may be required)');
}

function setWakeWord() {
    const word = document.getElementById('wake-word-input').value;
    if (!word) return alert('Please enter wake word');
    postJson('/api/hardware/voice/wake', { word: word }, 'Wake word set');
}

function setXiaoAi(enable) {
    postJson('/api/hardware/voice/xiaoai', { enable: enable }, 'Xiao Ai ' + (enable ? 'Enabled' : 'Disabled'));
}

/* Internal LED Control */
function setInternalLed(color) {
    fetch('/api/hardware/led/internal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ color: color })
    }).then(r => r.json()).then(data => {
        if (data.status !== 'success') showStatus('Lỗi: ' + (data.message || 'Unknown'), 'error');
    }).catch(err => showStatus('Lỗi kết nối LED', 'error'));
}

var ringLedTimeout = null;
function setRingLed(brightness) {
    const briInt = parseInt(brightness);
    const valObj = document.getElementById('ring-val');
    if (valObj) valObj.textContent = briInt;

    // Update hex input if exists
    const briHex = document.getElementById('ring-custom-brightness-hex');
    if (briHex) {
        briHex.value = briInt.toString(16).padStart(2, '0').toUpperCase();
    }

    clearTimeout(ringLedTimeout);
    ringLedTimeout = setTimeout(() => {
        fetch('/api/hardware/led/ring', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ brightness: briInt })
        }).then(r => r.json()).then(data => {
            if (data.status !== 'success') showStatus('Lỗi: ' + (data.message || 'Unknown'), 'error');
        }).catch(err => showStatus('Lỗi kết nối Ring LED', 'error'));
    }, 150);
}

// Unified Init LED on load
document.addEventListener('DOMContentLoaded', () => {
    initLedControl();
    initLedBitGrid();
    initRingLedBitGrid();
    loadMusicLedSyncStatus();
});

function initLedControl() {
    console.log('Initializing LED controls...');
    const rgbPresets = document.getElementById('rgb-presets');
    if (rgbPresets) {
        rgbPresets.querySelectorAll('.color-preset').forEach(preset => {
            preset.addEventListener('click', () => {
                const color = preset.dataset.color;
                rgbPresets.querySelectorAll('.color-preset').forEach(p => p.classList.remove('active'));
                preset.classList.add('active');

                const picker = document.getElementById('internal-rgb-picker');
                if (picker) picker.value = (color === "#000000" || !color) ? "#000000" : color;

                setInternalLed(color);
            });
        });
    }

    const internalPicker = document.getElementById('internal-rgb-picker');
    if (internalPicker) {
        internalPicker.addEventListener('change', (e) => {
            const color = e.target.value;
            if (rgbPresets) {
                rgbPresets.querySelectorAll('.color-preset').forEach(p => p.classList.remove('active'));
            }
            setInternalLed(color);
        });
    }

    const ringSlider = document.getElementById('ring-brightness');
    if (ringSlider) {
        ringSlider.addEventListener('input', (e) => {
            setRingLed(e.target.value);
        });
    }

    const ringHex = document.getElementById('ring-custom-brightness-hex');
    if (ringHex) {
        ringHex.addEventListener('input', (e) => {
            let val = parseInt(e.target.value, 16);
            if (!isNaN(val)) {
                val = Math.min(255, Math.max(0, val));
                const slider = document.getElementById('ring-brightness');
                if (slider) slider.value = val;
                const valDisp = document.getElementById('ring-val');
                if (valDisp) valDisp.textContent = val;
                // Don't call setRingLed directly to avoid double hex update
            }
        });
    }
}

function initLedBitGrid() {
    const grid = document.getElementById('led-bit-grid');
    if (!grid) return;

    grid.innerHTML = '';
    const radius = 60; // radius in px
    const centerX = 80;
    const centerY = 80;

    for (let i = 0; i < 15; i++) {
        const angle = (i / 15) * 2 * Math.PI - Math.PI / 2;
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);

        const wrapper = document.createElement('div');
        wrapper.className = 'bit-cb-wrapper';
        wrapper.style.cssText = `position:absolute; left:${x}px; top:${y}px; display:flex; flex-direction:column; align-items:center; transform:translate(-50%, -50%);`;

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.className = 'led-bit-cb';
        cb.id = `led-bit-${i}`;
        cb.value = 1 << i;
        cb.onchange = updateLedMaskFromCheckboxes;

        const label = document.createElement('label');
        label.setAttribute('for', `led-bit-${i}`);
        label.innerText = i + 1;
        label.style.fontSize = '9px';

        wrapper.appendChild(cb);
        wrapper.appendChild(label);
        grid.appendChild(wrapper);
    }
    updateCheckboxesFromMask();
}

function initRingLedBitGrid() {
    const grid = document.getElementById('ring-bit-grid');
    if (!grid) return;

    grid.innerHTML = '';
    const radius = 100; // larger radius for ring
    const centerX = 125;
    const centerY = 125;

    for (let i = 0; i < 24; i++) {
        const angle = (i / 24) * 2 * Math.PI - Math.PI / 2;
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);

        const wrapper = document.createElement('div');
        wrapper.style.cssText = `position:absolute; left:${x}px; top:${y}px; display:flex; flex-direction:column; align-items:center; transform:translate(-50%, -50%);`;

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.className = 'ring-bit-cb';
        cb.id = `ring-bit-${i}`;
        cb.onchange = updateRingLedMaskFromCheckboxes;

        const label = document.createElement('label');
        label.setAttribute('for', `ring-bit-${i}`);
        label.innerText = i + 1;
        label.style.fontSize = '8px';

        wrapper.appendChild(cb);
        wrapper.appendChild(label);
        grid.appendChild(wrapper);
    }
}

function updateLedMaskFromCheckboxes() {
    let mask = 0;
    document.querySelectorAll('.led-bit-cb').forEach(cb => {
        if (cb.checked) mask |= parseInt(cb.value);
    });
    document.getElementById('internal-mask-input').value = mask.toString(16);
}

function updateCheckboxesFromMask() {
    const input = document.getElementById('internal-mask-input');
    if (!input) return;
    const mask = parseInt(input.value, 16) || 0;
    document.querySelectorAll('.led-bit-cb').forEach(cb => {
        const bit = parseInt(cb.value);
        cb.checked = (mask & bit) !== 0;
    });
}

function setLedBits(checked) {
    document.querySelectorAll('.led-bit-cb').forEach(cb => cb.checked = checked);
    updateLedMaskFromCheckboxes();
}

function updateRingLedMaskFromCheckboxes() {
    let mask = BigInt(0);
    // Ring LEDs are bits 15-39. LED 1 = bit 15, LED 25 = bit 39.
    document.querySelectorAll('.ring-bit-cb').forEach((cb, i) => {
        if (cb.checked) {
            mask |= (BigInt(1) << BigInt(i + 15));
        }
    });
    document.getElementById('ring-mask-input').value = mask.toString(16).padStart(10, '0').toUpperCase();
}

function setRingLedBits(checked) {
    document.querySelectorAll('.ring-bit-cb').forEach(cb => cb.checked = checked);
    updateRingLedMaskFromCheckboxes();
}

function setCustomInternalLed() {
    const color = document.getElementById('internal-rgb-picker').value;
    const mask = document.getElementById('internal-mask-input').value.trim() || '7fff';

    showStatus('Đang cập nhật LED...', 'info', 'fa-spinner fa-spin');
    fetch('/api/hardware/led/custom', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ color: color, mask: mask })
    }).then(r => r.json()).then(data => {
        if (data.status === 'success') showStatus('Đã cập nhật LED bên trong', 'success', 'fa-check-circle');
        else showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
    }).catch(err => showStatus('Lỗi kết nối LED', 'error', 'fa-times-circle'));
}

function applyCustomRingLed() {
    const brightness = document.getElementById('ring-custom-brightness-hex').value.trim() || '00';
    const mask = document.getElementById('ring-mask-input').value.trim() || '0000000000';

    showStatus('Đang cập nhật Ring LED...', 'info', 'fa-spinner fa-spin');
    fetch('/api/hardware/led/custom', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ color: brightness, mask: mask })
    }).then(r => r.json()).then(data => {
        if (data.status === 'success') showStatus('Đã cập nhật Ring LED', 'success', 'fa-check-circle');
        else showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
    }).catch(err => showStatus('Lỗi kết nối LED', 'error', 'fa-times-circle'));
}

/* Music LED Sync */
var currentLedMode = 'SPECTRUM';

function loadMusicLedSyncStatus() {
    fetch('/api/music-led/status')
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success' && data.data) {
                const toggle = document.getElementById('musicLedToggle');
                if (toggle) toggle.checked = data.data.enabled;
                const mode = document.getElementById('musicLedMode');
                if (mode) mode.value = data.data.mode || 'SPECTRUM';
                const sens = document.getElementById('musicSensitivity');
                if (sens) {
                    sens.value = Math.round((data.data.sensitivity || 0.5) * 100);
                    const sensVal = document.getElementById('sensitivity-val');
                    if (sensVal) sensVal.textContent = sens.value;
                }
                const bright = document.getElementById('musicBrightness');
                if (bright) {
                    bright.value = data.data.brightness || 128;
                    const briVal = document.getElementById('musicbright-val');
                    if (briVal) briVal.textContent = bright.value;
                }
            }
        }).catch(err => console.error('Music LED status error:', err));
}

function toggleMusicLed() {
    const enabled = document.getElementById('musicLedToggle').checked;
    fetch('/api/music-led/enable', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
    }).then(r => r.json()).then(data => {
        if (data.status === 'success') showStatus(data.message || 'Music LED toggled', 'success', 'fa-check-circle');
        else { showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle'); document.getElementById('musicLedToggle').checked = !enabled; }
    });
}

function setMusicLedMode() {
    const mode = document.getElementById('musicLedMode').value;
    fetch('/api/music-led/mode', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode }) })
        .then(r => r.json()).then(data => data.status === 'success' ? showStatus('Mode: ' + mode, 'success', 'fa-check') : showStatus('Lỗi', 'error', 'fa-times'));
}

var musicLedSettingsTimeout = null;
function setMusicLedSettings() {
    const s = document.getElementById('musicSensitivity'), b = document.getElementById('musicBrightness');
    if (s) document.getElementById('sensitivity-val').textContent = s.value;
    if (b) document.getElementById('musicbright-val').textContent = b.value;
    clearTimeout(musicLedSettingsTimeout);
    musicLedSettingsTimeout = setTimeout(() => {
        const settings = {};
        if (s) settings.sensitivity = parseFloat(s.value) / 100;
        if (b) settings.brightness = parseInt(b.value);
        fetch('/api/music-led/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(settings) }).catch(console.error);
    }, 300);
}
