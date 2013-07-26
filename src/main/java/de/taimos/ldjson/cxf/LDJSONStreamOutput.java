package de.taimos.ldjson.cxf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LDJSONStreamOutput implements StreamingOutput {
	
	private static final String LINE_DELIMITER = "\r\n";
	
	private static final String ENCODING = "UTF-8";
	
	private static final Logger logger = LoggerFactory.getLogger(LDJSONStreamOutput.class);
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private final LinkedBlockingQueue<String> messageQ = new LinkedBlockingQueue<>();
	
	private final boolean heartbeat;
	
	private final int heartbeatMillis;
	
	
	/**
	 * same as LDJSONStreamOutput(false)
	 */
	public LDJSONStreamOutput() {
		this(false);
	}
	
	/**
	 * same as LDJSONStreamOutput(5000)
	 * 
	 * @param heartbeat
	 */
	public LDJSONStreamOutput(boolean heartbeat) {
		this.heartbeat = heartbeat;
		this.heartbeatMillis = 5000;
	}
	
	/**
	 * @param heartbeatMillis
	 */
	public LDJSONStreamOutput(int heartbeatMillis) {
		super();
		this.heartbeat = true;
		this.heartbeatMillis = heartbeatMillis;
	}
	
	/**
	 * @return the running
	 */
	public boolean isRunning() {
		return this.running.get();
	}
	
	/**
	 * @param running the running to set
	 */
	public void setRunning(final boolean running) {
		this.running.set(running);
	}
	
	@Override
	public void write(final OutputStream output) throws IOException, WebApplicationException {
		this.startStream();
		
		if (this.heartbeat) {
			this.startHeartbeat();
		}
		
		while (this.isRunning()) {
			try {
				final String poll = this.messageQ.take();
				if ((poll != null) && this.isRunning()) {
					try {
						output.write(poll.getBytes(LDJSONStreamOutput.ENCODING));
						output.write(LDJSONStreamOutput.LINE_DELIMITER.getBytes(LDJSONStreamOutput.ENCODING));
						output.flush();
					} catch (final Exception e) {
						this.running.set(false);
					}
				}
			} catch (final InterruptedException ie) {
				// Just retry
				LDJSONStreamOutput.logger.info("stream endpoint was interrupted");
			} catch (final Exception e) {
				LDJSONStreamOutput.logger.error("Error on stream endpoint", e);
			}
		}
		try {
			output.close();
		} catch (final Exception e) {
			// ignore
		}
		this.stopStream();
	}
	
	private void startHeartbeat() {
		final Thread heartbeatThread = new Thread() {
			
			@Override
			public void run() {
				String heartbeatMessage = LDJSONStreamOutput.this.getHeartbeatMessage();
				
				while (LDJSONStreamOutput.this.running.get()) {
					try {
						LDJSONStreamOutput.this.messageQ.add(heartbeatMessage);
					} catch (final Exception e) {
						LDJSONStreamOutput.logger.error("Error on stream heartbeat", e);
					}
					try {
						Thread.sleep(LDJSONStreamOutput.this.heartbeatMillis);
					} catch (final InterruptedException e) {
						// ignore
					}
				}
			}
		};
		heartbeatThread.setDaemon(true);
		heartbeatThread.start();
	}
	
	/**
	 * Writes the given JSON string to the stream
	 * 
	 * @param json the JSON string to write
	 */
	public void writeObject(String json) {
		this.messageQ.add(json);
	}
	
	/**
	 * Writes the given JSON object to the stream
	 * 
	 * @param json the JSON object to write
	 */
	public void writeObject(Object obj) throws JsonProcessingException {
		this.writeObject(this.getMapper().writeValueAsString(obj));
	}
	
	protected void stopStream() {
		//
	}
	
	protected void startStream() {
		//
	}
	
	/**
	 * @return the mapper to use
	 */
	protected ObjectMapper getMapper() {
		return new ObjectMapper();
	}
	
	/**
	 * @return the heartbeat message
	 */
	protected String getHeartbeatMessage() {
		return "{}";
	}
	
}
