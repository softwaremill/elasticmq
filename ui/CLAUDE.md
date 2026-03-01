# ElasticMQ UI - Project Documentation

## Project Overview

A modern web UI for monitoring and managing ElasticMQ message queues. Built with Next.js 16, this application provides real-time queue statistics, detailed queue information, and a clean interface with theme switching capabilities.

**Purpose:** Monitor ElasticMQ (in-memory SQS-compatible message queue) with a simple, modern web interface.

## Tech Stack

- **Framework:** Next.js 16 (App Router)
- **Language:** TypeScript (strict mode)
- **Styling:** Tailwind CSS v4
- **AWS Integration:** @aws-sdk/client-sqs
- **Runtime:** Node.js (LTS via nvm)
- **Package Manager:** npm

## Architecture

### Component Architecture

**Server Components (default):**
- `app/page.tsx` - Main queue list page
- `app/queues/[name]/page.tsx` - Queue details page
- Used for static layouts and initial page structure

**Client Components (`'use client'`):**
- `components/queue-list.tsx` - Queue list with polling
- `components/queue-details.tsx` - Queue details with polling
- `components/theme-switcher.tsx` - Theme toggle UI
- `components/page-header.tsx` - Page header with theme switcher
- Used when hooks, state, or browser APIs are needed

### Data Fetching Pattern

**Server Actions** (`lib/actions.ts`):
- `'use server'` directive at top
- `getQueues()` - Fetch all queues with attributes
- `getQueueDetails(queueName)` - Fetch single queue details
- Called from client components for real-time updates

**Polling Strategy:**
- Client components poll server actions every 1 second
- Manual refresh button for on-demand updates
- Display "last updated" timestamp
- Preserve UI state during background refreshes

### SQS Integration

**Endpoint:** ElasticMQ running on `localhost:9324`

**Client Configuration** (`lib/sqs-client.ts`):
```typescript
endpoint: http://localhost:9324
region: elasticmq
credentials: { accessKeyId: 'x', secretAccessKey: 'x' }
```

**Operations Used:**
- `ListQueuesCommand` - Get all queue URLs
- `GetQueueAttributesCommand` - Get queue attributes (AttributeNames: ['All'])

## Project Structure

```
ui/
├── app/
│   ├── layout.tsx              # Root layout with ThemeProvider
│   ├── page.tsx                # Queue list page
│   ├── globals.css             # Global styles + theme variables
│   └── queues/
│       └── [name]/
│           └── page.tsx        # Dynamic queue details page
├── components/
│   ├── queue-list.tsx          # Queue list with polling (client)
│   ├── queue-card.tsx          # Individual queue card display
│   ├── queue-details.tsx       # Queue details with polling (client)
│   ├── page-header.tsx         # Page header with theme switcher (client)
│   ├── theme-switcher.tsx      # Theme toggle component (client)
│   ├── loading-skeleton.tsx    # Loading state component
│   └── error-display.tsx       # Error state component
├── lib/
│   ├── types.ts                # TypeScript interfaces
│   ├── sqs-client.ts           # SQS client configuration
│   ├── actions.ts              # Server actions (data fetching)
│   ├── utils.ts                # Formatting utilities
│   └── theme-provider.tsx      # Theme context provider (client)
├── .env.local                  # Environment variables (gitignored)
└── CLAUDE.md                   # This file
```

## Conventions & Patterns

### File Naming
- Components: `kebab-case.tsx` (e.g., `queue-list.tsx`)
- Use `.tsx` for components, `.ts` for utilities/types
- Server actions: `actions.ts` in lib/
- Types: `types.ts` in lib/

### Import Aliases
- `@/` maps to root directory
- Example: `import { QueueList } from '@/components/queue-list'`

### Component Patterns

**Server Component Template:**
```typescript
// No 'use client' directive
export default function PageName() {
  return <ClientComponent />
}
```

