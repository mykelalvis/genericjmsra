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
package com.sun.genericra.inbound.async;

import com.sun.genericra.AbstractXAResourceType;
import com.sun.genericra.inbound.*;
import com.sun.genericra.util.*;

import java.util.logging.*;

import javax.jms.*;

import javax.resource.*;
import javax.resource.spi.*;
import javax.resource.spi.endpoint.*;

import javax.transaction.xa.XAResource;


/**
 * Helper class that delivers a message to MDB.
 * Important assumptions:
 * - There is one delivery helper for each message(delivery).
 * - Redelivery will be carried out by the same DeliveryHelper.
 * @author Binod P.G
 */
public class DeliveryHelper {
    private static Logger _logger;
    
    static {
        _logger = LogUtils.getLogger();
    }
    
    com.sun.genericra.inbound.ActivationSpec spec;
    InboundJmsResource jmsResource;
    XAResource xar;
    Message msg = null;
    Destination dest = null;
    boolean transacted;
    boolean sentToDmd = false;
    boolean redeliveryFailed = false;
    
    public DeliveryHelper(InboundJmsResource jmsResource,
            InboundJmsResourcePool pool) {
        this.spec = pool.getConsumer().getSpec();
        this.jmsResource = jmsResource;
        this.transacted = pool.isTransacted();
        
        AbstractXAResourceType xarObject = null;
        
        if (redeliveryRequired()) {
            xarObject = new InboundXAResourceProxy(jmsResource.getXAResource());
        } else {
            xarObject = new SimpleXAResourceProxy(jmsResource.getXAResource());
            
            //this.xar = jmsResource.getXAResource();
        }
        
        xarObject.setRMPolicy(this.spec.getRMPolicy());
        xarObject.setConnection(pool.getConnection());
        this.xar = xarObject;
    }
    
    public boolean redeliveryRequired() {
        return this.transacted && (this.spec.getRedeliveryAttempts() > 0);
    }
    
    public XAResource getXAResource() {
        return this.xar;
    }
    
    private DeadMessageProducer createProducer(Connection con, Destination dest)
    throws JMSException {
        return new DeadMessageProducer(con, jmsResource.getPool(), dest);
    }
    
    public void sendMessageToDMD() {
        _logger.log(Level.FINE, "Trying to send message  to DMD :" + dest);
        
        Session session = null;
        DeadMessageProducer msgProducer = null;
        Exception dmdexception = null;
        boolean dmdSendSuccess = true;
        try {
            if ((this.dest != null) && this.spec.getSendBadMessagesToDMD()) {
                _logger.log(Level.FINE, "Sending the message to DMD :" + dest);
                
                
                if (redeliveryRequired()) {
                    InboundXAResourceProxy localXar = (InboundXAResourceProxy) this.xar;
                    if (localXar.endCalled() == false) {
                        localXar.end(null, XAResource.TMSUCCESS);
                    }
                    localXar.prepare(null);
                    _logger.log(Level.FINE, "Prepared DMD transaction");
                } else {
                    SimpleXAResourceProxy localXar = (SimpleXAResourceProxy) this.xar;
                    localXar.end(null, XAResource.TMSUCCESS);
                    localXar.prepare(null);
                    _logger.log(Level.FINE, "Prepared DMD transaction");
                }
                Connection connection = jmsResource.getPool()
                .getConnectionForDMD();
                msgProducer = createProducer(connection, this.dest);
                msgProducer.send(this.msg);
                _logger.log(Level.FINE, "Sent message to DMD");
                if (redeliveryRequired()) {
                    InboundXAResourceProxy localXar = (InboundXAResourceProxy) this.xar;
                    localXar.commit(null, false);
                    _logger.log(Level.FINE, "Commited DMD transaction");
                    
                } else {
                    SimpleXAResourceProxy localXar = (SimpleXAResourceProxy) this.xar;
                    localXar.commit(null, false);
                    _logger.log(Level.FINE, "Commited DMD transaction");
                }
                /**
                 * We know that if commit/prepare fails we may have
                 * the message in the DMD, the message would be present in
                 * both destinations.
                 * Results in duplicate message in DMD.
                 */
                
            } else {
                dmdSendSuccess = false;
            }
        }catch (Exception e) {
            dmdSendSuccess = false;
            dmdexception = e;
            e.printStackTrace();
        }finally {
            this.msg = null;
            this.dest = null;
            this.sentToDmd = false;
            
            if (msgProducer != null) {
                try {
                    msgProducer.close();
                } catch (Exception me) {
                    me.printStackTrace();
                }
            }
        }
        if (!dmdSendSuccess) {
            if (redeliveryRequired()) {
                _logger.log(Level.SEVERE, "FAILED : sending message to DMD");
            }else {
                _logger.log(Level.SEVERE, "FAILED : sending message to DMD");
                SimpleXAResourceProxy localXar = (SimpleXAResourceProxy) this.xar;
                localXar.setToRollback(true);
                try {
                    localXar.rollback(null);
                } catch (Exception e) {
                    _logger.log(Level.SEVERE, "FAILED : to rollback XA" + e.getMessage());
                }
            }
            
            /*
            if (dmdexception != null) {
                throw ExceptionUtils.newRuntimeException(dmdexception);
            } else {
                throw ExceptionUtils.newRuntimeException(new Exception("DMD not configured"));
            }
             */
        }
        
    }
    
