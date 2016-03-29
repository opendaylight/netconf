/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class TesttoolParameters {

    private static final String HOST_KEY = "{HOST}";
    private static final String PORT_KEY = "{PORT}";
    private static final String SSH = "{SSH}";
    private static final String ADDRESS_PORT = "{ADDRESS:PORT}";
    private static final String dest = "http://{ADDRESS:PORT}/restconf/config/network-topology:network-topology/topology/topology-netconf/node/{PORT}-sim-device";

    private static final String RESOURCE = "/config-template.xml";
    private InputStream stream;

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
                .dest("thread-amount");

        parser.addArgument("--throttle")
                .type(Integer.class)
                .setDefault(5000)
                .help("Maximum amount of async requests that can be open at a time, " +
                        "with mutltiple threads this gets divided among all threads")
                .dest("throttle");

        parser.addArgument("--auth")
                .nargs(2)
                .help("Username and password for HTTP basic authentication in order username password.")
                .dest("auth");

        parser.addArgument("--controller-destination")
                .type(String.class)
                .help("Ip address and port of controller. Must be in following format <ip>:<port> "+
                      "if available it will be used for spawning netconf connectors via topology configuration as "+
                      "a part of URI. Example (http://<controller destination>/restconf/config/network-topology:network-topology/topology/topology-netconf/node/<node-id>)"+
                      "otherwise it will just start simulated devices and skip the execution of PUT requests")
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
                .setDefault(4000)
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

    void validate() {
        if (editContent == null) {
            stream = TesttoolParameters.class.getResourceAsStream(RESOURCE);
        } else {
            Preconditions.checkArgument(!editContent.isDirectory(), "Edit content file is a dir");
            Preconditions.checkArgument(editContent.canRead(), "Edit content file is unreadable");
        }

        if (controllerDestination != null) {
            Preconditions.checkArgument(controllerDestination.contains(":"), "Controller Destination needs to be in a following format <ip>:<port>");
            String[] parts = controllerDestination.split(Pattern.quote(":"));
            Preconditions.checkArgument(Integer.parseInt(parts[1]) > 0, "Port =< 0");
        }

        checkArgument(deviceCount > 0, "Device count has to be > 0");
        checkArgument(startingPort > 1023, "Starting port has to be > 1023");
        checkArgument(devicesPerPort > 0, "Atleast one device per port needed");

        if (schemasDir != null) {
            checkArgument(schemasDir.exists(), "Schemas dir has to exist");
            checkArgument(schemasDir.isDirectory(), "Schemas dir has to be a directory");
            checkArgument(schemasDir.canRead(), "Schemas dir has to be readable");
        }
    }

    public ArrayList<ArrayList<Execution.DestToPayload>> getThreadsPayloads(List<Integer> openDevices) {
        final String editContentString;
        try {
            if(stream == null)
            {
                editContentString = Files.toString(editContent, Charsets.UTF_8);
            } else {
                editContentString = CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8));
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + editContent);
        }

        final ArrayList<ArrayList<Execution.DestToPayload>> allThreadsPayloads = new ArrayList<>();
        for (int i = 0; i < threadAmount; i++) {
            final ArrayList<Execution.DestToPayload> payloads = new ArrayList<>();
            for (int j = 0; j < openDevices.size(); j++) {
                final StringBuilder destBuilder = new StringBuilder(dest);
                destBuilder.replace(destBuilder.indexOf(ADDRESS_PORT), destBuilder.indexOf(ADDRESS_PORT) + ADDRESS_PORT.length(), controllerDestination)
                        .replace(destBuilder.indexOf(PORT_KEY), destBuilder.indexOf(PORT_KEY) + PORT_KEY.length(), Integer.toString(openDevices.get(j)));
                payloads.add(new Execution.DestToPayload(destBuilder.toString(), prepareMessage(openDevices.get(j), editContentString)));
            }
            allThreadsPayloads.add(payloads);
        }

        return allThreadsPayloads;
    }

    private String prepareMessage(final int openDevice, final String editContentString) {
        StringBuilder messageBuilder = new StringBuilder(editContentString);

        if (editContentString.contains(HOST_KEY)) {
            messageBuilder.replace(messageBuilder.indexOf(HOST_KEY), messageBuilder.indexOf(HOST_KEY) + HOST_KEY.length(), generateConfigsAddress);
        }
        if (editContentString.contains(PORT_KEY)) {
            while (messageBuilder.indexOf(PORT_KEY) != -1)
                messageBuilder.replace(messageBuilder.indexOf(PORT_KEY), messageBuilder.indexOf(PORT_KEY) + PORT_KEY.length(), Integer.toString(openDevice));
        }
        if (editContentString.contains(SSH)) {
            messageBuilder.replace(messageBuilder.indexOf(SSH), messageBuilder.indexOf(SSH) + SSH.length(), Boolean.toString(!ssh));
        }
        return messageBuilder.toString();
    }
}
