/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class FilterCombiner {

    private XmlElement result;
    private Document ownerDocument;

    public FilterCombiner() {

    }

    public void combine(XmlElement element) {
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

    public XmlElement getResult() {
        return result;
    }

    @Override
    public String toString() {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Result output = new StreamResult(bos);
            Source input = new DOMSource(result.getDomElement());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(input, output);
            return "-------------------\nResult:\n" + bos.toString() + "\n-------------------";
        } catch (TransformerException e) {
            return "Transform failed " + Throwables.getStackTraceAsString(e);
        }

    }

    private void merge(XmlElement result, XmlElement toMerge) {
        for (XmlElement toMergeChild : toMerge.getChildElements()) {
            final List<XmlElement> resultChildren = result.getChildElementsWithinNamespace(toMergeChild.getName(),
                    toMergeChild.getNamespaceOptionally().orNull());
            if (resultChildren.isEmpty() || isLeafList(toMergeChild)) {
                append(result, toMergeChild);
            } else if (isList(toMergeChild)) {
                //TODO merge lists
            } else {
                Verify.verify(resultChildren.size() == 1);
                merge(resultChildren.get(0), toMergeChild);
            }
        }
    }

    private void append(XmlElement result, XmlElement toAppend) {
        final Element imported = (Element) ownerDocument.importNode(toAppend.getDomElement(), true);
        result.appendChild(imported);
    }

    private boolean isList(XmlElement element) {
        return false;
    }

    private boolean isLeafList(XmlElement element) {
        return false;
    }
}
