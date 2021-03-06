package org.apache.messagegateway.sms.providers.impl.twilio;

import org.apache.messagegateway.sms.util.SmsMessageStatusType;

import com.twilio.rest.api.v2010.account.Message;

public class TwilioStatus {

	public static SmsMessageStatusType smsStatus(final Message.Status twilioStatus) {
		SmsMessageStatusType smsStatus = SmsMessageStatusType.PENDING;
		switch(twilioStatus.toString()) {
		case "queued":
		case "sending":
			smsStatus = SmsMessageStatusType.WAITING_FOR_REPORT ;
			break ;
		case "sent" :
			smsStatus = SmsMessageStatusType.SENT ;
				break ;
		case "delivered":
			smsStatus = SmsMessageStatusType.DELIVERED;
			break ;
		case "undelivered":
		case "failed":
			smsStatus = SmsMessageStatusType.FAILED ;
			break ;
		}
		return smsStatus ;
	}
	
	public static SmsMessageStatusType smsStatus(final String twilioStatus) {
		SmsMessageStatusType smsStatus = SmsMessageStatusType.PENDING;
		switch(twilioStatus) {
		case "queued":
		case "sending":
			smsStatus = SmsMessageStatusType.WAITING_FOR_REPORT ;
			break ;
		case "sent" :
			smsStatus = SmsMessageStatusType.SENT ;
				break ;
		case "delivered":
			smsStatus = SmsMessageStatusType.DELIVERED;
			break ;
		case "undelivered":
		case "failed":
			smsStatus = SmsMessageStatusType.FAILED ;
			break ;
		}
		return smsStatus ;
	}
}
