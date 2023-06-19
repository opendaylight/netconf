/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import com.google.common.base.Joiner;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.Session1;
import org.opendaylight.yangtools.yang.binding.contract.Naming;
import org.opendaylight.yangtools.yang.common.QName;

final class MonitoringSession {

    @XmlTransient
    private Session managementSession;

    MonitoringSession(final Session managementSession) {
        this.managementSession = managementSession;
    }

    MonitoringSession() {
    }

    public void setManagementSession(final Session managementSession) {
        this.managementSession = managementSession;
    }

    @XmlElement(name = "session-id")
    public long getId() {
        return managementSession.getSessionId().longValue();
    }

    @XmlElement(name = "source-host")
    public String getSourceHost() {
        final IpAddress ipAddress = managementSession.getSourceHost().getIpAddress();
        if (ipAddress.getIpv4Address() != null) {
            return ipAddress.getIpv4Address().getValue();
        } else {
            return ipAddress.getIpv6Address().getValue();
        }
    }

    @XmlElement(name = "login-time")
    public String getLoginTime() {
        return managementSession.getLoginTime().getValue();
    }

    @XmlElement(name = "in-bad-rpcs")
    public Long getInBadRpcs() {
        return managementSession.getInBadRpcs().getValue().longValue();
    }

    @XmlElement(name = "in-rpcs")
    public Long getInRpcs() {
        return managementSession.getInRpcs().getValue().longValue();
    }

    @XmlElement(name = "out-notifications")
    public Long getOutNotifications() {
        return managementSession.getOutNotifications().getValue().longValue();
    }

    @XmlElement(name = "out-rpc-errors")
    public Long getOutRpcErrors() {
        return managementSession.getOutRpcErrors().getValue().longValue();
    }

    @XmlElement(name = "transport")
    public String getTransport() {
        try {
            final QName qualifiedName = (QName) managementSession.getTransport().implementedInterface()
                .getField(Naming.QNAME_STATIC_FIELD_NAME).get(null);
            // Add extension prefix if transport type is from extension yang module
            if (qualifiedName.getNamespace().toString().equals(MonitoringConstants.EXTENSION_NAMESPACE)) {
                return Joiner.on(':').join(MonitoringConstants.EXTENSION_NAMESPACE_PREFIX,
                        qualifiedName.getLocalName());
            } else {
                return qualifiedName.getLocalName();
            }
        } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new IllegalArgumentException("Unknown transport type " + managementSession.getTransport(), e);
        }
    }

    @XmlElement(name = "session-identifier", namespace = MonitoringConstants.EXTENSION_NAMESPACE)
    public String getSessionType() {
        return managementSession.augmentation(Session1.class).getSessionIdentifier();
    }

    @XmlElement(name = "username")
    public String getUsername() {
        return managementSession.getUsername();
    }
}
