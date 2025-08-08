import {defineConfig} from 'vitepress'

export default defineConfig({
    title: 'TORCH',
    description: 'TORCH Documentation',
    base: process.env.DOCS_BASE || '',
    lastUpdated: true,

    themeConfig: {
        siteTitle: false,

        editLink: {
            pattern: 'https://github.com/medizininformatik-initiative/torch/edit/main/docs/:path',
            text: 'Edit this page on GitHub'
        },

        socialLinks: [
            {icon: 'github', link: 'https://github.com/medizininformatik-initiative/torch'}
        ],

        footer: {
            message: 'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>',
        },

        search: {
            provider: 'local'
        },

        outline: {
            level: [2, 3]
        },

        nav: [
            {text: 'Home', link: '/'}
        ],

        sidebar: [
            {
                text: 'Home',
                link: '/index.md'
            },
            {
                text: 'Getting Started',
                items: [
                    {text: 'Introduction', link: '/getting-started'}
                ]
            },
            {
                text: 'Documentation',
                link: '/documentation',
                items: [
                    {text: 'Configuration', link: '/configuration'},
                    {text: 'API', link: '/api/api'},
                    {text: 'CRTDL', link: '/crtdl/crtdl'},
                    {
                        text: 'Implementation', items: [
                            {text: 'Implementation Overview', link: '/implementation/implementation-overview'},
                            {text: 'Consent', link: '/implementation/consent'},
                        ]
                    },
                    {text: 'Architecture', link: '/architecture/architecture'},

                ]
            }
        ]
    }
})
