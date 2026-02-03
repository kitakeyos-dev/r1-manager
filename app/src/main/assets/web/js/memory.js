// Memory Manager JavaScript - Key-Value Store + Context Notes
let memoryData = { keys: [], notes: [], formatted: '', summary: '', hasData: false };
let editingKey = null;

// Initialize memory tab when activated
function initMemoryTab() {
    memoryLoad();
}

// Load all memory data
async function memoryLoad() {
    try {
        memoryShowLoading();
        const response = await fetch('/api/memory');
        const data = await response.json();

        if (data.status === 'success') {
            memoryData = data.data;
            memoryRenderAll();
        } else {
            memoryShowError('Không thể tải bộ nhớ: ' + data.message);
        }
    } catch (error) {
        memoryShowError('Lỗi kết nối: ' + error.message);
    }
}

// Render all memory sections
function memoryRenderAll() {
    memoryRenderStats();
    memoryRenderKeys();
    memoryRenderNotes();
}

// Render stats
function memoryRenderStats() {
    const container = document.getElementById('memoryStatsContainer');
    if (!container) return;

    const keyCount = memoryData.keys ? memoryData.keys.length : 0;
    const noteCount = memoryData.notes ? memoryData.notes.length : 0;
    const summary = memoryData.summary || 'Trống';

    container.innerHTML = `
        <div class="info-grid" style="grid-template-columns: repeat(3, 1fr);">
            <div class="info-item">
                <div class="label">Thông tin</div>
                <div class="value">${keyCount}</div>
                <div class="sub">Keys</div>
            </div>
            <div class="info-item">
                <div class="label">Ghi chú</div>
                <div class="value">${noteCount}</div>
                <div class="sub">Notes</div>
            </div>
            <div class="info-item">
                <div class="label">Tóm tắt</div>
                <div class="value small" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-size:12px;">${memoryEscapeHtml(summary)}</div>
                <div class="sub">Status</div>
            </div>
        </div>
    `;
}

// Render keys list
function memoryRenderKeys() {
    const container = document.getElementById('memoryContainer');
    if (!container) return;

    if (!memoryData.hasData) {
        container.innerHTML = `
            <div class="empty">
                <i class="fas fa-brain"></i>
                <div style="margin-top:10px;">Chưa có thông tin nào</div>
                <div style="font-size:12px; margin-top:5px; color:var(--gray-500);">Bấm "Thêm" để lưu thông tin</div>
            </div>
        `;
        return;
    }

    // Show formatted data
    const formattedHtml = memoryData.formatted
        .split('\n')
        .map(line => {
            if (line.startsWith('===')) {
                return `<div style="color:var(--primary); font-weight:600; margin-top:15px; margin-bottom:8px;">${memoryEscapeHtml(line)}</div>`;
            } else if (line.startsWith('•')) {
                return `<div style="color:var(--gray-200); padding-left:10px;">${memoryEscapeHtml(line)}</div>`;
            } else if (line.trim()) {
                return `<div style="color:var(--gray-400);">${memoryEscapeHtml(line)}</div>`;
            }
            return '';
        })
        .join('');

    let html = `
        <div style="background:var(--gray-800); border-radius:8px; padding:15px; margin-bottom:15px;">
            <div style="font-size:12px; line-height:1.8; font-family: monospace;">
                ${formattedHtml}
            </div>
        </div>
    `;

    // Keys table for editing
    if (memoryData.keys && memoryData.keys.length > 0) {
        html += `<div style="margin-top:20px; margin-bottom:10px; color:var(--gray-400); font-size:12px;">QUẢN LÝ KEYS:</div>`;
        html += `<div class="app-list">`;

        for (const key of memoryData.keys) {
            html += `
                <div class="app-item" style="padding: 10px 15px;">
                    <div class="app-info">
                        <div class="app-name" style="font-size:13px;">
                            <i class="fas fa-key" style="color:var(--primary);"></i>
                            <span style="color:var(--gray-300);">${memoryEscapeHtml(key)}</span>
                        </div>
                    </div>
                    <div class="app-buttons">
                        <button class="btn btn-secondary btn-small" onclick="memoryEdit('${memoryEscapeHtml(key)}')">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-danger btn-small" onclick="memoryDelete('${memoryEscapeHtml(key)}')">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </div>
            `;
        }
        html += `</div>`;
    }

    container.innerHTML = html;
}

// Render notes section
function memoryRenderNotes() {
    const container = document.getElementById('memoryNotesContainer');
    if (!container) return;

    if (!memoryData.notes || memoryData.notes.length === 0) {
        container.innerHTML = `
            <div style="color:var(--gray-500); font-size:12px; text-align:center; padding:20px;">
                Chưa có ghi chú nào
            </div>
        `;
        return;
    }

    let html = `<div class="app-list">`;
    for (const note of memoryData.notes) {
        html += `
            <div class="app-item" style="padding: 8px 15px;">
                <div class="app-info">
                    <div style="font-size:12px; color:var(--gray-300);">
                        <i class="fas fa-sticky-note" style="color:var(--warning);"></i>
                        ${memoryEscapeHtml(note)}
                    </div>
                </div>
            </div>
        `;
    }
    html += `</div>`;

    container.innerHTML = html;
}

