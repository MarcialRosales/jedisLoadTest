package com.example;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

/**
 * 
 *   
 * @author mrosales
 *
 */
@SpringBootApplication
public class JedisMultipleTestClient  implements CommandLineRunner {

	Pool pool;

	/** Redis server hostname or Sentinel hostname */
	@Value("${redis:localhost}")
	String redis = "localhost";

	/** Redis server port */
	@Value("${redisPort:6379}")
	int redisPort = 6379;

	/** Sentinels defined as hostname:port separated by commas */
	@Value("${sentinels:localhost:26379}")
	String sentinels = "localhost:26379";

	/** Redis Sentinel master name. Leave it blank if you are not using Sentinel.   */
	@Value("${sentinelMaster:}")
	String sentinelMaster = "";
	
	/** maximum number of connections in the JedisPool */
	@Value("${poolSize:10}")
	int poolSize = 10;
	
	/** Concurrent number of running workflows. Note: if the poolSize is lower than this value, we will see an increase in latency.   */
	@Value("${concurrentProducers:1}")
	int concurrentProducers = 1;

	/** how many times we restart a workflow */
	@Value("${times:1}")
	int times = 1;

	/** abort the test if after 5 minutes it has not completed */
	@Value("${abortAfterNMin:5}")
	int abortAfterNMin = 5;

	/** how many times we repeat or iterate over a single workflow, simulating a client requesting the worklow 'workflowTimes' */
	@Value("${workflowTimes:10}")
	int workflowTimes = 10;

	/** how many distinct hash keys each workflow uses  */
	@Value("${entryCount:2}")
	int entryCount = 2;

	/** how many distinct hash attributes each workflow uses */
	@Value("${fieldCount:10}")
	int fieldCount = 10;
	
	/** Size in bytes of the key's value */ 
	@Value("${dataLength:100}")
	int dataLength = 100;
	
	/** Key's expiry  */
	@Value("${expiryInSec:120}")
	int expiryInSec = 120; // after 2 minutes
	
	/** delete keys when workflowTimes completes  */
	@Value("${deleteKeys:true}")
	boolean deleteKeys = true; 

	/** type of command used to set/get keys in a hash: Either single (uses hget/hset) vs multiple (hmset/hmget) */
	@Value("${cmdStrategy:single}")
	String strategy = "single";
	
	
	/** Maximum allowed or tolerated latency for each redis command. Every latency that exceeds this threshold is accounted  */
	@Value("${thresholdMsec:100}")
	int thresholdMsec = 100;
	
	enum CmdStrategyType {
		single, multiple
	}
	
	ExecutorService executor;
	Semaphore waitUntilCompleted = new Semaphore(0);
	Stat[] stats;
	CommandStrategy cmdStrategy;
	
	
	private void start() {
		
		
		executor = Executors.newFixedThreadPool(concurrentProducers);
		
		stats = new Stat[concurrentProducers];
		
		switch(CmdStrategyType.valueOf(strategy)) {
		case multiple:
			cmdStrategy = new HmgetHmsetStrategy();
			break;
		case single:
		default:
			cmdStrategy = new HgetHsetStrategy();
			break;
		}
		
		JedisPoolConfig config = new JedisPoolConfig();
		config.setBlockWhenExhausted(true);
		config.setMaxTotal(poolSize);
		config.setMaxWaitMillis(60000);

		if (sentinelMaster != "") {
			String[] values = sentinels.split(",");
			Set<String> sentinelSet = new HashSet<String>(Arrays.asList(values)); 
			this.pool = new SentinelPool(new JedisSentinelPool(sentinelMaster, sentinelSet, config));
			System.out.println("sentinels:" + sentinels);
			System.out.println("sentinelMaster:" + sentinelMaster);
		}else {
			this.pool = new StandardPool(new JedisPool(config, redis, 6379));
			System.out.println("redis hostname:" + redis);
		}
		
		System.out.println("poolSize:" + poolSize);
		System.out.println("concurrentProducers:" + concurrentProducers);
		System.out.println("times:" + times);
		System.out.println("workflowTimes:" + workflowTimes);
		System.out.println("dataLength:" + dataLength);
		System.out.println("entryCount:" + entryCount);
		System.out.println("expiryInSec:" + expiryInSec);
		System.out.println("deleteKeys:" + deleteKeys);
		System.out.println("thresholdMsec:" + thresholdMsec);
		System.out.println("cmdStrategy:" + strategy);
		
		System.out.println("total number of keys to generate:" + concurrentProducers * times * entryCount);
		
	}

