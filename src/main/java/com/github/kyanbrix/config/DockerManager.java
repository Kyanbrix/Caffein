package com.github.kyanbrix.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class DockerManager {


    private static final Logger log = LoggerFactory.getLogger(DockerManager.class);
    private final DockerClient dockerClient;
    private final String containerName = "caffein";
    public DockerManager() {

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();


        DockerHttpClient client = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(30))
                .build();


        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(client)
                .build();



    }

    public void stopContainer() {

        log.info("Docker Stopping.......");
        this.dockerClient.stopContainerCmd(containerName);

    }


    public void restartContainer() {
        this.dockerClient.restartContainerCmd(containerName);
    }



}
