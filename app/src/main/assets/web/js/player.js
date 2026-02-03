/**
 * Player Logic (State & UI)
 */

var playlistState = {
    playlist: [],
    currentSong: null,
    isPlaying: false,
    isPaused: false,
    playerState: 'IDLE',
    mode: 0, // 0: Seq, 1: RepOne, 2: RepAll, 3: Shuffle
    position: 0,
    duration: 0,
    volume: 80,
    speed: 1.0
};

var syncInterval = null;
var isDraggingSeek = false;
var isDraggingVolume = false;
var isUpdatingMode = false;
var seekTimeout;

function initPlayer() {
    startSync();
    // Bind controls
    const btnPrev = document.getElementById('btnPrev');
    if (btnPrev) btnPrev.onclick = () => sendControl('prev');

    const btnNext = document.getElementById('btnNext');
    if (btnNext) btnNext.onclick = () => sendControl('next');

    const btnPlay = document.getElementById('btnPlay');
    if (btnPlay) btnPlay.onclick = () => {
        if (playlistState.isPlaying) sendControl('pause');
        else sendControl('resume');
    };

    // Mode toggle
    const btnMode = document.getElementById('btnMode');
    if (btnMode) btnMode.onclick = toggleMode;

    // Seek
    const seekSlider = document.getElementById('seekSlider');
    if (seekSlider) {
        seekSlider.oninput = () => { isDraggingSeek = true; };
        seekSlider.onchange = (e) => {
            isDraggingSeek = false;
            sendControl('seek', e.target.value);
        };
    }

    // Volume slider
    const volumeSlider = document.getElementById('volumeSlider');
    if (volumeSlider) {
        volumeSlider.addEventListener('mousedown', () => { isDraggingVolume = true; });
        volumeSlider.addEventListener('mouseup', () => { isDraggingVolume = false; });
        volumeSlider.addEventListener('input', (e) => {
            const vol = parseInt(e.target.value);
            updateVolumeDisplay(vol);
            setVolume(vol); // Send immediately or debounce? Send immediately for responsiveness
        });
        volumeSlider.addEventListener('change', () => { isDraggingVolume = false; }); // Ensure false on release
    }

    // Speed selector
    const speedSelect = document.getElementById('speedSelect');
    if (speedSelect) {
        speedSelect.onchange = (e) => {
            const speed = parseFloat(e.target.value);
            setSpeed(speed);
        };
    }

    // Keyboard shortcuts
    setupKeyboardShortcuts();
}

function startSync() {
    if (syncInterval) clearInterval(syncInterval);
    sync(); // immediate
    syncInterval = setInterval(sync, 1000);
}

function sync() {
    fetch('/api/playlist/list')
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                if (isUpdatingMode) {
                    let savedMode = playlistState.mode;
                    playlistState = data.data;
                    playlistState.mode = savedMode; // Keep optimistic state
                } else {
                    playlistState = data.data;
                }
                updatePlayerUI();
            }
        })
        .catch(err => console.error('Sync error:', err));
}

function setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        // Ignore if typing in input
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

        switch (e.key) {
            case ' ': // Space - Play/Pause
                e.preventDefault();
                if (playlistState.isPlaying) sendControl('pause');
                else sendControl('resume');
                break;
            case 'ArrowLeft': // -5s
                e.preventDefault();
                const newPos1 = Math.max(0, playlistState.position - 5000);
                sendControl('seek', newPos1);
                break;
            case 'ArrowRight': // +5s
                e.preventDefault();
                const newPos2 = Math.min(playlistState.duration, playlistState.position + 5000);
                sendControl('seek', newPos2);
                break;
            case 'ArrowUp': // Volume +10
                e.preventDefault();
                setVolume(Math.min(100, playlistState.volume + 10));
                break;
            case 'ArrowDown': // Volume -10
                e.preventDefault();
                setVolume(Math.max(0, playlistState.volume - 10));
                break;
            case 'n': case 'N': // Next
                e.preventDefault();
                sendControl('next');
                break;
            case 'p': case 'P': // Previous
                e.preventDefault();
                sendControl('prev');
                break;
            case 'm': case 'M': // Mode toggle
                e.preventDefault();
                toggleMode();
                break;
        }
    });
}

