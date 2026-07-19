# PenMate Frontend

Vue 3 + TypeScript + Vite frontend for PenMate.

## Development

```bash
npm ci
npm run dev
```

Node.js 22 or newer and the npm version declared in `package.json#packageManager` are required.

## Quality checks

```bash
npm run lint
npm run format:check
npm run typecheck
npm run test:run
npm run test:e2e
npm run build
npm run budget
npm run audit:prod
```

`npm run optimize:images` regenerates WebP delivery assets from the PNG design sources.
