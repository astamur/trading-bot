package dev.astamur.trading;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;

@Testcontainers
public class AppTest {
    @Container
    public GenericContainer buxServer = new GenericContainer(
            new ImageFromDockerfile()
                    .withFileFromPath("bux-server.jar", Paths.get("docker/bux-server/bux-server.jar"))
                    .withFileFromPath("Dockerfile", Paths.get("docker/bux-server/Dockerfile")))
            .withExposedPorts(8080);

    @Test
    public void test() {
        System.out.println(buxServer.getMappedPort(8080));
    }
}