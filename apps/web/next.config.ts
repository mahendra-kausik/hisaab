import type { NextConfig } from "next";

// Where the Next.js server forwards /api requests. The browser only ever talks
// to Next.js (same origin), so auth cookies stay first-party. Falls back to the
// local API so `npm run build` works without any environment set up.
const API_URL = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  experimental: {
    // Every signed-in page waits for the browser to confirm the session (GET /me)
    // before it renders, because the auth cookies never reach Next.js page requests.
    // Those pages cannot be part of the static shell, so the dev-only automatic
    // "instant navigation" check can only report noise for them. Only validate pages
    // that explicitly export `instant`.
    instantInsights: { validationLevel: "manual-warning" },
  },
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
