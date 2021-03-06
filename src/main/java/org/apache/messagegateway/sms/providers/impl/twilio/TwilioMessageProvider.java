package org.apache.messagegateway.sms.providers.impl.twilio;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.messagegateway.configuration.HostConfig;
import org.apache.messagegateway.exception.MessageGatewayException;
import org.apache.messagegateway.sms.domain.SMSBridge;
import org.apache.messagegateway.sms.domain.SMSMessage;
import org.apache.messagegateway.sms.providers.SMSProvider;
import org.apache.messagegateway.sms.util.SmsMessageStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;

@Service(value="Twilio")
public class TwilioMessageProvider implements SMSProvider {

    private static final Logger logger = LoggerFactory.getLogger(TwilioMessageProvider.class);

    private HashMap<String, ArrayList<TwilioRestClient>> restClients = new HashMap<>() ; //tenantId, twilio clients
    
    
    private final String callBackUrl ;
    
    @Autowired
    TwilioMessageProvider(final HostConfig hostConfig) {
    	callBackUrl = String.format("%s://%s:%d/twilio/report/", hostConfig.getProtocol(),  hostConfig.getHostName(), hostConfig.getPort());
    	logger.info("Registering call back to twilio:"+callBackUrl);
    }

    
    @Override
    public void sendMessage(final SMSBridge smsBridgeConfig, final SMSMessage message)
        throws MessageGatewayException {
    	//Based on message id, register call back. so that we get notification from Twilio about message status
    	String statusCallback = callBackUrl+message.getId() ;
        final TwilioRestClient twilioRestClient = this.getRestClient(smsBridgeConfig);
        logger.info("Sending SMS to " + message.getMobileNumber() + " ...");
        MessageCreator creator = new MessageCreator(new PhoneNumber(message.getMobileNumber()), new PhoneNumber(smsBridgeConfig.getPhoneNo()) , message.getMessage() ) ;
        creator.setStatusCallback(statusCallback) ;
        try {
        	Message twilioMessage = creator.create(twilioRestClient) ;
        	message.setExternalId(twilioMessage.getSid());
        	logger.debug("TwilioMessageProvider.sendMessage():"+TwilioStatus.smsStatus(twilioMessage.getStatus()).getValue());
        	message.setDeliveryStatus(TwilioStatus.smsStatus(twilioMessage.getStatus()).getValue()) ;
        }catch (ApiException e) {
        	logger.debug("ApiException while sending message to :"+message.getMobileNumber());
        	message.setDeliveryStatus(SmsMessageStatusType.FAILED.getValue());
        	message.setDeliveryErrorMessage(e.getMessage());
        }
    }
    
    private TwilioRestClient getRestClient(final SMSBridge smsBridge) {
    	TwilioRestClient client = null ;
    	ArrayList<TwilioRestClient> tenantsClients = this.restClients.get(smsBridge.getTenantId()) ;
    	if(tenantsClients == null) {
    		tenantsClients = new ArrayList<>() ; 
    		client = this.get(smsBridge) ;
    		tenantsClients.add(client) ;
    		this.restClients.put(smsBridge.getTenantId(), tenantsClients) ;
    	}else {
    		for(TwilioRestClient client1: tenantsClients) {
    			if(client1.getAccountSid().equals(smsBridge.getConfigValue(TwilioMessageConstants.PROVIDER_ACCOUNT_ID))) {
    				client = client1 ;
    				break ;
    			}
    		}
    		if(client == null) {
    			client = this.get(smsBridge) ;
        		tenantsClients.add(client) ;
    		}
    	}
    	return client ;
    }
    
    TwilioRestClient get(final SMSBridge smsBridgeConfig) {
    	String providerAccountId = smsBridgeConfig.getConfigValue(TwilioMessageConstants.PROVIDER_ACCOUNT_ID) ;
    	String providerAuthToken = smsBridgeConfig.getConfigValue(TwilioMessageConstants.PROVIDER_AUTH_TOKEN) ;
        final TwilioRestClient client = new TwilioRestClient.Builder(providerAccountId, providerAuthToken).build();
        return client;
    }
}
