/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli;

import static org.junit.Assert.assertNotNull;
import static org.opendaylight.netconf.cli.io.IOUtil.PROMPT_SUFIX;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@Ignore
public class NetconfCliTest {

    private static SchemaContext loadSchemaContext(final String resourceDirectory) throws Exception {
        final URI uri = NetconfCliTest.class.getResource(resourceDirectory).toURI();
        final File testDir = new File(uri);
        final String[] fileList = testDir.list();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory);
        }
        final List<InputStream> streams = new ArrayList<>();
        for (final String fileName : fileList) {
            NetconfCliTest.class.getResourceAsStream(fileName);
        }
        return YangParserTestUtils.parseYangStreams(streams);
    }

    @Test
    public void cliTest() throws Exception {

        final SchemaContext schemaContext = loadSchemaContext("/schema-context");
        assertNotNull(schemaContext);

        final DataSchemaNode cont1 = findTopLevelElement("ns:model1", "2014-05-14", "cont1", schemaContext);
        final Map<String, Deque<String>> values = new HashMap<>();

        values.put(prompt("/cont1/cont11/lst111/[entry]/lf1111"), value("55", "32"));
        values.put(prompt("/cont1/cont11/lst111/[entry]"), value("Y", "Y"));
        values.put(prompt("/cont1/cont11/lst111/[entry]/cont111/lf1112"),
                value("value for lf1112", "2value for lf1112"));
        values.put(prompt("/cont1/cont11/lst111/[entry]/cont111/lflst1111"), value("Y", "N", "Y", "N"));
        values.put(prompt("/cont1/cont11/lst111/[entry]/cont111/lflst1111/[entry]"), value("10", "15", "20", "30"));

        values.put(prompt("/cont1/cont11/lst111"), value("Y", "N"));

        values.put(prompt("/cont1/cont12/chcA"), value("AB"));
        values.put(prompt("/cont1/cont12/chcA/cont12AB1/lf12AB1"), value("value for lf12AB1"));

        values.put(prompt("/cont1/cont12/lst121/[entry]/lf1211"), value("value for lf12112", "2value for lf12112"));
        values.put(prompt("/cont1/cont12/lst121/[entry]"), value("Y", "Y"));
        values.put(prompt("/cont1/cont12/lst121/[entry]/lst1211"), value("Y", "N", "Y", "N"));
        values.put(prompt("/cont1/cont12/lst121/[entry]/lst1211/[entry]"), value("Y", "Y", "Y", "Y"));
        values.put(prompt("/cont1/cont12/lst121/[entry]/lst1211/[entry]/lf12111"), value("5", "10", "21", "50"));
        values.put(prompt("/cont1/cont12/lst121/[entry]/lst1211/[entry]/lf12112"),
                value("value for lf12112", "2value for lf12112", "3value for lf12112", "4value for lf12112"));

        values.put(prompt("/cont1/cont12/lst121"), value("Y", "N"));

        values.put(prompt("/cont1/cont12/lst122"), value("Y", "N"));

        values.put(prompt("/cont1/lst11"), value("Y", "Y", "N"));
        values.put(prompt("/cont1/lst11/[entry]"), value("Y", "Y", "Y"));
        values.put(prompt("/cont1/lst11/[entry]/lf111"),
                value("1value for lf111", "2value for lf111", "3value for lf111"));

        values.put(prompt("/cont1/cont12/data"), value("<el1><el11>value</el11><el12>value1</el12></el1>"));

        final List<ValueForMessage> valuesForMessages = new ArrayList<>();
        valuesForMessages.add(new ValueForMessage("Y", "lst111", "[Y|N]"));
        valuesForMessages.add(new ValueForMessage("Y", "lst121", "[Y|N]"));
        valuesForMessages.add(new ValueForMessage("Y", "lst11", "[Y|N]"));

        final ConsoleIOTestImpl console = new ConsoleIOTestImpl(values, valuesForMessages);

//        final List<Node<?>> redData = new GenericReader(console, new CommandArgHandlerRegistry(console,
//                new SchemaContextRegistry(schemaContext)), schemaContext).read(cont1);
//        assertNotNull(redData);
//        assertEquals(1, redData.size());
//
//        assertTrue(redData.get(0) instanceof CompositeNode);
//        final CompositeNode redTopLevelNode = (CompositeNode) redData.get(0);

        //new NormalizedNodeWriter(console, new OutFormatter()).write(cont1, redData);

    }

    private static Deque<String> value(final String... values) {
        return new ArrayDeque<>(Arrays.asList(values));
    }

    private static String prompt(final String path) {
        return "/localhost" + path + PROMPT_SUFIX;
    }

    private static DataSchemaNode findTopLevelElement(final String namespace, final String revision,
            final String topLevelElement, final SchemaContext schemaContext) {
        final QName requiredElement = QName.create(namespace, revision, topLevelElement);
        for (final DataSchemaNode dataSchemaNode : schemaContext.getChildNodes()) {
            if (dataSchemaNode.getQName().equals(requiredElement)) {
                return dataSchemaNode;
            }
        }
        return null;

    }

}
