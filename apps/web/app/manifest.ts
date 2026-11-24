import type { MetadataRoute } from "next";

// Online-only PWA: a manifest is all browsers need to offer "Install".
// The PNG icons are rendered from app/icon.svg with sharp (run in apps/web):
//   node -e "const s=require('sharp');for(const [n,p] of [[192,'public/icon-192.png'],[512,'public/icon-512.png'],[180,'app/apple-icon.png']])s('app/icon.svg').resize(n,n).png().toFile(p)"
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Hisaab",
    short_name: "Hisaab",
    description: "Track expenses, income, and who owes what.",
    start_url: "/",
    display: "standalone",
    background_color: "#F7F8FA",
    theme_color: "#0F766E",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
      {
        src: "/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
