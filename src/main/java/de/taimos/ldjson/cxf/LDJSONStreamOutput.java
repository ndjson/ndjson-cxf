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

public abstract class LDJSONStreamOutput implements StreamingOutput {
	
	/**
	 * The MediaType for LD-JSON
	 */
	public static final String MEDIA_TYPE = "application/x-ldjson";
	
	private static final int DEFAULT_POLL_TIMEOUT = 5;
	
	private static final int DEFAULT_HEARTBEAT_RATE = 5000;
	
	private static final String LINE_DELIMITER = "\n";
	
	private static final String ENCODING = "UTF-8";
	
	private static final Logger logger = LoggerFactory.getLogger(LDJSONStreamOutput.class);
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	
	private final LinkedBlockingQueue<String> messageQ = new LinkedBlockingQueue<>();
	
	private final boolean heartbeat;
	
	private final int heartbeatMillis;
	
	private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
	
	
	/**
	 * same as LDJSONStreamOutput(false)
	 */
	public LDJSONStreamOutput() {
		this(false);
	}
	
	/**
	 * if true same as LDJSONStreamOutput(5000) otherwise heart beats are disabled
	 * 
	 * @param heartbeat - <code>true</code> to activate heart beats
	 */
	public LDJSONStreamOutput(boolean heartbeat) {
		this.heartbeat = heartbeat;
		this.heartbeatMillis = LDJSONStreamOutput.DEFAULT_HEARTBEAT_RATE;
	}
	
	/**
	 * @param heartbeatMillis - the millisecond interval for heart beat messages
	 */
	public LDJSONStreamOutput(int heartbeatMillis) {
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
		LDJSONStreamOutput.this.heartbeatExecutor.shutdown();
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
				final String poll = this.messageQ.poll(LDJSONStreamOutput.DEFAULT_POLL_TIMEOUT, TimeUnit.SECONDS);
				if ((poll != null) && this.isRunning()) {
					try {
						output.write(poll.getBytes(LDJSONStreamOutput.ENCODING));
						output.write(LDJSONStreamOutput.LINE_DELIMITER.getBytes(LDJSONStreamOutput.ENCODING));
						output.flush();
					} catch (final Exception e) {
						// If we cannot write to stream we stop streaming
						this.stop();
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
		Runnable cmd = new Runnable() {
			
			@Override
			public void run() {
				if (LDJSONStreamOutput.this.isRunning()) {
					try {
						String heartbeatMessage = LDJSONStreamOutput.this.getHeartbeatMessage();
						LDJSONStreamOutput.this.messageQ.add(heartbeatMessage);
					} catch (final Exception e) {
						LDJSONStreamOutput.logger.error("Error on stream heartbeat", e);
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
