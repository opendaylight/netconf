/*
 * Copyright (c) 2018 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.notif;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.confignetconfconnector.operations.AbstractConfigNetconfOperation;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.impl.mapping.operations.DefaultNetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.notifications.NetconfNotification;
import org.opendaylight.netconf.test.tool.Main;
import org.opendaylight.netconf.test.tool.TesttoolParameters;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfConfigChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class NotifBlaster {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final Set<String> MODELS = ImmutableSet.of(
        "notif/ericsson-adapter-yang-extensions@2018-03-12.yang",
        "notif/ericsson-lrat-enb-adapter@2018-02-21.yang",
        "notif/iana-crypt-hash@2014-08-06.yang",
        "notif/iana-if-type@2014-05-08.yang",
        "notif/ietf-datastores@2017-08-17.yang",
        "notif/ietf-inet-types@2013-07-15.yang",
        "notif/ietf-interfaces@2014-05-08.yang",
        "notif/ietf-ip@2014-06-16.yang",
        "notif/ietf-keystore@2016-10-31.yang",
        "notif/ietf-netconf@2011-06-01.yang",
        "notif/ietf-netconf-acm@2018-02-14.yang",
        "notif/ietf-netconf-monitoring@2010-10-04.yang",
        "notif/ietf-netconf-notifications@2012-02-06.yang",
        "notif/ietf-netconf-server@2016-11-02.yang",
        "notif/ietf-netconf-with-defaults@2011-06-01.yang",
        "notif/ietf-ssh-server@2016-11-02.yang",
        "notif/ietf-system@2014-08-06.yang",
        "notif/ietf-tls-server@2016-11-02.yang",
        "notif/ietf-x509-cert-to-name@2014-12-10.yang",
        "notif/ietf-yang-library@2018-01-17.yang",
        "notif/ietf-yang-metadata@2016-08-05.yang",
        "notif/ietf-yang-types@2013-07-15.yang",
        "notif/nc-notifications@2008-07-14.yang",
        "notif/notifications@2008-07-14.yang",
        "notif/yang@2017-02-20.yang");

    private static final Set<String> CAPABILITIES = ImmutableSet.of(
        "urn:ietf:params:netconf:base:1.0",
        "urn:ietf:params:netconf:base:1.1");
//        "urn:ietf:params:netconf:capability:notification:1.0");

    private static final Element DATASTORE;

    static {
        try {
            DATASTORE = XmlUtil.readXmlToElement(NotifBlaster.class.getResourceAsStream("/notif/configuration.xml"));
        } catch (SAXException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String BASE_NOTIF_URL = NetconfConfigChange.QNAME.getNamespace().toString();

    private NotifBlaster() {

    }

    public static void main(final String[] args) {
        final TesttoolParameters params = Main.preSetup(args);
        Configuration configuration = new ConfigurationBuilder()
                .from(params)
                .setModels(MODELS)
                .setCapabilities(CAPABILITIES)
                .setOperationsCreator(NetconfOperationServiceImpl::new)
                .build();

        // FIXME: initialize initial data, notification and response

        Main.runSimulator(params, configuration);
    }

    private static final class NetconfOperationServiceImpl implements NetconfOperationService {
        private final NetconfOperation getConfig;

        NetconfOperationServiceImpl(final Set<Capability> capabilities,
                final SessionIdProvider idProvider, final String netconfSessionIdForReporting) {
            getConfig = new GetConfig(netconfSessionIdForReporting);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            return ImmutableSet.of(getConfig);
        }

        @Override
        public void close() {
            // No-op
        }
    }

    private static final class GetConfig extends AbstractConfigNetconfOperation implements DefaultNetconfOperation {
        private NetconfServerSession session;
        private boolean rootReadDone;

        GetConfig(final String netconfSessionIdForReporting) {
            super(null, netconfSessionIdForReporting);
        }

        @Override
        public void setNetconfSession(final NetconfServerSession netconfServerSession) {
            session = netconfServerSession;
        }

        @Override
        protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
                throws DocumentedException {
            final Element element = XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.absent());

            if (!rootReadDone) {
                fillRootConfigData(element);
                rootReadDone = true;
            } else {
                fillLeafConfigData(element);
            }

            verifyNotNull(session).sendMessage(createNotification());
            return element;
        }

        @Override
        protected String getOperationName() {
            return XmlNetconfConstants.GET_CONFIG;
        }

        private static NetconfNotification createNotification() {
            final Document document = XmlUtil.newDocument();
            final Element notification = XmlUtil.createElement(document, "netconf-config-change",
                Optional.of(BASE_NOTIF_URL));

            final Element edit = XmlUtil.createElement(document, "edit", Optional.absent());
            edit.appendChild(XmlUtil.createTextElement(document, "target", "foo", Optional.absent()));
            edit.appendChild(XmlUtil.createTextElement(document, "operation", "replace", Optional.absent()));

            notification.appendChild(edit);
            document.appendChild(notification);
            return new NetconfNotification(document);
        }

        private static void fillRootConfigData(final Element element) {
            final NodeList children = DATASTORE.cloneNode(true).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                element.appendChild(element.getOwnerDocument().adoptNode(children.item(i)));
            }
        }

        private static void fillLeafConfigData(final Element element) {
            // FIXME: report just the changed element
            final NodeList children = DATASTORE.cloneNode(true).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                element.appendChild(element.getOwnerDocument().adoptNode(children.item(i).cloneNode(true)));
            }
        }
    }
}
