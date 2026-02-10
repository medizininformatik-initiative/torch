import {withMermaid} from "vitepress-plugin-mermaid";

export default withMermaid({
    title: 'TORCH',
    description: 'TORCH Documentation',
    base: process.env.DOCS_BASE || '/',
    appearance: true,
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
                link: '/index.md',
                activeMatch: '^/$'
            },
            {
                text: 'Overview',
                link: '/overview.md',
                activeMatch: '^/$'
            },
            {
                text: 'Getting Started',
                link: '/getting-started',
                activeMatch: '^/getting-started'
            },
            {
                text: 'Documentation',
                link: '/documentation',
                items: [
                    {text: 'Configuration', link: '/configuration'},
                    {text: 'API', link: '/api/api.md'},
                    {
                        text: 'CRTDL', link: '/crtdl/crtdl',
                        items: [
                            {text: 'Filter', link: '/crtdl/filter'},
                            {text: 'Consent Key', link: '/crtdl/consent-key'}
                        ]

                    },
                    {
                        text: 'Implementation',
                        link: '/implementation/implementation-overview',
                        items: [
                            {text: 'Error Codes', link: '/implementation/error-codes'},
                            {text: 'Consent', link: '/implementation/consent'},
                            {text: 'Direct Loading', link: '/implementation/direct-load'},
                            {text: 'Must Have Checking', link: '/implementation/must-have'},
                            {text: 'Cascading Delete', link: '/implementation/cascading-delete'},
                            {text: 'Reference Resolve', link: '/implementation/reference-resolve'},
                            {text: 'Redaction', link: '/implementation/redaction'},
                            {text: 'Data Extraction', link: '/implementation/data-extraction'}
                        ]
                    }

                ]
            }
        ]
    }
})
