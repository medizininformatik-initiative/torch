<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'

// Adapted from Aether's VersionSwitcher. The current version is injected at build time via
// VITE_DOCS_VERSION instead of being parsed from the URL, because the root (/torch/) serves the
// latest release and prerelease tags like v1.0.0-beta.7.2 do not match a simple pattern.
interface VersionEntry {
    version: string
    path: string
    latest: boolean
}

const currentVersion = import.meta.env.VITE_DOCS_VERSION
const versions = ref<VersionEntry[]>([])
const isOpen = ref(false)

function closeDropdown(e: MouseEvent) {
    if (!(e.target as HTMLElement).closest('.version-switcher')) {
        isOpen.value = false
    }
}

onMounted(async () => {
    document.addEventListener('click', closeDropdown)
    try {
        const res = await fetch('/torch/versions.json')
        if (res.ok) {
            versions.value = await res.json()
        }
    } catch {
        // No versions.json (e.g. before the first release deploy): only the current version is shown.
    }
})

onUnmounted(() => document.removeEventListener('click', closeDropdown))
</script>

<template>
    <div class="version-switcher" v-if="currentVersion && versions.length > 0">
        <button class="version-button" @click="isOpen = !isOpen" :aria-expanded="isOpen">
            {{ currentVersion }}
            <svg class="caret" xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24"
                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"/>
            </svg>
        </button>
        <div class="version-menu" v-show="isOpen">
            <a v-for="entry in versions"
               :key="entry.version"
               class="version-item"
               :class="{ active: entry.version === currentVersion }"
               :href="entry.path"
               target="_self">
                <!-- target opts out of VitePress' SPA routing: other versions are separate sites -->
                {{ entry.version }}
                <span v-if="entry.latest" class="latest-badge">latest</span>
            </a>
        </div>
    </div>
    <span v-else-if="currentVersion" class="version-text">{{ currentVersion }}</span>
</template>

<style scoped>
.version-switcher {
    position: relative;
    margin-right: 16px;
    padding-right: 16px;
    border-right: 1px solid var(--vp-c-divider);
}

.version-button {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 0 8px;
    height: 36px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: transparent;
    color: var(--vp-c-text-1);
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    cursor: pointer;
    transition: border-color 0.25s, color 0.25s;
}

.version-button:hover {
    border-color: var(--vp-c-brand-1);
    color: var(--vp-c-brand-1);
}

.caret {
    transition: transform 0.25s;
}

.version-button[aria-expanded="true"] .caret {
    transform: rotate(180deg);
}

.version-menu {
    position: absolute;
    top: calc(100% + 4px);
    left: 0;
    min-width: 160px;
    max-height: 60vh;
    overflow-y: auto;
    padding: 4px;
    border: 1px solid var(--vp-c-divider);
    border-radius: 8px;
    background: var(--vp-c-bg-elv);
    box-shadow: var(--vp-shadow-3);
    z-index: 100;
}

.version-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 6px;
    color: var(--vp-c-text-2);
    font-size: 13px;
    white-space: nowrap;
    text-decoration: none;
    transition: background-color 0.25s, color 0.25s;
}

.version-item:hover {
    background: var(--vp-c-default-soft);
    color: var(--vp-c-text-1);
}

.version-item.active {
    color: var(--vp-c-brand-1);
    font-weight: 600;
}

.version-text {
    margin-right: 16px;
    padding-right: 16px;
    border-right: 1px solid var(--vp-c-divider);
    font-size: 13px;
    color: var(--vp-c-text-2);
    white-space: nowrap;
}

.latest-badge {
    font-size: 10px;
    padding: 1px 5px;
    border-radius: 4px;
    background: var(--vp-c-brand-soft);
    color: var(--vp-c-brand-1);
    font-weight: 600;
}
</style>
