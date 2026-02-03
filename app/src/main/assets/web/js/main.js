/**
 * Main Entry Point
 */

// helper to bind events safely
function setupEventListeners() {
    const fileInput = document.getElementById('fileUpload');
    if (fileInput) {
        // Remove existing listeners by cloning (nuclear option to prevent duplicates)
        const newHelper = fileInput.cloneNode(true);
        if (fileInput.parentNode) {
            fileInput.parentNode.replaceChild(newHelper, fileInput);

            // Re-bind
            newHelper.addEventListener('change', function () {
                const label = this.parentElement.querySelector('.file-text');
                if (this.files && this.files.length > 0) {
                    if (label) {
                        label.textContent = this.files.length === 1
                            ? this.files[0].name
                            : `${this.files.length} files selected`;
                    }
                }
            });
        }
    }

    const apkInput = document.getElementById('apkUpload');
    if (apkInput) {
        const newHelper = apkInput.cloneNode(true);
        if (apkInput.parentNode) {
            apkInput.parentNode.replaceChild(newHelper, apkInput);

            newHelper.addEventListener('change', function () {
                const label = this.parentElement.querySelector('.file-text');
                if (this.files && this.files.length > 0) {
                    if (label) {
                        label.textContent = this.files[0].name;
                    }
                }
            });
        }
    }

    if (window.loadApps) loadApps();
    loadCurrentPort();

    // Init Player if elements exist
    if (window.initPlayer) initPlayer();
}

// Load current port setting
function loadCurrentPort() {
    fetch('/api/settings/port')
        .then(r => r.json())
        .then(data => {
            const portInput = document.getElementById('serverPort');
            if (portInput) {
                portInput.value = data.port || 8188;
            }
        })
        .catch(err => console.log('Could not load port:', err));
}

// Change server port
function changePort() {
    const portInput = document.getElementById('serverPort');
    const newPort = parseInt(portInput.value);

    if (isNaN(newPort) || newPort < 1024 || newPort > 65535) {
        showStatus('Port phải từ 1024 đến 65535', 'error', 'fa-exclamation-circle');
        return;
    }

    const currentPort = window.location.port || 8188;

    if (newPort == currentPort) {
        showStatus('Port không thay đổi', 'info', 'fa-info-circle');
        return;
    }

    showStatus('Đang thay đổi port...', 'info', 'fa-spinner fa-spin');

    fetch('/api/settings/port', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ port: newPort })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus('Port đã thay đổi! Đang chuyển hướng...', 'success', 'fa-check-circle');

                // Redirect to new port after delay
                setTimeout(() => {
                    const newUrl = window.location.protocol + '//' +
                        window.location.hostname + ':' + newPort + '/';
                    window.location.href = newUrl;
                }, 2000);
            } else {
                showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
            }
        })
        .catch(err => {
            showStatus('Lỗi: ' + err.message, 'error', 'fa-times-circle');
        });
}

// Run immediately since we are at end of body in index.html usually,
// but better to rely on DOMContentLoaded or explicit call
document.addEventListener('DOMContentLoaded', setupEventListeners);
