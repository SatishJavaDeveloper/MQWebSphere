package com.example.demo;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

public class Test {
	   @Autowired
	    private JmsTemplate queueTemplate;
	 	String temp = "hello";
	 	
	 	private String queue = "QL.ACADMGPTLBM01";
	 	@org.junit.Test()
	 	public void sendMessage(){
	 		System.out.println("sending mq message to ptl");
	 		System.out.println("queue template::"+queueTemplate);
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
