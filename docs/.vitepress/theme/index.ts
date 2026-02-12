import DefaultTheme, {VPBadge} from 'vitepress/theme'
import type {Theme} from 'vitepress'

// 1. Import the theme and styles
import {theme as openapiTheme} from 'vitepress-openapi/client'
import 'vitepress-openapi/dist/style.css'

export default {
    extends: DefaultTheme,
    enhanceApp({app}) {
        // Register the built-in VitePress Badge as 'VpBadge'
        // to avoid the "Component 'Badge' has already been registered" warning.
        app.component('VpBadge', VPBadge)

        // 2. Configure the OpenAPI theme
        openapiTheme.enhanceApp({
            app,
            config: {
                hideTryItOut: true,
                hideServers: true,
                proxy: undefined
            }
        })
    }
} satisfies Theme
