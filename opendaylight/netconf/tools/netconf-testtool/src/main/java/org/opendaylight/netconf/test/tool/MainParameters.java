/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

public class MainParameters {

    private static final Logger LOG = LoggerFactory.getLogger(MainParameters.class);

    private static final String HOST_KEY = "{HOST}";
    private static final String PORT_KEY = "{PORT}";
    private static final String NODE_ID = "{NODE-ID}";
    private static final String ADDRESS_PORT = "{ADDRESS:PORT}";
    private static final String dest = "http://{ADDRESS:PORT}/restconf/config/network-topology:network-topology/topology/topology-netconf/node/restConf{NODE-ID}";


    @Arg(dest = "ip")
    public String ip;

    @Arg(dest = "port")
    public int port;

    @Arg(dest = "destination")
    public String destination;

    @Arg(dest = "edit-count")
    public int editCount;

    @Arg(dest = "edit-content")
    public File editContent;

    @Arg(dest = "async")
    public boolean async;

    @Arg(dest = "thread-amount")
    public int threadAmount;

    @Arg(dest = "same-device")
    public boolean sameDevice;

    @Arg(dest = "device-port-range-start")
    public int devicePortRangeStart;

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

    static ArgumentParser getParser() {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("netconf stress client");

        parser.description("Netconf stress client");

        parser.addArgument("--ip")
                .type(String.class)
                .setDefault("127.0.0.1")
                .help("Restconf server IP")
                .dest("ip");

        parser.addArgument("--port")
                .type(Integer.class)
                .setDefault(8181)
                .help("Restconf server port")
                .dest("port");

        parser.addArgument("--destination")
                .type(String.class)
                .setDefault("/restconf/config/network-topology:network-topology/topology/topology-netconf/node/" +
                        "{DEVICE_PORT}-sim-device/yang-ext:mount/cisco-vpp:vpp/bridge-domains/bridge-domain/a")
                .help("Destination to send the requests to after the ip:port part of the uri. " +
                        "Use {DEVICE_PORT} tag to use the device-port-range-start argument")
                .dest("destination");

        parser.addArgument("--edits")
                .type(Integer.class)
                .setDefault(50000)
                .help("Amount requests to be sent")
                .dest("edit-count");

        parser.addArgument("--edit-content")
                .type(File.class)
                .setDefault(new File("opendaylight/netconf/tools/netconf-testtool/src/main/resources/config.txt"))
                .dest("edit-content");

        parser.addArgument("--async-requests")
                .type(Boolean.class)
                .setDefault(true)
                .dest("async");

        parser.addArgument("--thread-amount")
                .type(Integer.class)
                .setDefault(1)
                .dest("thread-amount");

        parser.addArgument("--same-device")
                .type(Boolean.class)
                .setDefault(true)
                .help("If true, every thread edits the device at the first port. If false, n-th thread edits device at n-th port.")
                .dest("same-device");

        parser.addArgument("--device-port-range-start")
                .type(Integer.class)
                .setDefault(17830)
                .dest("device-port-range-start");

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
                .setDefault("localhost:8181")
                .help(".")
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

        return parser;
    }

    void validate() {
        Preconditions.checkArgument(port > 0, "Port =< 0");
        Preconditions.checkArgument(editCount > 0, "Edit count =< 0");
        Preconditions.checkArgument(editContent.exists(), "Edit content file missing");
        Preconditions.checkArgument(editContent.isDirectory() == false, "Edit content file is a dir");
        Preconditions.checkArgument(editContent.canRead(), "Edit content file is unreadable");

        Preconditions.checkArgument(destination.startsWith("/"), "Destination should start with a '/'");

        Preconditions.checkArgument(controllerDestination.contains(":"),"error");
        String[] parts = controllerDestination.split(Pattern.quote(":"));
        Preconditions.checkArgument(Integer.parseInt(parts[1]) > 0, "Port =< 0");

        checkArgument(deviceCount > 0, "Device count has to be > 0");
        checkArgument(startingPort > 1023, "Starting port has to be > 1023");
        checkArgument(devicesPerPort > 0, "Atleast one device per port needed");

        if(schemasDir != null) {
            checkArgument(schemasDir.exists(), "Schemas dir has to exist");
            checkArgument(schemasDir.isDirectory(), "Schemas dir has to be a directory");
            checkArgument(schemasDir.canRead(), "Schemas dir has to be readable");
        }
        // TODO validate
    }

    public static MainParameters parseArgs(final String[] args, final ArgumentParser parser) {
        final MainParameters opt = new MainParameters();
        try {
            parser.parseArgs(args, opt);
            return opt;
        } catch (final ArgumentParserException e) {
            parser.handleError(e);
        }

        System.exit(1);
        return null;
    }
/*
    public InetSocketAddress getInetAddress() {
        try {
            return new InetSocketAddress(InetAddress.getByName(ip), port);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Unknown ip", e);
        }
    }
*/
    public ArrayList<ArrayList<Execution.DestToPayload>> getThreadsPayloads(List<Integer> openDevices){
        int newThrottle = throttle / threadAmount;

        if (async && threadAmount > 1) {
            LOG.info("Throttling per thread: {}", newThrottle);
        }

        final String editContentString;
        try {
            editContentString = Files.toString(editContent, Charsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + editContent);
        }


        final ArrayList<ArrayList<Execution.DestToPayload>> allThreadsPayloads = new ArrayList<>();
        for (int i = 0; i < threadAmount; i++) {
            final ArrayList<Execution.DestToPayload> payloads = new ArrayList<>();
            for (int j = 0; j < openDevices.size(); j++) {
                final StringBuilder destBuilder = new StringBuilder(dest);
                final int id = i * openDevices.size() +j;
                destBuilder.replace(destBuilder.indexOf(ADDRESS_PORT), destBuilder.indexOf(ADDRESS_PORT) + ADDRESS_PORT.length(), controllerDestination)
                        .replace(destBuilder.indexOf(NODE_ID), destBuilder.indexOf(NODE_ID) + NODE_ID.length(), Integer.toString(id));

                payloads.add(new Execution.DestToPayload(destBuilder.toString(), prepareMessage(openDevices.get(j),generateConfigsAddress, editContentString, j)));
            }
            allThreadsPayloads.add(payloads);
        }

        return allThreadsPayloads;
    }

    private static String prepareMessage(final int openDevice, final String address, final String editContentString, final int j) {
        StringBuilder messageBuilder = new StringBuilder(editContentString);

        if (editContentString.contains(HOST_KEY)) {
            messageBuilder.replace(messageBuilder.indexOf(HOST_KEY), messageBuilder.indexOf(HOST_KEY) + HOST_KEY.length(), address);
        }
        if (editContentString.contains(PORT_KEY)) {
            messageBuilder.replace(messageBuilder.indexOf(PORT_KEY), messageBuilder.indexOf(PORT_KEY) + PORT_KEY.length(), Integer.toString(openDevice));
        }
        if (editContentString.contains(NODE_ID)) {
            messageBuilder.replace(messageBuilder.indexOf(NODE_ID), messageBuilder.indexOf(NODE_ID) + NODE_ID.length(), Integer.toString(j));
        }
        return messageBuilder.toString();
    }
}