**Client Component Template:**
```typescript
'use client'

import { useState, useEffect } from 'react';

export function ComponentName() {
  // hooks, state, effects
  return (/* JSX */)
}
```

### Styling Conventions

**Theme Variables:**
- Use CSS custom properties: `var(--foreground)`, `var(--card-bg)`, etc.
- Defined in `app/globals.css`
- Support light/dark/auto modes via `data-theme` attribute

**Tailwind Usage:**
```typescript
// Structure with Tailwind
className="flex gap-4 p-6 rounded-lg"

// Colors with CSS variables
style={{ backgroundColor: 'var(--card-bg)' }}
```

**Responsive Design:**
- Mobile-first approach
- Use Tailwind responsive prefixes: `md:`, `lg:`
- Test on mobile and desktop

### TypeScript

**Strict Mode:** Enabled in `tsconfig.json`

**Type Definitions:**
- All interfaces in `lib/types.ts`
- Export and reuse across components
- Avoid `any` - use proper types or `unknown`

**Example:**
```typescript
import { QueueData } from '@/lib/types';

interface Props {
  queue: QueueData;
}
```

### Error Handling

**Pattern:**
```typescript
try {
  const data = await serverAction();
  setData(data);
  setError(null);
} catch (err) {
  setError(err instanceof Error ? err.message : 'An error occurred');
}
```

**User-Facing Errors:**
- Display with `ErrorDisplay` component
- Provide retry functionality
- Include helpful context (e.g., "Make sure ElasticMQ is running")

## Development Workflow

### Environment Setup

**Prerequisites:**
- Node.js LTS (managed via nvm)
- ElasticMQ running on localhost:9324

**First Time Setup:**
```bash
# Use Node LTS
nvm use lts

# Install dependencies
npm install

# Ensure .env.local exists (already created)
```

### Development Commands

```bash
# Start dev server (http://localhost:3000)
npm run dev

# Build for production
npm run build

# Start production server
npm start

# Run linter
npm run lint
```

### Testing ElasticMQ

**Start ElasticMQ:**
```bash
docker run -p 9324:9324 -p 9325:9325 softwaremill/elasticmq
```

**Create Test Queues:**
```bash
aws --endpoint-url=http://localhost:9324 sqs create-queue --queue-name test-queue-1
aws --endpoint-url=http://localhost:9324 sqs create-queue --queue-name test-queue-2
aws --endpoint-url=http://localhost:9324 sqs create-queue --queue-name dlq-queue
```

**Send Test Messages:**
```bash
aws --endpoint-url=http://localhost:9324 sqs send-message \
  --queue-url http://localhost:9324/queue/test-queue-1 \
  --message-body "Test message"
```

## Features

### ✅ Implemented

- **Queue List View:** Display all queues with real-time statistics
- **Queue Details View:** Click queue to see full attributes table
- **Real-Time Updates:** Auto-refresh every 1 second via polling
- **Manual Refresh:** Refresh button with loading indicator
- **Theme Switching:** Light/Dark/Auto mode with persistence
- **Error Handling:** Friendly errors with retry functionality
- **Empty States:** Helpful messages when no queues exist
- **Loading States:** Skeleton loaders during data fetch
- **Responsive Design:** Works on mobile and desktop
- **Dark Mode:** Automatic via system preference or manual toggle
- **Send Message:** Full SQS SendMessage support with all options (delay, message attributes, system attributes, FIFO fields)

### 🚫 Out of Scope (Not Implemented)

- Receive/browse messages
- Purge queue action
- Create/delete queue UI
- Message browsing
- Graphs or historical metrics
- Authentication/authorization
- Multi-region support

## Theme System

### How It Works

1. **ThemeProvider** (`lib/theme-provider.tsx`):
   - Wraps app in `app/layout.tsx`
   - Manages theme state: `'light' | 'dark' | 'auto'`
   - Persists to localStorage
   - Listens to system preference in auto mode

