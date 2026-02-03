/**
 * App Management Logic
 */

function loadApps() {
    const appList = document.getElementById('appList');
    if (!appList) return;

    appList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>';

    fetch('/api/apps')
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const apps = response.data || response;
            if (!apps || apps.length === 0) {
                appList.innerHTML = '<div class="empty"><i class="fas fa-box-open"></i>Không có ứng dụng</div>';
                return;
            }

            // Sort: Running first, then User, then System
            apps.sort((a, b) => {
                if (a.isRunning !== b.isRunning) return b.isRunning - a.isRunning;
                if (a.isSystem !== b.isSystem) return a.isSystem - b.isSystem;
                return a.name.localeCompare(b.name);
            });

            appList.innerHTML = apps.map(app => {
                const isSystem = app.isSystem;
                const isRunning = app.isRunning;

                let badges = '';
                if (isSystem) badges += '<span class="badge badge-system">System</span>';
                if (isRunning) badges += '<span class="badge badge-running">Running</span>';
                if (!isSystem && !isRunning) badges += '<span class="badge badge-user">User</span>';

                const isSelf = app.package === 'com.phicomm.r1manager';

                return `
                    <div class="app-item ${isSystem ? 'system-app' : 'user-app'} ${isRunning ? 'running' : ''}">
                        <div class="app-flex" style="display: flex; justify-content: space-between; align-items: center;">
                            <div class="app-info" style="margin-bottom: 0;">
                                <div class="app-name"><i class="fas fa-cube"></i>${escapeHtml(app.name)} ${badges}</div>
                                <div class="app-package">${escapeHtml(app.package)}</div>
                            </div>
                            <div class="app-actions">
                                ${!isSystem && !isRunning && !isSelf ? `
                                <button class="btn btn-small btn-success" onclick="launchApp('${app.package}')" title="Chạy">
                                    <i class="fas fa-play"></i>
                                </button>` : ''}
                                ${!isSystem && isRunning && !isSelf ? `
                                <button class="btn btn-small btn-warning" onclick="stopApp('${app.package}')" title="Dừng">
                                    <i class="fas fa-stop"></i>
                                </button>` : ''}
                                <button class="btn btn-small btn-secondary" onclick="exportApk('${app.package}')" title="Xuất APK">
                                    <i class="fas fa-file-export"></i>
                                </button>
                                ${!isSystem && !isSelf ? `
                                <button class="btn btn-small btn-danger" onclick="confirmUninstall('${app.package}', ${isSystem})" title="Gỡ cài đặt">
                                    <i class="fas fa-trash"></i>
                                </button>` : ''}
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        })
        .catch(err => {
            console.error(err);
            appList.innerHTML = `<div class="empty"><i class="fas fa-exclamation-circle"></i>Lỗi tải danh sách: ${escapeHtml(err.message)}</div>`;
        });
}

function exportApk(packageName) {
    window.open(`/api/apps/export?package=${encodeURIComponent(packageName)}`, '_blank');
}

function installApk() {
    const input = document.getElementById('apkUpload');
    const files = input.files;
    if (!files || files.length === 0) {
        input.click();
        return;
    }

    const file = files[0];
    const formData = new FormData();
    formData.append('file', file);

    const statusObj = document.getElementById('installStatus');
    statusObj.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Đang cài đặt... (Có thể mất vài phút)';
    statusObj.className = 'status show info';

    fetch('/api/apps/install', {
        method: 'POST',
        body: formData
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                statusObj.innerHTML = '<i class="fas fa-check-circle"></i> Cài đặt thành công!';
                statusObj.className = 'status show success';
                input.value = ''; // clear
                document.querySelector('label[for=apkUpload] .file-text').textContent = 'Chọn file APK';
                setTimeout(() => {
                    statusObj.className = 'status';
                    loadApps();
                }, 3000);
            } else {
                statusObj.innerHTML = '<i class="fas fa-times-circle"></i> Lỗi: ' + data.message;
                statusObj.className = 'status show error';
            }
        })
        .catch(err => {
            statusObj.innerHTML = '<i class="fas fa-times-circle"></i> Lỗi kết nối: ' + err.message;
            statusObj.className = 'status show error';
        });
}

function launchApp(pkg) {
    fetch('/api/apps/launch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ package: pkg })
    }).then(r => r.json()).then(d => {
        if (d.status === 'success') showStatus('Đã gửi lệnh chạy app', 'success', 'fa-play');
        else showStatus('Lỗi: ' + d.message, 'error', 'fa-exclamation');
        setTimeout(loadApps, 2000); // refresh status
    });
}

function stopApp(pkg) {
    fetch('/api/apps/stop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ package: pkg })
    }).then(r => r.json()).then(d => {
        if (d.status === 'success') showStatus('Đã dừng app', 'success', 'fa-stop');
        else showStatus('Lỗi: ' + d.message, 'error', 'fa-exclamation');
        setTimeout(loadApps, 2000);
    });
}

var pendingPkgToUninstall = null;
function confirmUninstall(pkg, isSystem) {
    pendingPkgToUninstall = pkg;
    const modal = document.getElementById('confirmModal');
    document.getElementById('modalTitle').textContent = 'Xác nhận gỡ cài đặt';
    document.getElementById('modalMessage').innerHTML = `Bạn có chắc chắn muốn gỡ cài đặt <b>${pkg}</b>?<br>` +
        (isSystem ? '<b style="color:red">CẢNH BÁO: Đây là ứng dụng hệ thống!</b>' : '');

    // Unbind previous
    const btn = document.getElementById('confirmBtn');
    const newBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(newBtn, btn);

    newBtn.addEventListener('click', () => {
        uninstallApp(pendingPkgToUninstall);
        closeModal();
    });

    modal.classList.add('show');
}

function uninstallApp(pkg) {
    showStatus('Đang gỡ cài đặt...', 'info', 'fa-spinner fa-spin');
    fetch('/api/apps/uninstall', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ package: pkg })
    }).then(r => r.json()).then(d => {
        if (d.status === 'success') {
            showStatus('Đã gỡ cài đặt', 'success', 'fa-check');
            loadApps();
        } else {
            showStatus('Lỗi: ' + d.message, 'error', 'fa-exclamation');
        }
    });
}
