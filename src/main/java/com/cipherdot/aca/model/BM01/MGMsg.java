//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-792 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.07.07 at 05:45:41 PM IST 
//


package com.cipherdot.aca.model.BM01;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="MsgHdr" type="{http://www.dhl.com/ACA}MsgHdrTyp"/>
 *         &lt;element name="BondMgmt" type="{http://www.dhl.com/ACA}BondMgmtTyp"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {

})
@XmlRootElement(name = "MGMsg")
public class MGMsg {

    @XmlElement(name = "MsgHdr", required = true)
    protected MsgHdrTyp msgHdr;
    @XmlElement(name = "BondMgmt", required = true)
    protected BondMgmtTyp bondMgmt;

    /**
     * Gets the value of the msgHdr property.
     * 
     * @return
     *     possible object is
     *     {@link MsgHdrTyp }
     *     
     */
    public MsgHdrTyp getMsgHdr() {
        return msgHdr;
    }

    /**
     * Sets the value of the msgHdr property.
     * 
     * @param value
     *     allowed object is
     *     {@link MsgHdrTyp }
     *     
     */
    public void setMsgHdr(MsgHdrTyp value) {
        this.msgHdr = value;
    }

    /**
     * Gets the value of the bondMgmt property.
     * 
     * @return
     *     possible object is
     *     {@link BondMgmtTyp }
     *     
     */
    public BondMgmtTyp getBondMgmt() {
        return bondMgmt;
    }

    /**
     * Sets the value of the bondMgmt property.
     * 
     * @param value
     *     allowed object is
     *     {@link BondMgmtTyp }
     *     
     */
    public void setBondMgmt(BondMgmtTyp value) {
        this.bondMgmt = value;
    }

}