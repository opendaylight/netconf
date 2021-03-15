/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.yangtools.yang.common.YangConstants;

@SuppressFBWarnings({"DM_EXIT", "DM_DEFAULT_ENCODING"})
public class TesttoolParameters {
    private static final Pattern YANG_FILENAME_PATTERN = Pattern
        .compile("(?<name>.*)@(?<revision>\\d{4}-\\d{2}-\\d{2})\\.yang");
    private static final Pattern REVISION_DATE_PATTERN = Pattern.compile("revision\\s+\"?(\\d{4}-\\d{2}-\\d{2})\"?");

    @Arg(dest = "async")
    private boolean async;
    @Arg(dest = "thread-amount")
    private int threadAmount;
    @Arg(dest = "throttle")
    private int throttle;
    @Arg(dest = "controller-auth-username")
    private String controllerAuthUsername;
    @Arg(dest = "controller-auth-password")
    private String controllerAuthPassword;
    @Arg(dest = "controller-ip")
    private String controllerIp;
    @Arg(dest = "controller-port")
    private Integer controllerPort;
    @Arg(dest = "schemas-dir")
    private File schemasDir;
    @Arg(dest = "devices-count")
    private int deviceCount;
    @Arg(dest = "devices-per-port")
    private int devicesPerPort;
    @Arg(dest = "starting-port")
    private int startingPort;
    @Arg(dest = "generate-config-connection-timeout")
    private int generateConfigsTimeout;
    @Arg(dest = "generate-config-address")
    private String generateConfigsAddress;
    @Arg(dest = "distro-folder")
    private File distroFolder;
    @Arg(dest = "generate-configs-batch-size")
    private int generateConfigBatchSize;
    @Arg(dest = "ssh")
    private boolean ssh;
    @Arg(dest = "exi")
    private boolean exi = true;
    @Arg(dest = "debug")
    private boolean debug;
    @Arg(dest = "notification-file")
    private File notificationFile;
    @Arg(dest = "md-sal")
    private boolean mdSal;
    @Arg(dest = "initial-config-xml-file")
    private File initialConfigXMLFile;
    @Arg(dest = "time-out")
    private long timeOut;
    @Arg(dest = "ip")
    private String ip;
    @Arg(dest = "thread-pool-size")
    private int threadPoolSize;
    @Arg(dest = "rpc-config")
    private File rpcConfig;

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
        if (getControllerIp() != null) {
            //FIXME Ip validation
            checkArgument(getControllerPort() != null, "Controller port is missing");
            //FIXME Is there specific bound
            checkArgument(getControllerPort() >= 0, "Controller port should be non-negative integer");
            checkArgument(getControllerPort() < 65354, "Controller port should be less than 65354");
        } else {
            checkArgument(getControllerPort() == null, "Controller ip is missing");
        }

        checkArgument(getDeviceCount() > 0, "Device count has to be > 0");
        checkArgument(getStartingPort() > 1023, "Starting port has to be > 1023");
        checkArgument(getDevicesPerPort() > 0, "Atleast one device per port needed");

