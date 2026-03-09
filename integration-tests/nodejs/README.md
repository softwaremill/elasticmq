# ElasticMQ Integration Tests - Node.js

This directory contains integration tests for ElasticMQ using the AWS SDK for JavaScript v3.

## Prerequisites

- Node.js 18+ (for ES modules support)
- Docker (for testcontainers)
- ElasticMQ Docker image built with tag `elasticmq-int:latest`

## Quick Start

### Option 1: Using Local Server (Fastest)

1. Start ElasticMQ locally:
   ```bash
   docker run -p 9324:9324 softwaremill/elasticmq:latest
   ```

2. Install dependencies and run tests in local server mode:
   ```bash
   npm install
   npm run test:local
   ```

### Option 2: Using Docker Containers (Full Integration)

1. Ensure Docker is running and pull the image:
   ```bash
   docker pull softwaremill/elasticmq:latest
   # Or use the native image
   docker pull softwaremill/elasticmq-native:latest
   ```

2. Install dependencies and run tests:
   ```bash
   npm install
   npm test                  # Uses softwaremill/elasticmq:latest
   npm run test:native       # Uses softwaremill/elasticmq-native:latest
   ```

## Setup

Install dependencies:

```bash
npm install
```

## Running Tests

Run all tests (Docker container mode):

```bash
npm test
```

Run all tests using native image:

```bash
npm run test:native
```

Run all tests using local server:

```bash
npm run test:local
```

Run tests in watch mode:

```bash
npm run test:watch
```

Run tests with coverage:

```bash
npm run test:coverage
```

Run tests in verbose mode:

```bash
npm run test:verbose                # Container mode (default image)
npm run test:native:verbose         # Container mode (native image)
npm run test:local:verbose          # Local server mode
```

### Docker Images

The tests support different ElasticMQ Docker images:

- **`softwaremill/elasticmq:latest`** (default) - JVM-based ElasticMQ
- **`softwaremill/elasticmq-native:latest`** - GraalVM native image (faster startup)

You can specify the image in three ways:

1. **Using npm scripts** (recommended):
   ```bash
   npm test                # Uses default image
   npm run test:native     # Uses native image
   ```

2. **Using environment variable**:
   ```bash
   ELASTICMQ_IMAGE=softwaremill/elasticmq-native:latest npm test
   ELASTICMQ_IMAGE=custom/elasticmq:tag npm test
   ```

3. **In test code** (for specific test files):
   ```javascript
   const container = new ElasticMqContainer('messages-storage', {
     image: 'softwaremill/elasticmq-native:latest'
   });
   ```

## Local Server Mode

For faster development iteration, you can run tests against a locally running ElasticMQ server instead of starting Docker containers. This is useful when:

- You want faster test execution
- Docker is not available or has issues
- You're debugging the ElasticMQ server itself
- You want to inspect the server state between test runs

### Starting a Local Server

Start ElasticMQ locally on port 9324:

```bash
# Using Docker
docker run -p 9324:9324 softwaremill/elasticmq:latest

# Or with custom config
docker run -p 9324:9324 -v $(pwd)/../conf/messages-storage.conf:/opt/elasticmq.conf softwaremill/elasticmq:latest

# Or using the standalone JAR
java -jar elasticmq-server.jar
```

### Switching to Local Server Mode

Tests automatically detect local server mode via the `USE_LOCAL_SERVER` environment variable:

```bash
# Run tests against local server
npm run test:local

# Or set the environment variable manually
USE_LOCAL_SERVER=true npm test

# Specify custom endpoint (default is http://localhost:9324)
USE_LOCAL_SERVER=true LOCAL_ENDPOINT=http://localhost:9999 npm test
```

### How It Works

The `ElasticMqContainer` class automatically detects local server mode from environment variables:

```javascript
// In test/basic.test.js
import { ElasticMqContainer } from '../src/elasticmq-container.js';

// Simply create the container - it automatically detects the mode
const container = new ElasticMqContainer('messages-storage');
await container.start();

// When USE_LOCAL_SERVER=true is set, it skips container startup
// When not set, it starts a Docker container
```

The container checks environment variables in this priority:
1. `options` parameter (if provided in code)
2. Environment variables (`USE_LOCAL_SERVER`, `LOCAL_ENDPOINT`)
3. Default values

**Note:** When using local server mode:
- The `start()` method will skip container initialization
- The `stop()` method will not stop your local server
- You're responsible for starting/stopping the server manually
- The `.data` directory is still created but won't be mounted to the server

### Running Individual Tests

Run a specific test file:

```bash
npm test integration.test.js
```

Run a specific test suite (describe block):

```bash
npm test -- -t "Message Storage"
```

Run a specific test case:

```bash
npm test -- -t "should persist messages after restart"
```

Run tests matching a pattern:

```bash
npm test -- --testNamePattern="Queue Storage"
```

Run only tests in a specific file matching a pattern:

```bash
npm test integration.test.js -- -t "Dead Letter"
```

