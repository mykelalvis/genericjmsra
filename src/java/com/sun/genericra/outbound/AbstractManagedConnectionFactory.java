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
package com.sun.genericra.outbound;
import java.io.PrintWriter;
import java.util.logging.*;
import java.util.Set;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;
import com.sun.genericra.GenericJMSRAProperties;
import com.sun.genericra.util.*;

/**
 * <code>ManagedConnectionFactory</code> implementation of the Generic 
 * JMS resource adapter and is a factory of both <code>ManagedConnection</code> and 
 * JMS-specific <code>ConnectionFactory</code> instances.
 *  
 * @author Sivakumar Thyagarajan
 */
public abstract class AbstractManagedConnectionFactory extends GenericJMSRAProperties
                implements javax.resource.spi.ManagedConnectionFactory, ResourceAdapterAssociation {
    
    //MCF-specific configurable properties
    private String connectionFactoryJndiName;
    private String clientId = null;
    //by default, run as non-ACC. Use System Property or MCF property to enable
    private static boolean inAppClientContainer = false; 
    private boolean connectionValidationEnabled = false; //disabled by default

    //MCF state
    private static final String INACC_SYSTEM_PROP_KEY = 
                                    "genericra.inAppClientContainer";
    private PrintWriter logWriter;
    private ConnectionFactory connectionFactory = null;
    
    protected int destinationMode = Constants.UNIFIED_SESSION;

    private static Logger logger;
    static {
        logger = LogUtils.getLogger();
    }

    //Use system property to determine ACC status. 
    static {
        String s = System.getProperty(INACC_SYSTEM_PROP_KEY);
        if (s != null) {
            inAppClientContainer = (new Boolean(s)).booleanValue();
        }
    }
    
    public AbstractManagedConnectionFactory(){
    }
    
    public PrintWriter getLogWriter() throws ResourceException {
        return logWriter;
    }

    public void setLogWriter(PrintWriter pw) throws ResourceException {
        this.logWriter = pw;
    }

    public Object createConnectionFactory() throws ResourceException {
        //instantiate connection factory with RA's default simple ConnectionManager
        ConnectionManager cm = new com.sun.genericra.outbound.ConnectionManager();
        return new com.sun.genericra.outbound.ConnectionFactory(this, cm);
    }

    public Object createConnectionFactory(ConnectionManager cm)
                    throws ResourceException {
        return new com.sun.genericra.outbound.ConnectionFactory(this, cm);
    }

    /* 
     * @see javax.resource.spi.ManagedConnectionFactory#createManagedConnection(javax.security.auth.Subject, javax.resource.spi.ConnectionRequestInfo)
     */
    public ManagedConnection createManagedConnection(Subject subject,
                    ConnectionRequestInfo cri) throws ResourceException {
         //Create or lookup JMS connection factory if not already done
         //Create a connection from the JMS CF and create a ManagedConnection
         //from the created connection
    	try {
            initializeConnectionFactory();
            PasswordCredential pc = SecurityUtils.getPasswordCredential(this, 
                                                    subject, cri);
            javax.jms.Connection physicalCon = createPhysicalConnection(pc);
            
            return new com.sun.genericra.outbound.ManagedConnection(this, pc,
                            (com.sun.genericra.outbound.ConnectionRequestInfo) cri, 
                             physicalCon);
        } catch (ResourceException e) {
        	throw ExceptionUtils.newResourceException(e);
        } catch (JMSException e) {
        	throw ExceptionUtils.newResourceException(e);
        }
    }
    
    private javax.jms.Connection createPhysicalConnection(PasswordCredential pc) throws JMSException {
        javax.jms.Connection physicalCon = null;
        
        if (this.getSupportsXA()) {
            physicalCon = createXAConnection(pc,this.connectionFactory);
        } else {
            physicalCon = createConnection(pc,this.connectionFactory);
        }

        return physicalCon;
    }

    protected abstract javax.jms.XAConnection 
    createXAConnection(PasswordCredential pc, ConnectionFactory cf) throws JMSException;

    protected abstract javax.jms.Connection 
    createConnection(PasswordCredential pc, ConnectionFactory cf) throws JMSException;

    /**
     * Overridden by MCF for TCF, QCF and JMS CF to return their appropriate
     * CF class names, so that the relevant JavaBean class can be used
     * by the builder.
     * 
     * @return String ClassName of the MoM-specific ConnectionFactory to be 
     *                 created by the MCF.
     */
    protected abstract String getActualConnectionFactoryClassName();

    private void initializeConnectionFactory() throws ResourceException {
    	if (this.connectionFactory == null) {
            ObjectBuilder cfBuilder = null;
            ObjectBuilderFactory obf = new ObjectBuilderFactory();
            if (this.getProviderIntegrationMode().
                            equalsIgnoreCase(Constants.JNDI_BASED)) {
                cfBuilder =   obf.createUsingJndiName(
                              this.getConnectionFactoryJndiName(),
                              this.getJndiProperties());
            }  else {
                cfBuilder = obf.createUsingClassName(getActualConnectionFactoryClassName());
                cfBuilder.setProperties(this.getConnectionFactoryProperties());
            }
            String setMethod = this.getCommonSetterMethodName();
            if (!StringUtils.isNull(setMethod)) { 
                cfBuilder.setCommonSetterMethodName(setMethod);
            }
            
            this.connectionFactory = (ConnectionFactory) cfBuilder.build();
    	}
    }

    /* 
     * @see javax.resource.spi.ManagedConnectionFactory#matchManagedConnections(java.util.Set, javax.security.auth.Subject, javax.resource.spi.ConnectionRequestInfo)
     */
    public ManagedConnection matchManagedConnections(Set connectionSet, 
                    Subject subject, ConnectionRequestInfo cxRequestInfo) 
                        throws ResourceException {
        if(connectionSet == null) {
            return null;
        }
        
        PasswordCredential pc = SecurityUtils.getPasswordCredential(this, subject, cxRequestInfo);
        
        java.util.Iterator iter = connectionSet.iterator();
        com.sun.genericra.outbound.ManagedConnection mc = null;
        while(iter.hasNext()) {
            try {
                mc = (com.sun.genericra.outbound.ManagedConnection) iter.next();
                debug("Matching managed connections ->" + mc);
            } catch(java.util.NoSuchElementException nsee) {
                throw ExceptionUtils.newResourceException(nsee);
            }
            
            if(pc == null && this.equals(mc.getManagedConnectionFactory())) {
                    if (!mc.isDestroyed()) return mc;
            } else if(SecurityUtils.isPasswordCredentialEqual(pc, mc.getPasswordCredential()) == true) {
                	if (!mc.isDestroyed()) return mc;
            }
        }
        return null;
    }

    public String getClientId() {
        return this.clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return Returns the connectionFactoryJndiName.
     */
    public String getConnectionFactoryJndiName() {
    	return connectionFactoryJndiName;
    }
    
    /**
     * @param connectionFactoryJndiName The connectionFactoryJndiName to set.
     */
    public void setConnectionFactoryJndiName(String connectionFactoryJndiName) {
    	this.connectionFactoryJndiName = connectionFactoryJndiName;
    }
	
    /**
     * @return Returns the inAppClientContainer.
     */
    public boolean isInAppClientContainer() {
        return inAppClientContainer;
    }
    
    /**
     * @return Returns the enableValidation.
     */
    public boolean getConnectionValidationEnabled() {
    	return this.connectionValidationEnabled;
    }
    
    /**
     * @param enableValidation The enableValidation to set.
     */
    public void setConnectionValidationEnabled(boolean connectionValidationEnabled) {
    	this.connectionValidationEnabled = connectionValidationEnabled;
    }

    public int hashCode(){
        //XXX: enhance
        return super.hashCode(); 
    }
    
    public boolean equals(Object obj){
        //debug("equals" + obj);
        
        //XXX: enhance
        if (obj == null) return false;
        if(!(super.equals(obj))) return false;
        //if (obj == null) return false;
        if (!(obj instanceof AbstractManagedConnectionFactory)) return false; 
        debug("equals - no false yet");

        AbstractManagedConnectionFactory other = (AbstractManagedConnectionFactory) obj;
        boolean eq = (StringUtils.isEqual(this.clientId, other.clientId)
                 && StringUtils.isEqual(this.connectionFactoryJndiName, other.connectionFactoryJndiName)
                 && this.destinationMode == other.destinationMode);
        debug(" equals - final: " + eq);
        return eq;
    }

    public int getDestinationMode() {
        return this.destinationMode;
    }
    
    private void debug(String s) {
        logger.log (Level.FINEST, "[AbstractMCF] " + s);
    }
}
