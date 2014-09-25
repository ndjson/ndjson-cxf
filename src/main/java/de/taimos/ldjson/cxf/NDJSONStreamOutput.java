package de.taimos.ldjson.cxf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copyright 2014 Taimos GmbH<br>
 * <br>
 *
 * @author thoeger
 *
 */
public abstract class NDJSONStreamOutput implements StreamingOutput {
	
	/**
	 * The MediaType for ND-JSON
	 */
	public static final String MEDIA_TYPE = "application/x-ndjson";
	
	private static final int DEFAULT_POLL_TIMEOUT = 5;
	
	private static final int DEFAULT_HEARTBEAT_RATE = 5000;
	
	private static final String LINE_DELIMITER = "\n";
	
	private static final String ENCODING = "UTF-8";
	
	private static final Logger logger = LoggerFactory.getLogger(NDJSONStreamOutput.class);
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private final LinkedBlockingQueue<String> messageQ = new LinkedBlockingQueue<>();
	
	private final boolean heartbeat;
	
	private final int heartbeatMillis;
	
	private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
	
	
	/**
	 * same as NLDJSONStreamOutput(false)
	 */
	public NDJSONStreamOutput() {
		this(false);
	}
	
	/**
	 * if true same as NLDJSONStreamOutput(5000) otherwise heart beats are disabled
	 *
	 * @param heartbeat - <code>true</code> to activate heart beats
	 */
	public NDJSONStreamOutput(boolean heartbeat) {
		this.heartbeat = heartbeat;
		this.heartbeatMillis = NDJSONStreamOutput.DEFAULT_HEARTBEAT_RATE;
	}
	
	/**
	 * @param heartbeatMillis - the millisecond interval for heart beat messages
	 */
	public NDJSONStreamOutput(int heartbeatMillis) {
		if (heartbeatMillis <= 0) {
			throw new IllegalArgumentException();
		}
		this.heartbeat = true;
		this.heartbeatMillis = heartbeatMillis;
	}
	
	/**
	 * @return <code>true</code> if this stream is running; <code>false</code> otherwise
	 */
	public final boolean isRunning() {
		return this.running.get();
	}
	
	/**
	 * stop streaming
	 */
	public final void stop() {
		NDJSONStreamOutput.this.heartbeatExecutor.shutdown();
		this.running.set(false);
	}
	
	@Override
	public final void write(final OutputStream output) throws IOException, WebApplicationException {
		this.startStream();
		
		if (this.heartbeat) {
			this.startHeartbeat();
		}
		
		while (this.isRunning()) {
			try {
				final String poll = this.messageQ.poll(NDJSONStreamOutput.DEFAULT_POLL_TIMEOUT, TimeUnit.SECONDS);
				if ((poll != null) && this.isRunning()) {
					try {
						output.write(poll.getBytes(NDJSONStreamOutput.ENCODING));
						output.write(NDJSONStreamOutput.LINE_DELIMITER.getBytes(NDJSONStreamOutput.ENCODING));
						output.flush();
					} catch (final Exception e) {
						// If we cannot write to stream we stop streaming
						this.stop();
					}
				}
			} catch (final InterruptedException ie) {
				// Just retry
				NDJSONStreamOutput.logger.info("stream endpoint was interrupted");
			} catch (final Exception e) {
				NDJSONStreamOutput.logger.error("Error on stream endpoint", e);
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
		Runnable cmd = new Runnable() {
			
			@Override
			public void run() {
				if (NDJSONStreamOutput.this.isRunning()) {
					try {
						String heartbeatMessage = NDJSONStreamOutput.this.getHeartbeatMessage();
						NDJSONStreamOutput.this.messageQ.add(heartbeatMessage);
					} catch (final Exception e) {
						NDJSONStreamOutput.logger.error("Error on stream heartbeat", e);
					}
				}
			}
		};
		this.heartbeatExecutor.scheduleAtFixedRate(cmd, this.heartbeatMillis, this.heartbeatMillis, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Writes the given JSON string to the stream
	 *
	 * @param json the JSON string to write
	 */
	public final void writeObject(String json) {
		this.messageQ.add(json);
	}
	
	/**
	 * Writes the given object as JSON to the stream
	 *
	 * @param obj the object to write
	 */
	public abstract void writeObject(Object obj);
	
	/**
	 * Override to add tear down functionality
	 */
	protected void stopStream() {
		//
	}
	
	/**
	 * Override to add set up functionality
	 */
	protected void startStream() {
		//
	}
	
	/**
	 * Override to customize the heart beat message
	 *
	 * @return the heart beat message
	 */
	protected String getHeartbeatMessage() {
		return "{}";
	}
	
}
