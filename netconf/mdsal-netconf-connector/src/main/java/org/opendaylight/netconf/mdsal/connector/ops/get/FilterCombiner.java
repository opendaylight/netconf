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

    void combine(final XmlElement element, FilterTree filterTree) {
        if (result == null) {
            result = element;
            ownerDocument = setupXMLDocument();
        } else {
            merge(result, element, filterTree);
        }
    }

    private Document setupXMLDocument() {
        final Element domElement = result.getDomElement();
        if (domElement.getNodeType() == Node.DOCUMENT_NODE) {
            return (Document) domElement;
        }
        return domElement.getOwnerDocument();
    }

    private void merge(XmlElement result, XmlElement toMerge, FilterTree filterTree) {
        for (final XmlElement toMergeChild : toMerge.getChildElements()) {
            FilterTree toMergeChildFilter = findFilterTree(toMergeChild, filterTree);
            List<XmlElement> resultChildren = result.getChildElementsWithinNamespace(toMergeChild.getName(),
                    toMergeChild.getNamespaceOptionally().orNull());
            if (resultChildren.isEmpty()) {
                // if child with same name isn't present, we can append it
                append(result, toMergeChild);
            } else if (toMergeChildFilter.getFilterTreeType() == FilterTreeType.LIST) {
                processList(result, toMergeChild, toMergeChildFilter, resultChildren);
            } else if (toMergeChildFilter.getFilterTreeType() == FilterTreeType.CHOICE_CASE) {
                processLeafList(result, toMerge, toMergeChild, resultChildren);
            } else {
                processOther(toMergeChild, toMergeChildFilter, resultChildren);
            }
        }
    }

    private void processOther(XmlElement toMergeChild, FilterTree toMergeChildFilter, List<XmlElement> resultChildren) {
        // element to be merged is neither list nor leaf-list, so we can merge it
        Verify.verify(resultChildren.size() == 1);
        merge(resultChildren.get(0), toMergeChild, toMergeChildFilter);
    }

    private void processList(XmlElement result, XmlElement toMergeChild, FilterTree toMergeChildFilter,
                             List<XmlElement> resultChildren) {
        Map<String, String> keys = getKeys(toMergeChild, toMergeChildFilter);
        if (keys.isEmpty()) {
            append(result, toMergeChild);
        } else {
            Optional<XmlElement> entry = getEntryWithSameKey(resultChildren, keys);
            if (entry.isPresent()) {
                merge(entry.get(), toMergeChild, toMergeChildFilter);
            } else {
                append(result, toMergeChild);
            }
        }
    }

    private void processLeafList(XmlElement result, XmlElement toMerge, XmlElement toMergeChild,
                                 List<XmlElement> resultChildren) {
        boolean alreadyPresent = resultChildren.stream()
                        .anyMatch(c -> c.getOnlyTextContentOptionally()
                        .equals(toMerge.getOnlyTextContentOptionally()));
        if (!alreadyPresent) {
            append(result, toMergeChild);
        }
    }

    private FilterTree findFilterTree(XmlElement toMergeChild, FilterTree filterTree) {
        for (FilterTree filterTreeInner : filterTree.getChildren()) {
            if (filterTreeInner.getName().getLocalName().equals(toMergeChild.getName())) {
                return filterTreeInner;
            }
        }
        throw new UnsupportedOperationException("Can't find the filterTree for the Element: " + toMergeChild.getName());
    }

    private Map<String, String> getKeys(XmlElement toMergeChild, FilterTree toMergeChildFilter) {
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

    private Optional<XmlElement> getEntryWithSameKey(List<XmlElement> listEntries, Map<String, String> keys) {
        java.util.Optional<XmlElement> result = listEntries.stream().filter(e -> keyMatches(e, keys)).findFirst();
        return fromJavaUtil(result);
    }

    private boolean keyMatches(XmlElement entry, Map<String, String> keys) {
        for (final String keyName : keys.keySet()) {
            final Optional<XmlElement> keyValue = entry.getOnlyChildElementOptionally(keyName);
            if (keyValue.isPresent()) {
                String textContent = keyValue.get().getOnlyTextContentOptionally().or("");
                if (!textContent.equals(keys.get(keyName))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void append(XmlElement result, XmlElement toAppend) {
        Element imported = (Element) ownerDocument.importNode(toAppend.getDomElement(), true);
        result.appendChild(imported);
    }

    XmlElement getResult() {
        return result;
    }

    private <T> Optional<T> fromJavaUtil(java.util.Optional<T> javaUtilOptional) {
        return (javaUtilOptional == null) ? null : Optional.fromNullable(javaUtilOptional.orElse(null));
    }
}
