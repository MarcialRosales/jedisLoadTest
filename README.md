# jedisLoadTest

To execute the load test run the following command. The load test simulates concurrent user requests where a user request triggers various redis commands. From now
on we call Workflow to the set of commands invoked upon a user request:

java -jar target/spring-data-jedis-0.0.1-SNAPSHOT.jar  -Dtimes=10 -DworkflowTimes=100 -DconcurrentProducers=150 -DpoolSize=150 -DentryCount=1 -DfieldCount=10

Workflow: A set of redis commands that are invoked upon a user´s request. They are executed within the context of a single Redis Connection.
The commands invoked by a workflow are hash commands: HSet/HMSet and Hget/HMGet. The workflow uses as many keys as indicated by the parameter entryCount.
And as many fields as 'fieldCount'.
The first time we invoke a workflow, it creates a unique set of keys. 
And the parameter workflowTimes indicates how many times we invoke a workflow. When we reach the total 'workflowTimes', the workflow deletes the keys simulating the 
user has terminated or closed its session.

The parameter 'times' allows us to initiate many workflows, one after the other.
The parameter 'concurrentProducers' allows us to have parallel workflows running at the same time.

Number of unique keys generated as the result of invoking the above command: times * entryCount * concurrentProducers

Additional parameters:<br>
parameterName:defaultValue<br>

redis:localhost       location of redis server  <br>
poolSize:10           number of connections allowed in the pool<br>
concurrentProducers:1<br>
times:1<br>
abortAfterNMin:5      abort the test if it does not finish in less than 5 minutes<br>
workflowTimes:10     <br>
entryCount:2          number of keys<br>
fieldCount:10         number of fields per key<br>
dataLength:100        field´s value lenght in bytes<br>
expiryInSec:120       expiry time in seconds for the keys<br>
cmdStrategy:single    single uses hget/hset , multiple uses hmget/hmset<br> 
thresholdMsec:100     track how many commands executed over 100msec.<br>


