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


package com.sun.genericra.monitoring;

import com.sun.genericra.inbound.async.InboundJmsResourcePool;

import com.sun.genericra.inbound.async.InboundJmsResource;

/**
 * Object that stores the pool parameters of an inbound resource pool.
 * 
 */
public class PoolStatistics {
    
    
    private InboundJmsResourcePool pool;
    
    private static String MAX_SIZE = "Maximum Size of the pool";
    
    private static String MAX_WAIT_TIME = "Maximum wait time to queue the request for a resource";
    
    private static String CURRENT_RESOURCES = "No of resources in the pool (FREE + BUSY)";
    
    private static String BUSY_RESOURCES = "No of resources busy";
    
    private static String FREE_RESOURCES = "No of free resources";
    
    private static String CONNECTIONS_IN_USE = "No of connections being used ";
    
    private static String REQUESTS_WAITING = "No of requests waiting for resource";
    private static String SEPARATOR = " : ";
    private static String NEW_LINE = "\n";
    
    /** Creates a new instance of PoolStatistics */
    public PoolStatistics(InboundJmsResourcePool pool) {
        this.pool = pool;
    }
    
    public void setPool(InboundJmsResourcePool pool) {
        this.pool = pool;
    }
    
    
    /**
     * Getter for property currentSize.
     * @return Value of property currentSize.
     */
    private  int getCurrentSize() {
        return pool.getCurrentResources();
    }
    
    /**
     * Getter for property busyResources.
     * @return Value of property busyResources.
     */
    private int getBusyResources() {
        return pool.getBusyResources();
    }
    
    
    /**
     * Getter for property freeResources.
     * @return Value of property freeResources.
     */
    private int getFreeResources() {
        return pool.getFreeResources();
    }
    
    /**
     * Getter for property waiting.
     * @return Value of property waiting.
     */
    private int getWaiting() {
        return pool.getWaiting();
    }
    
    private int getConnections() {
        return pool.getConnectionsInUse();
    }
    
    private int getMaxSize() {
        return pool.getMaxSize();
    }
    
    private long getMaxWaitTime() {
        return  pool.getMaxWaitTime();
    }
    public String formatStatistics() {
        StringBuffer output = new StringBuffer();
        
        output.append(this.MAX_SIZE);
        output.append(this.SEPARATOR);
        output.append(this.getMaxSize());
        output.append(this.NEW_LINE);
        
        
        output.append(this.MAX_WAIT_TIME);
        output.append(this.SEPARATOR);
        output.append(this.getMaxWaitTime());
        output.append(this.NEW_LINE);
        
        output.append(this.CONNECTIONS_IN_USE);
        output.append(this.SEPARATOR);
        output.append(this.getConnections());
        output.append(this.NEW_LINE);
        
        output.append(this.CURRENT_RESOURCES);
        output.append(this.SEPARATOR);
        output.append(this.getCurrentSize());
        output.append(this.NEW_LINE);
        
        output.append(this.BUSY_RESOURCES);
        output.append(this.SEPARATOR);
        output.append(this.getBusyResources());
        output.append(this.NEW_LINE);
        
        output.append(this.FREE_RESOURCES);
        output.append(this.SEPARATOR);
        output.append(this.getFreeResources());
        output.append(this.NEW_LINE);
        
        output.append(this.REQUESTS_WAITING);
        output.append(this.SEPARATOR);
        output.append(this.getWaiting());
        output.append(this.NEW_LINE);
        
        
        return output.toString();
    }
    
}
