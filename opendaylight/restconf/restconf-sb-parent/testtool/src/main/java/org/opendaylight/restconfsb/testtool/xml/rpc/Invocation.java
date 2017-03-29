/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.rpc;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlType;
import org.w3c.dom.Element;

@XmlAccessorType(XmlAccessType.FIELD)
public class Invocation {

    private Input input;

    private Output output;

    public Input getInput() {
        return input;
    }

    public void setInput(final Input input) {
        this.input = input;
    }

    public Output getOutput() {
        return output;
    }

    public void setOutput(final Output output) {
        this.output = output;
    }

    @XmlType(name = "input")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Input {

        @XmlAnyElement
        private Element inputElement;

        public Element getElement() {
            return inputElement;
        }

        public void setElement(final Element inputElement) {
            this.inputElement = inputElement;
        }
    }

    @XmlType(name = "output")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Output {

        @XmlAnyElement
        private Element outputElement;

        public Element getElement() {
            return outputElement;
        }

        public void setElement(final Element outputElement) {
            this.outputElement = outputElement;
        }
    }
}
