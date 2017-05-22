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
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class FilterCombiner {

    private XmlElement result;
    private Document ownerDocument;

    FilterCombiner() {

    }

    void combine(final XmlElement element, FilterTree filterTree) {
        if (result == null) {
            result = element;
            final Element domElement = result.getDomElement();
            if (domElement.getNodeType() == Node.DOCUMENT_NODE) {
                ownerDocument = (Document) domElement;
            } else {
                ownerDocument = domElement.getOwnerDocument();
            }
        } else {
            merge(result, element, filterTree);
        }
    }

    XmlElement getResult() {
        return result;
    }

    private FilterTree findFilterTree(XmlElement toMergeChild, FilterTree filterTree) {
        for (FilterTree filterTreeInner : filterTree.getChildren()) {
            if (filterTreeInner.getName().getLocalName().equals(toMergeChild.getName())) {
                return filterTreeInner;
            }
        }
        throw new RuntimeException();
    }

    private void merge(final XmlElement result, final XmlElement toMerge, FilterTree filterTree) {
        for (final XmlElement toMergeChild : toMerge.getChildElements()) {
            FilterTree toMergeChildFilter = findFilterTree(toMergeChild, filterTree);
            List<XmlElement> resultChildren = result.getChildElementsWithinNamespace(toMergeChild.getName(),
                    toMergeChild.getNamespaceOptionally().orNull());
            if (resultChildren.isEmpty()) {
                // if child with same name isn't present, we can append it
                append(result, toMergeChild);
            } else if (toMergeChildFilter.getFilterTreeType() == FilterTreeType.LIST) {
                Map<String, String> keys = getKeys(toMergeChild, toMergeChildFilter);
                if (keys.isEmpty()) {
                    append(result, toMergeChild);
                } else {
                    java.util.Optional<XmlElement> entry = getEntryWithSameKey(resultChildren, keys);
                    if (entry.isPresent()) {
                        merge(entry.get(), toMergeChild, toMergeChildFilter);
                    } else {
                        append(result, toMergeChild);
                    }
                }
            } else if (toMergeChildFilter.getFilterTreeType() == FilterTreeType.CHOICE_CASE) {
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
                merge(resultChildren.get(0), toMergeChild, toMergeChildFilter);
            }
        }
    }

    private Map<String, String> getKeys(final XmlElement toMergeChild, FilterTree toMergeChildFilter) {
        ListSchemaNode listSchemaNode = (ListSchemaNode) toMergeChildFilter.getSchemaNode();
        List<QName> keyNameList = listSchemaNode.getKeyDefinition();

        Map<String, String> map = Maps.newHashMap();
        for (QName qname : keyNameList) {
            Optional<XmlElement> keyValueOpt = toMergeChild.getOnlyChildElementOptionally(qname.getLocalName());
            if (!keyValueOpt.isPresent()) {
                return Collections.emptyMap();
            }
            String keyValue = keyValueOpt.get().getOnlyTextContentOptionally().or("");
            map.put(qname.getLocalName(), keyValue);
        }
        return map;
    }

    private java.util.Optional<XmlElement> getEntryWithSameKey(final List<XmlElement> listEntries,
                                                               final Map<String, String> keys) {
        return listEntries.stream().filter(e -> keyMatches(e, keys)).findFirst();
    }

    private boolean keyMatches(final XmlElement entry, final Map<String, String> keys) {
        for (final String keyName : keys.keySet()) {
            final Optional<XmlElement> keyValue = entry.getOnlyChildElementOptionally(keyName);
            if (keyValue.isPresent()) {
                String onlyTextContentOptionally = keyValue.get().getOnlyTextContentOptionally().or("");
                if (!onlyTextContentOptionally.equals(keys.get(keyName))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
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
