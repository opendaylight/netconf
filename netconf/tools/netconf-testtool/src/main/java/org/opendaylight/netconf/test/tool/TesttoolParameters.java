/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_DASHES;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.netconf.test.tool.model.Node;
import org.opendaylight.netconf.test.tool.model.Payload;
import org.opendaylight.netconf.test.tool.model.Topology;

@SuppressFBWarnings({"DM_EXIT", "DM_DEFAULT_ENCODING"})
public class TesttoolParameters {

    private static final String RESTCONF_NETCONF_TOPOLOGY_PATH_TEMPLATE =
        "http://%s:%s/rests/data/network-topology:network-topology/topology=topology-netconf/";
    private static final Pattern YANG_FILENAME_PATTERN = Pattern
        .compile("(?<name>.*)@(?<revision>\\d{4}-\\d{2}-\\d{2})\\.yang");
    private static final Pattern REVISION_DATE_PATTERN = Pattern.compile("revision\\s+\"?(\\d{4}-\\d{2}-\\d{2})\"?");

    private static final Gson RESTCONF_REQUEST_GSON = new GsonBuilder().setFieldNamingStrategy(LOWER_CASE_WITH_DASHES).create();

    private static final String DEFAULT_TOPOLOGY_ID = "topology-netconf";

    private static final String NODE_ID_TEMPLATE = "%d-sim-device";
    private static final String DEFAULT_NODE_PASSWORD = "admin";
    private static final String DEFAULT_NODE_USERNAME = "admin";
    private static final int DEFAULT_NODE_KEEPALIVE_DELAY = 0;
    private static final Boolean DEFAULT_NODE_SCHEMALESS = false;

    @Arg(dest = "async")
    public boolean async;
    @Arg(dest = "thread-amount")
    public int threadAmount;
    @Arg(dest = "throttle")
    public int throttle;
    @Arg(dest = "controller-auth-username")
    public String controllerAuthUsername;
    @Arg(dest = "controller-auth-password")
    public String controllerAuthPassword;
    @Arg(dest = "controller-ip")
    public String controllerIp;
    @Arg(dest = "controller-port")
    public Integer controllerPort;
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
    public boolean exi = true;
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
                .setDefault(Boolean.FALSE)
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

        parser.addArgument("--controller-auth-username")
                .type(String.class)
                .setDefault("admin")
                .help("Username for HTTP basic authentication to destination controller.")
                .dest("controller-auth-username");

        parser.addArgument("--controller-auth-password")
                .type(String.class)
                .setDefault("admin")
                .help("Password for HTTP basic authentication to destination controller.")
                .dest("controller-auth-password");

        parser.addArgument("--controller-ip")
                .type(String.class)
                .help("Ip of controller if available it will be used for spawning netconf connectors via topology"
                        + " configuration as a part of"
                        + " URI(http://<controller-ip>:<controller-port>/rests/data/...)"
                        + " otherwise it will just start simulated devices and skip the execution of PUT requests")
                .dest("controller-ip");

        parser.addArgument("--controller-port")
                .type(Integer.class)
                .help("Port of controller if available it will be used for spawning netconf connectors via topology "
                        + "configuration as a part of"
                        + " URI(http://<controller-ip>:<controller-port>/rests/data/...) "
                        + "otherwise it will just start simulated devices and skip the execution of PUT requests")
                .dest("controller-port");

        parser.addArgument("--device-count")
                .type(Integer.class)
                .setDefault(1)
                .help("Number of simulated netconf devices to spin. This is the number of actual ports open for the devices.")
                .dest("devices-count");

        parser.addArgument("--devices-per-port")
                .type(Integer.class)
                .setDefault(1)
                .help("Amount of config files generated per port to spoof more devices than are actually running")
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
                .setDefault(Boolean.TRUE)
                .help("Whether to use ssh for transport or just pure tcp")
                .dest("ssh");

