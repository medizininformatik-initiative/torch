// .vitepress/theme/index.ts
import DefaultTheme, {VPBadge} from 'vitepress/theme'

export default {
    extends: DefaultTheme,
    enhanceApp({app}) {
        // Register Badge component globally
        app.component('Badge', VPBadge)
    }
}
