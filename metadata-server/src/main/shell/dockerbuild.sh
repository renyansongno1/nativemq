mvn clean package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 && docker build -f src/main/docker/Dockerfile.native -t nativemq/metadata-server:1.0.0-SNAPSHOT .