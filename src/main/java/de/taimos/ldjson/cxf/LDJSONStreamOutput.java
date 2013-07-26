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
	
	private static final Logger logger = LoggerFactory.getLogger(LDJSONStreamOutput.class);
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private final LinkedBlockingQueue<String> messageQ = new LinkedBlockingQueue<>();
	
	private final boolean heartbeat;
	
	
	public LDJSONStreamOutput() {
		this(false);
	}
	
	/**
	 * @param heartbeat
	 */
	public LDJSONStreamOutput(boolean heartbeat) {
		this.heartbeat = heartbeat;
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
						output.write(poll.getBytes("UTF-8"));
						output.write("\r\n".getBytes("UTF-8"));
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
				final long timeout = 5000;
				
				while (LDJSONStreamOutput.this.running.get()) {
					try {
						LDJSONStreamOutput.this.messageQ.add("{}");
					} catch (final Exception e) {
						LDJSONStreamOutput.logger.error("Error on stream heartbeat", e);
					}
					try {
						Thread.sleep(timeout);
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
	
}