    public void deliver(Message message, Destination d){
        this.msg = message;
        this.dest = d;
        deliver();
    }
    
    public void deliver() {
        int myattempts = 0;
        int attempts = this.spec.getRedeliveryAttempts();
        
        InboundXAResourceProxy localXar = null;
        while (true) {
            try {
                deliverMessage(msg);
                
                if (redeliveryRequired()) {
                    
                    /*
                     * Make the XA rollback  enable.
                     *  This is because we will start the XA now.
                     */
                    localXar =  (InboundXAResourceProxy) xar;
                    localXar.startDelayedXA();
                    localXar.setToRollback(true);
                }
                
                break;
            } catch (Exception e) {
                if (redeliveryRequired()) {
                    /*
                     * Do not allow roll back here, because we know that
                     * the XA is not started, we start the XA only when delivery
                     * is successful, here delivery has failed.
                     */
                    localXar =  (InboundXAResourceProxy) xar;
                    localXar.setToRollback(false);
                    try {
                        _logger.log(Level.FINE,
                                "Setting JMSRedelivered header on message");
                        msg.setJMSRedelivered(true);
                        
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    SimpleXAResourceProxy simpleXar =  (SimpleXAResourceProxy) this.xar;
                    simpleXar.setToRollback(false);
                }
                if (transacted) {
                    if (myattempts < attempts) {
                        myattempts++;
                        
                        _logger.log(Level.FINEST,
                                "Releasing the endpoint after an exception");
                        this.jmsResource.releaseEndpoint();                   
                        
                        try {
                            Thread.sleep(spec.getRedeliveryInterval() * 1000);
                            _logger.log(Level.FINE,
                                    "getting the endpoint after an exception");
                            this.jmsResource.refresh();
                        } catch (Exception ie) {
                            ie.printStackTrace();
                        }
                    } else {
                        
                        this.markForDMD();
                        
                        /**
                         * Start XA now.
                         * And we also know that the resource is our proxy
                         */
                        try {
                            this.msg.setJMSRedelivered(false);
                            _logger.log(Level.FINE, "Resetting JMS redelivered header");
                        } catch (Exception jmse) {
                            _logger.log(Level.FINE, "Cannot reset JMS redelivered header");;
                        }
                        if (redeliveryRequired()) {
                            localXar.startDelayedXA();
                        }
                        /**
                         * Start the transaction now so that the DMD delivery
                         * can go on.
                         */
                        /**
                         * If all the delivery attempts fail we have to throw an
                         * exception so that the message listener throws an exception
                         * and the broker will retain the message.
                         * If we do not propagate the error status from here
                         * the broker loses the message and we also lose it.
                         */
                        
                        return;
                    }
                } else {
                    
                    return;
                    
                }
            }
        }
    }
    
    public void markForDMD() {
        this.sentToDmd = true;
    }
    
    public boolean markedForDMD() {
        return this.sentToDmd;
    }
    
    private void deliverMessage(Message message) throws ResourceException {
        MessageEndpoint endPoint = jmsResource.getEndpoint();
        
        try {
            _logger.log(Level.FINEST,
                    "Now it is feeding the message to MDB instance");
            ((javax.jms.MessageListener) endPoint).onMessage(message);
        } catch (Exception e) {
            if (transacted) {
                throw ExceptionUtils.newResourceException(e);
            }
        }
    }
}