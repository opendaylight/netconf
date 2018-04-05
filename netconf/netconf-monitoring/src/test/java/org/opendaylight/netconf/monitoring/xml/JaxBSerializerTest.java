/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.monitoring.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.monitoring.xml.model.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.NetconfTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.Session1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfSsh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SchemasBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter32;

public class JaxBSerializerTest {

    private static final String IPV4 = "192.168.1.1";
    private static final String IPV6 = "FE80:0000:0000:0000:0202:B3FF:FE1E:8329";
    private static final String SESSION_XML = "<session>"
            + "<session-id>1</session-id>"
            + "<in-bad-rpcs>0</in-bad-rpcs>"
            + "<in-rpcs>0</in-rpcs>"
            + "<login-time>2010-10-10T12:32:32Z</login-time>"
            + "<out-notifications>0</out-notifications>"
            + "<out-rpc-errors>0</out-rpc-errors>"
            + "<ncme:session-identifier>client</ncme:session-identifier>"
            + "<source-host>%s</source-host>"
            + "<transport>ncme:netconf-tcp</transport>"
            + "<username>username</username>"
            + "</session>";

    @Mock
    private NetconfMonitoringService monitoringService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(new SessionsBuilder().setSession(Lists.newArrayList(
                getMockIPv4Session(NetconfTcp.class),
                getMockIPv4Session(NetconfSsh.class),
                getMockIPv6Session(NetconfTcp.class),
                getMockIPv6Session(NetconfSsh.class)
        )).build())
                .when(monitoringService).getSessions();
        doReturn(new SchemasBuilder().setSchema(Lists.newArrayList(getMockSchema("id", "v1", Yang.class),
                getMockSchema("id2", "", Yang.class))).build()).when(monitoringService).getSchemas();
    }

    @Test
    public void testSerialization() throws Exception {

        final NetconfState model = new NetconfState(monitoringService);
        final String xml = XmlUtil.toString(new JaxBSerializer().toXml(model)).replaceAll("\\s", "");
        assertThat(xml, CoreMatchers.containsString(
                "<schema>"
                        + "<format>yang</format>"
                        + "<identifier>id</identifier>"
                        + "<location>NETCONF</location>"
                        + "<namespace>localhost</namespace>"
                        + "<version>v1</version>"
                        + "</schema>"));

        assertThat(xml, CoreMatchers.containsString(
                String.format(SESSION_XML, IPV4)));
        assertThat(xml, CoreMatchers.containsString(
                String.format(SESSION_XML, IPV6)));
    }

    private Schema getMockSchema(final String id, final String version, final Class<Yang> format) {
        final Schema mock = mock(Schema.class);

        doReturn(format).when(mock).getFormat();
        doReturn(id).when(mock).getIdentifier();
        doReturn(new Uri("localhost")).when(mock).getNamespace();
        doReturn(version).when(mock).getVersion();
        doReturn(Lists.newArrayList(new Schema.Location(Schema.Location.Enumeration.NETCONF))).when(mock).getLocation();
        doReturn(new SchemaKey(format, id, version)).when(mock).getKey();
        return mock;
    }

    private Session getMockIPv4Session(final Class<? extends Transport> transportType) {
        final Session mocked = getMockSession(transportType);
        doReturn(new Host(new IpAddress(new Ipv4Address(IPV4)))).when(mocked).getSourceHost();
        return mocked;
    }

    private Session getMockIPv6Session(final Class<? extends Transport> transportType) {
        final Session mocked = getMockSession(transportType);
        doReturn(new Host(new IpAddress(new Ipv6Address(IPV6)))).when(mocked).getSourceHost();
        return mocked;
    }

    private Session getMockSession(final Class<? extends Transport> transportType) {
        final Session mocked = mock(Session.class);
        final Session1 mockedSession1 = mock(Session1.class);
        doReturn("client").when(mockedSession1).getSessionIdentifier();
        doReturn(1L).when(mocked).getSessionId();
        doReturn(new DateAndTime("2010-10-10T12:32:32Z")).when(mocked).getLoginTime();
        doReturn(new ZeroBasedCounter32(0L)).when(mocked).getInBadRpcs();
        doReturn(new ZeroBasedCounter32(0L)).when(mocked).getInRpcs();
        doReturn(new ZeroBasedCounter32(0L)).when(mocked).getOutNotifications();
        doReturn(new ZeroBasedCounter32(0L)).when(mocked).getOutRpcErrors();
        doReturn(transportType).when(mocked).getTransport();
        doReturn("username").when(mocked).getUsername();
        doReturn(mockedSession1).when(mocked).getAugmentation(Session1.class);
        return mocked;
    }
}