        if (getSchemasDir() != null) {
            checkArgument(getSchemasDir().exists(), "Schemas dir has to exist");
            checkArgument(getSchemasDir().isDirectory(), "Schemas dir has to be a directory");
            checkArgument(getSchemasDir().canRead(), "Schemas dir has to be readable");

            final File[] filesArray = getSchemasDir().listFiles();
            final List<File> files = filesArray != null ? Arrays.asList(filesArray) : Collections.emptyList();
            for (final File file : files) {
                final Matcher matcher = YANG_FILENAME_PATTERN.matcher(file.getName());
                if (!matcher.matches()) {
                    try {
                        final String correctName = correctedName(file);
                        if (correctName != null) {
                            Files.move(file.toPath(), Paths.get(correctName),
                                    StandardCopyOption.ATOMIC_MOVE);
                        }
                    } catch (final IOException e) {
                        // print error to console (test tool is running from console)
                        e.printStackTrace();
                    }
                }
            }
        }
        if (getRpcConfig() != null) {
            checkArgument(getRpcConfig().exists(), "Rpc config file has to exist");
            checkArgument(!getRpcConfig().isDirectory(), "Rpc config file can't be a directory");
            checkArgument(getRpcConfig().canRead(), "Rpc config file to be readable");
        }
    }

    private static String correctedName(final File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null && !REVISION_DATE_PATTERN.matcher(line).find()) {
                line = reader.readLine();
            }
            if (line != null) {
                final Matcher m = REVISION_DATE_PATTERN.matcher(line);
                checkState(m.find(), "Revision pattern %s did not match line %s", REVISION_DATE_PATTERN, line);
                String moduleName = file.getAbsolutePath();
                if (file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION)) {
                    moduleName = moduleName.substring(0, moduleName.length() - 5);
                }

                return moduleName + "@" + m.group(1) + YangConstants.RFC6020_YANG_FILE_EXTENSION;
            }
        }
        return null;
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

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public int getThreadAmount() {
        return threadAmount;
    }

    public void setThreadAmount(int threadAmount) {
        this.threadAmount = threadAmount;
    }

    public int getThrottle() {
        return throttle;
    }

    public void setThrottle(int throttle) {
        this.throttle = throttle;
    }

    public String getControllerAuthUsername() {
        return controllerAuthUsername;
    }

    public void setControllerAuthUsername(String controllerAuthUsername) {
        this.controllerAuthUsername = controllerAuthUsername;
    }

    public String getControllerAuthPassword() {
        return controllerAuthPassword;
    }

    public void setControllerAuthPassword(String controllerAuthPassword) {
        this.controllerAuthPassword = controllerAuthPassword;
    }

    public String getControllerIp() {
        return controllerIp;
    }

    public void setControllerIp(String controllerIp) {
        this.controllerIp = controllerIp;
    }

    public Integer getControllerPort() {
        return controllerPort;
    }

    public void setControllerPort(Integer controllerPort) {
        this.controllerPort = controllerPort;
    }

    public File getSchemasDir() {
        return schemasDir;
    }

    public void setSchemasDir(File schemasDir) {
        this.schemasDir = schemasDir;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public int getDevicesPerPort() {
        return devicesPerPort;
    }

    public void setDevicesPerPort(int devicesPerPort) {
        this.devicesPerPort = devicesPerPort;
    }

    public int getStartingPort() {
        return startingPort;
    }

    public void setStartingPort(int startingPort) {
        this.startingPort = startingPort;
    }

    public int getGenerateConfigsTimeout() {
        return generateConfigsTimeout;
    }

    public void setGenerateConfigsTimeout(int generateConfigsTimeout) {
        this.generateConfigsTimeout = generateConfigsTimeout;
    }

    public String getGenerateConfigsAddress() {
        return generateConfigsAddress;
    }

    public void setGenerateConfigsAddress(String generateConfigsAddress) {
        this.generateConfigsAddress = generateConfigsAddress;
    }

    public File getDistroFolder() {
        return distroFolder;
    }

    public void setDistroFolder(File distroFolder) {
        this.distroFolder = distroFolder;
    }

    public int getGenerateConfigBatchSize() {
        return generateConfigBatchSize;
    }

    public void setGenerateConfigBatchSize(int generateConfigBatchSize) {
        this.generateConfigBatchSize = generateConfigBatchSize;
    }

    public boolean isSsh() {
        return ssh;
    }

    public void setSsh(boolean ssh) {
        this.ssh = ssh;
    }

    public boolean isExi() {
        return exi;
    }

    public void setExi(boolean exi) {
        this.exi = exi;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public File getNotificationFile() {
        return notificationFile;
    }

    public void setNotificationFile(File notificationFile) {
        this.notificationFile = notificationFile;
    }

    public boolean isMdSal() {
        return mdSal;
    }

    public void setMdSal(boolean mdSal) {
        this.mdSal = mdSal;
    }

    public File getInitialConfigXMLFile() {
        return initialConfigXMLFile;
    }

    public void setInitialConfigXMLFile(File initialConfigXMLFile) {
        this.initialConfigXMLFile = initialConfigXMLFile;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public File getRpcConfig() {
        return rpcConfig;
    }

    public void setRpcConfig(File rpcConfig) {
        this.rpcConfig = rpcConfig;
    }
}
