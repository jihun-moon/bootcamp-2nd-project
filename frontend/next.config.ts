import type { NextConfig } from "next";

// 브라우저는 같은 출처 /api 로 부르고 Next 가 Spring 으로 넘긴다. CORS 설정을 따로 두지 않으려고
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

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
  // 카카오 로그인 시작(/oauth2/...)과 카카오가 돌려보내는 곳(/login/oauth2/...)도 Spring 이 처리한다.
  // 같은 출처로 넘겨야 세션 쿠키가 프론트 주소에 붙는다
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backendUrl}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${backendUrl}/login/oauth2/:path*` },
    ];
  },
};

export default nextConfig;
