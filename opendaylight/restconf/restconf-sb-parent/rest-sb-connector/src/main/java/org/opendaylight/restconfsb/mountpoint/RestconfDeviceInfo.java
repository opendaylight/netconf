/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.parser.RestconfParser;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfXmlParser;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RestconfDeviceInfo queries information about connected device.
 */
public class RestconfDeviceInfo {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDeviceInfo.class);

    private final Sender sender;
    private final RestconfParser parser;

    public RestconfDeviceInfo(final Sender sender) {
        this.sender = sender;
        this.parser = new RestconfXmlParser();
    }

    private static Module createModule(final QName qname) {
        return new ModuleBuilder()
                .setName(new YangIdentifier(qname.getLocalName()))
                .setNamespace(new Uri(qname.getNamespace().toString()))
                .setRevision(new RevisionIdentifier(qname.getFormattedRevision()))
                .setCapabilityOrigin(Module.CapabilityOrigin.UserDefined)
                .build();
    }

    /**
     * Asks device about supported modules.
     *
     * @param restconfNode
     * @return supported modules
     * @throws NodeConnectionException
     */
    public List<Module> getModules(final RestconfNode restconfNode) throws NodeConnectionException {
        final Request request = Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA);
        final ListenableFuture<InputStream> r = sender.get(request);
        try {
            if (restconfNode.getYangModuleCapabilities() == null) {
                return parser.parseModules(r.get());
            } else {
                final RestconfPreferences preferences = RestconfPreferences.fromStrings(restconfNode.getYangModuleCapabilities().getCapability(),
                        Module.CapabilityOrigin.UserDefined);
                final Boolean override = restconfNode.getYangModuleCapabilities().isOverride();
                if (override) {
                    final List<Module> moduleList = new ArrayList<>();
                    for (final QName qname : preferences.getModuleBasedCaps()) {
                        moduleList.add(createModule(qname));
                    }
                    return moduleList;
                } else {
                    final List<Module> moduleList = parser.parseModules(r.get());
                    for (final QName qname : preferences.getModuleBasedCaps()) {
                        moduleList.add(createModule(qname));
                    }
                    return moduleList;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new NodeConnectionException(e);
        }
    }

    /**
     * Asks device about supported streams.
     *
     * @return supported streams
     */
    public List<Stream> getStreams() {
        final ListenableFuture<InputStream> r = sender.get(Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA));

        try {
            return parser.parseStreams(r.get());
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof NotFoundException) {
                LOG.info("Streams not found", e);
            } else if (e.getCause() instanceof HttpException) {
                final HttpException exc = (HttpException) e.getCause();
                LOG.error("read failed {} {}", exc.getMsg(), exc.getStatus(), e);
            } else {
                LOG.error("Error parsing streams", e);
            }
            return Collections.emptyList();
        } catch (final Exception e) {
            LOG.error("Error while trying to get streams", e);
            return Collections.emptyList();
        }
    }
}
