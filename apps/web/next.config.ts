import type { NextConfig } from "next";

// Where the Next.js server forwards /api requests. The browser only ever talks
// to Next.js (same origin), so auth cookies stay first-party. Falls back to the
// local API so `npm run build` works without any environment set up.
const API_URL = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_URL}/api/:path*` }];
  },
};

export default nextConfig;
