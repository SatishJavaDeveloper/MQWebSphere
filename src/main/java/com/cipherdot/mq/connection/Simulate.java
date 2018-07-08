package com.cipherdot.mq.connection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.annotation.PostConstruct;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

import com.cipherdot.aca.model.BM01.MGMsg;
import com.cipherdot.aca.model.BM01.ObjectFactory;

@Service
public class Simulate {
	@Autowired
    private JmsTemplate queueTemplate;
 	String temp = "hello";
 	
 	private String queue = "QL.ACADMGPTLBM01";
    @PostConstruct
    public void init() throws JAXBException, UnsupportedEncodingException {
         System.out.println("Inside init()");
         File file = new File(getClass().getClassLoader().getResource("xml/BM01.xml").getFile());  
         JAXBContext jaxbContext = JAXBContext.newInstance(MGMsg.class);      
         Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();  
         MGMsg bm01Msg= (MGMsg) jaxbUnmarshaller.unmarshal(file); 
         System.out.println("obj::"+bm01Msg.getBondMgmt().getBondLoc());
         // create jaxb marshaller
         Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

         // pretty print xml
//         jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
         jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
         Marshaller m = jaxbContext.createMarshaller();
         m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,"http://www.dhl.com/AMGBM01.XSD");
         m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
         m.setProperty("com.sun.xml.internal.bind.xmlHeaders", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
         StringWriter sw = new StringWriter();
         ByteArrayOutputStream xmlStream = new ByteArrayOutputStream();
          m.marshal(bm01Msg, xmlStream);
         temp = new String(xmlStream.toByteArray(), "UTF-8");
         System.out.println("msg>>>>:"+temp);
     	queueTemplate.send(queue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
            	BytesMessage message = session.createBytesMessage();
                message.writeUTF(temp);
               return message;
            }
        });
    }
}