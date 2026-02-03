/**
 * Zing MP3 Search
 */

function searchZing() {
    searchZingJava();
}

function searchZingJava() {
    const input = document.getElementById('zingSearchInput');
    const results = document.getElementById('zingSearchResults');
    const status = document.getElementById('zingSearchStatus');
    const keyword = input.value.trim();

    if (!keyword) {
        showStatusInElement(status, 'Vui lòng nhập từ khóa tìm kiếm', 'warning');
        return;
    }

    results.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i>Đang tìm kiếm...</div>';
    showStatusInElement(status, 'Đang gọi API qua thiết bị...', 'info');

    fetch('/api/zing/search?q=' + encodeURIComponent(keyword))
        .then(r => r.json())
        .then(data => {
            if (data.status === 'error') {
                results.innerHTML = '<div class="empty"><i class="fas fa-exclamation-triangle"></i> ' + data.message + '</div>';
                showStatusInElement(status, data.message, 'error');
                return;
            }

            let parsed = data.data;
            if (typeof parsed === 'string') {
                try { parsed = JSON.parse(parsed); } catch (e) { console.error('Parse error:', e); }
            }

            let songs = [];

            if (parsed && parsed.data && parsed.data.items && Array.isArray(parsed.data.items)) {
                for (const item of parsed.data.items) {
                    if (item.suggestions && Array.isArray(item.suggestions)) {
                        songs = item.suggestions.filter(s => s.type === 1).map(s => ({
                            encodeId: s.id,
                            title: s.title,
                            artistsNames: s.artists ? s.artists.map(a => a.name).join(', ') : 'Unknown',
                            thumbnailM: s.thumb
                        }));
                        break;
                    }
                }
            } else if (parsed && parsed.songs && Array.isArray(parsed.songs)) {
                songs = parsed.songs;
            } else if (parsed && parsed.data && parsed.data.songs) {
                songs = parsed.data.songs;
            } else if (Array.isArray(parsed)) {
                songs = parsed;
            }

            if (songs.length === 0) {
                results.innerHTML = '<div class="empty"><i class="fas fa-search"></i> Không tìm thấy bài hát</div>';
                showStatusInElement(status, 'Không có kết quả', 'warning');
                return;
            }

            showStatusInElement(status, 'Tìm thấy ' + songs.length + ' bài hát!', 'success');

            results.innerHTML = songs.map(song => {
                const thumb = song.thumbnailM || song.thumb || '';
                const title = escapeHtml(song.title || song.name || 'Unknown');
                const artist = escapeHtml(song.artistsNames || song.artist || 'Unknown');
                const id = song.encodeId || song.id;

                return `
                <div class="song-item">
                    ${thumb ? `<img src="${thumb}" class="song-thumb" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'">` : ''}
                    <div class="song-thumb-placeholder" style="display:${thumb ? 'none' : 'flex'}; width:48px; height:48px; background:var(--gray-800); border-radius:6px; align-items:center; justify-content:center; margin-right:12px;">
                        <i class="fas fa-music" style="color:var(--gray-500)"></i>
                    </div>
                    
                    <div class="song-info" onclick="playZingSong('${id}', '${title}', '${artist}', '${thumb}', 'play')">
                        <div class="song-title">${title}</div>
                        <div class="song-artist">${artist}</div>
                    </div>
                    
                    <div class="song-action" style="display:flex; gap:8px;">
                        <button class="song-play-btn" onclick="playZingSong('${id}', '${title}', '${artist}', '${thumb}', 'add')" title="Thêm vào danh sách phát">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button class="song-play-btn" onclick="playZingSong('${id}', '${title}', '${artist}', '${thumb}', 'play')" title="Phát ngay">
                            <i class="fas fa-play"></i>
                        </button>
                    </div>
                </div>
                `;
            }).join('');
        })
        .catch(err => {
            console.error('Java search error:', err);
            results.innerHTML = '<div class="empty"><i class="fas fa-exclamation-triangle"></i> Lỗi: ' + err.message + '</div>';
            showStatusInElement(status, 'Lỗi kết nối', 'error');
        });
}
