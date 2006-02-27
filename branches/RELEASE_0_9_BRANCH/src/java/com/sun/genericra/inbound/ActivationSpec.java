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
package com.sun.genericra.inbound;
import javax.resource.ResourceException;
import javax.resource.spi.*;
import java.util.logging.*;
import com.sun.genericra.GenericJMSRA;
import com.sun.genericra.GenericJMSRAProperties;
import com.sun.genericra.util.*;

/**
 * ActivationSpec for javax.jms.MessageListener.
 *
 * @author Binod P.G
 */
public class ActivationSpec extends GenericJMSRAProperties 
                    implements javax.resource.spi.ActivationSpec{
    
    private String cfJndiName;
    private String cfProperties;
    private String destJndiName;
    private String destProperties;
    private String destinationType;
    private String messageSelector;
    private String subscriptionDurability = Constants.NONDURABLE;
    private String subscriptionName;
    private String clientId;
    private int redeliveryAttempts;
    private int redeliveryInterval;
    private int reconnectAttempts;
    private int reconnectInterval;
    private int maxPoolSize = 8;
    private int maxWaitTime = 3;

    private boolean isDmd = false;
    private String dmClassName; 
    private String dmJndiName; 
    private String dmProperties; 

    private static Logger logger;
    static {
        logger = LogUtils.getLogger();
    }

    private StringManager sm = StringManager.getManager(
        GenericJMSRA.class );

    public void setMaxWaitTime(int waitTime) {
        this.maxWaitTime = waitTime;
    }

    public int getMaxWaitTime() {
        return this.maxWaitTime;
    }

    public void setRedeliveryInterval(int interval) {
        this.redeliveryInterval = interval;
    }

    public int getRedeliveryInterval() {
        return this.redeliveryInterval;
    }

    public void setRedeliveryAttempts(int attempts) {
        this.redeliveryAttempts = attempts;
    }

    public int getRedeliveryAttempts() {
        return this.redeliveryAttempts;
    }

    public void setReconnectInterval(int interval) {
        this.reconnectInterval = interval;
    }

    public int getReconnectInterval() {
        return this.reconnectInterval;
    }

    public void setReconnectAttempts(int attempts) {
        this.reconnectAttempts = attempts;
    }

    public int getReconnectAttempts() {
        return this.reconnectAttempts;
    }

    public void setSubscriptionDurability(String durability) {
        this.subscriptionDurability = durability;
    }

    public String getSubscriptionDurability() {
        return this.subscriptionDurability;
    }

    public void setSubscriptionName(String name) {
        this.subscriptionName = name;
    }

    public String getSubscriptionName() {
        return this.subscriptionName;
    }

    public void setMessageSelector(String selector) {
        this.messageSelector = selector;
    }

    public String getMessageSelector() {
        return this.messageSelector;
    }

    public void setClientID(String clientId) {
        this.clientId = clientId;
    }

    public String getClientID() {
        return this.clientId;
    }

    public void setConnectionFactoryJndiName(String name) {
        this.cfJndiName = name;
    }

    public String getConnectionFactoryJndiName() {
        return this.cfJndiName;
    }

    public void setDestinationJndiName(String name) {
        this.destJndiName = name;
    }

    public String getDestinationJndiName() {
        return this.destJndiName;
    }

    public void setDestinationProperties(String properties) {
        this.destProperties = properties;
    }

    public String getDestinationProperties() {
        return this.destProperties;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getMaxPoolSize() {
        return this.maxPoolSize;
    }

    public void setSendBadMessagesToDMD(boolean isDmd) {
        this.isDmd = isDmd;
    }

    public boolean getSendBadMessagesToDMD() {
        return this.isDmd;
    }

    public void setDeadMessageJndiName(String jndiName) {
        this.dmJndiName = jndiName;
    }

    public String getDeadMessageDestinationJndiName() {
        return this.dmJndiName;
    }

    public void setDeadMessageDestinationClassName(String className) {
        this.dmClassName = className;
    }

    public String getDeadMessageDestinationClassName() {
        return this.dmClassName;
    }

    public void setDeadMessageDestinationProperties(String dmdProps) {
        this.dmProperties = dmdProps;
    }

    public String getDeadMessageDestinationProperties() {
        return this.dmProperties;
    }
          
    public void validate() throws InvalidPropertyException{
        logger.log(Level.FINE, ""+ this);
        //XXX: perform CF, XAQCF, XATCF validation!
        if (getMaxPoolSize() <= 0) {
            String msg = sm.getString("maxpoolsize_iszero");
            throw new InvalidPropertyException(msg);
        }

        if (getMaxWaitTime() < 0) {
            String msg = sm.getString("maxwaittime_lessthan_zero");
            throw new InvalidPropertyException(msg);
        }

        if (getRedeliveryAttempts() < 0) {
            String msg = sm.getString("redelivery_attempts_lessthan_zero");
            throw new InvalidPropertyException(msg);
        }

        if (getRedeliveryInterval() < 0) {
            String msg = sm.getString("redelivery_attempts_lessthan_zero");
            throw new InvalidPropertyException(msg);
        }

        if (getSendBadMessagesToDMD()) {
            if (getProviderIntegrationMode().equalsIgnoreCase(Constants.JNDI_BASED)) {
                if (StringUtils.isNull(getDeadMessageDestinationJndiName())){
                   String msg = sm.getString("dmd_jndi_null");
                   throw new InvalidPropertyException(msg);
                }
            } else {
                if (StringUtils.isNull(getDeadMessageDestinationClassName())){
                   String msg = sm.getString("dmd_class_null");
                   throw new InvalidPropertyException(msg);
                }

                if (StringUtils.isNull(getDeadMessageDestinationProperties())){
                   String msg = sm.getString("dmd_props_null");
                   throw new InvalidPropertyException(msg);
                }
            }
        }
    }

    public String toString() {
        String s = super.toString();
        s = s + "{RedeliveryInterval = " + getRedeliveryInterval() + "},";
        s = s + "{RedeliveryAttempts = " + getRedeliveryAttempts() + "},";
        s = s + "{ClientID = " + getClientID() + "},";
        s = s + "{MessageSelector = " + getMessageSelector() + "},";
        s = s + "{SubscriptionDurability = " + getSubscriptionDurability() + "},";
        s = s + "{ConnectionFactoryJNDIName = " + getConnectionFactoryJndiName() + "},";
        s = s + "{SubscriptionName = " + getSubscriptionName() + "},";
        s = s + "{DestinationJNDIName = " + getDestinationJndiName() + "},";
        s = s + "{DestinationType = " + getDestinationType() + "},";
        s = s + "{MaxPoolSize = " + getMaxPoolSize() + "},";
        s = s + "{DestinationProperties = " + getDestinationProperties() + "},";
        s = s + "{DeadMessageDestinationJndiName = " + getDeadMessageDestinationJndiName() + "},";
        s = s + "{DeadMessageDestinationClassName = " + getDeadMessageDestinationClassName() + "},";
        s = s + "{DeadMessageDestinationProperties = " + getDeadMessageDestinationProperties() + "},";
        s = s + "{SendBadMessagesToDMD = " + getSendBadMessagesToDMD() + "},";
        return s;
    }


    public String getDestinationType() {
        return destinationType;
    }
    
    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }
}