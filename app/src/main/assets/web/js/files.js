/**
 * File Manager Logic
 */

var currentPath = '/sdcard';

function loadFiles(path) {
    if (path) currentPath = path;

    const fileList = document.getElementById('fileList');
    if (!fileList) return;
    fileList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Đang tải...</div>';

    fetch(`/api/files?path=${encodeURIComponent(currentPath)}&t=${new Date().getTime()}`)
        .then(r => r.json())
        .then(response => {
            // Handle ApiResponse wrapper
            const data = response.data || response;
            if (data.files) {
                renderFileList(data);
                updateBreadcrumb(data.path);
                document.getElementById('fileCount').textContent = data.count || 0;
            } else {
                fileList.innerHTML = '<div class="error">Lỗi: ' + (response.message || 'Không thể đọc thư mục') + '</div>';
            }
        })
        .catch(err => {
            fileList.innerHTML = '<div class="error">Lỗi: ' + err.message + '</div>';
        });
}

function renderFileList(data) {
    const fileList = document.getElementById('fileList');

    if (!data.files || data.files.length === 0) {
        fileList.innerHTML = '<div class="empty">Thư mục trống</div>';
        return;
    }

    // Client-side sorting: Dirs first, then files
    data.files.sort((a, b) => {
        if (a.isDir !== b.isDir) return b.isDir - a.isDir;
        return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
    });

    let html = '';
    data.files.forEach(file => {
        const icon = file.isDir ? 'fa-folder' : getFileIcon(file.name);
        const iconColor = file.isDir ? 'color: #ffd54f;' : '';
        const size = file.isDir ? '' : formatSize(file.size);
        const modified = new Date(file.modified).toLocaleString();

        html += `
            <div class="file-item" onclick="${file.isDir ? `loadFiles('${currentPath}/${file.name}')` : ''}">
                <div class="file-icon" style="${iconColor}">
                    <i class="fas ${icon}"></i>
                </div>
                <div class="file-info">
                    <div class="file-name">${file.name}</div>
                    <div class="file-meta">${size} ${size ? '•' : ''} ${modified}</div>
                </div>
                <div class="file-actions">
                    ${!file.isDir ? `<button class="btn btn-small" onclick="event.stopPropagation(); downloadFile('${currentPath}/${file.name}')"><i class="fas fa-download"></i></button>` : ''}
                    <button class="btn btn-small" onclick="event.stopPropagation(); renameFile('${currentPath}/${file.name}', '${file.name}')"><i class="fas fa-edit"></i></button>
                    <button class="btn btn-small btn-danger" onclick="event.stopPropagation(); deleteFile('${currentPath}/${file.name}')"><i class="fas fa-trash"></i></button>
                </div>
            </div>
        `;
    });

    fileList.innerHTML = html;
}

function updateBreadcrumb(path) {
    currentPath = path;
    const parts = path.split('/').filter(p => p);
    let html = '<i class="fas fa-folder-open"></i>';
    let cumPath = '';

    html += `<span class="breadcrumb-item" onclick="loadFiles('/')">/</span>`;

    parts.forEach((part, i) => {
        cumPath += '/' + part;
        const isLast = i === parts.length - 1;
        html += `<span class="breadcrumb-sep">/</span>`;
        html += `<span class="breadcrumb-item${isLast ? ' active' : ''}" onclick="loadFiles('${cumPath}')">${part}</span>`;
    });

    document.getElementById('fileBreadcrumb').innerHTML = html;
}

function fileGoUp() {
    if (currentPath === '/' || currentPath === '') return;
    const parent = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/';
    loadFiles(parent);
}

function downloadFile(path) {
    window.open(`/api/files/download?path=${encodeURIComponent(path)}`, '_blank');
}

function uploadFiles() {
    const input = document.getElementById('fileUpload');
    const files = input.files;

    if (files.length === 0) {
        // User hasn't selected files yet, trigger the dialog
        input.click();
        return;
    }

    showStatus('Đang upload...', 'info', 'fa-spinner fa-spin');

    let uploaded = 0;
    Array.from(files).forEach(file => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('filename', file.name);

        fetch(`/api/files/upload?path=${encodeURIComponent(currentPath)}`, {
            method: 'POST',
            body: formData
        })
            .then(r => r.json())
            .then(data => {
                uploaded++;
                if (uploaded === files.length) {
                    showStatus(`Upload xong ${uploaded} file`, 'success', 'fa-check-circle');
                    setTimeout(() => loadFiles(), 1000);
                    input.value = '';
                    const label = document.querySelector('.file-text');
                    if (label) label.textContent = 'Chọn file để upload';
                }
            })
            .catch(err => {
                showStatus('Upload lỗi: ' + err.message, 'error', 'fa-times-circle');
            });
    });
}

function createFolder() {
    const name = prompt('Tên thư mục mới:');
    if (!name) return;

    fetch('/api/files/mkdir', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: currentPath + '/' + name })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus('Đã tạo thư mục', 'success', 'fa-check-circle');
                setTimeout(() => loadFiles(), 1000);
            } else {
                showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
            }
        })
        .catch(err => showStatus('Lỗi: ' + err.message, 'error', 'fa-times-circle'));
}

function renameFile(path, oldName) {
    const newName = prompt('Tên mới:', oldName);
    if (!newName || newName === oldName) return;

    fetch('/api/files/rename', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: path, newName: newName })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus('Đã đổi tên', 'success', 'fa-check-circle');
                loadFiles();
            } else {
                showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
            }
        })
        .catch(err => showStatus('Lỗi: ' + err.message, 'error', 'fa-times-circle'));
}

function deleteFile(path) {
    if (!confirm('Xóa: ' + path.split('/').pop() + '?')) return;

    fetch(`/api/files?path=${encodeURIComponent(path)}`, { method: 'DELETE' })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus('Đã xóa', 'success', 'fa-check-circle');
                setTimeout(() => loadFiles(), 1000); // 1s delay to ensure FS update
            } else {
                showStatus('Lỗi: ' + data.message, 'error', 'fa-times-circle');
            }
        })
        .catch(err => showStatus('Lỗi: ' + err.message, 'error', 'fa-times-circle'));
}
