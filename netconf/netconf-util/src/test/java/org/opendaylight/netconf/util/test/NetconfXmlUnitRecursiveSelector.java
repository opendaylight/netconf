/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.test;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlunit.diff.ByNameAndTextRecSelector;
import org.xmlunit.diff.ElementSelector;

/**
 * Custom xmlunit qualifier that doesn't care about order when deeper in the recursion
 * defaults to comparing element name and text content.
 */
public class NetconfXmlUnitRecursiveSelector implements ElementSelector {

    private final ElementSelector elementSelector;

    public NetconfXmlUnitRecursiveSelector() {
        this.elementSelector = new ByNameAndTextRecSelector();
    }

    @Override
    public boolean canBeCompared(final Element controlElement, final Element testElement) {
        return compareNodes(controlElement, testElement);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean compareNodes(final Node currentControl, final Node currentTest) {
        try {

            if (!elementSelector.canBeCompared((Element) currentControl, (Element) currentTest)) {
                return false;
            }

            final NodeList controlNodes;
            final NodeList testNodes;

            if (currentControl.hasChildNodes() && currentTest.hasChildNodes()) {
                controlNodes = currentControl.getChildNodes();
                testNodes = currentTest.getChildNodes();
            } else {
                return !(currentControl.hasChildNodes() || currentTest.hasChildNodes());
            }

            return (countNodesWithoutConsecutiveTextNodes(controlNodes)
                    == countNodesWithoutConsecutiveTextNodes(testNodes)) && checkChildren(controlNodes, testNodes);

        } catch (final Exception e) {
            return false;
        }
    }

    private boolean checkChildren(final NodeList controlNodes, final NodeList testNodes) {
        for (int i = 0; i < controlNodes.getLength(); i++) {
            boolean matchFound = false;
            for (int j = 0; j < testNodes.getLength(); j++) {
                final Node controlNode = controlNodes.item(i);
                final Node testNode = testNodes.item(j);

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
        final StringBuilder builder = new StringBuilder();
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
        final int length = nodeList.getLength();
        for (int i = 0; i < length; i++) {
            Node node = nodeList.item(i);
            if (!lastNodeWasText || node.getNodeType() != Node.TEXT_NODE) {
                count++;
            }
            lastNodeWasText = node.getNodeType() == Node.TEXT_NODE;
        }
        return count;
    }
}
