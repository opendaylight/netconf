/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.stress;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.AsyncSshHandler;
import org.opendaylight.netconf.test.tool.TestToolUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.CommitInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfigInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class StressClient {
    private static final Logger LOG = LoggerFactory.getLogger(StressClient.class);

    static final RemoteDevice<NetconfDeviceCommunicator> LOGGING_REMOTE_DEVICE = new RemoteDevice<>() {
        @Override
        public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
                final NetconfDeviceCommunicator netconfDeviceCommunicator) {
            LOG.info("Session established");
        }

        @Override
        public void onRemoteSessionDown() {
            LOG.info("Session down");
        }

        @Override
        public void onNotification(final NetconfMessage notification) {
            LOG.info("Notification received: {}", notification);
        }
    };

    static final QName COMMIT_QNAME = QName.create(CommitInput.QNAME, "commit");
    public static final NetconfMessage COMMIT_MSG = new NetconfMessage(readString("""
        <rpc message-id="commit-batch" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <commit/>
        </rpc>"""));

    static final QName EDIT_QNAME = QName.create(EditConfigInput.QNAME, "edit-config");
    static final Document EDIT_CANDIDATE_BLUEPRINT = readString("""
        <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <edit-config>
                <target>
                    <candidate/>
                </target>
                <default-operation>none</default-operation>
                <config/>
            </edit-config>
        </rpc>""");
    static final Document EDIT_RUNNING_BLUEPRINT  = readString("""
        <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <edit-config>
                <target>
                    <running/>
                </target>
                <default-operation>none</default-operation>
                <config/>
            </edit-config>
        </rpc>""");

    private static Document readString(final String str) {
        try {
            return XmlUtil.readXmlToDocument(str);
        } catch (SAXException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String MSG_ID_PLACEHOLDER_REGEX = "\\{MSG_ID\\}";
    private static final String PHYS_ADDR_PLACEHOLDER = "{PHYS_ADDR}";

    private static long macStart = 0xAABBCCDD0000L;

    private static Parameters params;

    private StressClient() {
        // Hidden on purpose
    }

    public static void main(final String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        if (initParameters(args)) {
            return;
        }
        params.validate();

        final var root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(params.debug ? Level.DEBUG : Level.INFO);

        final int threadAmount = params.threadAmount;
        LOG.info("thread amount: {}", threadAmount);
        final int requestsPerThread = params.editCount / params.threadAmount;
        LOG.info("requestsPerThread: {}", requestsPerThread);
        final int leftoverRequests = params.editCount % params.threadAmount;
        LOG.info("leftoverRequests: {}", leftoverRequests);

        LOG.info("Preparing messages");
        // Prepare all msgs up front
        final var allPreparedMessages = new ArrayList<List<NetconfMessage>>(threadAmount);
        for (int i = 0; i < threadAmount; i++) {
            if (i != threadAmount - 1) {
                allPreparedMessages.add(new ArrayList<>(requestsPerThread));
            } else {
                allPreparedMessages.add(new ArrayList<>(requestsPerThread + leftoverRequests));
            }
        }


        final String editContentString;
        try {
            editContentString = Files.readString(params.editContent.toPath());
        } catch (final IOException e) {
            throw new IllegalArgumentException("Cannot read content of " + params.editContent, e);
        }

        for (int i = 0; i < threadAmount; i++) {
            final var preparedMessages = allPreparedMessages.get(i);
            int padding = 0;
            if (i == threadAmount - 1) {
                padding = leftoverRequests;
            }
            for (int j = 0; j < requestsPerThread + padding; j++) {
                LOG.debug("id: {}", i * requestsPerThread + j);
                preparedMessages.add(prepareMessage(i * requestsPerThread + j, editContentString));
            }
        }

        final var nioGroup = new NioEventLoopGroup();
        final var timer = new HashedWheelTimer();

        final var netconfClientDispatcher = configureClientDispatcher(nioGroup, timer);

        final var callables = new ArrayList<StressClientCallable>(threadAmount);
        for (var messages : allPreparedMessages) {
            callables.add(new StressClientCallable(params, netconfClientDispatcher, messages));
        }

        final var executorService = Executors.newFixedThreadPool(threadAmount);

        LOG.info("Starting stress test");
        final var sw = Stopwatch.createStarted();
        final var futures = executorService.invokeAll(callables);
        for (var future : futures) {
            future.get(4L, TimeUnit.MINUTES);
        }
        executorService.shutdownNow();
        sw.stop();

        LOG.info("FINISHED. Execution time: {}", sw);
        LOG.info("Requests per second: {}", params.editCount * 1000.0 / sw.elapsed(TimeUnit.MILLISECONDS));

        // Cleanup
        timer.stop();
        try {
            nioGroup.shutdownGracefully().get(20L, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.warn("Unable to close executor properly", e);
        }
        //stop the underlying ssh thread that gets spawned if we use ssh
        if (params.ssh) {
            AsyncSshHandler.DEFAULT_CLIENT.stop();
        }
    }

    static NetconfMessage prepareMessage(final int id, final String editContentString) {
        final Document msg = XmlUtil.createDocumentCopy(
            params.candidateDatastore ? EDIT_CANDIDATE_BLUEPRINT : EDIT_RUNNING_BLUEPRINT);
        msg.getDocumentElement().setAttribute("message-id", Integer.toString(id));
        final NetconfMessage netconfMessage = new NetconfMessage(msg);

        final Element editContentElement;
        try {
            // Insert message id where needed
            String specificEditContent = editContentString.replaceAll(MSG_ID_PLACEHOLDER_REGEX, Integer.toString(id));

            final var sb = new StringBuilder(specificEditContent);
            int idx = sb.indexOf(PHYS_ADDR_PLACEHOLDER);
            while (idx != -1) {
                sb.replace(idx, idx + PHYS_ADDR_PLACEHOLDER.length(), TestToolUtils.getMac(macStart++));
                idx = sb.indexOf(PHYS_ADDR_PLACEHOLDER);
            }
            specificEditContent = sb.toString();

            editContentElement = XmlUtil.readXmlToElement(specificEditContent);
            final var config = ((Element) msg.getDocumentElement().getElementsByTagName("edit-config").item(0))
                    .getElementsByTagName("config").item(0);
            config.appendChild(msg.importNode(editContentElement, true));
        } catch (final IOException | SAXException e) {
            throw new IllegalArgumentException("Edit content file is unreadable", e);
        }

        return netconfMessage;
    }

    @SuppressFBWarnings(value = "DM_EXIT", justification = "Exit from CLI with error without throwing an exception")
    private static boolean initParameters(final String[] args) {
        final var parser = Parameters.getParser();
        params = new Parameters();
        try {
            parser.parseArgs(args, args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return true;
        }
        return false;
    }

    @Deprecated
    private static NetconfClientDispatcherImpl configureClientDispatcher(final NioEventLoopGroup nioGroup,
            final Timer timer) {
        if (params.exi) {
            return params.legacyFraming ? ConfigurableClientDispatcher.createLegacyExi(nioGroup, nioGroup, timer)
                : ConfigurableClientDispatcher.createChunkedExi(nioGroup, nioGroup, timer);
        }
        return params.legacyFraming ? ConfigurableClientDispatcher.createLegacy(nioGroup, nioGroup, timer)
            : ConfigurableClientDispatcher.createChunked(nioGroup, nioGroup, timer);
    }
}
