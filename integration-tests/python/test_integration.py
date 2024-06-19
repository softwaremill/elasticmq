import pytest
import boto3
import logging
import os
import signal
import time
import shutil
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

class ElasticMqContainer(DockerContainer):
    def __init__(self, conf_base_name, image="elasticmq-int:latest", **kwargs):
        super(ElasticMqContainer, self).__init__(image)

        data_dir = os.path.join(os.getcwd(), ".data")
        if os.path.exists(data_dir):
            shutil.rmtree(data_dir)
        os.makedirs(data_dir)
        time.sleep(1) # some weird bug with Docker Desktop/MacOS makes the directory not available immediately

        self.with_volume_mapping(os.path.join(os.getcwd(), "../..", "native-server/src/main/resources/META-INF/native-image"), "/elasticmq/native-image", mode="rw")
        self.with_volume_mapping(os.path.join(os.getcwd(), ".data"), "/elasticmq/data", mode="rw")
        self.with_volume_mapping(os.path.join(os.getcwd(), f"../conf/{conf_base_name}.conf"), "/elasticmq/conf/elasticmq.conf")
        self.with_exposed_ports(9321)

    def start(self):
        lock_file = os.path.join(os.getcwd(), "../..", "native-server/src/main/resources/META-INF/native-image/.lock")
        if os.path.exists(lock_file):
            os.remove(lock_file)
        super().start()
        wait_for_logs(self, "=== ElasticMQ server", timeout=10)

    def stop(self):
        logging.getLogger().info("Stopping ElasticMQ container")
        self._container.kill(signal=signal.SIGINT)
        self.get_wrapped_container().wait(timeout=10)
        logging.getLogger().info("ElasticMQ container stopped")

        stdout, stderr = self.get_logs()
        logging.getLogger().info('======= STDOUT =======')
        logging.getLogger().info(stdout.decode())
        logging.getLogger().info('======= STDERR =======')
        logging.getLogger().info(stderr.decode())

        super().stop()

    def get_elasticmq_port(self):
        return self.get_exposed_port(9321)

    def create_sqs_client(self):
        port = self.get_elasticmq_port()
        return boto3.resource('sqs', region_name='radom', endpoint_url=f'http://localhost:{port}', aws_access_key_id='test', aws_secret_access_key='test')


@pytest.fixture(scope="function")
def message_storage_container():
    container = ElasticMqContainer('messages-storage')
    container.start()
    yield container
    container.stop()

@pytest.fixture(scope="function")
def queue_storage_container():
    container = ElasticMqContainer('queue-storage')
    container.start()
    yield container
    container.stop()

def test_messages_storage(message_storage_container):
    sqs = message_storage_container.create_sqs_client()
    queue = sqs.create_queue(QueueName='simpleQueue', Attributes={'VisibilityTimeout': '1'})
    assert queue is not None
    queue.send_message(MessageBody='Hello 1')
    queue.send_message(MessageBody='Hello 2')
    queue.send_message(MessageBody='Hello 3')
    messages = queue.receive_messages(MaxNumberOfMessages=10)
    print(messages)
    assert len(messages) == 3
    for message in messages:
        if message.body == 'Hello 2':
            print("Deleting message")
            message.delete()

    message_storage_container.stop()

    message_storage_container.start()

    sqs = message_storage_container.create_sqs_client()
    queue = sqs.get_queue_by_name(QueueName='simpleQueue')
    assert queue is not None
    queue.send_message(MessageBody='Hello 4')
    queue.send_message(MessageBody='Hello 5')
    messages = queue.receive_messages(MaxNumberOfMessages=10)
    print(messages)
    print(set([message.body for message in messages]))
    assert len(messages) == 4
    assert set([message.body for message in messages]) == {'Hello 1', 'Hello 3', 'Hello 4', 'Hello 5'}
    assert not os.path.exists(os.path.join(os.getcwd(), ".data", "queues.conf"))

def test_queue_storage(queue_storage_container):
    sqs = queue_storage_container.create_sqs_client()
    queue = sqs.create_queue(QueueName='simpleQueue', Attributes={'VisibilityTimeout': '1'})
    assert queue is not None
    queue.send_message(MessageBody='Hello 1')
    queue.send_message(MessageBody='Hello 2')
    queue.send_message(MessageBody='Hello 3')
    messages = queue.receive_messages(MaxNumberOfMessages=10)
    print(messages)
    assert len(messages) == 3
    for message in messages:
        if message.body == 'Hello 2':
            print("Deleting message")
            message.delete()

    queue_storage_container.stop()
    assert os.path.exists(os.path.join(os.getcwd(), ".data", "queues.conf"))

    queue_storage_container.start()

    sqs = queue_storage_container.create_sqs_client()
    queue = sqs.get_queue_by_name(QueueName='simpleQueue')
    assert queue is not None
    queue.send_message(MessageBody='Hello 4')
    queue.send_message(MessageBody='Hello 5')
    messages = queue.receive_messages(MaxNumberOfMessages=10)
    print(messages)
    print(set([message.body for message in messages]))
    assert len(messages) == 2
    assert set([message.body for message in messages]) == {'Hello 4', 'Hello 5'}
    assert os.path.exists(os.path.join(os.getcwd(), ".data", "queues.conf"))
