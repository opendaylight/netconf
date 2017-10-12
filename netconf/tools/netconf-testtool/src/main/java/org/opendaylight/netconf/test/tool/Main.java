/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkNotNull;

import ch.qos.logback.classic.Level;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;
import org.apache.karaf.features.internal.model.ConfigFile;
import org.apache.karaf.features.internal.model.Feature;
import org.apache.karaf.features.internal.model.Features;
import org.apache.karaf.features.internal.model.JaxbUtil;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {

    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void main(final String[] args) {
        final TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());
        params.validate();
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);

        final Configuration configuration = new ConfigurationBuilder().from(params).build();
        final NetconfDeviceSimulator netconfDeviceSimulator = new NetconfDeviceSimulator(configuration);
        try {
            LOG.debug("Trying to start netconf test-tool with parameters {}", params);
            final List<Integer> openDevices = netconfDeviceSimulator.start();
            if (openDevices.size() == 0) {
                LOG.error("Failed to start any simulated devices, exiting...");
                System.exit(1);
            }
            if (params.controllerDestination != null) {
                final ArrayList<ArrayList<Execution.DestToPayload>> allThreadsPayloads = params
                    .getThreadsPayloads(openDevices);
                final ArrayList<Execution> executions = new ArrayList<>();
                for (ArrayList<Execution.DestToPayload> payloads : allThreadsPayloads) {
                    executions.add(new Execution(params, payloads));
                }
                final ExecutorService executorService = Executors.newFixedThreadPool(params.threadAmount);
                final Stopwatch time = Stopwatch.createStarted();
                List<Future<Void>> futures = executorService.invokeAll(executions, params.timeOut, TimeUnit.SECONDS);
                int threadNum = 0;
                for (Future<Void> future : futures) {
                    threadNum++;
                    if (future.isCancelled()) {
                        LOG.info("{}. thread timed out.",threadNum);
                    } else {
                        try {
                            future.get();
                        } catch (final ExecutionException e) {
                            LOG.info("{}. thread failed.", threadNum, e);
                        }
                    }
                }
                time.stop();
                LOG.info("Time spent with configuration of devices: {}.",time);
            }

            if (params.distroFolder != null) {
                final ConfigGenerator configGenerator = new ConfigGenerator(params.distroFolder, openDevices);
                final List<File> generated = configGenerator.generate(
                        params.ssh, params.generateConfigBatchSize,
                        params.generateConfigsTimeout, params.generateConfigsAddress,
                        params.devicesPerPort);
                configGenerator.updateFeatureFile(generated);
                configGenerator.changeLoadOrder();
            }
        } catch (final Exception e) {
            LOG.error("Unhandled exception", e);
            netconfDeviceSimulator.close();
            System.exit(1);
        }

        // Block main thread
        synchronized (netconfDeviceSimulator) {
            try {
                netconfDeviceSimulator.wait();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ConfigGenerator {
        public static final String NETCONF_CONNECTOR_XML = "/99-netconf-connector-simulated.xml";
        public static final String SIM_DEVICE_SUFFIX = "-sim-device";

        private static final String SIM_DEVICE_CFG_PREFIX = "simulated-devices_";
        private static final String ETC_KARAF_PATH = "etc/";
        private static final String ETC_OPENDAYLIGHT_KARAF_PATH = ETC_KARAF_PATH + "opendaylight/karaf/";

        public static final String NETCONF_CONNECTOR_ALL_FEATURE = "odl-netconf-connector-all";
        private static final String ORG_OPS4J_PAX_URL_MVN_CFG = "org.ops4j.pax.url.mvn.cfg";

        private final File configDir;
        private final List<Integer> openDevices;
        private final List<File> ncFeatureFiles;
        private final File etcDir;
        private final File loadOrderCfgFile;

        ConfigGenerator(final File directory, final List<Integer> openDevices) {
            this.configDir = new File(directory, ETC_OPENDAYLIGHT_KARAF_PATH);
            this.etcDir = new File(directory, ETC_KARAF_PATH);
            this.loadOrderCfgFile = new File(etcDir, ORG_OPS4J_PAX_URL_MVN_CFG);
            this.ncFeatureFiles = getFeatureFile(directory, "features-netconf-connector", "xml");
            this.openDevices = openDevices;
        }

        public List<File> generate(final boolean useSsh, final int batchSize,
                                   final int generateConfigsTimeout, final String address,
                                   final int devicesPerPort) {
            if (!configDir.exists()) {
                Preconditions.checkState(configDir.mkdirs(), "Unable to create directory " + configDir);
            }

            for (final File file : configDir.listFiles(pathname ->
                    !pathname.isDirectory() && pathname.getName().startsWith(SIM_DEVICE_CFG_PREFIX))) {
                Preconditions.checkState(file.delete(), "Unable to clean previous generated file %s", file);
            }

            try (InputStream stream = Main.class.getResourceAsStream(NETCONF_CONNECTOR_XML)) {
                checkNotNull(stream, "Cannot load %s", NETCONF_CONNECTOR_XML);
                String configBlueprint = CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));

                final String before = configBlueprint.substring(0, configBlueprint.indexOf("<module>"));
                final String middleBlueprint = configBlueprint.substring(
                    configBlueprint.indexOf("<module>"), configBlueprint.indexOf("</module>"));
                final String after = configBlueprint.substring(
                    configBlueprint.indexOf("</module>") + "</module>".length());

                int connectorCount = 0;
                Integer batchStart = null;
                StringBuilder builder = new StringBuilder();
                builder.append(before);

                final List<File> generatedConfigs = Lists.newArrayList();

                for (final Integer openDevice : openDevices) {
                    if (batchStart == null) {
                        batchStart = openDevice;
                    }

                    for (int i = 0; i < devicesPerPort; i++) {
                        final String name = String.valueOf(openDevice)
                            + SIM_DEVICE_SUFFIX + (i == 0 ? "" : "-" + String.valueOf(i));
                        String configContent = String.format(
                            middleBlueprint, name, address, String.valueOf(openDevice), String.valueOf(!useSsh));
                        configContent = String.format(
                            "%s%s%d%s\n%s\n", configContent, "<connection-timeout-millis>",
                            generateConfigsTimeout, "</connection-timeout-millis>", "</module>");

                        builder.append(configContent);
                        connectorCount++;
                        if (connectorCount == batchSize) {
                            builder.append(after);
                            final File to = new File(
                                configDir, String.format(SIM_DEVICE_CFG_PREFIX + "%d-%d.xml", batchStart, openDevice));
                            generatedConfigs.add(to);
                            Files.write(builder.toString(), to, StandardCharsets.UTF_8);
                            connectorCount = 0;
                            builder = new StringBuilder();
                            builder.append(before);
                            batchStart = null;
                        }
                    }
                }

                // Write remaining
                if (connectorCount != 0) {
                    builder.append(after);
                    final File to = new File(configDir, String.format(
                            SIM_DEVICE_CFG_PREFIX + "%d-%d.xml", batchStart, openDevices.get(openDevices.size() - 1)));
                    generatedConfigs.add(to);
                    Files.write(builder.toString(), to, StandardCharsets.UTF_8);
                }

                LOG.info("Config files generated in {}", configDir);
                return generatedConfigs;
            } catch (final IOException e) {
                throw new RuntimeException("Unable to generate config files", e);
            }
        }

        public void updateFeatureFile(final List<File> generated) {
            for (final File fileFeatures : ncFeatureFiles) {
                try {
                    final Features f =  JaxbUtil.unmarshal(fileFeatures.toURI().toString(), false);

                    for (final Feature feature : f.getFeature()) {
                        if (NETCONF_CONNECTOR_ALL_FEATURE.equals(feature.getName())) {
                            //Clean all previously generated configFiles
                            feature.getConfigfile().clear();

                            //Create new configFiles
                            for (final File gen : generated) {
                                final ConfigFile cf = new ConfigFile();

                                final String generatedName = ETC_OPENDAYLIGHT_KARAF_PATH + gen.getName();

                                cf.setFinalname(generatedName);
                                cf.setLocation("file:" + generatedName);

                                feature.getConfigfile().add(cf);
                            }
                        }
                    }
                    JaxbUtil.marshal(f, new FileWriter(fileFeatures));
                    LOG.info("Feature file {} updated", fileFeatures);
                } catch (JAXBException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private static List<File> getFeatureFile(final File distroFolder, final String featureName,
                                                 final String suffix) {
            checkExistingDir(distroFolder, String.format("Folder %s does not exist", distroFolder));

            final File systemDir = checkExistingDir(new File(distroFolder, "system"),
                String.format("Folder %s does not contain a karaf distro, folder system is missing", distroFolder));

            //check if beryllium path exists, if it doesnt check for lithium and fail/succeed after
            File netconfConnectorFeaturesParentDir = new File(systemDir, "org/opendaylight/netconf/" + featureName);
            if (!netconfConnectorFeaturesParentDir.exists() || !netconfConnectorFeaturesParentDir.isDirectory()) {
                netconfConnectorFeaturesParentDir = checkExistingDir(new File(systemDir,
                    "org/opendaylight/controller/" + featureName),
                    String.format("Karaf distro in %s does not contain netconf-connector features", distroFolder));
            }

            // Find newest version for features
            final File newestVersionDir = Collections.max(
                Lists.newArrayList(netconfConnectorFeaturesParentDir.listFiles(File::isDirectory)),
                Comparator.comparing(File::getName));

            return Lists.newArrayList(newestVersionDir.listFiles(
                pathname -> pathname.getName().contains(featureName)
                    && Files.getFileExtension(pathname.getName()).equals(suffix)));
        }

        private static File checkExistingDir(final File folder, final String msg) {
            Preconditions.checkArgument(folder.exists(), msg);
            Preconditions.checkArgument(folder.isDirectory(), msg);
            return folder;
        }

        public void changeLoadOrder() {
            try {
                Files.write(ByteStreams.toByteArray(getClass().getResourceAsStream(
                    "/" + ORG_OPS4J_PAX_URL_MVN_CFG)), loadOrderCfgFile);
                LOG.info("Load order changed to prefer local bundles/features by rewriting file {}", loadOrderCfgFile);
            } catch (IOException e) {
                throw new RuntimeException("Unable to rewrite features file " + loadOrderCfgFile, e);
            }
        }
    }
}
