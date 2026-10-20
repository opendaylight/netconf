/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2026 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codegen.wadl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFile;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileLifecycle;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFilePath;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileType;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class WadlGeneratorTest {
    private static Map<GeneratedFilePath, Map<GeneratedFileType, GeneratedFile>> WADL_FILES;

    @BeforeAll
    static void before() {
        final var modelContext = YangParserTestUtils.parseYangResourceDirectory("/wadl-gen");
        WADL_FILES = assertThat(new WadlGenerator().generateFiles(modelContext, Set.copyOf(modelContext.getModules()),
            (module, representation) -> List.of()).columnMap())
            .hasSize(3)
            .actual();
    }

    @Test
    void testControllerNetwork() {
        assertTransientResource("controller-network", """
            <?xml version="1.0"?>
            <application xmlns="http://wadl.dev.java.net/2009/02" \
            xmlns:inet="urn:ietf:params:xml:ns:yang:ietf-inet-types"
             xmlns:topos="urn:opendaylight:controller:network">

                <grammars>
                    <include href="controller-network.yang"/>
                    <include href="Unqualified{localName=ietf-inet-types}.yang"/>
                </grammars>

                <resources base="http://localhost:9998/restconf">
                    <resource path="config">
                        <method name="POST">
                            <request>
                                <representation mediaType="application/xml" element="network"/>
                                <representation mediaType="text/xml" element="network"/>
                                <representation mediaType="application/json" element="network"/>
                                <representation mediaType="application/yang.data+xml" element="network"/>
                                <representation mediaType="application/yang.data+json" element="network"/>
                            </request>
                        </method>
                        <resource path="controller-network:network">
                            <method name="GET">
                                <response>
                                    <representation mediaType="application/xml" element="network"/>
                                    <representation mediaType="text/xml" element="network"/>
                                    <representation mediaType="application/json" element="network"/>
                                    <representation mediaType="application/yang.data+xml" element="network"/>
                                    <representation mediaType="application/yang.data+json" element="network"/>
                                </response>
                            </method>
                            <method name="DELETE" />
                            <method name="PUT">
                                <request>
                                    <representation mediaType="application/xml" element="network"/>
                                    <representation mediaType="text/xml" element="network"/>
                                    <representation mediaType="application/json" element="network"/>
                                    <representation mediaType="application/yang.data+xml" element="network"/>
                                    <representation mediaType="application/yang.data+json" element="network"/>
                                </request>
                            </method>
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="topologies"/>
                                    <representation mediaType="text/xml" element="topologies"/>
                                    <representation mediaType="application/json" element="topologies"/>
                                    <representation mediaType="application/yang.data+xml" element="topologies"/>
                                    <representation mediaType="application/yang.data+json" element="topologies"/>
                                </request>
                            </method>
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="network-elements"/>
                                    <representation mediaType="text/xml" element="network-elements"/>
                                    <representation mediaType="application/json" element="network-elements"/>
                                    <representation mediaType="application/yang.data+xml" element="network-elements"/>
                                    <representation mediaType="application/yang.data+json" element="network-elements"/>
                                </request>
                            </method>
                            <resource path="topologies">
                                <method name="GET">
                                    <response>
                                        <representation mediaType="application/xml" element="topologies"/>
                                        <representation mediaType="text/xml" element="topologies"/>
                                        <representation mediaType="application/json" element="topologies"/>
                                        <representation mediaType="application/yang.data+xml" element="topologies"/>
                                        <representation mediaType="application/yang.data+json" element="topologies"/>
                                    </response>
                                </method>
                                <method name="DELETE" />
                                <method name="PUT">
                                    <request>
                                        <representation mediaType="application/xml" element="topologies"/>
                                        <representation mediaType="text/xml" element="topologies"/>
                                        <representation mediaType="application/json" element="topologies"/>
                                        <representation mediaType="application/yang.data+xml" element="topologies"/>
                                        <representation mediaType="application/yang.data+json" element="topologies"/>
                                    </request>
                                </method>
                                <method name="POST">
                                    <request>
                                        <representation mediaType="application/xml" element="topology"/>
                                        <representation mediaType="text/xml" element="topology"/>
                                        <representation mediaType="application/json" element="topology"/>
                                        <representation mediaType="application/yang.data+xml" element="topology"/>
                                        <representation mediaType="application/yang.data+json" element="topology"/>
                                    </request>
                                </method>
                                <resource path="topology'/{topology-id}">
                                    <param required="true" style="template" name="topology-id" type="topology-id"/>
                                    <method name="GET">
                                        <response>
                                            <representation mediaType="application/xml" element="topology"/>
                                            <representation mediaType="text/xml" element="topology"/>
                                            <representation mediaType="application/json" element="topology"/>
                                            <representation mediaType="application/yang.data+xml" element="topology"/>
                                            <representation mediaType="application/yang.data+json" element="topology"/>
                                        </response>
                                    </method>
                                    <method name="DELETE" />
                                    <method name="PUT">
                                        <request>
                                            <representation mediaType="application/xml" element="topology"/>
                                            <representation mediaType="text/xml" element="topology"/>
                                            <representation mediaType="application/json" element="topology"/>
                                            <representation mediaType="application/yang.data+xml" element="topology"/>
                                            <representation mediaType="application/yang.data+json" element="topology"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="types"/>
                                            <representation mediaType="text/xml" element="types"/>
                                            <representation mediaType="application/json" element="types"/>
                                            <representation mediaType="application/yang.data+xml" element="types"/>
                                            <representation mediaType="application/yang.data+json" element="types"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="nodes"/>
                                            <representation mediaType="text/xml" element="nodes"/>
                                            <representation mediaType="application/json" element="nodes"/>
                                            <representation mediaType="application/yang.data+xml" element="nodes"/>
                                            <representation mediaType="application/yang.data+json" element="nodes"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="links"/>
                                            <representation mediaType="text/xml" element="links"/>
                                            <representation mediaType="application/json" element="links"/>
                                            <representation mediaType="application/yang.data+xml" element="links"/>
                                            <representation mediaType="application/yang.data+json" element="links"/>
                                        </request>
                                    </method>
                                    <resource path="types">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="types"/>
                                                <representation mediaType="text/xml" element="types"/>
                                                <representation mediaType="application/json" element="types"/>
                                                <representation mediaType="application/yang.data+xml" element="types"/>
                                                <representation mediaType="application/yang.data+json" element="types"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="types"/>
                                                <representation mediaType="text/xml" element="types"/>
                                                <representation mediaType="application/json" element="types"/>
                                                <representation mediaType="application/yang.data+xml" element="types"/>
                                                <representation mediaType="application/yang.data+json" element="types"/>
                                            </request>
                                        </method>
                                    </resource>
                                    <resource path="nodes">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="nodes"/>
                                                <representation mediaType="text/xml" element="nodes"/>
                                                <representation mediaType="application/json" element="nodes"/>
                                                <representation mediaType="application/yang.data+xml" element="nodes"/>
                                                <representation mediaType="application/yang.data+json" element="nodes"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="nodes"/>
                                                <representation mediaType="text/xml" element="nodes"/>
                                                <representation mediaType="application/json" element="nodes"/>
                                                <representation mediaType="application/yang.data+xml" element="nodes"/>
                                                <representation mediaType="application/yang.data+json" element="nodes"/>
                                            </request>
                                        </method>
                                        <method name="POST">
                                            <request>
                                                <representation mediaType="application/xml" element="node"/>
                                                <representation mediaType="text/xml" element="node"/>
                                                <representation mediaType="application/json" element="node"/>
                                                <representation mediaType="application/yang.data+xml" element="node"/>
                                                <representation mediaType="application/yang.data+json" element="node"/>
                                            </request>
                                        </method>
                                        <resource path="node'/{node-id}">
                                            <param required="true" style="template" name="node-id" type="node-id"/>
                                            <method name="GET">
                                                <response>
                                                    <representation mediaType="application/xml" element="node"/>
                                                    <representation mediaType="text/xml" element="node"/>
                                                    <representation mediaType="application/json" element="node"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="node"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="node"/>
                                                </response>
                                            </method>
                                            <method name="DELETE" />
                                            <method name="PUT">
                                                <request>
                                                    <representation mediaType="application/xml" element="node"/>
                                                    <representation mediaType="text/xml" element="node"/>
                                                    <representation mediaType="application/json" element="node"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="node"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="node"/>
                                                </request>
                                            </method>
                                            <method name="POST">
                                                <request>
                                                    <representation mediaType="application/xml" \
            element="termination-points"/>
                                                    <representation mediaType="text/xml" element="termination-points"/>
                                                    <representation mediaType="application/json" \
            element="termination-points"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="termination-points"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="termination-points"/>
                                                </request>
                                            </method>
                                            <resource path="termination-points">
                                                <method name="GET">
                                                    <response>
                                                        <representation mediaType="application/xml" \
            element="termination-points"/>
                                                        <representation mediaType="text/xml" \
            element="termination-points"/>
                                                        <representation mediaType="application/json" \
            element="termination-points"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="termination-points"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="termination-points"/>
                                                    </response>
                                                </method>
                                                <method name="DELETE" />
                                                <method name="PUT">
                                                    <request>
                                                        <representation mediaType="application/xml" \
            element="termination-points"/>
                                                        <representation mediaType="text/xml" \
            element="termination-points"/>
                                                        <representation mediaType="application/json" \
            element="termination-points"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="termination-points"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="termination-points"/>
                                                    </request>
                                                </method>
                                                <method name="POST">
                                                    <request>
                                                        <representation mediaType="application/xml" \
            element="termination-point"/>
                                                        <representation mediaType="text/xml" \
            element="termination-point"/>
                                                        <representation mediaType="application/json" \
            element="termination-point"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="termination-point"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="termination-point"/>
                                                    </request>
                                                </method>
                                                <resource path="termination-point'/{tp-id}">
                                                    <param required="true" style="template" name="tp-id" type="tp-id"/>
                                                    <method name="GET">
                                                        <response>
                                                            <representation mediaType="application/xml" \
            element="termination-point"/>
                                                            <representation mediaType="text/xml" \
            element="termination-point"/>
                                                            <representation mediaType="application/json" \
            element="termination-point"/>
                                                            <representation mediaType="application/yang.data+xml" \
            element="termination-point"/>
                                                            <representation mediaType="application/yang.data+json" \
            element="termination-point"/>
                                                        </response>
                                                    </method>
                                                    <method name="DELETE" />
                                                    <method name="PUT">
                                                        <request>
                                                            <representation mediaType="application/xml" \
            element="termination-point"/>
                                                            <representation mediaType="text/xml" \
            element="termination-point"/>
                                                            <representation mediaType="application/json" \
            element="termination-point"/>
                                                            <representation mediaType="application/yang.data+xml" \
            element="termination-point"/>
                                                            <representation mediaType="application/yang.data+json" \
            element="termination-point"/>
                                                        </request>
                                                    </method>
                                                </resource>
                                            </resource>
                                        </resource>
                                    </resource>
                                    <resource path="links">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="links"/>
                                                <representation mediaType="text/xml" element="links"/>
                                                <representation mediaType="application/json" element="links"/>
                                                <representation mediaType="application/yang.data+xml" element="links"/>
                                                <representation mediaType="application/yang.data+json" element="links"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="links"/>
                                                <representation mediaType="text/xml" element="links"/>
                                                <representation mediaType="application/json" element="links"/>
                                                <representation mediaType="application/yang.data+xml" element="links"/>
                                                <representation mediaType="application/yang.data+json" element="links"/>
                                            </request>
                                        </method>
                                        <method name="POST">
                                            <request>
                                                <representation mediaType="application/xml" element="link"/>
                                                <representation mediaType="text/xml" element="link"/>
                                                <representation mediaType="application/json" element="link"/>
                                                <representation mediaType="application/yang.data+xml" element="link"/>
                                                <representation mediaType="application/yang.data+json" element="link"/>
                                            </request>
                                        </method>
                                        <resource path="link'/{link-id}">
                                            <param required="true" style="template" name="link-id" type="link-id"/>
                                            <method name="GET">
                                                <response>
                                                    <representation mediaType="application/xml" element="link"/>
                                                    <representation mediaType="text/xml" element="link"/>
                                                    <representation mediaType="application/json" element="link"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="link"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="link"/>
                                                </response>
                                            </method>
                                            <method name="DELETE" />
                                            <method name="PUT">
                                                <request>
                                                    <representation mediaType="application/xml" element="link"/>
                                                    <representation mediaType="text/xml" element="link"/>
                                                    <representation mediaType="application/json" element="link"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="link"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="link"/>
                                                </request>
                                            </method>
                                            <method name="POST">
                                                <request>
                                                    <representation mediaType="application/xml" element="source"/>
                                                    <representation mediaType="text/xml" element="source"/>
                                                    <representation mediaType="application/json" element="source"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="source"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="source"/>
                                                </request>
                                            </method>
                                            <method name="POST">
                                                <request>
                                                    <representation mediaType="application/xml" element="destination"/>
                                                    <representation mediaType="text/xml" element="destination"/>
                                                    <representation mediaType="application/json" element="destination"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                                </request>
                                            </method>
                                            <resource path="source">
                                                <method name="GET">
                                                    <response>
                                                        <representation mediaType="application/xml" element="source"/>
                                                        <representation mediaType="text/xml" element="source"/>
                                                        <representation mediaType="application/json" element="source"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="source"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="source"/>
                                                    </response>
                                                </method>
                                                <method name="DELETE" />
                                                <method name="PUT">
                                                    <request>
                                                        <representation mediaType="application/xml" element="source"/>
                                                        <representation mediaType="text/xml" element="source"/>
                                                        <representation mediaType="application/json" element="source"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="source"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="source"/>
                                                    </request>
                                                </method>
                                            </resource>
                                            <resource path="destination">
                                                <method name="GET">
                                                    <response>
                                                        <representation mediaType="application/xml" \
            element="destination"/>
                                                        <representation mediaType="text/xml" element="destination"/>
                                                        <representation mediaType="application/json" \
            element="destination"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                                    </response>
                                                </method>
                                                <method name="DELETE" />
                                                <method name="PUT">
                                                    <request>
                                                        <representation mediaType="application/xml" \
            element="destination"/>
                                                        <representation mediaType="text/xml" element="destination"/>
                                                        <representation mediaType="application/json" \
            element="destination"/>
                                                        <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                                        <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                                    </request>
                                                </method>
                                            </resource>
                                        </resource>
                                    </resource>
                                </resource>
                            </resource>
                            <resource path="network-elements">
                                <method name="GET">
                                    <response>
                                        <representation mediaType="application/xml" element="network-elements"/>
                                        <representation mediaType="text/xml" element="network-elements"/>
                                        <representation mediaType="application/json" element="network-elements"/>
                                        <representation mediaType="application/yang.data+xml" \
            element="network-elements"/>
                                        <representation mediaType="application/yang.data+json" \
            element="network-elements"/>
                                    </response>
                                </method>
                                <method name="DELETE" />
                                <method name="PUT">
                                    <request>
                                        <representation mediaType="application/xml" element="network-elements"/>
                                        <representation mediaType="text/xml" element="network-elements"/>
                                        <representation mediaType="application/json" element="network-elements"/>
                                        <representation mediaType="application/yang.data+xml" \
            element="network-elements"/>
                                        <representation mediaType="application/yang.data+json" \
            element="network-elements"/>
                                    </request>
                                </method>
                                <method name="POST">
                                    <request>
                                        <representation mediaType="application/xml" element="network-element"/>
                                        <representation mediaType="text/xml" element="network-element"/>
                                        <representation mediaType="application/json" element="network-element"/>
                                        <representation mediaType="application/yang.data+xml" \
            element="network-element"/>
                                        <representation mediaType="application/yang.data+json" \
            element="network-element"/>
                                    </request>
                                </method>
                                <resource path="network-element'/{element-id}">
                                    <param required="true" style="template" name="element-id" type="element-id"/>
                                    <method name="GET">
                                        <response>
                                            <representation mediaType="application/xml" element="network-element"/>
                                            <representation mediaType="text/xml" element="network-element"/>
                                            <representation mediaType="application/json" element="network-element"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-element"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-element"/>
                                        </response>
                                    </method>
                                    <method name="DELETE" />
                                    <method name="PUT">
                                        <request>
                                            <representation mediaType="application/xml" element="network-element"/>
                                            <representation mediaType="text/xml" element="network-element"/>
                                            <representation mediaType="application/json" element="network-element"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-element"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-element"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="ports"/>
                                            <representation mediaType="text/xml" element="ports"/>
                                            <representation mediaType="application/json" element="ports"/>
                                            <representation mediaType="application/yang.data+xml" element="ports"/>
                                            <representation mediaType="application/yang.data+json" element="ports"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="flow-tables"/>
                                            <representation mediaType="text/xml" element="flow-tables"/>
                                            <representation mediaType="application/json" element="flow-tables"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="flow-tables"/>
                                            <representation mediaType="application/yang.data+json" \
            element="flow-tables"/>
                                        </request>
                                    </method>
                                    <resource path="ports">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="ports"/>
                                                <representation mediaType="text/xml" element="ports"/>
                                                <representation mediaType="application/json" element="ports"/>
                                                <representation mediaType="application/yang.data+xml" element="ports"/>
                                                <representation mediaType="application/yang.data+json" element="ports"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="ports"/>
                                                <representation mediaType="text/xml" element="ports"/>
                                                <representation mediaType="application/json" element="ports"/>
                                                <representation mediaType="application/yang.data+xml" element="ports"/>
                                                <representation mediaType="application/yang.data+json" element="ports"/>
                                            </request>
                                        </method>
                                        <method name="POST">
                                            <request>
                                                <representation mediaType="application/xml" element="port"/>
                                                <representation mediaType="text/xml" element="port"/>
                                                <representation mediaType="application/json" element="port"/>
                                                <representation mediaType="application/yang.data+xml" element="port"/>
                                                <representation mediaType="application/yang.data+json" element="port"/>
                                            </request>
                                        </method>
                                        <resource path="port'/{logical-port-id}">
                                            <param required="true" style="template" name="logical-port-id" \
            type="int32"/>
                                            <method name="GET">
                                                <response>
                                                    <representation mediaType="application/xml" element="port"/>
                                                    <representation mediaType="text/xml" element="port"/>
                                                    <representation mediaType="application/json" element="port"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="port"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="port"/>
                                                </response>
                                            </method>
                                            <method name="DELETE" />
                                            <method name="PUT">
                                                <request>
                                                    <representation mediaType="application/xml" element="port"/>
                                                    <representation mediaType="text/xml" element="port"/>
                                                    <representation mediaType="application/json" element="port"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="port"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="port"/>
                                                </request>
                                            </method>
                                        </resource>
                                    </resource>
                                    <resource path="flow-tables">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="flow-tables"/>
                                                <representation mediaType="text/xml" element="flow-tables"/>
                                                <representation mediaType="application/json" element="flow-tables"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="flow-tables"/>
                                                <representation mediaType="application/yang.data+json" \
            element="flow-tables"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="flow-tables"/>
                                                <representation mediaType="text/xml" element="flow-tables"/>
                                                <representation mediaType="application/json" element="flow-tables"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="flow-tables"/>
                                                <representation mediaType="application/yang.data+json" \
            element="flow-tables"/>
                                            </request>
                                        </method>
                                        <method name="POST">
                                            <request>
                                                <representation mediaType="application/xml" element="flow-table"/>
                                                <representation mediaType="text/xml" element="flow-table"/>
                                                <representation mediaType="application/json" element="flow-table"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="flow-table"/>
                                                <representation mediaType="application/yang.data+json" \
            element="flow-table"/>
                                            </request>
                                        </method>
                                        <resource path="flow-table'/{flow-table-id}">
                                            <param required="true" style="template" name="flow-table-id" type="string"/>
                                            <method name="GET">
                                                <response>
                                                    <representation mediaType="application/xml" element="flow-table"/>
                                                    <representation mediaType="text/xml" element="flow-table"/>
                                                    <representation mediaType="application/json" element="flow-table"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="flow-table"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="flow-table"/>
                                                </response>
                                            </method>
                                            <method name="DELETE" />
                                            <method name="PUT">
                                                <request>
                                                    <representation mediaType="application/xml" element="flow-table"/>
                                                    <representation mediaType="text/xml" element="flow-table"/>
                                                    <representation mediaType="application/json" element="flow-table"/>
                                                    <representation mediaType="application/yang.data+xml" \
            element="flow-table"/>
                                                    <representation mediaType="application/yang.data+json" \
            element="flow-table"/>
                                                </request>
                                            </method>
                                        </resource>
                                    </resource>
                                </resource>
                            </resource>
                        </resource>
                    </resource>
                    <resource path="operations">
                        <resource path="controller-network:activate-software-image">
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="input"/>
                                    <representation mediaType="text/xml" element="input"/>
                                    <representation mediaType="application/json" element="input"/>
                                    <representation mediaType="application/yang.data+xml" element="input"/>
                                    <representation mediaType="application/yang.data+json" element="input"/>
                                </request>
                                <response>
                                    <representation mediaType="application/xml" element="output"/>
                                    <representation mediaType="text/xml" element="output"/>
                                    <representation mediaType="application/json" element="output"/>
                                    <representation mediaType="application/yang.data+xml" element="output"/>
                                    <representation mediaType="application/yang.data+json" element="output"/>
                                </response>
                            </method>
                        </resource>
                        <resource path="controller-network:rock-the-house">
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="input"/>
                                    <representation mediaType="text/xml" element="input"/>
                                    <representation mediaType="application/json" element="input"/>
                                    <representation mediaType="application/yang.data+xml" element="input"/>
                                    <representation mediaType="application/yang.data+json" element="input"/>
                                </request>
                                <response>
                                    <representation mediaType="application/xml" element="output"/>
                                    <representation mediaType="text/xml" element="output"/>
                                    <representation mediaType="application/json" element="output"/>
                                    <representation mediaType="application/yang.data+xml" element="output"/>
                                    <representation mediaType="application/yang.data+json" element="output"/>
                                </response>
                            </method>
                        </resource>
                    </resource>
                </resources>
            </application>
            """);
    }

    @Test
    void testControllerNetworkRpcs() {
        assertTransientResource("controller-network-rpcs", """
            <?xml version="1.0"?>
            <application xmlns="http://wadl.dev.java.net/2009/02" \
            xmlns:inet="urn:ietf:params:xml:ns:yang:ietf-inet-types"
             xmlns:topos="urn:opendaylight:controller:network:rpcs">

                <grammars>
                    <include href="controller-network-rpcs.yang"/>
                    <include href="Unqualified{localName=ietf-inet-types}.yang"/>
                </grammars>

                <resources base="http://localhost:9998/restconf">
                    <resource path="operations">
                        <resource path="controller-network-rpcs:activate-software-image">
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="input"/>
                                    <representation mediaType="text/xml" element="input"/>
                                    <representation mediaType="application/json" element="input"/>
                                    <representation mediaType="application/yang.data+xml" element="input"/>
                                    <representation mediaType="application/yang.data+json" element="input"/>
                                </request>
                                <response>
                                    <representation mediaType="application/xml" element="output"/>
                                    <representation mediaType="text/xml" element="output"/>
                                    <representation mediaType="application/json" element="output"/>
                                    <representation mediaType="application/yang.data+xml" element="output"/>
                                    <representation mediaType="application/yang.data+json" element="output"/>
                                </response>
                            </method>
                        </resource>
                        <resource path="controller-network-rpcs:rock-the-house">
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="input"/>
                                    <representation mediaType="text/xml" element="input"/>
                                    <representation mediaType="application/json" element="input"/>
                                    <representation mediaType="application/yang.data+xml" element="input"/>
                                    <representation mediaType="application/yang.data+json" element="input"/>
                                </request>
                                <response>
                                    <representation mediaType="application/xml" element="output"/>
                                    <representation mediaType="text/xml" element="output"/>
                                    <representation mediaType="application/json" element="output"/>
                                    <representation mediaType="application/yang.data+xml" element="output"/>
                                    <representation mediaType="application/yang.data+json" element="output"/>
                                </response>
                            </method>
                        </resource>
                    </resource>
                </resources>
            </application>
            """);
    }

    @Test
    void testDemoTopology() {
        assertTransientResource("demo-topology", """
            <?xml version="1.0"?>
            <application xmlns="http://wadl.dev.java.net/2009/02"  xmlns:tp="urn:model.1demo-275topology.4.5.my">

                <grammars>
                    <include href="demo-topology.yang"/>
                </grammars>

                <resources base="http://localhost:9998/restconf">
                    <resource path="config">
                        <method name="POST">
                            <request>
                                <representation mediaType="application/xml" element="topology"/>
                                <representation mediaType="text/xml" element="topology"/>
                                <representation mediaType="application/json" element="topology"/>
                                <representation mediaType="application/yang.data+xml" element="topology"/>
                                <representation mediaType="application/yang.data+json" element="topology"/>
                            </request>
                        </method>
                        <resource path="demo-topology:topology">
                            <method name="GET">
                                <response>
                                    <representation mediaType="application/xml" element="topology"/>
                                    <representation mediaType="text/xml" element="topology"/>
                                    <representation mediaType="application/json" element="topology"/>
                                    <representation mediaType="application/yang.data+xml" element="topology"/>
                                    <representation mediaType="application/yang.data+json" element="topology"/>
                                </response>
                            </method>
                            <method name="DELETE" />
                            <method name="PUT">
                                <request>
                                    <representation mediaType="application/xml" element="topology"/>
                                    <representation mediaType="text/xml" element="topology"/>
                                    <representation mediaType="application/json" element="topology"/>
                                    <representation mediaType="application/yang.data+xml" element="topology"/>
                                    <representation mediaType="application/yang.data+json" element="topology"/>
                                </request>
                            </method>
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="network-nodes"/>
                                    <representation mediaType="text/xml" element="network-nodes"/>
                                    <representation mediaType="application/json" element="network-nodes"/>
                                    <representation mediaType="application/yang.data+xml" element="network-nodes"/>
                                    <representation mediaType="application/yang.data+json" element="network-nodes"/>
                                </request>
                            </method>
                            <method name="POST">
                                <request>
                                    <representation mediaType="application/xml" element="network-links"/>
                                    <representation mediaType="text/xml" element="network-links"/>
                                    <representation mediaType="application/json" element="network-links"/>
                                    <representation mediaType="application/yang.data+xml" element="network-links"/>
                                    <representation mediaType="application/yang.data+json" element="network-links"/>
                                </request>
                            </method>
                            <resource path="network-nodes">
                                <method name="GET">
                                    <response>
                                        <representation mediaType="application/xml" element="network-nodes"/>
                                        <representation mediaType="text/xml" element="network-nodes"/>
                                        <representation mediaType="application/json" element="network-nodes"/>
                                        <representation mediaType="application/yang.data+xml" element="network-nodes"/>
                                        <representation mediaType="application/yang.data+json" element="network-nodes"/>
                                    </response>
                                </method>
                                <method name="DELETE" />
                                <method name="PUT">
                                    <request>
                                        <representation mediaType="application/xml" element="network-nodes"/>
                                        <representation mediaType="text/xml" element="network-nodes"/>
                                        <representation mediaType="application/json" element="network-nodes"/>
                                        <representation mediaType="application/yang.data+xml" element="network-nodes"/>
                                        <representation mediaType="application/yang.data+json" element="network-nodes"/>
                                    </request>
                                </method>
                                <method name="POST">
                                    <request>
                                        <representation mediaType="application/xml" element="network-node"/>
                                        <representation mediaType="text/xml" element="network-node"/>
                                        <representation mediaType="application/json" element="network-node"/>
                                        <representation mediaType="application/yang.data+xml" element="network-node"/>
                                        <representation mediaType="application/yang.data+json" element="network-node"/>
                                    </request>
                                </method>
                                <resource path="network-node'/{node-id}">
                                    <param required="true" style="template" name="node-id" type="string"/>
                                    <method name="GET">
                                        <response>
                                            <representation mediaType="application/xml" element="network-node"/>
                                            <representation mediaType="text/xml" element="network-node"/>
                                            <representation mediaType="application/json" element="network-node"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-node"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-node"/>
                                        </response>
                                    </method>
                                    <method name="DELETE" />
                                    <method name="PUT">
                                        <request>
                                            <representation mediaType="application/xml" element="network-node"/>
                                            <representation mediaType="text/xml" element="network-node"/>
                                            <representation mediaType="application/json" element="network-node"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-node"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-node"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="network-interface"/>
                                            <representation mediaType="text/xml" element="network-interface"/>
                                            <representation mediaType="application/json" element="network-interface"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-interface"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-interface"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="node-attributes"/>
                                            <representation mediaType="text/xml" element="node-attributes"/>
                                            <representation mediaType="application/json" element="node-attributes"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="node-attributes"/>
                                            <representation mediaType="application/yang.data+json" \
            element="node-attributes"/>
                                        </request>
                                    </method>
                                    <resource path="network-interface'/{interface-id}">
                                        <param required="true" style="template" name="interface-id" type="uint8"/>
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" \
            element="network-interface"/>
                                                <representation mediaType="text/xml" element="network-interface"/>
                                                <representation mediaType="application/json" \
            element="network-interface"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="network-interface"/>
                                                <representation mediaType="application/yang.data+json" \
            element="network-interface"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" \
            element="network-interface"/>
                                                <representation mediaType="text/xml" element="network-interface"/>
                                                <representation mediaType="application/json" \
            element="network-interface"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="network-interface"/>
                                                <representation mediaType="application/yang.data+json" \
            element="network-interface"/>
                                            </request>
                                        </method>
                                    </resource>
                                    <resource path="node-attributes">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="node-attributes"/>
                                                <representation mediaType="text/xml" element="node-attributes"/>
                                                <representation mediaType="application/json" element="node-attributes"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="node-attributes"/>
                                                <representation mediaType="application/yang.data+json" \
            element="node-attributes"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="node-attributes"/>
                                                <representation mediaType="text/xml" element="node-attributes"/>
                                                <representation mediaType="application/json" element="node-attributes"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="node-attributes"/>
                                                <representation mediaType="application/yang.data+json" \
            element="node-attributes"/>
                                            </request>
                                        </method>
                                    </resource>
                                </resource>
                            </resource>
                            <resource path="network-links">
                                <method name="GET">
                                    <response>
                                        <representation mediaType="application/xml" element="network-links"/>
                                        <representation mediaType="text/xml" element="network-links"/>
                                        <representation mediaType="application/json" element="network-links"/>
                                        <representation mediaType="application/yang.data+xml" element="network-links"/>
                                        <representation mediaType="application/yang.data+json" element="network-links"/>
                                    </response>
                                </method>
                                <method name="DELETE" />
                                <method name="PUT">
                                    <request>
                                        <representation mediaType="application/xml" element="network-links"/>
                                        <representation mediaType="text/xml" element="network-links"/>
                                        <representation mediaType="application/json" element="network-links"/>
                                        <representation mediaType="application/yang.data+xml" element="network-links"/>
                                        <representation mediaType="application/yang.data+json" element="network-links"/>
                                    </request>
                                </method>
                                <method name="POST">
                                    <request>
                                        <representation mediaType="application/xml" element="network-link"/>
                                        <representation mediaType="text/xml" element="network-link"/>
                                        <representation mediaType="application/json" element="network-link"/>
                                        <representation mediaType="application/yang.data+xml" element="network-link"/>
                                        <representation mediaType="application/yang.data+json" element="network-link"/>
                                    </request>
                                </method>
                                <resource path="network-link'/{link-id}">
                                    <param required="true" style="template" name="link-id" type="string"/>
                                    <method name="GET">
                                        <response>
                                            <representation mediaType="application/xml" element="network-link"/>
                                            <representation mediaType="text/xml" element="network-link"/>
                                            <representation mediaType="application/json" element="network-link"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-link"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-link"/>
                                        </response>
                                    </method>
                                    <method name="DELETE" />
                                    <method name="PUT">
                                        <request>
                                            <representation mediaType="application/xml" element="network-link"/>
                                            <representation mediaType="text/xml" element="network-link"/>
                                            <representation mediaType="application/json" element="network-link"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="network-link"/>
                                            <representation mediaType="application/yang.data+json" \
            element="network-link"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="source"/>
                                            <representation mediaType="text/xml" element="source"/>
                                            <representation mediaType="application/json" element="source"/>
                                            <representation mediaType="application/yang.data+xml" element="source"/>
                                            <representation mediaType="application/yang.data+json" element="source"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="destination"/>
                                            <representation mediaType="text/xml" element="destination"/>
                                            <representation mediaType="application/json" element="destination"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                            <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                        </request>
                                    </method>
                                    <method name="POST">
                                        <request>
                                            <representation mediaType="application/xml" element="link-attributes"/>
                                            <representation mediaType="text/xml" element="link-attributes"/>
                                            <representation mediaType="application/json" element="link-attributes"/>
                                            <representation mediaType="application/yang.data+xml" \
            element="link-attributes"/>
                                            <representation mediaType="application/yang.data+json" \
            element="link-attributes"/>
                                        </request>
                                    </method>
                                    <resource path="source">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="source"/>
                                                <representation mediaType="text/xml" element="source"/>
                                                <representation mediaType="application/json" element="source"/>
                                                <representation mediaType="application/yang.data+xml" element="source"/>
                                                <representation mediaType="application/yang.data+json" \
            element="source"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="source"/>
                                                <representation mediaType="text/xml" element="source"/>
                                                <representation mediaType="application/json" element="source"/>
                                                <representation mediaType="application/yang.data+xml" element="source"/>
                                                <representation mediaType="application/yang.data+json" \
            element="source"/>
                                            </request>
                                        </method>
                                    </resource>
                                    <resource path="destination">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="destination"/>
                                                <representation mediaType="text/xml" element="destination"/>
                                                <representation mediaType="application/json" element="destination"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                                <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="destination"/>
                                                <representation mediaType="text/xml" element="destination"/>
                                                <representation mediaType="application/json" element="destination"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="destination"/>
                                                <representation mediaType="application/yang.data+json" \
            element="destination"/>
                                            </request>
                                        </method>
                                    </resource>
                                    <resource path="link-attributes">
                                        <method name="GET">
                                            <response>
                                                <representation mediaType="application/xml" element="link-attributes"/>
                                                <representation mediaType="text/xml" element="link-attributes"/>
                                                <representation mediaType="application/json" element="link-attributes"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="link-attributes"/>
                                                <representation mediaType="application/yang.data+json" \
            element="link-attributes"/>
                                            </response>
                                        </method>
                                        <method name="DELETE" />
                                        <method name="PUT">
                                            <request>
                                                <representation mediaType="application/xml" element="link-attributes"/>
                                                <representation mediaType="text/xml" element="link-attributes"/>
                                                <representation mediaType="application/json" element="link-attributes"/>
                                                <representation mediaType="application/yang.data+xml" \
            element="link-attributes"/>
                                                <representation mediaType="application/yang.data+json" \
            element="link-attributes"/>
                                            </request>
                                        </method>
                                    </resource>
                                </resource>
                            </resource>
                        </resource>
                    </resource>
                </resources>
            </application>
            """);
    }

    private static void assertTransientResource(final String moduleName, final String expected) {
        final var typeToFile = assertThat(WADL_FILES.get(GeneratedFilePath.ofPath(Path.of(moduleName + ".wadl"))))
            .hasSize(1)
            .actual();
        final var file = typeToFile.get(GeneratedFileType.RESOURCE);
        assertNotNull(file);
        assertEquals(GeneratedFileLifecycle.TRANSIENT, file.getLifecycle());

        final var baos = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> file.writeBody(baos));
        assertEquals(expected, new String(baos.toByteArray(), StandardCharsets.UTF_8));
    }
}
