package com.cipherdot.mq.connection;


import javax.jms.MessageListener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.SimpleMessageListenerContainer;

import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

@Configuration
public class JMSConfig {
	
	@Value("${redpine.aca.mq.host}")
	private String host;
	@Value("${redpine.aca.mq.port}")
	private Integer port;
	@Value("${redpine.aca.mq.jms.queueManager}")
	private String queueManager;
	@Value("${redpine.aca.mq.jms.channel}")
	private String channel;
	@Value("${servers.mq.timeout}")
	private long timeout;
	
	@Bean
	@Primary
	public MQQueueConnectionFactory mqQueueConnectionFactory() {
		MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
		try {	
			mqQueueConnectionFactory.setHostName(host);
			mqQueueConnectionFactory.setQueueManager(queueManager);
			mqQueueConnectionFactory.setPort(port);
			mqQueueConnectionFactory.setChannel(channel);
			mqQueueConnectionFactory.setAppName(System.getProperty("user.name"));
			mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
			mqQueueConnectionFactory.setCCSID(1208);					
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mqQueueConnectionFactory;
	}
	@Bean
	public SimpleMessageListenerContainer queueContainer(MQQueueConnectionFactory mqQueueConnectionFactory) {
		MessageListener listener = new BM01Listener();
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(mqQueueConnectionFactory);
		container.setDestinationName("QL.ACADMGPTLBM01");
		container.setMessageListener(listener);
		container.start();
		System.out.println("connected to mq BM01");
		return container;
	}
	@Bean
	public SimpleMessageListenerContainer queueContainer1(MQQueueConnectionFactory mqQueueConnectionFactory) {
		MessageListener listener = new SU01Listener();
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		container.setConnectionFactory(mqQueueConnectionFactory);
		container.setDestinationName("QL.ACADMGPTLSU01");
		container.setMessageListener(listener);
		container.start();
		System.out.println("connected to mq SU01");
		return container;
	}
	@Bean
	public JmsTemplate queueTemplate(MQQueueConnectionFactory mqQueueConnectionFactory) {
		JmsTemplate jmsTemplate = new JmsTemplate(mqQueueConnectionFactory);
		jmsTemplate.setReceiveTimeout(timeout);
		return jmsTemplate;
	}
	

}

