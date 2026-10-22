package com.actmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class ActMcpApplication {

    public static void main(String[] args) throws IOException {
        // SQLite 不会自动创建父目录，启动前确保数据目录存在
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(ActMcpApplication.class, args);
    }
}
