import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  typedRoutes: true,
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "images.unsplash.com"
      },
      {
        protocol: "https",
        hostname: "media.bookvenuemart.in"
      },
      {
        protocol: "http",
        hostname: "localhost",
        port: "9000"
      }
    ]
  }
};

export default nextConfig;
