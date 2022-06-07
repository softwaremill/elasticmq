# ElasticMQ Connect to SQL Database

Persisting queue data using PostgreSQL.

## Start Database
```sh
cd database && docker-compose up
```

## Config Examples

Create queue when ElasticMQ starts:
```sh
queues {
  queue1 {
    defaultVisibilityTimeout = 10 seconds
    delay = 5 seconds
    receiveMessageWait = 0 seconds
    deadLettersQueue {
      name = "queue1-dead-letters"
      maxReceiveCount = 3 // from 1 to 1000
    }
    fifo = false
    contentBasedDeduplication = false
    copyTo = "audit-queue-name"
    moveTo = "redirect-queue-name"
    tags {
      tag1 = "tagged1"
      tag2 = "tagged2"
    }
  }
  queue1-dead-letters { }
  audit-queue-name { }
  redirect-queue-name { }
}
```

Use PostgreSQL to persist data:
```
messages-storage {
  enabled = true
  uri = "jdbc:postgresql://localhost:15432/queuedb"
  driver-class = "org.postgresql.Driver"
  username = "root"
  password = "admin"
}
```

## Build and Run

You need `sbt` to build.

If you are using Mac:
```sh
brew install sbt
```

To build and run with debug (this will listen for a remote debugger on port 5005):
```sh
sbt -jvm-debug 5005
> project server
> run
```

To build a jar-with-dependencies:
```
sbt
> project server
> assembly
```

## Test with AWS-CLI
```sh
export AWS_ACCESS_KEY_ID=x
export AWS_SECRET_ACCESS_KEY=x
export AWS_DEFAULT_REGION=us-east-1

alias awslocal="aws --endpoint-url=http://localhost:9324"

# list queue
awslocal sqs list-queues

# create queue
awslocal sqs create-queue --queue-name myqueue

# get a queue's url
awslocal sqs get-queue-url --queue-name myqueue

export QUEUE_URL=http://localhost:9324/000000000000/myqueue

# send message
awslocal sqs send-message --queue-url $QUEUE_URL --message-body hi

# get message
awslocal sqs receive-message --queue-url $QUEUE_URL
```