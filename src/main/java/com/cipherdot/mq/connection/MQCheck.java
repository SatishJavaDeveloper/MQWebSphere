package com.cipherdot.mq.connection;

import com.ibm.mq.MQEnvironment;

import com.ibm.mq.MQQueueManager;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.MQConstants;

public class MQCheck {
    public static void main(String args[]) {
    try {
        int openOptions = CMQC.MQOO_INQUIRE | CMQC.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT;
        MQEnvironment.hostname = "localhost";
        MQEnvironment.port = 1414;
        MQEnvironment.channel = "PTL.SVRCONN";
        MQEnvironment.properties.put(CMQC.USER_ID_PROPERTY, "admin");
        MQEnvironment.properties.put(CMQC.PASSWORD_PROPERTY, "passw0rd");          
        MQEnvironment.properties.put(CMQC.TRANSPORT_PROPERTY, CMQC.TRANSPORT_MQSERIES);
        MQQueueManager qMgr;
        qMgr = new MQQueueManager("TestMQ");
        MQQueue destQueue = qMgr.accessQueue("QL.ACADMGPTLBM01", openOptions);
        for (int k=0; k<100; k++) {
        MQMessage hello_world = new MQMessage();
        hello_world.writeUTF("Blah...blah...bleah...test message no.1...!");
        MQPutMessageOptions pmo = new MQPutMessageOptions();
        destQueue.put(hello_world, pmo);
        }
        destQueue.close();
        qMgr.disconnect();
        System.out.println("------------------------success...");            
    } catch (Exception e) {
        System.out.println("Exception: " + e);
        e.printStackTrace();
    }
    }
}