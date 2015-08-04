package com.example;

import redis.clients.jedis.Jedis;

public class RedisMonitor {

	private static final String CONNECTED_CLIENTS = "connected_clients";
	private static final String TOTAL_COMMANDS_PROCESSED = "total_commands_processed";
	private static final String TOTAL_CONNECTIONS_RECEIVED = "total_connections_received";
	private static final String USED_MEMORY_RSS = "used_memory_rss";
	private static final String USED_MEMORY_PEAK = "used_memory_peak";
	
	
	public static void main(String[] args) throws InterruptedException {
		// Connecting to Redis server on localhost
		final Jedis jedis = new Jedis("localhost");

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				jedis.disconnect();
			}
		});

		System.out.println("Connection to server sucessfully");

		// check whether server is running or not
		System.out.println("Server is running: " + jedis.ping());

		Parser parser = new Parser(jedis);
		
		StringBuilder sb = new StringBuilder("currentTimeMs ").append(",").append( USED_MEMORY_RSS).append(",").append( USED_MEMORY_PEAK).append(",")
				.append( TOTAL_CONNECTIONS_RECEIVED).append(",").append( TOTAL_COMMANDS_PROCESSED).append(",").append("TotalCommandsProcessedInLastSecond");
		System.out.println(sb.toString());
		
		long lastTotalCommandProcessed = 0;
		long curTotalCommandProcessed = 0;
		for (;;) {
			parser.memory();
			sb.setLength(0);
			sb.append(System.currentTimeMillis()).append(",").append(parser.extractAsLong(USED_MEMORY_RSS)).append(",").append(parser.extractAsLong(USED_MEMORY_PEAK)).append(",");
			parser.stats();
			sb.append(parser.extractAsLong(TOTAL_CONNECTIONS_RECEIVED)).append(",").append(curTotalCommandProcessed = parser.extractAsLong(TOTAL_COMMANDS_PROCESSED)).append(",")
			.append(curTotalCommandProcessed - lastTotalCommandProcessed).append(",");
			
			lastTotalCommandProcessed = curTotalCommandProcessed;
		
			parser.clients();
			sb.append(parser.extractAsLong(CONNECTED_CLIENTS)).append(",");
			
			System.out.println(sb.toString());
			
			Thread.sleep(1000);
		}

	}

	public static class Parser {

		String content;
		int index;

		long lValue;

		Jedis jedis;

		Parser(Jedis jedis) {
			this.jedis = jedis;
		}

		void memory() {
			content = jedis.info("memory");
			index = 0;
		}
		void stats() {
			content = jedis.info("stats");
			index = 0;
		}
		void clients() {
			content = jedis.info("clients");
			index = 0;
		}
		long extractAsLong(String name) {

			String token = name + ":";
			index = content.indexOf(token, index) + token.length();
			long val = 0;
			for (char c = content.charAt(index); index < content.length() && c != '\n'
					&& c != '\r'; index++, c = content.charAt(index)) {
				val = (val * 10) + Character.getNumericValue(c);
			}

			return val;
		}

	}
}
