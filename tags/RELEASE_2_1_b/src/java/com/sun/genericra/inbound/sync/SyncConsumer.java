/**
 * Copyright (c) 2004-2005 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.genericra.inbound.sync;

import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;

import java.util.logging.Level;
import java.util.ArrayList;
import com.sun.genericra.inbound.AbstractJmsResourcePool;
import com.sun.genericra.util.ExceptionUtils;
import javax.jms.*;
import javax.transaction.TransactionManager;
/**
 *
 * @author rp138409
 */
public class SyncConsumer extends  com.sun.genericra.inbound.AbstractConsumer {
    
    private boolean mIsStopped = true;
    private String applicationName;
    private int mBatchSize;
    private boolean mHoldUntilAck;
    private SyncJmsResourcePool jmsPool;
    private ArrayList mWorkers;
    private SyncReconnectHelper reconHelper = null;
    /** Creates a new instance of SyncConsumer */
    public SyncConsumer(MessageEndpointFactory mef,
            javax.resource.spi.ActivationSpec actspec) throws ResourceException {
        super(mef, actspec);
    }
    
    
    public AbstractJmsResourcePool getPool() {
        return jmsPool;
    }
    public void initialize(boolean istx) throws ResourceException {
        super.validate();
        
        if ((mBatchSize > 1 || mHoldUntilAck) && this.transacted) {
            TxMgr txmgr = new TxMgr();
            TransactionManager mgr = null;
            try {
                mgr = txmgr.getTransactionManager();
            }catch (Exception e) {
                throw ExceptionUtils.newResourceException(e);
            }
            if (( mgr == null) && mHoldUntilAck) {
                logger.log(Level.FINE, "TxMgr could not be obtained: ");
                throw new RuntimeException("Could not obtain TxMgr which is crucial for HUA mode: " );
            }
            
        }
        jmsPool = new SyncJmsResourcePool(this, istx);
        jmsPool.initialize();
    }
    
    public void start() throws ResourceException {
        setTransaction();
        initialize(this.transacted);
        _start(jmsPool, this.dest);
    }
    public void restart() throws ResourceException {
         _start(reconHelper.getPool(), dest);
    }
    private void _start(SyncJmsResourcePool pool, Destination destination) throws ResourceException {
        try {
            this.reconHelper = new SyncReconnectHelper(pool, this);
            if (spec.getReconnectAttempts() > 0) {
                pool.getConnection().setExceptionListener(reconHelper);
            }
            pool.getConnection().start();
        } catch (JMSException e) {
            stop();
            throw ExceptionUtils.newResourceException(e);
        }
    }
    
    public void stop() {
        try {
            jmsPool.destroy();
            logger.log(Level.FINE, "Destroyed the pool ");
            if (jmsPool.getConnection() != null) {
                jmsPool.getConnection().close();
                logger.log(Level.FINE, "Closed the connection ");
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unexpected exception stopping JMS connection: " + ex, ex);
        }
    }
    
    public javax.jms.Connection getConnection() {
        return jmsPool.getConnection();
    }
}
