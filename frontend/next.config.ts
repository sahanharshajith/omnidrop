import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Support offline multi-device access without CORS blocking on image/fonts
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