// Open create modal
function memoryOpenCreateModal() {
    editingKey = null;
    document.getElementById('memoryModalTitle').textContent = 'Thêm Thông Tin';
    document.getElementById('memoryForm').reset();
    document.getElementById('memoryKey').value = '';
    document.getElementById('memoryKey').disabled = false;
    document.getElementById('memoryValue').value = '';

    const modal = document.getElementById('memoryModal');
    if (modal) {
        modal.classList.add('active');
        modal.style.display = 'flex';
    }
}

// Edit memory
async function memoryEdit(key) {
    try {
        const response = await fetch(`/api/memory/get?key=${encodeURIComponent(key)}`);
        const data = await response.json();

        if (data.status === 'success') {
            editingKey = key;
            document.getElementById('memoryModalTitle').textContent = 'Sửa Thông Tin';
            document.getElementById('memoryKey').value = data.data.key;
            document.getElementById('memoryKey').disabled = true; // Don't allow changing key when editing
            document.getElementById('memoryValue').value = data.data.value;

            const modal = document.getElementById('memoryModal');
            if (modal) {
                modal.classList.add('active');
                modal.style.display = 'flex';
            }
        } else {
            alert('Không thể tải: ' + data.message);
        }
    } catch (error) {
        alert('Lỗi kết nối: ' + error.message);
    }
}

// Delete memory
async function memoryDelete(key) {
    if (!confirm(`Bạn có chắc muốn xóa "${key}"?`)) {
        return;
    }
    try {
        const response = await fetch(`/api/memory/delete?key=${encodeURIComponent(key)}`, {
            method: 'POST'
        });
        const data = await response.json();
        if (data.status === 'success') {
            console.log('✅ Đã xóa:', key);
            memoryLoad();
        } else {
            alert('Không thể xóa: ' + data.message);
        }
    } catch (error) {
        alert('Lỗi kết nối: ' + error.message);
    }
}

// Clear all memories
async function memoryClearAll() {
    if (!confirm('⚠️ BẠN CÓ CHẮC MUỐN XÓA TẤT CẢ?\n\nHành động này không thể hoàn tác!')) {
        return;
    }
    try {
        const response = await fetch('/api/memory/clear', { method: 'POST' });
        const data = await response.json();
        if (data.status === 'success') {
            console.log('✅ Đã xóa tất cả');
            memoryLoad();
        } else {
            alert('Không thể xóa: ' + data.message);
        }
    } catch (error) {
        alert('Lỗi kết nối: ' + error.message);
    }
}

// Clear only notes
async function memoryClearNotes() {
    if (!confirm('Xóa tất cả ghi chú?')) {
        return;
    }
    try {
        const response = await fetch('/api/memory/clear-notes', { method: 'POST' });
        const data = await response.json();
        if (data.status === 'success') {
            console.log('✅ Đã xóa ghi chú');
            memoryLoad();
        } else {
            alert('Không thể xóa: ' + data.message);
        }
    } catch (error) {
        alert('Lỗi kết nối: ' + error.message);
    }
}

// Handle form submit
function memoryHandleSubmit(event) {
    event.preventDefault();

    const key = document.getElementById('memoryKey').value.trim();
    const value = document.getElementById('memoryValue').value.trim();

    if (!key) {
        alert('Vui lòng nhập Key');
        return;
    }
    if (!value) {
        alert('Vui lòng nhập Value');
        return;
    }

    (async () => {
        try {
            const response = await fetch('/api/memory/set', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key, value })
            });
            const data = await response.json();

            if (data.status === 'success') {
                console.log('✅ Đã lưu:', key);
                memoryCloseModal();
                memoryLoad();
            } else {
                alert('Lỗi: ' + data.message);
            }
        } catch (error) {
            alert('Lỗi kết nối: ' + error.message);
        }
    })();
}

// Add note
async function memoryAddNote() {
    const note = prompt('Nhập ghi chú:');
    if (!note || !note.trim()) return;

    try {
        const response = await fetch('/api/memory/note', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ note: note.trim() })
        });
        const data = await response.json();

        if (data.status === 'success') {
            console.log('✅ Đã thêm ghi chú');
            memoryLoad();
        } else {
            alert('Lỗi: ' + data.message);
        }
    } catch (error) {
        alert('Lỗi kết nối: ' + error.message);
    }
}

// Close modal
function memoryCloseModal() {
    const modal = document.getElementById('memoryModal');
    if (modal) {
        modal.classList.remove('active');
        modal.style.display = '';
    }
    editingKey = null;
    document.getElementById('memoryKey').disabled = false;
}

// Refresh memories
function memoryRefresh() {
    memoryLoad();
}

// Show loading state
function memoryShowLoading() {
    const container = document.getElementById('memoryContainer');
    if (container) {
        container.innerHTML = `
            <div class="loading">
                <i class="fas fa-spinner fa-spin"></i> <div>Đang tải...</div>
            </div>
        `;
    }
}

// Show error
function memoryShowError(message) {
    const container = document.getElementById('memoryContainer');
    if (container) {
        container.innerHTML = `
            <div class="empty">
                <i class="fas fa-exclamation-circle" style="color:var(--danger)"></i>
                <h3>Lỗi</h3>
                <p>${memoryEscapeHtml(message)}</p>
                <button class="btn btn-primary" onclick="memoryLoad()" style="margin-top:10px;">Thử lại</button>
            </div>
        `;
    }
}

// Escape HTML to prevent XSS
function memoryEscapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
