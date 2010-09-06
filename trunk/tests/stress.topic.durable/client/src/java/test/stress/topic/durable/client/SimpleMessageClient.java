/**
 * Copyright 2004-2005 Sun Microsystems, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package test.stress.topic.durable.client;

import javax.jms.*;
import javax.naming.*;

import test.common.client.GenericClient;


import java.util.ArrayList;
import java.util.HashMap;

/**
 * Simple stress test with Queue. The test starts NUM_CLIENT threads. Each thread sends NUM_CYCLES messages to a Queue.
 * An MDB consumes these messages concurrently and responds with a reply Message to a ReplyQueue. While the threads are
 * being exercised, an attempt to read NUM_CLIENT*NUM_CYCLES messages is performed. An attempt to read an extra message
 * is also done to ascertain that no more than NUM_CLIENT*NUM_CYCLES messages are available.
 */
public class SimpleMessageClient extends GenericClient implements Runnable {

	// use a low number of message because if this is a sync test the test will run single-threaded
	// however if this is an async test this is too low to be a useful stress test
	static int NUM_CLIENTS = 6;
	static int NUM_CYCLES = 5;
	 
	static int TIME_OUT = 60000;
	static long MDB_SLEEP_TIME = 2000;
	static boolean debug = false;

	int id = 0;

	public SimpleMessageClient(int i) {
		this.id = i;
	}

	public static void main(String[] args) throws Exception {

		drainQueue("java:comp/env/jms/outboundQueue");

		/**
		 * Start the threads that will send messages to MDB
		 */
		ArrayList al = new ArrayList();
		try {
			for (int i = 0; i < NUM_CLIENTS / 2; i++) {
				// for (int i =0; i < NUM_CLIENTS; i++) {
				Thread client = new Thread(new SimpleMessageClient(i));
				al.add(client);
				client.start();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		for (int i = 0; i < al.size(); i++) {
			Thread client = (Thread) al.get(i);
			try {
				client.join();
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
		/*
		 * System.out.println("You have 40 seconds to reconnect"); Thread.sleep(40000);
		 */
		System.out.println("Now reading the outbound queue");

		al = new ArrayList();
		try {
			for (int i = NUM_CLIENTS / 2; i < NUM_CLIENTS; i++) {
				Thread client = new Thread(new SimpleMessageClient(i));
				al.add(client);
				client.start();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		Context jndiContext = null;
		QueueConnectionFactory queueConnectionFactory = null;
		QueueConnection queueConnection = null;
		QueueSession queueSession = null;
		Queue queue = null;
		QueueReceiver queueReceiver = null;
		TextMessage message = null;

		try {
			jndiContext = new InitialContext();
			queueConnectionFactory = (QueueConnectionFactory) jndiContext.lookup("java:comp/env/jms/QCFactory");
			queue = (Queue) jndiContext.lookup("java:comp/env/jms/outboundQueue");

			queueConnection = queueConnectionFactory.createQueueConnection();
			queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
			queueConnection.start();
			queueReceiver = queueSession.createReceiver(queue);

			HashMap map = new HashMap();

			long startTime = System.currentTimeMillis();
			boolean pass = true;
			//
			// Receives all the messages and keep in the data structure
			//
			for (int i = 0; i < NUM_CLIENTS * NUM_CYCLES; i++) {
				TextMessage msg = (TextMessage) queueReceiver.receive(TIME_OUT);
				if (msg == null) {
					System.out.println("Received :" + i + " messages before exiting");
					pass = false;
					break;
				}
				Integer id = new Integer(msg.getIntProperty("replyid"));
				if (map.containsKey(id)) {
					pass = false;
					debug("Duplicate :" + id);
				}
				map.put(id, msg.getText());
			}
			long totalTime = System.currentTimeMillis() - startTime;
			System.out.println("Received messages in :" + totalTime + " milliseconds");
			System.out.println("------------------------------------------------------");

			//
			// Now examine the received data
			//
			for (int i = 0; i < NUM_CLIENTS * NUM_CYCLES; i++) {
				String reply = (String) map.get(new Integer(i));
				if (!reply.equals("REPLIED:CLIENT")) {
					pass = false;
				}
				System.out.println("Receeived :" + i + ":" + reply);
			}

			// Try to receive one more message than expected.
			TextMessage msg = (TextMessage) queueReceiver.receive(TIME_OUT);

			if (msg != null) {
				pass = false;
				System.out.println("Received more than expected number of messages :" + msg.getText());
			}

			if (pass) {
				System.out.println("Concurrent message delivery test - Queue Stess Test : PASS");
			} else {
				System.out.println("Concurrent message delivery test - Queue Stress Test : FAIL");
			}
		} catch (Throwable t) {
			t.printStackTrace();
			System.out.println("Concurrent message delivery test - Queue stress test :  FAIL");
		} finally {
			for (int i = 0; i < al.size(); i++) {
				Thread client = (Thread) al.get(i);
				try {
					client.join();
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
			System.exit(0);
		}

	}

	private static void drainQueue(String queueJNDIname) throws NamingException, JMSException {

		Context jndiContext = new InitialContext();
		QueueConnectionFactory queueConnectionFactory = (QueueConnectionFactory) jndiContext
				.lookup("java:comp/env/jms/QCFactory");
		Queue queue = (Queue) jndiContext.lookup(queueJNDIname);
		QueueConnection queueConnection = queueConnectionFactory.createQueueConnection();
		QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
		QueueReceiver queueReceiver = queueSession.createReceiver(queue);
		queueConnection.start();
		int numMessages = 0;
		while (true) {
			Message message = queueReceiver.receive(1000);
			if (message == null)
				break;
			numMessages++;
		}
		if (numMessages > 0)
			System.out.println("Drained " + numMessages + " from queue with JNDI name " + queueJNDIname);
		queueConnection.close();
	}

	public void run() {

		Context jndiContext = null;
		TopicConnectionFactory topicConnectionFactory = null;
		TopicConnection topicConnection = null;
		TopicSession topicSession = null;
		Topic topic = null;
		TopicPublisher topicPublisher = null;
		TextMessage message = null;

		try {
			jndiContext = new InitialContext();
			topicConnectionFactory = (TopicConnectionFactory) jndiContext.lookup("java:comp/env/jms/TCFactory");
			topic = (Topic) jndiContext.lookup("java:comp/env/jms/inboundTopic");

			int startId = id * NUM_CYCLES;
			int endId = (id * NUM_CYCLES) + NUM_CYCLES;
			for (int i = startId; i < endId; i++) {
				try {
					topicConnection = topicConnectionFactory.createTopicConnection();
					topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
					topicConnection.start();
					topicPublisher = topicSession.createPublisher(topic);
					message = topicSession.createTextMessage();
					message.setText("CLIENT");
					message.setIntProperty("id", i);
					message.setLongProperty("sleeptime", MDB_SLEEP_TIME);
					topicPublisher.send(message);
					debug("Send the message :" + message.getIntProperty("id") + ":" + message.getText());
				} catch (Exception e) {
					System.out.println("Exception occurred: " + e.toString());
				} finally {
					if (topicConnection != null) {
						try {
							topicConnection.close();
						} catch (JMSException e) {
						}
					} // if
				} // finally
			}
		} catch (Throwable e) {
			System.out.println("Exception occurred: " + e.toString());
		} // finally
	} // main

	static void debug(String msg) {
		if (debug) {
			System.out.println(msg);
		}
	}
} // class

