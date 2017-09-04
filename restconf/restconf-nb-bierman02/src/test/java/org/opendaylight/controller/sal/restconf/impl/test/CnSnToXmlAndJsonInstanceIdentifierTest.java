/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class CnSnToXmlAndJsonInstanceIdentifierTest extends YangAndXmlAndDataSchemaLoader {

    @BeforeClass
    public static void initialize() throws FileNotFoundException, ReactorException {
        dataLoad("/instanceidentifier/yang", 4, "instance-identifier-module", "cont");
    }


    private static void validateXmlOutput(final String xml) throws XMLStreamException {
        final XMLInputFactory xmlInFactory = XMLInputFactory.newInstance();
        XMLEventReader eventReader;

        eventReader = xmlInFactory.createXMLEventReader(new ByteArrayInputStream(xml.getBytes()));
        String augmentAugmentModulePrefix = null;
        String augmentModulePrefix = null;
        String instanceIdentifierModulePrefix = null;
        while (eventReader.hasNext()) {
            final XMLEvent nextEvent = eventReader.nextEvent();
            if (nextEvent.isStartElement()) {
                final StartElement startElement = (StartElement) nextEvent;
                if (startElement.getName().getLocalPart().equals("lf111")) {
                    final Iterator<?> prefixes =
                            startElement.getNamespaceContext().getPrefixes("augment:augment:module");

                    while (prefixes.hasNext() && augmentAugmentModulePrefix == null) {
                        final String prefix = (String) prefixes.next();
                        if (!prefix.isEmpty()) {
                            augmentAugmentModulePrefix = prefix;
                        }
                    }

                    augmentModulePrefix = startElement.getNamespaceContext().getPrefix("augment:module");
                    instanceIdentifierModulePrefix =
                            startElement.getNamespaceContext().getPrefix("instance:identifier:module");
                    break;
                }
            }
        }

        assertNotNull(augmentAugmentModulePrefix);
        assertNotNull(augmentModulePrefix);
        assertNotNull(instanceIdentifierModulePrefix);

        final String instanceIdentifierValue = "/" + instanceIdentifierModulePrefix + ":cont/"
                + instanceIdentifierModulePrefix + ":cont1/" + augmentModulePrefix + ":lst11[" + augmentModulePrefix
                + ":keyvalue111='value1'][" + augmentModulePrefix + ":keyvalue112='value2']/"
                + augmentAugmentModulePrefix + ":lf112";

        assertTrue(xml.contains(instanceIdentifierValue));

    }

    private static void validateXmlOutputWithLeafList(final String xml) throws XMLStreamException {
        final XMLInputFactory xmlInFactory = XMLInputFactory.newInstance();
        XMLEventReader eventReader;

        eventReader = xmlInFactory.createXMLEventReader(new ByteArrayInputStream(xml.getBytes()));
        String augmentModuleLfLstPrefix = null;
        String iiModulePrefix = null;
        while (eventReader.hasNext()) {
            final XMLEvent nextEvent = eventReader.nextEvent();
            if (nextEvent.isStartElement()) {
                final StartElement startElement = (StartElement) nextEvent;
                if (startElement.getName().getLocalPart().equals("lf111")) {
                    final Iterator<?> prefixes =
                            startElement.getNamespaceContext().getPrefixes("augment:module:leaf:list");

                    while (prefixes.hasNext() && augmentModuleLfLstPrefix == null) {
                        final String prefix = (String) prefixes.next();
                        if (!prefix.isEmpty()) {
                            augmentModuleLfLstPrefix = prefix;
                        }
                    }
                    iiModulePrefix = startElement.getNamespaceContext().getPrefix("instance:identifier:module");
                    break;
                }
            }
        }

        assertNotNull(augmentModuleLfLstPrefix);
        assertNotNull(iiModulePrefix);

        final String instanceIdentifierValue = "/" + iiModulePrefix + ":cont/" + iiModulePrefix + ":cont1/"
                + augmentModuleLfLstPrefix + ":lflst11[.='lflst11_1']";

        assertTrue(xml.contains(instanceIdentifierValue));

    }

    private static YangInstanceIdentifier createInstanceIdentifier() throws URISyntaxException {
        final List<PathArgument> pathArguments = new ArrayList<>();
        pathArguments.add(new NodeIdentifier(new QName(new URI("instance:identifier:module"), "cont")));
        pathArguments.add(new NodeIdentifier(new QName(new URI("instance:identifier:module"), "cont1")));

        final QName qName = new QName(new URI("augment:module"), "lst11");
        final Map<QName, Object> keyValues = new HashMap<>();
        keyValues.put(new QName(new URI("augment:module"), "keyvalue111"), "value1");
        keyValues.put(new QName(new URI("augment:module"), "keyvalue112"), "value2");
        final NodeIdentifierWithPredicates nodeIdentifierWithPredicates =
                new NodeIdentifierWithPredicates(qName, keyValues);
        pathArguments.add(nodeIdentifierWithPredicates);

        pathArguments.add(new NodeIdentifier(new QName(new URI("augment:augment:module"), "lf112")));

        return YangInstanceIdentifier.create(pathArguments);
    }

    private static YangInstanceIdentifier createInstanceIdentifierWithLeafList() throws URISyntaxException {
        final List<PathArgument> pathArguments = new ArrayList<>();
        pathArguments.add(new NodeIdentifier(new QName(new URI("instance:identifier:module"), "cont")));
        pathArguments.add(new NodeIdentifier(new QName(new URI("instance:identifier:module"), "cont1")));
        pathArguments.add(new NodeWithValue<>(new QName(new URI("augment:module:leaf:list"), "lflst11"), "lflst11_1"));

        return YangInstanceIdentifier.create(pathArguments);
    }

}
