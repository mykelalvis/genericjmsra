/**
 * Copyright (c) 2004-2014 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.genericra.util.*;

import java.util.logging.*;

import javax.resource.spi.work.*;


/**
 * Work object as per JCA 1.5 specification.
 * This class makes sure that each message delivery happens in
 * different thread.
 *
 * @author Binod P.G
 */
public class WorkImpl implements Work {
    private static Logger _logger;

    static {
        _logger = LogUtils.getLogger();
    }

    InboundJmsResource jmsResource;

    public WorkImpl(InboundJmsResource jmsResource) {
        this.jmsResource = jmsResource;
    }

    public void run() {
        try {
            _logger.log(Level.FINER, "Now running the message consumption");
            this.jmsResource.refresh();
            this.jmsResource.getSession().run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {                
                this.jmsResource.releaseEndpoint();
                DeliveryHelper helper = this.jmsResource.getDeliveryHelper();
                if (helper.markedForDMD()) {
                    helper.sendMessageToDMD();
                }
            } catch (Exception e) {
                _logger.log(Level.SEVERE,
                        "Exception while releasing the JMS endpoint" + e.getMessage());
            } finally {
                try {
                    this.jmsResource.release();
                } catch (Exception e) {
                    _logger.log(Level.SEVERE, 
                            "Exception while releasing the JMS resource" + e.getMessage());
                }
            }
            _logger.log(Level.FINER, "Freed the resource now");
        }
    }

    public void release() {
        // For now do nothing.
    }
}