### Useful Jest Options

- `--verbose` - Show individual test results
- `--silent` - Suppress console output
- `--runInBand` - Run tests serially (useful for debugging)
- `--detectOpenHandles` - Detect open handles preventing Jest from exiting

Example:

```bash
npm test -- --runInBand --verbose integration.test.js
```

## Test Structure

### ElasticMqContainer

The `ElasticMqContainer` class provides a wrapper around testcontainers for managing ElasticMQ Docker containers in tests. It handles:

- Container lifecycle (start/stop)
- Volume mounting for persistence testing
- Port mapping
- SQS client creation
- Local server mode (bypasses Docker)

#### Constructor Options

```javascript
new ElasticMqContainer(confBaseName, options)
```

**Parameters:**
- `confBaseName` (string) - Configuration file base name without .conf extension (e.g., 'messages-storage')
- `options` (object, optional):
  - `image` (string) - Docker image to use. Default: `process.env.ELASTICMQ_IMAGE || 'softwaremill/elasticmq:latest'`
  - `useLocalServer` (boolean) - Use local server instead of Docker. Default: `false`
  - `localEndpoint` (string) - Local server endpoint URL. Default: `'http://localhost:9324'`

**Environment Variables:**
- `ELASTICMQ_IMAGE` - Override the default Docker image (e.g., `softwaremill/elasticmq-native:latest`)
- `USE_LOCAL_SERVER` - Set to `'true'` to use local server instead of container
- `LOCAL_ENDPOINT` - Custom endpoint for local server mode

**Examples:**

```javascript
import { ElasticMqContainer } from '../src/elasticmq-container.js';

// Simple usage - automatically detects mode from environment variables
const container = new ElasticMqContainer('messages-storage');

// Override image (ignores ELASTICMQ_IMAGE env var)
const container = new ElasticMqContainer('messages-storage', {
  image: 'elasticmq-int:latest'
});

// Force local server mode (ignores USE_LOCAL_SERVER env var)
const container = new ElasticMqContainer('messages-storage', {
  useLocalServer: true,
  localEndpoint: 'http://localhost:9324'
});
```

### Project Structure

```
integration-tests/nodejs/
├── src/
│   └── elasticmq-container.js    # Container wrapper class
├── test/
│   └── basic.test.js              # Integration tests
├── jest.config.js                 # Jest configuration
├── package.json                   # Dependencies and scripts
└── README.md                      # Documentation
```

### Test Files

**test/basic.test.js** - Basic ElasticMQ operations
- Creating queues
- Sending and receiving messages
- Listing queues
- Message attributes
- Error handling (non-existent queues)
- Automatically switches between container and local server mode based on `USE_LOCAL_SERVER` environment variable

## Configuration Files

Tests use configuration files from `../conf/`:

- `messages-storage.conf` - Configuration for message persistence testing
- `queue-storage.conf` - Configuration for queue metadata persistence testing

## Data Directory

The `.data` directory is created during test runs and contains:

- Persisted queue data
- Message storage files
- Lock files

This directory is automatically cleaned before each test run.

## Debugging

The framework includes comprehensive logging at multiple levels:

### Container Logging

The `ElasticMqContainer` class logs:
- Container initialization (image, config, paths)
- Path validation
- Volume mounting details
- Container startup progress
- Port mapping
- Container logs on stop or failure
- Error details with stack traces

### Verbose Test Execution

Run tests with maximum verbosity:

```bash
npm run test:verbose
```

This enables:
- Jest verbose mode (shows all test results)
- Sequential test execution (easier to follow)
- Testcontainers debug logging (shows Docker operations)

### Manual Debug Mode

For even more control:

```bash
DEBUG=testcontainers* npm test -- --verbose --runInBand
```

### Environment Variables

Useful environment variables for debugging:
- `DEBUG=testcontainers*` - Show testcontainers debug output
- `TESTCONTAINERS_RYUK_DISABLED=true` - Disable resource reaper (for debugging)
- `TESTCONTAINERS_HOST_OVERRIDE=localhost` - Override Docker host

### Container Logs

Container logs are automatically displayed:
- When containers stop normally
- When container startup fails
- In the test output during test execution

### Common Issues

**Container not starting:**
1. Verify Docker is running: `docker ps`
2. Check if image exists: `docker images | grep elasticmq`
3. Pull the image if needed: `docker pull softwaremill/elasticmq:latest`
4. Run tests in verbose mode: `npm run test:verbose`

**Permission errors:**
- On Linux, ensure your user is in the `docker` group
- Check bind mount permissions for `.data` directory

**Port conflicts:**
- Check if port 9324 is already in use
- Stop other ElasticMQ instances

## Architecture

The tests follow a similar pattern to the Python integration tests, using:

- **testcontainers** for Docker container management
- **Jest** as the test runner
- **@aws-sdk/client-sqs** for SQS operations
- **ES modules** for modern JavaScript syntax
