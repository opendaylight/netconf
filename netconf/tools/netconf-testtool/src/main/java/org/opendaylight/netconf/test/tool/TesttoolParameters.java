/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class TesttoolParameters {

    private static final String HOST_KEY = "{HOST}";
    private static final String PORT_KEY = "{PORT}";
    private static final String TCP_ONLY = "{TCP_ONLY}";
    private static final String ADDRESS_PORT = "{ADDRESS:PORT}";
    private static final String DEST =
        "http://{ADDRESS:PORT}/restconf/config/network-topology:network-topology/topology/topology-netconf/";
    private static final Pattern YANG_FILENAME_PATTERN = Pattern
        .compile("(?<name>.*)@(?<revision>\\d{4}-\\d{2}-\\d{2})\\.yang");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private static final String RESOURCE = "/config-template.json";
    @Arg(dest = "edit-content")
    public File editContent;
    @Arg(dest = "async")
    public boolean async;
    @Arg(dest = "thread-amount")
    public int threadAmount;
    @Arg(dest = "throttle")
    public int throttle;
    @Arg(dest = "auth")
    public ArrayList<String> auth;
    @Arg(dest = "controller-destination")
    public String controllerDestination;
    @Arg(dest = "schemas-dir")
    public File schemasDir;
    @Arg(dest = "devices-count")
    public int deviceCount;
    @Arg(dest = "devices-per-port")
    public int devicesPerPort;
    @Arg(dest = "starting-port")
    public int startingPort;
    @Arg(dest = "generate-config-connection-timeout")
    public int generateConfigsTimeout;
    @Arg(dest = "generate-config-address")
    public String generateConfigsAddress;
    @Arg(dest = "distro-folder")
    public File distroFolder;
    @Arg(dest = "generate-configs-batch-size")
    public int generateConfigBatchSize;
    @Arg(dest = "ssh")
    public boolean ssh;
    @Arg(dest = "exi")
    public boolean exi;
    @Arg(dest = "debug")
    public boolean debug;
    @Arg(dest = "notification-file")
    public File notificationFile;
    @Arg(dest = "md-sal")
    public boolean mdSal;
    @Arg(dest = "initial-config-xml-file")
    public File initialConfigXMLFile;
    @Arg(dest = "time-out")
    public long timeOut;
    private InputStream stream;
    @Arg(dest = "ip")
    public String ip;
    @Arg(dest = "thread-pool-size")
    public int threadPoolSize;
    @Arg(dest = "rpc-config")
    public File rpcConfig;

    @SuppressWarnings("checkstyle:lineLength")
    static ArgumentParser getParser() {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("netconf testtool");

        parser.description("netconf testtool");

        parser.addArgument("--edit-content")
                .type(String.class)
                .dest("edit-content");

        parser.addArgument("--async-requests")
                .type(Boolean.class)
                .setDefault(false)
                .dest("async");

        parser.addArgument("--thread-amount")
                .type(Integer.class)
                .setDefault(1)
                .dest("thread-amount")
                .help("The number of threads to use for configuring devices.");

        parser.addArgument("--throttle")
                .type(Integer.class)
                .setDefault(5000)
                .help("Maximum amount of async requests that can be open at a time, "
                        + "with mutltiple threads this gets divided among all threads")
                .dest("throttle");

        parser.addArgument("--auth")
                .nargs(2)
                .help("Username and password for HTTP basic authentication in order username password.")
                .dest("auth");

        parser.addArgument("--controller-destination")
                .type(String.class)
                .help("Ip address and port of controller. Must be in following format <ip>:<port> "
                        + "if available it will be used for spawning netconf connectors via topology configuration as "
                        + "a part of URI. Example (http://<controller destination>/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<node-id>)"
                        + "otherwise it will just start simulated devices and skip the execution of PUT requests")
                .dest("controller-destination");

        parser.addArgument("--device-count")
                .type(Integer.class)
                .setDefault(1)
                .help("Number of simulated netconf devices to spin. This is the number of actual ports open for the devices.")
                .dest("devices-count");

        parser.addArgument("--devices-per-port")
                .type(Integer.class)
                .setDefault(1)
                .help("Amount of config files generated per port to spoof more devices then are actually running")
                .dest("devices-per-port");

        parser.addArgument("--schemas-dir")
                .type(File.class)
                .help("Directory containing yang schemas to describe simulated devices. Some schemas e.g. netconf monitoring and inet types are included by default")
                .dest("schemas-dir");

        parser.addArgument("--notification-file")
                .type(File.class)
                .help("Xml file containing notifications that should be sent to clients after create subscription is called")
                .dest("notification-file");

        parser.addArgument("--initial-config-xml-file")
                .type(File.class)
                .help("Xml file containing initial simulatted configuration to be returned via get-config rpc")
                .dest("initial-config-xml-file");

        parser.addArgument("--starting-port")
                .type(Integer.class)
                .setDefault(17830)
                .help("First port for simulated device. Each other device will have previous+1 port number")
                .dest("starting-port");

        parser.addArgument("--generate-config-connection-timeout")
                .type(Integer.class)
                .setDefault((int) TimeUnit.MINUTES.toMillis(30))
                .help("Timeout to be generated in initial config files")
                .dest("generate-config-connection-timeout");

        parser.addArgument("--generate-config-address")
                .type(String.class)
                .setDefault("127.0.0.1")
                .help("Address to be placed in generated configs")
                .dest("generate-config-address");

        parser.addArgument("--generate-configs-batch-size")
                .type(Integer.class)
                .setDefault(1)
                .help("Number of connector configs per generated file")
                .dest("generate-configs-batch-size");

        parser.addArgument("--distribution-folder")
                .type(File.class)
                .help("Directory where the karaf distribution for controller is located")
                .dest("distro-folder");

        parser.addArgument("--ssh")
                .type(Boolean.class)
                .setDefault(true)
                .help("Whether to use ssh for transport or just pure tcp")
                .dest("ssh");

        parser.addArgument("--exi")
                .type(Boolean.class)
                .setDefault(true)
                .help("Whether to use exi to transport xml content")
                .dest("exi");

        parser.addArgument("--debug")
                .type(Boolean.class)
                .setDefault(false)
                .help("Whether to use debug log level instead of INFO")
                .dest("debug");

        parser.addArgument("--md-sal")
                .type(Boolean.class)
                .setDefault(false)
                .help("Whether to use md-sal datastore instead of default simulated datastore.")
                .dest("md-sal");

        parser.addArgument("--time-out")
                .type(long.class)
                .setDefault(20)
                .help("the maximum time in seconds for executing each PUT request")
                .dest("time-out");

        parser.addArgument("-ip")
                .type(String.class)
                .setDefault("0.0.0.0")
                .help("Ip address which will be used for creating a socket address."
                        + "It can either be a machine name, such as "
                        + "java.sun.com, or a textual representation of its IP address.")
                .dest("ip");

        parser.addArgument("--thread-pool-size")
                .type(Integer.class)
                .setDefault(8)
                .help("The number of threads to keep in the pool, when creating a device simulator. Even if they are idle.")
                .dest("thread-pool-size");
        parser.addArgument("--rpc-config")
                .type(File.class)
                .help("Rpc config file. It can be used to define custom rpc behavior, or override the default one."
                    + "Usable for testing buggy device behavior.")
                .dest("rpc-config");

        return parser;
    }

    public static TesttoolParameters parseArgs(final String[] args, final ArgumentParser parser) {
        final TesttoolParameters opt = new TesttoolParameters();
        try {
            parser.parseArgs(args, opt);
            return opt;
        } catch (final ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }

    private static String modifyMessage(final StringBuilder payloadBuilder, final int payloadPosition, final int size) {
        if (size == 1) {
            return payloadBuilder.toString();
        }

        if (payloadPosition == 0) {
            payloadBuilder.insert(payloadBuilder.toString().indexOf('{', 2), "[");
            payloadBuilder.replace(payloadBuilder.length() - 1, payloadBuilder.length(), ",");
        } else if (payloadPosition + 1 == size) {
            payloadBuilder.delete(0, payloadBuilder.toString().indexOf(':') + 1);
            payloadBuilder.insert(payloadBuilder.toString().indexOf('}', 2) + 1, "]");
        } else {
            payloadBuilder.delete(0, payloadBuilder.toString().indexOf(':') + 1);
            payloadBuilder.replace(payloadBuilder.length() - 2, payloadBuilder.length() - 1, ",");
            payloadBuilder.deleteCharAt(payloadBuilder.toString().lastIndexOf('}'));
        }
        return payloadBuilder.toString();
    }

    @SuppressWarnings("checkstyle:regexpSinglelineJava")
    void validate() {
        if (editContent == null) {
            stream = TesttoolParameters.class.getResourceAsStream(RESOURCE);
        } else {
            Preconditions.checkArgument(!editContent.isDirectory(), "Edit content file is a dir");
            Preconditions.checkArgument(editContent.canRead(), "Edit content file is unreadable");
        }

        if (controllerDestination != null) {
            Preconditions.checkArgument(controllerDestination.contains(":"),
                "Controller Destination needs to be in a following format <ip>:<port>");
            final String[] parts = controllerDestination.split(Pattern.quote(":"));
            Preconditions.checkArgument(Integer.parseInt(parts[1]) > 0, "Port =< 0");
        }

        checkArgument(deviceCount > 0, "Device count has to be > 0");
        checkArgument(startingPort > 1023, "Starting port has to be > 1023");
        checkArgument(devicesPerPort > 0, "Atleast one device per port needed");

        if (schemasDir != null) {
            checkArgument(schemasDir.exists(), "Schemas dir has to exist");
            checkArgument(schemasDir.isDirectory(), "Schemas dir has to be a directory");
            checkArgument(schemasDir.canRead(), "Schemas dir has to be readable");

            final List<File> files = Arrays.asList(schemasDir.listFiles());
            for (final File file : files) {
                final Matcher matcher = YANG_FILENAME_PATTERN.matcher(file.getName());
                if (!matcher.matches()) {
                    final BufferedReader reader;
                    try {
                        reader = new BufferedReader(new FileReader(file));
                        String line = reader.readLine();
                        while (!DATE_PATTERN.matcher(line).find()) {
                            line = reader.readLine();
                        }
                        final Matcher m = DATE_PATTERN.matcher(line);

                        if (m.find()) {
                            String moduleName = file.getAbsolutePath();
                            if (file.getName().endsWith(".yang")) {
                                moduleName = moduleName.substring(0, moduleName.length() - 5);
                            }
                            final String revision = m.group(1);
                            final String correctName = moduleName + "@" + revision + ".yang";
                            final File correctNameFile = new File(correctName);
                            file.renameTo(correctNameFile);
                        }
                    } catch (final IOException e) {
                        // print error to console (test tool is running from console)
                        e.printStackTrace();
                    }
                }
            }
        }
        if (rpcConfig != null) {
            checkArgument(rpcConfig.exists(), "Rpc config file has to exist");
            checkArgument(!rpcConfig.isDirectory(), "Rpc config file can't be a directory");
            checkArgument(rpcConfig.canRead(), "Rpc config file to be readable");
        }
    }

    public ArrayList<ArrayList<Execution.DestToPayload>> getThreadsPayloads(final List<Integer> openDevices) {
        final String editContentString;
        try {
            if (stream == null) {
                editContentString = Files.toString(editContent, StandardCharsets.UTF_8);
            } else {
                editContentString = CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + editContent);
        }

        int from;
        int to;
        Iterator<Integer> iterator;

        final ArrayList<ArrayList<Execution.DestToPayload>> allThreadsPayloads = new ArrayList<>();
        if (generateConfigBatchSize > 1) {

            final int batchedRequests = openDevices.size() / generateConfigBatchSize;
            final int batchedRequestsPerThread = batchedRequests / threadAmount;
            final int leftoverBatchedRequests = (batchedRequests) % threadAmount;
            final int leftoverRequests = openDevices.size() - (batchedRequests * generateConfigBatchSize);

            final StringBuilder destBuilder = new StringBuilder(DEST);
            destBuilder.replace(destBuilder.indexOf(ADDRESS_PORT),
                destBuilder.indexOf(ADDRESS_PORT) + ADDRESS_PORT.length(),
                controllerDestination);

            for (int l = 0; l < threadAmount; l++) {
                from = l * (batchedRequests * batchedRequestsPerThread);
                to = from + (batchedRequests * batchedRequestsPerThread);
                iterator = openDevices.subList(from, to).iterator();
                allThreadsPayloads.add(createBatchedPayloads(batchedRequestsPerThread, iterator, editContentString,
                    destBuilder.toString()));
            }
            ArrayList<Execution.DestToPayload> payloads = null;
            if (leftoverBatchedRequests > 0) {
                from = threadAmount * (batchedRequests * batchedRequestsPerThread);
                to = from + (batchedRequests * batchedRequestsPerThread);
                iterator = openDevices.subList(from, to).iterator();
                payloads = createBatchedPayloads(leftoverBatchedRequests, iterator, editContentString,
                    destBuilder.toString());
            }
            String payload = "";

            for (int j = 0; j < leftoverRequests; j++) {
                from = openDevices.size() - leftoverRequests;
                to = openDevices.size();
                iterator = openDevices.subList(from, to).iterator();
                final StringBuilder payloadBuilder = new StringBuilder(
                    prepareMessage(iterator.next(), editContentString));
                payload += modifyMessage(payloadBuilder, j, leftoverRequests);
            }
            if (leftoverRequests > 0 || leftoverBatchedRequests > 0) {

                if (payloads != null) {
                    payloads.add(new Execution.DestToPayload(destBuilder.toString(), payload));
                }
                allThreadsPayloads.add(payloads);
            }
        } else {
            final int requestPerThreads = openDevices.size() / threadAmount;
            final int leftoverRequests = openDevices.size() % threadAmount;

            for (int i = 0; i < threadAmount; i++) {
                from = i * requestPerThreads;
                to = from + requestPerThreads;
                iterator = openDevices.subList(from, to).iterator();
                allThreadsPayloads.add(createPayloads(iterator, editContentString));
            }

            if (leftoverRequests > 0) {
                from = (threadAmount) * requestPerThreads;
                to = from + leftoverRequests;
                iterator = openDevices.subList(from, to).iterator();
                allThreadsPayloads.add(createPayloads(iterator, editContentString));
            }
        }
        return allThreadsPayloads;
    }

    private String prepareMessage(final int openDevice, final String editContentString) {
        final StringBuilder messageBuilder = new StringBuilder(editContentString);

        if (editContentString.contains(HOST_KEY)) {
            messageBuilder.replace(messageBuilder.indexOf(HOST_KEY),
                messageBuilder.indexOf(HOST_KEY) + HOST_KEY.length(),
                generateConfigsAddress);
        }
        if (editContentString.contains(PORT_KEY)) {
            while (messageBuilder.indexOf(PORT_KEY) != -1) {
                messageBuilder.replace(messageBuilder.indexOf(PORT_KEY),
                    messageBuilder.indexOf(PORT_KEY) + PORT_KEY.length(),
                    Integer.toString(openDevice));
            }
        }
        if (editContentString.contains(TCP_ONLY)) {
            messageBuilder.replace(messageBuilder.indexOf(TCP_ONLY),
                messageBuilder.indexOf(TCP_ONLY) + TCP_ONLY.length(),
                Boolean.toString(!ssh));
        }
        return messageBuilder.toString();
    }

    private ArrayList<Execution.DestToPayload> createPayloads(final Iterator<Integer> openDevices,
                                                              final String editContentString) {
        final ArrayList<Execution.DestToPayload> payloads = new ArrayList<>();

        while (openDevices.hasNext()) {
            final StringBuilder destBuilder = new StringBuilder(DEST);
            destBuilder.replace(destBuilder.indexOf(ADDRESS_PORT),
                destBuilder.indexOf(ADDRESS_PORT) + ADDRESS_PORT.length(), controllerDestination);
            payloads.add(new Execution.DestToPayload(
                destBuilder.toString(), prepareMessage(openDevices.next(), editContentString)));
        }
        return payloads;
    }

    private ArrayList<Execution.DestToPayload> createBatchedPayloads(final int batchedRequestsCount,
            final Iterator<Integer> openDevices, final String editContentString, final String destination) {
        final ArrayList<Execution.DestToPayload> payloads = new ArrayList<>();

        for (int i = 0; i < batchedRequestsCount; i++) {
            String payload = "";
            for (int j = 0; j < generateConfigBatchSize; j++) {
                final StringBuilder payloadBuilder = new StringBuilder(
                    prepareMessage(openDevices.next(), editContentString));
                payload += modifyMessage(payloadBuilder, j, generateConfigBatchSize);
            }
            payloads.add(new Execution.DestToPayload(destination, payload));
        }
        return payloads;
    }

    @Override
    public String toString() {
        final List<Field> fields = Arrays.asList(this.getClass().getDeclaredFields());
        final StringJoiner joiner = new StringJoiner(", \n", "TesttoolParameters{", "}\n");
        fields.stream()
                .filter(field -> field.getAnnotation(Arg.class) != null)
                .map(this::getFieldString)
                .forEach(joiner::add);
        return joiner.toString();
    }

    private String getFieldString(final Field field) {
        try {
            return field.getName() + "='" + field.get(this) + "'";
        } catch (final IllegalAccessException e) {
            return field.getName() + "= UNKNOWN";
        }
    }
}
