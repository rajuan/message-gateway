package org.apache.messagegateway.sms.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.messagegateway.sms.data.DeliveryStatusData;
import org.apache.messagegateway.sms.domain.SMSMessage;
import org.apache.messagegateway.sms.providers.SMSProviderFactory;
import org.apache.messagegateway.sms.providers.impl.twilio.TwilioStatus;
import org.apache.messagegateway.sms.repository.SmsOutboundMessageRepository;
import org.apache.messagegateway.sms.util.SmsMessageStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class SMSMessageService {

	 private static final Logger logger = LoggerFactory.getLogger(SMSMessageService.class);
	 
	private final SmsOutboundMessageRepository smsOutboundMessageRepository ;
	
	private final SMSProviderFactory smsProviderFactory ;
	
	private final JdbcTemplate jdbcTemplate ;
	
	private ExecutorService executorService ;
	
	private ScheduledExecutorService scheduledExecutorService ;
	
	@Autowired
	public SMSMessageService(final SmsOutboundMessageRepository smsOutboundMessageRepository,
			final SMSProviderFactory smsProviderFactory,
			final DataSource dataSource) {
		this.smsOutboundMessageRepository = smsOutboundMessageRepository ;
		this.smsProviderFactory = smsProviderFactory ;
		this.jdbcTemplate = new JdbcTemplate(dataSource) ;
	}
	
	@PostConstruct
	public void init() {
		logger.debug("Intializing SMSMessage Service.....");
		executorService = Executors.newSingleThreadExecutor();
		scheduledExecutorService = Executors.newSingleThreadScheduledExecutor() ;
		scheduledExecutorService.schedule(new BootupPendingMessagesTask(this.smsOutboundMessageRepository, this.smsProviderFactory) , 1, TimeUnit.MINUTES) ;
		//When do I have to shutdown  scheduledExecutorService ? :-( as it is no use after triggering BootupPendingMessagesTask
		//Shutdown scheduledExecutorService on application close event
	}
	
	public void sendShortMessage(final Collection<SMSMessage> messages) {
		logger.debug("Request Received to send messages.....");
		Date date = new Date() ;
		for(SMSMessage message: messages) {
			message.setSubmittedOnDate(date);
		}
		this.smsOutboundMessageRepository.save(messages) ;
		this.executorService.execute(new MessageTask(this.smsOutboundMessageRepository, this.smsProviderFactory, messages));
	}
	
	public Collection<DeliveryStatusData> getDeliveryStatus(final String tenantId, final Collection<Long> internalIds) {
		DeliveryStatusDataRowMapper mapper = new DeliveryStatusDataRowMapper() ;
		String internaIdString = internalIds.toString() ;
		internaIdString = internaIdString.replace("[", "(") ;
		internaIdString = internaIdString.replace("]", ")") ;
		String query = mapper.schema() + " where m.tenant_id=?"+" and m.internal_id in " +internaIdString;
		Collection<DeliveryStatusData> datas = this.jdbcTemplate.query(query, mapper, new Object[] {tenantId}) ;
		return datas ;
	}
	
	public void updateDeliverStatusFromServer(Long messageId, Map<String, String> parseResponse) {
		SMSMessage message = this.smsOutboundMessageRepository.findOne(messageId) ;
		message.setDeliveryStatus(TwilioStatus.smsStatus(parseResponse.get("MessageStatus")).getValue());
		
	}
	
	class DeliveryStatusDataRowMapper implements RowMapper<DeliveryStatusData> {

		private final StringBuilder buff = new StringBuilder() ;
		
		public DeliveryStatusDataRowMapper() {
			buff.append("select internal_id, external_id, delivered_on_date, delivery_status, delivery_error_message from m_outbound_messages m") ;
		}
		
		public String schema() {
			return buff.toString() ;
		}
		
		@Override
		public DeliveryStatusData mapRow(ResultSet rs, int rowNum) throws SQLException { 
			String internalId = rs.getString("internal_id") ;
			String externalId = rs.getString("external_id") ;
			Date deliveredOnDate = rs.getDate("delivered_on_date") ;
			Integer deliveryStatus = rs.getInt("delivery_status") ;
			String errorMessage = rs.getString("delivery_error_message") ;
			DeliveryStatusData data = new DeliveryStatusData(internalId, externalId, deliveredOnDate, deliveryStatus, errorMessage) ;
			return data;
		}
	}
	
	class MessageTask implements Runnable {

		final Collection<SMSMessage> messages ;
		final SmsOutboundMessageRepository smsOutboundMessageRepository ;
		final SMSProviderFactory smsProviderFactory ;
		
		public MessageTask(final SmsOutboundMessageRepository smsOutboundMessageRepository, 
				final SMSProviderFactory smsProviderFactory,
				final Collection<SMSMessage> messages) {
			this.messages = messages ;
			this.smsOutboundMessageRepository = smsOutboundMessageRepository ;
			this.smsProviderFactory = smsProviderFactory ;
		}
		
		@Override
		public void run() {
			this.smsProviderFactory.sendShortMessage(messages);
			this.smsOutboundMessageRepository.save(messages) ;
		}
	}
	
	class BootupPendingMessagesTask implements Callable<Integer> {

		final SmsOutboundMessageRepository smsOutboundMessageRepository ;
		final SMSProviderFactory smsProviderFactory ;
		
		public BootupPendingMessagesTask(final SmsOutboundMessageRepository smsOutboundMessageRepository, 
				final SMSProviderFactory smsProviderFactory) {
			this.smsOutboundMessageRepository = smsOutboundMessageRepository ;
			this.smsProviderFactory = smsProviderFactory ;
		}

		@Override
		public Integer call() throws Exception {
			logger.info("Sending Pending Messages on bootup.....");
			Integer page = 0;
			Integer initialSize = 200;
			Integer totalPageSize = 0;
			do {
				PageRequest pageRequest = new PageRequest(page, initialSize);
				Page<SMSMessage> messages = this.smsOutboundMessageRepository.findByDeliveryStatus(SmsMessageStatusType.PENDING.getValue(), pageRequest) ;
				page++;
				totalPageSize = messages.getTotalPages();
				this.smsProviderFactory.sendShortMessage(messages.getContent());
				this.smsOutboundMessageRepository.save(messages) ;
			}while (page < totalPageSize);
			return totalPageSize;
		}
	}
}
