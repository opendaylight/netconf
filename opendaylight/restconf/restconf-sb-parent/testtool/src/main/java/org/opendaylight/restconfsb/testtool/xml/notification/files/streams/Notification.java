/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.notification.files.streams;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>Java class for anonymous complex type.
 * <p>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;complexType>
 *   &lt;complexString>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="delay" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="period" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="content" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexString>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "delay"
        , "period"
        , "content"
})
public class Notification {

    protected int delay;
    protected int period;

    @XmlAnyElement(ContentHandler.class)
    protected String content;

    /**
     * Gets the value of the delay property.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the value of the delay property.
     */
    public void setDelay(int value) {
        this.delay = value;
    }

    /**
     * Gets the value of the period property.
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Sets the value of the period property.
     */
    public void setPeriod(int value) {
        this.period = value;
    }

    /**
     * Gets the value of the content property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the value of the content property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setContent(String value) {
        this.content = value;
    }

}
