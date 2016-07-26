package com.jose.githubtemplate;

import com.beust.jcommander.JCommander;
import com.jose.githubtemplate.models.JCommanderProperties;
import com.jose.githubtemplate.models.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.trimou.Mustache;
import org.trimou.engine.MustacheEngineBuilder;
import org.trimou.util.ImmutableMap;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringJoiner;

/**
 * Assumming REST endpoint returns JSON in the following format:
 * {
 * "servers": [
 * {
 * "host": "node1",
 * "port": "8500"
 * },
 * {
 * "host": "node2",
 * "port": "8500"
 * },
 * {
 * "host": "node3",
 * "port": "8500"
 * }
 * ]
 * }
 *
 * Exit codes
 * 0 = changed nothing
 * 1 = there was a user error, missed providing some arg
 * 2 = changed the target file
 */

@SpringBootApplication
public class App implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final String FILENAME_WITHOUT_EXTENSION = "\\.(?=[^\\.]+$)";
    private static final int ERROR_EXIT_CODE = 1;
    private static final int CONTENTS_CHANGED_EXIT_CODE = 2;
    private static final int NOTHING_CHANGED_EXIT_CODE = 0;
    private static final String TEMPLATE_ALIAS = "template";

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        JCommanderProperties props = new JCommanderProperties();
        new JCommander(props, args);

        if ("".equals(props.getSource()) && props.getSource() != null) {
            log.error("There's nothing to do, so exit code 1");
            System.exit(ERROR_EXIT_CODE);
        }

        RestTemplate restTemplate = new RestTemplate();
        ServerConfig[] servers = new ServerConfig[0];
        try {
            servers = restTemplate.getForObject(props.getSource(), ServerConfig[].class);
        } catch (RestClientException e) {
            log.error(String.format("There was a problem connecting to source %s. %s", props.getSource(), e.getCause().toString()));
            System.exit(ERROR_EXIT_CODE);
        }

        StringJoiner sj = new StringJoiner(",");

        for (ServerConfig server : servers) {
            sj.add(String.format("%s:%s", server.getHost(), server.getPort()));
        }

        Mustache mustache;
        StringWriter writer = new StringWriter();
        String destinationFilename;
        String templateContents;
        String destinationContents;
        boolean changedFile = false;

        // We are going to work with each file passed in
        for (String arg : props.getFiles()) {
            Path template = Paths.get(arg);
            if (Files.isRegularFile(template) & Files.isReadable(template)) {
                templateContents = new String(Files.readAllBytes(Paths.get(arg)));

                mustache = MustacheEngineBuilder
                        .newBuilder()
                        .build()
                        .compileMustache(TEMPLATE_ALIAS, templateContents);
                // TODO Remove hardcoded reference to servers and see about reading the tokens already in the template file
                mustache.render(writer, ImmutableMap.<String, Object>of("servers", sj.toString()));

                destinationFilename = arg.split(FILENAME_WITHOUT_EXTENSION)[0];

                // Check to see if a destination file already exists and compare its contents
                Path checkDestinationFile = Paths.get(destinationFilename);
                if (Files.isRegularFile(checkDestinationFile) & Files.isReadable(checkDestinationFile)) {
                    destinationContents = new String(Files.readAllBytes(Paths.get(destinationFilename)));

                    // Only create destination file is generated contents is not that same as what already exists.
                    if (!destinationContents.equals(writer.toString())) {
                        Path path = Paths.get(destinationFilename);
                        try (BufferedWriter writerOut = Files.newBufferedWriter(path)) {
                            writerOut.write(writer.toString());
                        }
                        log.info(String.format("Overwriting %s since contents have changed.", destinationFilename));
                    } else {
                        log.info(String.format("Not re-creating %s file since contents have not changed.", destinationFilename));
                    }
                }
            }
        }

        if (changedFile) {
            System.exit(CONTENTS_CHANGED_EXIT_CODE);
        } else {
            System.exit(NOTHING_CHANGED_EXIT_CODE);
        }
    }
}