function updatePlayerUI() {
    const { isPlaying, isPaused, playerState, position, duration, mode, currentSong, playlist, volume, speed } = playlistState;

    // Button states
    const btnPlay = document.getElementById('btnPlay');
    if (btnPlay) {
        if (isPlaying) {
            btnPlay.innerHTML = '<i class="fas fa-pause"></i>';
        } else if (isPaused) {
            btnPlay.innerHTML = '<i class="fas fa-play"></i>';
            btnPlay.style.opacity = '0.8';
        } else {
            btnPlay.innerHTML = '<i class="fas fa-play"></i>';
            btnPlay.style.opacity = '1';
        }
    }

    // Time
    const timeCurrent = document.getElementById('timeCurrent');
    if (timeCurrent) timeCurrent.innerText = formatTime(position);

    const timeDuration = document.getElementById('timeDuration');
    if (timeDuration) timeDuration.innerText = formatTime(duration);

    // Mode Icon
    const btnMode = document.getElementById('btnMode');
    const modeIcons = [
        'fas fa-long-arrow-alt-right', // 0: Seq
        'fas fa-redo-alt', // 1: Repeat One (overlay 1)
        'fas fa-redo',     // 2: Repeat All
        'fas fa-random'    // 3: Shuffle
    ];
    if (btnMode && !isUpdatingMode) {
        btnMode.innerHTML = `<i class="${modeIcons[mode]}"></i>`;
        if (mode === 1) btnMode.innerHTML += '<span style="font-size:10px; font-weight:bold; position:absolute; top:50%; left:50%; transform:translate(-50%, -50%); -webkit-text-stroke: 1px black;">1</span>';
        btnMode.style.color = mode === 0 ? '' : 'white';
    }

    // Update Progress
    const seekSlider = document.getElementById('seekSlider');
    const timeLabel = document.getElementById('timeLabel');

    if (seekSlider && !isDraggingSeek) {
        seekSlider.max = duration;
        seekSlider.value = position;
    }
    if (timeLabel) timeLabel.innerText = formatTime(position) + ' / ' + formatTime(duration);

    // Volume UI
    const volumeSlider = document.getElementById('volumeSlider');
    if (volumeSlider && !isDraggingVolume && volumeSlider.value != volume) {
        volumeSlider.value = volume;
    }
    updateVolumeDisplay(volume);

    // Speed UI
    const speedSelect = document.getElementById('speedSelect');
    if (speedSelect && speedSelect.value != speed) {
        speedSelect.value = speed;
    }
    const speedDisplay = document.getElementById('speedDisplay');
    if (speedDisplay) {
        speedDisplay.innerText = speed === 1.0 ? '1x' : speed + 'x';
    }

    // Update Now Playing Info
    const npTitle = document.getElementById('npTitle');
    const npArtist = document.getElementById('npArtist');
    const npThumb = document.getElementById('npThumb');
    const npPlaceholder = document.getElementById('npPlaceholder');

    const placeholder = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBmaWxsPSIjNTU1IiBkPSJNMTIgM3YxMC41NWMtLjU5LS4zNC0xLjI3LS41NS0yLS41NS0yLjIxIDAtNCAxLjc5LTQgNHMxLjc5IDQgNCA0IDQtMS43OSA0LTRWMN2g0VjNoLTR6Ii8+PC9zdmc+';

    if (npTitle && npArtist && npThumb && npPlaceholder) {
        if (currentSong) {
            npTitle.innerText = currentSong.title;
            npArtist.innerText = currentSong.artist;
            if (currentSong.thumbnail) {
                npThumb.src = currentSong.thumbnail;
                npThumb.style.display = 'block';
                npPlaceholder.style.display = 'none';
            } else {
                npThumb.style.display = 'none';
                npPlaceholder.style.display = 'flex';
            }
        } else {
            npTitle.innerText = 'Chưa chọn bài';
            npArtist.innerText = '...';
            npThumb.style.display = 'none';
            npPlaceholder.style.display = 'flex';
        }
    }

    renderPlaylist(playlist, currentSong, isPlaying);
}

function updateVolumeDisplay(vol) {
    const volValue = document.getElementById('volumeValue');
    const volIcon = document.getElementById('volumeIcon');

    if (volValue) volValue.innerText = vol;

    if (volIcon) {
        if (vol === 0) {
            volIcon.className = 'fas fa-volume-mute';
        } else if (vol < 33) {
            volIcon.className = 'fas fa-volume-off';
        } else if (vol < 66) {
            volIcon.className = 'fas fa-volume-down';
        } else {
            volIcon.className = 'fas fa-volume-up';
        }
    }
}

