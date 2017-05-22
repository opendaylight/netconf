/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import com.google.common.base.Verify;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class FilterCombiner {

    private XmlElement result;
    private Document ownerDocument;

    FilterCombiner() {

    }

    void combine(final XmlElement element) {
        if (result == null) {
            result = element;
            final Element domElement = result.getDomElement();
            if (domElement.getNodeType() == Node.DOCUMENT_NODE) {
                ownerDocument = (Document) domElement;
            } else {
                ownerDocument = domElement.getOwnerDocument();
            }
        }
        merge(result, element);
    }

    XmlElement getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "-------------------\nResult:\n" + XmlUtil.toString(result) + "\n-------------------";

    }

    private void merge(final XmlElement result, final XmlElement toMerge) {
        for (final XmlElement toMergeChild : toMerge.getChildElements()) {
            final List<XmlElement> resultChildren = result.getChildElementsWithinNamespace(toMergeChild.getName(),
                    toMergeChild.getNamespaceOptionally().orNull());
            if (resultChildren.isEmpty()) {
                // if child with same name isn't present, we can append it
                append(result, toMergeChild);
            } else if (isList(toMergeChild)) {
                final Map<String, String> keys = getKeys(toMergeChild);
                final java.util.Optional<XmlElement> entry = getEntryWithSameKey(resultChildren, keys);
                if (entry.isPresent()) {
                    merge(entry.get(), toMergeChild);
                } else {
                    append(result, toMergeChild);
                }
                // TODO merge lists
                /*
                if 'user' is a list and 'name' is a key then
                <filter>
                    <top>
                        <users>
                            <user>
                                <name>Name1</name>
                            </user>
                        </users>
                    </top>
                    <top>
                        <users>
                            <user>
                                <name>Name2</name>
                            </user>
                        </users>
                    </top>
                </filter>
                Result should be
                <filter>
                    <top>
                        <users>
                            <user>
                                <name>Name1</name>
                            </user>
                            <user>
                                <name>Name2</name>
                            </user>
                        </user>
                    </top>
                </filter>
                 */
            } else if (isLeafList(toMergeChild)) {
                final boolean alreadyPresent =
                        resultChildren.stream()
                                .anyMatch(c -> c.getOnlyTextContentOptionally()
                                        .equals(toMerge.getOnlyTextContentOptionally()));
                if (!alreadyPresent) {
                    append(result, toMergeChild);
                }
            } else {
                // element to be merged is neither list nor leaf-list, so we can merge it
                Verify.verify(resultChildren.size() == 1);
                merge(resultChildren.get(0), toMergeChild);
            }
        }
    }

    private Map<String, String> getKeys(final XmlElement toMergeChild) {
        return Collections.emptyMap();
    }

    private java.util.Optional<XmlElement> getEntryWithSameKey(final List<XmlElement> listEntries,
                                                               final Map<String, String> keys) {
        return listEntries.stream().filter(e -> keyMatches(e, keys)).findFirst();
    }

    private boolean keyMatches(final XmlElement entry, final Map<String, String> keys) {
        boolean matches = true;
        for (final String keyName : keys.keySet()) {
            final Optional<XmlElement> keyValue = entry.getOnlyChildElementOptionally(keyName);
            if (keyValue.isPresent()) {
                final String onlyTextContentOptionally = keyValue.get().getOnlyTextContentOptionally().or("");
                matches = matches && onlyTextContentOptionally.equals(keys.get(keyName));
            }
        }
        return matches;
    }

    private void append(final XmlElement result, final XmlElement toAppend) {
        final Element imported = (Element) ownerDocument.importNode(toAppend.getDomElement(), true);
        result.appendChild(imported);
    }

    private boolean isList(final XmlElement element) {
        // TODO implement
        return false;
    }

    private boolean isLeafList(final XmlElement element) {
        // TODO implement
        return false;
    }
}
