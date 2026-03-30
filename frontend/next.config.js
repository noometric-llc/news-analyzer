/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  output: 'standalone',

  // Required in Next.js 14 to load instrumentation.ts (OTel SDK init).
  // Became stable in Next.js 15, so this flag can be removed on upgrade.
  experimental: {
    instrumentationHook: true,
    // OTel packages use native gRPC bindings and Node.js-only APIs — webpack
    // cannot bundle them. Marking them as server externals tells Next.js to
    // require() them at runtime instead of bundling. Renamed to
    // serverExternalPackages in Next.js 15.
    serverComponentsExternalPackages: [
      '@opentelemetry/sdk-node',
      '@opentelemetry/auto-instrumentations-node',
      '@opentelemetry/exporter-trace-otlp-grpc',
      '@opentelemetry/exporter-metrics-otlp-grpc',
      '@opentelemetry/resources',
      '@opentelemetry/sdk-metrics',
    ],
  },

  // Image optimization configuration
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'bioguide.congress.gov',
        pathname: '/bioguide/photo/**',
      },
    ],
  },

  // Redirects from old factbase routes to new knowledge-base routes
  // All factbase routes use permanent (308) redirects as they are legacy routes
  async redirects() {
    return [
      // Root factbase redirect
      {
        source: '/factbase',
        destination: '/knowledge-base',
        permanent: true,
      },

      // Branch-specific redirects → new hierarchical government pages (UI-3.A.2)
      // These must come before generic :path* redirect
      {
        source: '/factbase/organizations/executive',
        destination: '/knowledge-base/government/executive',
        permanent: true,
      },
      {
        source: '/factbase/organizations/legislative',
        destination: '/knowledge-base/government/legislative',
        permanent: true,
      },
      {
        source: '/factbase/organizations/judicial',
        destination: '/knowledge-base/government/judicial',
        permanent: true,
      },

      // Generic organization redirects
      {
        source: '/factbase/organizations/:path*',
        destination: '/knowledge-base/organizations',
        permanent: true,
      },
      {
        source: '/factbase/government-orgs',
        destination: '/knowledge-base/government',
        permanent: true,
      },

      // People redirects with subtype preservation
      {
        source: '/factbase/people/federal-judges',
        destination: '/knowledge-base/people?type=judges',
        permanent: true,
      },
      {
        source: '/factbase/people/congressional-members',
        destination: '/knowledge-base/people?type=members',
        permanent: true,
      },
      {
        source: '/factbase/people/executive-appointees',
        destination: '/knowledge-base/people?type=appointees',
        permanent: true,
      },
      {
        source: '/factbase/people/:path*',
        destination: '/knowledge-base/people',
        permanent: true,
      },

      // Committees redirect
      {
        source: '/factbase/committees',
        destination: '/knowledge-base/committees',
        permanent: true,
      },
      {
        source: '/factbase/committees/:path*',
        destination: '/knowledge-base/committees',
        permanent: true,
      },

      // Extracted entity type redirects (UI-3.B.2)
      // These redirect old KB routes for extracted entities to Article Analyzer
      // Using 307 (temporary) during transition period
      {
        source: '/knowledge-base/person',
        destination: '/article-analyzer/entities?type=person',
        permanent: false,
      },
      {
        source: '/knowledge-base/organization',
        destination: '/article-analyzer/entities?type=organization',
        permanent: false,
      },
      {
        source: '/knowledge-base/event',
        destination: '/article-analyzer/entities?type=event',
        permanent: false,
      },
      {
        source: '/knowledge-base/location',
        destination: '/article-analyzer/entities?type=location',
        permanent: false,
      },
    ]
  },

  // API proxy — server-side only, so use BACKEND_INTERNAL_URL (not NEXT_PUBLIC_).
  // In production Docker, set BACKEND_INTERNAL_URL=http://backend:8080.
  // In local dev, defaults to http://localhost:8080.
  async rewrites() {
    const backendUrl = process.env.BACKEND_INTERNAL_URL || 'http://localhost:8080';
    return {
      // Use fallback so Next.js API routes (e.g. /api/eval/results) are served
      // directly, and only unmatched /api/* requests are proxied to the backend.
      fallback: [
        {
          source: '/api/:path*',
          destination: `${backendUrl}/api/:path*`,
        },
      ],
    }
  },

  // Security headers
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
        ],
      },
    ]
  },

  // Optimization
  compiler: {
    removeConsole: process.env.NODE_ENV === 'production',
  },

  // Belt-and-suspenders: explicitly exclude OTel packages from webpack bundling.
  // serverComponentsExternalPackages (above) handles Server Components, but
  // instrumentation.ts is compiled via a separate webpack pass in Next.js 14.x
  // that may not honour that config. This webpack hook covers both cases.
  webpack(config, { isServer }) {
    if (isServer) {
      config.externals = [
        ...(Array.isArray(config.externals) ? config.externals : [config.externals]),
        '@opentelemetry/sdk-node',
        '@opentelemetry/auto-instrumentations-node',
        '@opentelemetry/exporter-trace-otlp-grpc',
        '@opentelemetry/exporter-metrics-otlp-grpc',
        '@opentelemetry/resources',
        '@opentelemetry/sdk-metrics',
      ];
    }
    return config;
  },
}

module.exports = nextConfig