        parser.addArgument("--exi")
                .type(Boolean.class)
                .setDefault(Boolean.TRUE)
                .help("Whether to use exi to transport xml content")
                .dest("exi");

        parser.addArgument("--debug")
                .type(Boolean.class)
                .setDefault(Boolean.FALSE)
                .help("Whether to use debug log level instead of INFO")
                .dest("debug");

        parser.addArgument("--md-sal")
                .type(Boolean.class)
                .setDefault(Boolean.FALSE)
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

    @SuppressWarnings("checkstyle:regexpSinglelineJava")
    void validate() {
        if (controllerIp != null) {
            //FIXME Ip validation
            Preconditions.checkArgument(controllerPort != null, "Controller port is missing");
            //FIXME Is there specific bound
            Preconditions.checkArgument(controllerPort >= 0, "Controller port should be non-negative integer");
            Preconditions.checkArgument(controllerPort < 65354, "Controller port should be less than 65354");
        } else {
            Preconditions.checkArgument(controllerPort == null, "Controller ip is missing");
        }

        checkArgument(deviceCount > 0, "Device count has to be > 0");
        checkArgument(startingPort > 1023, "Starting port has to be > 1023");
        checkArgument(devicesPerPort > 0, "Atleast one device per port needed");

        if (schemasDir != null) {
            checkArgument(schemasDir.exists(), "Schemas dir has to exist");
            checkArgument(schemasDir.isDirectory(), "Schemas dir has to be a directory");
            checkArgument(schemasDir.canRead(), "Schemas dir has to be readable");

            final File[] filesArray = schemasDir.listFiles();
            final List<File> files = filesArray != null ? Arrays.asList(filesArray) : Collections.emptyList();
            for (final File file : files) {
                final Matcher matcher = YANG_FILENAME_PATTERN.matcher(file.getName());
                if (!matcher.matches()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                        String line = reader.readLine();
                        while (line != null && !REVISION_DATE_PATTERN.matcher(line).find()) {
                            line = reader.readLine();
                        }
                        if (line != null) {
                            final Matcher m = REVISION_DATE_PATTERN.matcher(line);
                            Preconditions.checkState(m.find());
                            String moduleName = file.getAbsolutePath();
                            if (file.getName().endsWith(".yang")) {
                                moduleName = moduleName.substring(0, moduleName.length() - 5);
                            }
                            final String revision = m.group(1);
                            final String correctName = moduleName + "@" + revision + ".yang";
                            final File correctNameFile = new File(correctName);
                            if (!file.renameTo(correctNameFile)) {
                                throw new IllegalStateException("Failed to rename '%s'." + file);
                            }
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

    public List<List<Execution.DestToPayload>> getThreadsPayloads(final List<Integer> openDevices) {
        //FIXME Move this to validate() and rename it to init() or create init() and move there.
        //FIXME Make it field.
        final String restconfNetconfTopologyPath = String.format(RESTCONF_NETCONF_TOPOLOGY_PATH_TEMPLATE,
                controllerIp, controllerPort);
        final List<Payload> payloads = createPayloads(openDevices);
        final List<Execution.DestToPayload> destinationPayloadPairs = payloads.stream()
                .map(RESTCONF_REQUEST_GSON::toJson)
                .map(stringPayload -> new Execution.DestToPayload(restconfNetconfTopologyPath, stringPayload))
                .collect(Collectors.toList());

        final int requestsPerThread = IntMath.divide(destinationPayloadPairs.size(), threadAmount, RoundingMode.UP);

        return Lists.partition(destinationPayloadPairs, requestsPerThread);
    }

    private List<Payload> createPayloads(List<Integer> openDevices) {
        final List<Payload> payloads;
        if (generateConfigBatchSize > 1) {
            final List<List<Integer>> portsInBatches = Lists.partition(openDevices, generateConfigBatchSize);
            payloads = createBatchedPayloads(portsInBatches);
        } else {
            payloads = createSingleNodePayloads(openDevices);
        }
        return payloads;
    }

    private List<Payload> createBatchedPayloads(final List<List<Integer>> portsInBatches) {
        final List<List<String>> nodeIdsInBatches = portsInBatches.stream()
                .map(portsInRequest -> portsInRequest.stream()
                        .map(port -> String.format(NODE_ID_TEMPLATE, port))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        return createBatchedPayloads(DEFAULT_TOPOLOGY_ID, portsInBatches, nodeIdsInBatches, generateConfigsAddress,
                DEFAULT_NODE_USERNAME, DEFAULT_NODE_PASSWORD, !ssh, DEFAULT_NODE_KEEPALIVE_DELAY,
                DEFAULT_NODE_SCHEMALESS);
    }

    private List<Payload> createBatchedPayloads(final String topologyId, final List<List<Integer>> portsInBatches,
                                                final List<List<String>> nodeIdsInBatches, final String host,
                                                final String username, final String password, final boolean tcpOnly,
                                                final int keepaliveDelay, final boolean schemaless) {
        final List<Payload> payloads = new ArrayList<>();
        final Iterator<List<String>> nodeIdsIterator = nodeIdsInBatches.iterator();
        for (final List<Integer> portsInBatch : portsInBatches) {
            final List<String> nodeIdsInBatch = nodeIdsIterator.next();
            payloads.add(createPayload(topologyId, portsInBatch, nodeIdsInBatch ,host, username, password, tcpOnly,
                    keepaliveDelay, schemaless));
        }
        return payloads;
    }

    private List<Payload> createSingleNodePayloads(final List<Integer> batchedPorts) {
        final List<String> nodeIdsInBatches = batchedPorts.stream()
                .map(port -> String.format(NODE_ID_TEMPLATE, port))
                .collect(Collectors.toList());
        return createSingleNodePayloads(DEFAULT_TOPOLOGY_ID, batchedPorts, nodeIdsInBatches, generateConfigsAddress,
                DEFAULT_NODE_USERNAME, DEFAULT_NODE_PASSWORD, !ssh, DEFAULT_NODE_KEEPALIVE_DELAY,
                DEFAULT_NODE_SCHEMALESS);
    }

    private List<Payload> createSingleNodePayloads(final String topologyId, final List<Integer> portsInBatches,
                                                   final List<String> nodeIdsInBatches, final String host,
                                                   final String username, final String password, final boolean tcpOnly,
                                                   final int keepaliveDelay, final boolean schemaless) {
        final List<Payload> payloads = new ArrayList<>();
        final Iterator<String> nodeIdsIterator = nodeIdsInBatches.iterator();
        for (final int portsInBatch : portsInBatches) {
            final String nodeIdsInBatch = nodeIdsIterator.next();
            payloads.add(createPayload(topologyId, portsInBatch, nodeIdsInBatch ,host, username, password, tcpOnly,
                    keepaliveDelay, schemaless));
        }
        return payloads;
    }

    public Payload createPayload(final String topologyId, final int port, final String nodeId, final String host,
                                 final String username, final String password, final Boolean tcpOnly,
                                 final int keepaliveDelay, final boolean schemaless) {
        final Topology topology = new Topology(topologyId);
        topology.addNode(new Node(nodeId, host, port, username, password, tcpOnly, keepaliveDelay, schemaless));
        return new Payload(topology);
    }

    public Payload createPayload(final String topologyId, final Iterable<Integer> ports, final Iterable<String> nodeIds,
                                 final String host, final String username, final String password,
                                 final boolean tcpOnly, final int keepaliveDelay, final boolean schemaless) {
        final Topology topology = new Topology(topologyId);
        final Iterator<String> nodeIdsIterator = nodeIds.iterator();
        for (final Integer port : ports) {
            final String nodeId = nodeIdsIterator.next();
            topology.addNode(new Node(nodeId, host, port, username, password, tcpOnly, keepaliveDelay,schemaless));
        }
        return new Payload(topology);
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
