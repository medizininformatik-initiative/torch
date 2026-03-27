import DefaultTheme, {VPBadge} from 'vitepress/theme'
import type {Theme} from 'vitepress'

import 'vitepress-openapi/dist/style.css'

export default {
    extends: DefaultTheme,
    async enhanceApp({app}) {
        // Register the built-in VitePress Badge as 'VpBadge'
        // to avoid the "Component 'Badge' has already been registered" warning.
        app.component('VpBadge', VPBadge)

        // vitepress-openapi uses the browser File API at module init time, which
        // does not exist in Node.js 18. Dynamic import + SSR guard keeps it client-only.
        if (!import.meta.env.SSR) {
            const {theme: openapiTheme} = await import('vitepress-openapi/client')
            openapiTheme.enhanceApp({
                app,
                config: {
                    hideTryItOut: true,
                    hideServers: true,
                    proxy: undefined
                }
            })
        }
    }
} satisfies Theme
