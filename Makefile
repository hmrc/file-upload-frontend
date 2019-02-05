SERVICE_NAME = file-upload-frontend
SERVICE_VERSION = 1.63.0-SNAPSHOT

build:
	sbt clean test docker:publishLocal

.dockerize:
	cd target/docker/stage && \
	docker build -t $(SERVICE_NAME) .

run:	.dockerize
	docker run -d -p 9000:9000 $(SERVICE_NAME):latest