function renderPlaylist(playlist, currentSong, isPlaying) {
    const container = document.getElementById('playlistContainer');
    if (!container) return;

    if (!playlist || playlist.length === 0) {
        container.innerHTML = '<div class="empty">Danh sách trống</div>';
        return;
    }

    const html = playlist.map((song, index) => {
        const isCurrent = currentSong && currentSong.id === song.id;
        const thumb = song.thumbnail || '';
        const title = escapeHtml(song.title || 'Unknown');
        const artist = escapeHtml(song.artist || 'Unknown');

        // Use song-item class for consistent styling
        // Highlight active song with a border or background
        const activeClass = isCurrent ? 'playing-song' : '';
        const playingIcon = (isCurrent && isPlaying) ? '<i class="fas fa-volume-up playing-icon"></i>' : '';

        return `
        <div class="song-item ${activeClass}">
             ${thumb ? `<img src="${thumb}" class="song-thumb" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'">` : ''}
             <div class="song-thumb-placeholder" style="display:${thumb ? 'none' : 'flex'}; width:48px; height:48px; background:var(--gray-800); border-radius:6px; align-items:center; justify-content:center; margin-right:12px;">
                 <i class="fas fa-music" style="color:var(--gray-500)"></i>
             </div>

            <div class="song-info" onclick="playPlaylistIndex(${index})">
                 <div class="song-title" style="${isCurrent ? 'color:var(--primary)' : ''}">${index + 1}. ${title} ${playingIcon}</div>
                 <div class="song-artist">${artist}</div>
            </div>

            <div class="song-action" style="display:flex; gap:8px;">
                 
                 <button class="song-play-btn" onclick="removePlaylistIndex(${index}, event)" title="Xóa">
                     <i class="fas fa-trash"></i>
                 </button>
            </div>
        </div>
        `;
    }).join('');

    container.innerHTML = html;
}

function playPlaylistIndex(index) {
    if (isDraggingSeek) return;
    fetch('/api/playlist/play-index', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ index: index })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                sync();
            } else {
                showStatus(data.message, 'error');
            }
        });
}

function removePlaylistIndex(index, event) {
    if (event) event.stopPropagation();
    if (!confirm('Xóa bài hát này khỏi danh sách?')) return;

    fetch('/api/playlist/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ index: index })
    })
        .then(r => r.json())
        .then(data => {
            if (data.status === 'success') {
                showStatus('Đã xóa bài hát', 'success');
                sync();
            } else {
                showStatus(data.message, 'error');
            }
        });
}

function sendControl(action, value) {
    fetch('/api/playlist/control', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, value })
    }).then(sync);
}

function toggleMode() {
    let newMode = playlistState.mode + 1;
    if (newMode > 3) newMode = 0;

    // Optimistic Update
    isUpdatingMode = true;
    playlistState.mode = newMode;
    const btnMode = document.getElementById('btnMode');
    const modeIcons = [
        'fas fa-long-arrow-alt-right',
        'fas fa-redo-alt',
        'fas fa-redo',
        'fas fa-random'
    ];
    if (btnMode) {
        btnMode.innerHTML = `<i class="${modeIcons[newMode]}"></i>`;
        if (newMode === 1) btnMode.innerHTML += '<span style="font-size:10px; font-weight:bold; position:absolute; top:50%; left:50%; transform:translate(-50%, -50%); -webkit-text-stroke: 1px black;">1</span>';
        btnMode.style.color = newMode === 0 ? '' : 'white';
    }

    sendControl('mode', newMode).then(() => {
        setTimeout(() => { isUpdatingMode = false; }, 2000); // 2s lock to ensure sync is new
    });
}

function setVolume(volume) {
    fetch('/api/playlist/volume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ volume: volume })
    }).then(() => {
        playlistState.volume = volume;
        updateVolumeDisplay(volume);
    });
}

function setSpeed(speed) {
    fetch('/api/playlist/speed', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ speed: speed })
    }).then(() => {
        playlistState.speed = speed;
        const speedDisplay = document.getElementById('speedDisplay');
        if (speedDisplay) speedDisplay.innerText = speed === 1.0 ? '1x' : speed + 'x';
    });
}

function formatTime(ms) {
    if (!ms) return '00:00';
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec < 10 ? '0' : ''}${sec}`;
}

function playZingSong(id, title, artist, thumb, mode = 'play') {
    const msg = mode === 'add' ? 'Đang thêm vào danh sách...' : 'Đang lấy link nhạc...';
    showStatus(msg, 'info');

    fetch('/api/zing/play', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=UTF-8' },
        body: JSON.stringify({
            id: id,
            mode: mode,
            title: title,
            artist: artist,
            thumbnail: thumb
        })
    })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success' || data.status === 200) {
                showStatus(data.message || 'Thành công', 'success');
                if (mode === 'play') setTimeout(sync, 500);
            } else {
                showStatus(data.message, 'error');
            }
        })
        .catch(err => {
            showStatus('Lỗi kết nối', 'error');
        });
}

function stopMusic() {
    fetch('/api/hardware/stop', { method: 'POST' })
        .then(() => {
            const nowPlayingCard = document.getElementById('nowPlayingCard');
            if (nowPlayingCard) nowPlayingCard.style.display = 'none';
            const status = document.getElementById('zingSearchStatus');
            showStatusInElement(status, 'Đã dừng phát nhạc', 'info');
        })
        .catch(console.error);
}
