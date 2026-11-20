import { defineConfig } from "vitepress";

export default defineConfig({
    base: "/Amrita-PYQ-Fetcher/",
    title: "AMRITA PYQ FETCHER",
    titleTemplate: ":title | AMRITA PYQ FETCHER",
    description: "An interactive Terminal CLI and Android App suite for accessing Amrita University PYQs.",
    cleanUrls: true,

    themeConfig: {
        nav: [
            { text: "Home", link: "/" },
            { text: "Docs", link: "/getting-started" },
        ],
        search: {
            provider: "local",
            placeholder: "Search",
        },
        sidebar: [
            {
                text: "Docs",
                items: [
                    { text: "Getting Started", link: "/getting-started" },
                    { text: "Testing", link: "/testing" },
                ],
            },
        ],
        socialLinks: [
            {
                icon: "github",
                link: "https://github.com/pkpranav-projects/Amrita-PYQ-Fetcher",
            },
        ],
    },
});
