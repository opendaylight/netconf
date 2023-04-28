/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.util;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.custommonkey.xmlunit.ElementNameAndTextQualifier;
import org.custommonkey.xmlunit.ElementQualifier;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Custom xmlunit qualifier that doesn't care about order when deeper in the recursion
 * defaults to comparing element name and text content.
 */
public class NetconfXmlUnitRecursiveQualifier implements ElementQualifier {
    private final ElementQualifier qualifier;

    public NetconfXmlUnitRecursiveQualifier() {
        this(new ElementNameAndTextQualifier());
    }

    public NetconfXmlUnitRecursiveQualifier(final ElementQualifier qualifier) {
        this.qualifier = requireNonNull(qualifier);
    }

    @Override
    public boolean qualifyForComparison(final Element currentControl,
                                        final Element currentTest) {
        return compareNodes(currentControl, currentTest);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION")
    private boolean compareNodes(final Node currentControl, final Node currentTest) {
        try {
            if (!qualifier.qualifyForComparison((Element) currentControl, (Element) currentTest)) {
                return false;
            }

            NodeList controlNodes;
            NodeList testNodes;

            if (currentControl.hasChildNodes() && currentTest.hasChildNodes()) {
                controlNodes = currentControl.getChildNodes();
                testNodes = currentTest.getChildNodes();
            } else {
                return !currentControl.hasChildNodes() && !currentTest.hasChildNodes();
            }

            return countNodesWithoutConsecutiveTextNodes(controlNodes)
                    == countNodesWithoutConsecutiveTextNodes(testNodes) && checkChildren(controlNodes, testNodes);

        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkChildren(final NodeList controlNodes, final NodeList testNodes) {
        for (int i = 0; i < controlNodes.getLength(); i++) {
            boolean matchFound = false;
            for (int j = 0; j < testNodes.getLength(); j++) {
                Node controlNode = controlNodes.item(i);
                Node testNode = testNodes.item(j);

                if (controlNode.getNodeType() != testNode.getNodeType()) {
                    continue;
                }

                if (controlNode.getNodeType() == Node.TEXT_NODE) {
                    if (concatenateText(controlNode).equals(concatenateText(testNode))) {
                        matchFound = true;
                        break;
                    }

                } else if (compareNodes(controlNode, testNode)) {
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                return false;
            }
        }

        return true;
    }

    private static String concatenateText(final Node textNode) {
        StringBuilder builder = new StringBuilder();
        Node next = textNode;

        do {
            if (next.getNodeValue() != null) {
                builder.append(next.getNodeValue().trim());
                next = next.getNextSibling();
            }
        } while (next != null && next.getNodeType() == Node.TEXT_NODE);

        return builder.toString();
    }

    private static int countNodesWithoutConsecutiveTextNodes(final NodeList nodeList) {
        int count = 0;
        boolean lastNodeWasText = false;
        for (int i = 0, length = nodeList.getLength(); i < length; i++) {
            Node node = nodeList.item(i);
            if (!lastNodeWasText || node.getNodeType() != Node.TEXT_NODE) {
                count++;
            }
            lastNodeWasText = node.getNodeType() == Node.TEXT_NODE;
        }
        return count;
    }
}