	interface Pool {
		Jedis getResource();
		void destroy();
	}
	
	class StandardPool implements Pool {
		JedisPool pool;
		
		public StandardPool(JedisPool pool) {
			super();
			this.pool = pool;
		}

		public Jedis getResource() {
			return pool.getResource();
		}

		@Override
		public void destroy() {
			pool.destroy();
			
		}
	}
	class SentinelPool implements Pool {
		JedisSentinelPool pool;
		
		public SentinelPool(JedisSentinelPool pool) {
			super();
			this.pool = pool;
		}

		public Jedis getResource() {
			return pool.getResource();
		}
		@Override
		public void destroy() {
			pool.destroy();
			
		}

	}
	
	private void healthCheck() {
		// check whether server is running or not
		try (Jedis jedis = pool.getResource()) {
			System.out.println("Server is running: " + jedis.ping());
		}
	}

	private void test() throws InterruptedException {
		
		System.out.println("Executing Test with "+ concurrentProducers + " concurrent connections");
		
		// we no need to start them at the same time (i..e semaphore, etc)
		for (int i = 0; i < concurrentProducers; i++){
			executor.execute(new WorkflowSequencer(new WorkflowImpl(i, cmdStrategy,  stats[i] = new Stat())));
		}
		
		// Note: we wait abortAfterNMin minutes for safety but clearly 
		if (waitUntilCompleted.tryAcquire(concurrentProducers, abortAfterNMin, TimeUnit.MINUTES)) {
			System.out.println("All " + concurrentProducers + " completed");
		}else {
			System.out.println("Only " + waitUntilCompleted.availablePermits() + " completed after " + abortAfterNMin + "(abortAfterNMin) minutes");
		}
		printStats();
	}
	
	private void printStats() {
		long maxElapsed = 0;
		long totalElapsed = 0;
		long totalCommands = 0;
		long greaterThanThreshold = 0;
		
		for  (int i = 0; i < stats.length; i++) {
			if (stats[i].maxElapsed > maxElapsed) {
				maxElapsed = stats[i].maxElapsed;
			}
			
			totalElapsed += stats[i].totalElapsed;
			totalCommands += stats[i].totalCommands;
			greaterThanThreshold += stats[i].greaterThanThreshold;
		}
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("Total Commands:").append(totalCommands);
		System.out.println(sb.toString());
		
		sb.setLength(0);
		sb.append("Max/Average ResponseTime: ").append(maxElapsed).append("/").append(totalElapsed / totalCommands);
		System.out.println(sb.toString());
		
		sb.setLength(0);
		if (greaterThanThreshold > 0) {
			sb.append("% greater than threshold(").append(thresholdMsec).append("msec)").append(greaterThanThreshold).append("").append(greaterThanThreshold * 100 / totalCommands)
			.append("% (").append(greaterThanThreshold).append(")");
		}else {
			sb.append("Latency below ").append(thresholdMsec).append("msec");
		}
		System.out.println(sb.toString());
		
	}
	
	private void terminate() {
		executor.shutdown();
		pool.destroy();
	}
	
	class WorkflowSequencer implements Runnable {
		
		Workflow workflow;
		
		WorkflowSequencer(Workflow workflow) {
			this.workflow = workflow;
		}
		
		public void run() {
			
			// every time simulates a distinct workflow/user
			for (int i = 0; i < times; i++) {
				
				workflow.begin();
				
				for (int j = 0; j < workflowTimes; j++) {
					workflow.invoke();
				}
				
				workflow.terminate();
				
			}
			waitUntilCompleted.release();
		}
	}
	
	public interface Workflow {
		void begin();
		void terminate();
		void invoke();
	}
	
	/**
	 * This class represents the traffic in terms of Jedis commands generated by a user interacting with an application that uses Jedis
	 * as its cache mechanism.
	 * Every time we run a workflow, it will set 'entryCount' keys (entryCount) and it will get them too. 
	 * 
	 * Typically, each workflow will be invoked by one and only one thread. 
	 * 
	 * @author mrosales
	 *
	 */
	class WorkflowImpl implements Workflow {
		int id;
		Stat stat;
		CommandStrategy cmdStrategy;
		
		
		byte[][] keys;
		Map<byte[], byte[]> fields;
		byte[][] fieldNames;
		
		byte[] data;
		
