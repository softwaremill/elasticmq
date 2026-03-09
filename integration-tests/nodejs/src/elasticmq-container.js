import { GenericContainer, Wait } from 'testcontainers';
import { SQSClient } from '@aws-sdk/client-sqs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { existsSync, mkdirSync, rmSync, unlinkSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export class ElasticMqContainer {
  /**
   * Create an ElasticMQ container wrapper
   * @param {string} confBaseName - Configuration file base name (without .conf extension)
   * @param {object} options - Configuration options
   * @param {string} [options.image] - Docker image to use. Defaults to process.env.ELASTICMQ_IMAGE or 'softwaremill/elasticmq:latest'
   * @param {boolean} [options.useLocalServer=false] - Use local server instead of container
   * @param {string} [options.localEndpoint='http://localhost:9324'] - Local server endpoint
   *
   * Environment variables:
   * - ELASTICMQ_IMAGE: Override default Docker image
   * - USE_LOCAL_SERVER: Set to 'true' to use local server
   * - LOCAL_ENDPOINT: Custom endpoint for local server
   */
  constructor(confBaseName, options = {}) {
    console.log('\n=== ElasticMqContainer Constructor ===');
    console.log(`Configuration: ${confBaseName}`);

    this.confBaseName = confBaseName;

    // Priority for image: options.image > ELASTICMQ_IMAGE env var > default
    this.image = options.image || process.env.ELASTICMQ_IMAGE || 'softwaremill/elasticmq:latest';

    // Priority for local server mode: options.useLocalServer > USE_LOCAL_SERVER env var > false
    this.useLocalServer = options.useLocalServer ?? (process.env.USE_LOCAL_SERVER === 'true');

    // Priority for local endpoint: options.localEndpoint > LOCAL_ENDPOINT env var > default
    this.localEndpoint = options.localEndpoint || process.env.LOCAL_ENDPOINT || 'http://localhost:9324';

    this.container = null;
    this.dataDir = join(__dirname, '.data');

    if (this.useLocalServer) {
      console.log('Mode: LOCAL SERVER');
      console.log(`Local endpoint: ${this.localEndpoint}`);
    } else {
      console.log('Mode: DOCKER CONTAINER');
      console.log(`Image: ${this.image}`);
    }

    console.log(`Data directory: ${this.dataDir}`);

    // Clean and recreate data directory
    if (existsSync(this.dataDir)) {
      console.log('Removing existing data directory...');
      rmSync(this.dataDir, { recursive: true, force: true });
    }
    console.log('Creating data directory...');
    mkdirSync(this.dataDir, { recursive: true });
    console.log('Data directory ready');
  }

  async start() {
    if (this.useLocalServer) {
      console.log('\n=== Using Local ElasticMQ Server ===');
      console.log(`Endpoint: ${this.localEndpoint}`);
      console.log('⚠ Skipping container startup - using existing server');
      console.log('⚠ Make sure your local ElasticMQ server is running!');

      // Extract port from endpoint for compatibility
      const url = new URL(this.localEndpoint);
      this.localPort = parseInt(url.port) || 9324;
      console.log(`Port: ${this.localPort}`);

      return null;
    }

    console.log('\n=== Starting ElasticMQ Container ===');

    // Validate and log paths
    const confPath = join(__dirname, '..', '..', 'conf', `${this.confBaseName}.conf`);
    console.log(`Configuration file path: ${confPath}`);

    if (!existsSync(confPath)) {
      const error = `Configuration file not found: ${confPath}`;
      console.error(`ERROR: ${error}`);
      throw new Error(error);
    }
    console.log('✓ Configuration file exists');

    if (!existsSync(this.dataDir)) {
      const error = `Data directory not found: ${this.dataDir}`;
      console.error(`ERROR: ${error}`);
      throw new Error(error);
    }
    console.log('✓ Data directory exists');

    console.log('\nMounting volumes:');
    console.log(`  - ${this.dataDir} -> /elasticmq/data (rw)`);
    console.log(`  - ${confPath} -> /elasticmq/conf/elasticmq.conf (ro)`);

    console.log(`\nPulling/using image: ${this.image}`);
    console.log('Exposed ports: 9324');
    console.log('Wait strategy: Looking for "=== ElasticMQ server" in logs');

    try {
      console.log('\nStarting container...');
      this.container = await new GenericContainer(this.image)
        .withBindMounts([
          { source: this.dataDir, target: '/elasticmq/data', mode: 'rw' },
          { source: confPath, target: '/elasticmq/conf/elasticmq.conf', mode: 'ro' }
        ])
        .withExposedPorts(9324)
        .withWaitStrategy(Wait.forLogMessage('=== ElasticMQ server'))
        .start();

      const mappedPort = this.container.getMappedPort(9324);
      console.log(`✓ Container started successfully!`);
      console.log(`  Container ID: ${this.container.getId()}`);
      console.log(`  Mapped port: 9324 -> ${mappedPort}`);
      console.log(`  Endpoint: http://localhost:${mappedPort}`);

      return this.container;
    } catch (error) {
      console.error('\n✗ Container failed to start!');
      console.error('Error details:', error.message);

      if (error.stack) {
        console.error('\nStack trace:');
        console.error(error.stack);
      }

      // Try to get logs if container was partially started
      if (this.container) {
        try {
          console.log('\nAttempting to retrieve container logs...');
          const logs = await this.container.logs();
          console.log('======= CONTAINER LOGS =======');
          console.log(logs.toString());
          console.log('==============================');
        } catch (logError) {
          console.error('Could not retrieve container logs:', logError.message);
        }
      }

      throw error;
    }
  }

  async stop() {
    if (this.useLocalServer) {
      console.log('\n=== Using Local Server - No Container to Stop ===');
      console.log('⚠ Remember to manually stop your local ElasticMQ server if needed');
      return;
    }

    if (this.container) {
      console.log('\n=== Stopping ElasticMQ Container ===');
      console.log(`Container ID: ${this.container.getId()}`);

      try {
        // Get logs before stopping
        console.log('Retrieving container logs...');
        const logs = await this.container.logs();
        console.log('\n======= CONTAINER LOGS =======');
        console.log(logs.toString());
        console.log('==============================\n');

        console.log('Stopping container...');
        await this.container.stop();
        console.log('✓ ElasticMQ container stopped successfully');
      } catch (error) {
        console.error('✗ Error stopping container:', error.message);
        throw error;
      }
    } else {
      console.log('No container to stop (container not initialized)');
    }
  }

  getElasticMqPort() {
    if (this.useLocalServer) {
      console.log(`Using local server port: ${this.localPort}`);
      return this.localPort;
    }

    if (!this.container) {
      const error = 'Container not started - cannot get port';
      console.error(`ERROR: ${error}`);
      throw new Error(error);
    }
    const port = this.container.getMappedPort(9324);
    console.log(`Mapped port: ${port}`);
    return port;
  }

  createSqsClient() {
    console.log('\n=== Creating SQS Client ===');

    let endpoint;
    if (this.useLocalServer) {
      endpoint = this.localEndpoint;
      console.log(`Using local server endpoint: ${endpoint}`);
    } else {
      const port = this.getElasticMqPort();
      endpoint = `http://localhost:${port}`;
      console.log(`Using container endpoint: ${endpoint}`);
    }

    console.log('Region: elasticmq');
    console.log('Credentials: test/test');

    const client = new SQSClient({
      region: 'elasticmq',
      endpoint: endpoint,
      credentials: {
        accessKeyId: 'test',
        secretAccessKey: 'test'
      }
    });

    console.log('✓ SQS Client created');
    return client;
  }

  getDataDir() {
    return this.dataDir;
  }
}