2. **CSS Variables** (`app/globals.css`):
   - Light theme (default)
   - Dark theme via `data-theme="dark"` or media query
   - All colors defined as CSS custom properties

3. **ThemeSwitcher Component**:
   - Three buttons: ☀️ Light, 🌙 Dark, 💻 Auto
   - Available on all pages
   - Immediate visual feedback

## Common Tasks

### Adding a New Component

```typescript
// 1. Create component file
// components/my-component.tsx
'use client' // Only if needed

export function MyComponent() {
  return <div>Content</div>
}

// 2. Import and use
import { MyComponent } from '@/components/my-component'
```

### Adding a Server Action

```typescript
// lib/actions.ts
'use server'

export async function myAction(param: string) {
  try {
    // AWS SDK calls or other server-side logic
    return data;
  } catch (error) {
    throw new Error('User-friendly error message');
  }
}
```

### Adding New Queue Attributes

```typescript
// 1. Update types if needed (lib/types.ts)
// 2. Format in lib/utils.ts if special formatting required
// 3. Component will automatically display new attributes
```

## Known Limitations

- **Polling Performance:** 1-second polling may be aggressive for many queues (consider increasing interval)
- **No Pagination:** Assumes reasonable number of queues (<100)
- **No Message Actions:** Read-only queue monitoring
- **Local Only:** Assumes ElasticMQ on localhost (no remote endpoints)

## Environment Variables

**File:** `.env.local` (gitignored)

```bash
# SQS endpoint (browser-accessible)
NEXT_PUBLIC_SQS_ENDPOINT=http://localhost:9324

# AWS configuration (server-only)
AWS_REGION=elasticmq
AWS_ACCESS_KEY_ID=x
AWS_SECRET_ACCESS_KEY=x
```

**Note:** `NEXT_PUBLIC_*` variables are exposed to the browser.

## Troubleshooting

### "Failed to fetch queues" Error
- Ensure ElasticMQ is running: `docker ps`
- Check endpoint: `curl http://localhost:9324`
- Verify `.env.local` configuration

### Theme Not Persisting
- Check browser localStorage: `localStorage.getItem('theme')`
- Clear cache and reload
- Ensure ThemeProvider wraps app

### TypeScript Errors
- Run `npm run build` to see all errors
- Check imports use `@/` alias correctly
- Verify all types are exported from `lib/types.ts`

### npm Command Issues
- Ensure using Node LTS: `nvm use lts`
- Clear node_modules and reinstall: `rm -rf node_modules && npm install`

## Performance Considerations

- **Polling Interval:** Currently 1 second - adjust in components if needed
- **Attribute Fetching:** Gets all attributes per queue - consider caching
- **Client-Side Rendering:** Most components are client-side due to polling

## Future Enhancements (Ideas)

- WebSocket connection for real-time updates (remove polling)
- Queue creation/deletion UI
- Message browser (paginated list)
- Send/receive message functionality
- Queue metrics visualization (charts)
- Filter/search queues
- Export queue configuration
- Batch operations
- Multiple ElasticMQ instances

## Contributing

When working on this project:

1. **Test incrementally** - Don't build everything before testing
2. **Follow existing patterns** - Match the architecture described above
3. **Update this file** - Keep CLAUDE.md in sync with changes
4. **Use TypeScript strictly** - No `any` types
5. **Maintain responsiveness** - Test on mobile
6. **Preserve theme support** - Use CSS variables for colors

## Resources

- [Next.js App Router Docs](https://nextjs.org/docs/app)
- [Tailwind CSS v4 Docs](https://tailwindcss.com/docs)
- [AWS SDK for JavaScript v3](https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/)
- [ElasticMQ Documentation](https://github.com/softwaremill/elasticmq)

---

**Last Updated:** 2026-03-01
**Version:** 1.0.0
**Maintainer:** Generated with Claude Code
