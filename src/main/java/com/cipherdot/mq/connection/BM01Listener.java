/*package com.cipherdot.mq.connection;

import javax.jms.BytesMessage;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

public class BM01Listener implements MessageListener {

	public void onMessage(Message message) {
		if (message instanceof TextMessage) {
			try {
				System.out.println(((TextMessage) message).getText());
			}
			
			catch (JMSException ex) {
				throw new RuntimeException(ex);
			}
		} else if (message instanceof BytesMessage){
			try{
			BytesMessage byteMessage = (BytesMessage) message;
			byte[] byteData = null;
			byteData = new byte[(int) byteMessage.getBodyLength()];
			byteMessage.readBytes(byteData);
			byteMessage.reset();
			String payloadText = new String(byteData);
			System.out.println("message::"+payloadText);
		}
			catch(Exception ex)
			{
				}
			}
	}

}*/