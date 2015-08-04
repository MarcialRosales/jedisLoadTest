# jedisLoadTest

To execute the load test run the following command. The load test simulates concurrent user requests where a user request triggers various redis commands. From now
on we call Workflow to the set of commands invoked upon a user request:

java -Dtimes=10 -DworkflowTimes=100 -DconcurrentProducers=150 -DpoolSize=150 -DentryCount=1 -DfieldCount=10 -jar target/spring-data-jedis-0.0.1-SNAPSHOT.jar 
  
   ... if redis server is running in the current machine and listening on localhost
   
java -redis=redisServer -Dtimes=10 -DworkflowTimes=100 -DconcurrentProducers=150 -DpoolSize=150 -DentryCount=1 -DfieldCount=10 -jar target/spring-data-jedis-0.0.1-SNAPSHOT.jar 
   
   ... if redis server is listening on the host 'redisServer'
   

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

<table>
<tr><th>parameter:defaultValue</th><th>description</th></tr>
<tr><td>redis:localhost </td><td>      location of redis server (if sentinelMaster is blank)  </td>
<tr><td>sentinelMaster:   </td><td> name of the sentinel master. Blank if we are not using Sentinel. </td>
<tr><td>sentinels:locahost:26379</td><td>comma-separated listed of sentinels. This setting is used if sentinelMaster is not blank. </td>    
<tr><td>poolSize:10       </td><td>    number of connections allowed in the pool </td>
<tr><td>concurrentProducers:1</td><td></td>
<tr><td>times:1</td><td></td>
<tr><td>abortAfterNMin:5  </td><td>    abort the test if it does not finish in less than 5 minutes</td>
<tr><td>workflowTimes:10     </td><td></td>
<tr><td>entryCount:2        </td><td>  number of keys</td>
<tr><td>fieldCount:10       </td><td>  number of fields per key</td>
<tr><td>dataLength:100      </td><td>  field´s value lenght in bytes</td>
<tr><td>expiryInSec:120     </td><td>  expiry time in seconds for the keys</td>
<tr><td>deleteKeys:true  </td><td>     delete the keys after running a full workflow (i.e. workflowTimes). if false, the keys are not removed and redis removes them when they expire.</td>
<tr><td>cmdStrategy:single  </td><td>  single uses hget/hset , multiple uses hmget/hmset</td>
<tr><td>thresholdMsec:100     </td><td>track how many commands executed over 100msec</td>
</table>