		WorkflowImpl(int id, CommandStrategy cmdStrategy, Stat stat) {
			this.id = id;
			this.stat = stat;
			this.cmdStrategy = cmdStrategy;
			keys = new byte[entryCount][];
			fields = new HashMap<byte[], byte[]>();
			fieldNames = new byte[fieldCount][];
			data = new byte[dataLength];
			Arrays.fill(data, (byte)1);
			
			for (int j = 0; j < fieldCount; j++) {
				fields.put(fieldNames[j] = ("field" + j).getBytes(), data);
				
			}
		}
		int sequence = 0;
		public void begin() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < keys.length; i++) {
				String key = sb.append("K").append(id).append(System.currentTimeMillis()).append(i).toString();
				keys[i] = key.getBytes();
				sb.setLength(0);
			}
		
		}
		public void terminate() {
			// remove the key(s) : simulates the client logging out
			if (deleteKeys) {
				try (Jedis jedis = pool.getResource()) {
					jedis.del(keys);
				}
			}
		}
		public void invoke() {
			try (Jedis jedis = pool.getResource()) {
				cmdStrategy.set(jedis, stat, keys, fields, expiryInSec);
				cmdStrategy.get(jedis, stat, keys, fieldNames);
			}			
		}
	}
	
	public interface CommandStrategy {
		void set(Jedis jedis, Stat stat, byte[][] keys, Map<byte[],byte[]> fields,  long expiry);
		void get(Jedis jedis, Stat stat, byte[][] keys, byte[][] fields);
	}
	public class HgetHsetStrategy implements CommandStrategy {

		@Override
		public void set(Jedis jedis, Stat stat, byte[][] keys,  Map<byte[],byte[]> fields, long expiry) {
			for (int i = 0; i < entryCount; i++) {
				for (Map.Entry<byte[], byte[]> entry : fields.entrySet()) {
					long t0 = System.currentTimeMillis();
					jedis.hset(keys[i], entry.getKey(), entry.getValue());
					long t1 = System.currentTimeMillis();
					
					stat.reportCommand(t0, t1);
				}
				
				// reset the expiry time for the key
				jedis.expire(keys[i], expiryInSec);
				
			}
		}

		@Override
		public void get(Jedis jedis, Stat stat, byte[][] keys, byte[][] fields) {
			for (int i = 0; i < entryCount; i++) {
				for (int j = 0; j < fieldCount; j++) {
					long t0 = System.currentTimeMillis();
					assert jedis.hget(keys[i], fields[j]) != null;
					long t1 = System.currentTimeMillis();

					stat.reportCommand(t0, t1);
				}
			}
		}
		
	}
	public class HmgetHmsetStrategy implements CommandStrategy {

		@Override
		public void set(Jedis jedis, Stat stat, byte[][] keys, Map<byte[],byte[]> fields, long expiry) {
			for (int i = 0; i < entryCount; i++) {
				if (keys[i] == null) {
					String keyPrefix = "key" + System.currentTimeMillis();
					keys[i] = (keyPrefix + i).getBytes();
				}
				long t0 = System.currentTimeMillis();
				jedis.hmset(keys[i], fields);
				long t1 = System.currentTimeMillis();
				
				stat.reportCommand(t0, t1);
			
				// reset the expiry time for the key
				jedis.expire(keys[i], expiryInSec);
				
			}
		}

		@Override
		public void get(Jedis jedis, Stat stat, byte[][] keys, byte[][] fields) {
			for (int i = 0; i < entryCount; i++) {
				long t0 = System.currentTimeMillis();
				assert jedis.hmget(keys[i], fields) != null;
				long t1 = System.currentTimeMillis();

				stat.reportCommand(t0, t1);
			}
		}
		
	}
	
	class Stat {
		long totalElapsed = 0;
		long maxElapsed = 0;
		long greaterThanThreshold = 0;
		int totalCommands = 0;
		
		public void reportCommand(long t0, long t1) {
			long elapsed = t1 - t0;
			totalElapsed += elapsed;
			if (elapsed > maxElapsed) {
				maxElapsed = elapsed;
			}
			if (elapsed > thresholdMsec) {
				greaterThanThreshold++;
			}
			
			totalCommands++;
		}
	}
	

	public static void main(String[] args) {
        SpringApplication.run(JedisMultipleTestClient.class, args);

	}
	
	@Override
	public void run(String... args) throws Exception {
		
		start();
		healthCheck();
		
		try {
			test();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		terminate();
	}

	
}
