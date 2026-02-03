/**
 * Tab Navigation Logic
 */

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
        });
    });

    document.querySelectorAll('.sub-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const subtab = tab.dataset.subtab;
            switchSubTab(tab, subtab);
        });
    });
});

// Original logic allowed overriding switchTab, so we define it as variable if needed, or simply function
var switchTab = function (tabName) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    document.getElementById(`${tabName}-tab`).classList.add('active');

    // Load data based on tab
    // Load data based on tab
    switch (tabName) {
        case 'apps':
            if (window.loadApps) loadApps();
            break;
        case 'system':
            // Default load Info if active, or just generic system poll
            if (window.loadSystemInfo && document.getElementById('sys-info').classList.contains('active')) loadSystemInfo();
            if (window.loadVolume) loadVolume();
            break;
        case 'files':
            if (window.loadFiles) loadFiles();
            break;
        case 'music':
            // Load Music LED Sync status
            if (window.loadMusicLedSyncStatus) loadMusicLedSyncStatus();
            break;
        case 'xiaozhi':
            if (window.loadBotDashboard) loadBotDashboard();
            break;
        case 'memory':
            if (window.initMemoryTab) initMemoryTab();
            break;
    }
}

function switchSubTab(clickedTab, subtab) {
    const parent = clickedTab.closest('.tab-content');
    parent.querySelectorAll('.sub-tab').forEach(t => t.classList.remove('active'));
    clickedTab.classList.add('active');

    parent.querySelectorAll('.sub-content').forEach(c => c.classList.remove('active'));
    document.getElementById(subtab).classList.add('active');

    // Load data
    // Load data
    switch (subtab) {
        case 'sys-info':
            if (window.loadSystemInfo) loadSystemInfo();
            if (window.loadVolume) loadVolume();
            break;
        case 'sys-network':
            if (window.loadWifiInfo) loadWifiInfo();
            if (window.renderWolDevices) renderWolDevices();
            break;
        case 'sys-lights':
            // Internal LED controls don't need explicit load, rely on polling or init
            break;
        case 'sys-tools':
            // Nothing to preload
            break;
        // Legacy or unused below
        case 'all-apps':
            if (window.loadApps) loadApps();
            break;
        case 'wifi-client': // Legacy ID backup
            if (window.loadWifiInfo) loadWifiInfo();
            break;
    }
}
