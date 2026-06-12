package com.danhaywood.aregress;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;
import picocli.spring.PicocliSpringFactory;

/**
 * Spring Boot bootstrap for the aregress CLI (no web server).
 *
 * Runs the Picocli {@link ReplayCommand} within the Spring context — using {@link PicocliSpringFactory}
 * so the command (and its {@link AregressProperties}) are Spring-managed — and propagates Picocli's
 * exit code (0/1/2) to the shell via {@link ExitCodeGenerator} + {@link SpringApplication#exit}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AregressProperties.class)
public class AregressApplication implements CommandLineRunner, ExitCodeGenerator {

    private final ReplayCommand command;
    private final CommandLine.IFactory factory;
    private int exitCode;

    public AregressApplication(ReplayCommand command, ApplicationContext context) {
        this.command = command;
        this.factory = new PicocliSpringFactory(context);
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(command, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AregressApplication.class, args)));
    }
}
