```
## Akka based simulation of Chord


Course Project - CS 441    
Team : Ashesh Singh, Ajith Nair, Karan Raghani

The aim of the project is to implement a cloud simulator that uses the **Chord protocol** 
(https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf).

We use Akka, a toolkit for building highly concurrent, distributed, and resilient message-driven applications to implement Chord.
The projects simulates assignment of task from multiple Front Ends (users) and the Worker Nodes using Chord. 
The implemntation is reselient to network failures, provides necessary logging to see work routing/assignment and 
messages passed between the nodes.
```

Sample showing node lookup and REST invocation:
![Basic Setup Run](doc/sample-run.gif)

Sample showing Docker run:  
![Basic Setup Run](doc/sample-run-docker.gif)


```

-----
INDEX
-----
1. About the Chord Protocol
2. Some Important Files
3. Application Design
4. Setup Instructions (Docker)
5. Usage Details
    - Configuration Details
    - Sample Exection Results
6. Additional logs
7. Visualization and Demo


---------------------------
1. About the Chord Protocol
---------------------------

As mentioned in https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf, 
Chord is a protocol with  with communication cost and the state maintained by each node scaling logarithmically with the number 
of Chord nodes. Chord is a distributed lookup protocol that addresses the problem of efficeient lookup in a 
peer-to-pee application an s based on **consistent hashing**. Chord provides support for just 
one operation i.e. given a key, it maps the key on to a node. Data location can be easily implemented on
top of Chord by associating a key with each data item , and storing the key/data pair at the node to which the key maps. 
Chord adapts efficiently as nodes join and leave the system.


----------------------
2 Some Important Files
----------------------

These are some of the important project files

    ```
    src/main/scala/com/ashessin/cs441/project/
        Main.scala                       the main project class file
        workers/FrontEnd.scala                actor class for making work requests
        workers/Worker.scala                actor class for computing work requests using chord protocol
        workers/Master.scala                utility actor class to for printing results and debugging
        chord/Finger.scala                for creating, computing and updating finger tables
    ```

Listed below are different dependencies required for this porjects:

* [Scalatest.FunSuite] - Unit testing framework for the Scala
* [slf4j] - Simple Logging Facade for Java
* [Typesafe] - to manage configuration files
* [Akka] - Actor-based message-driven runtime, Part of Lightbend Platform


---------------------
3. Application Design
---------------------

The Chord protocol can be implemented in an iterative or recursive style (https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf). 
In the iterative style, a node resolving a lookup initiates all communication: it asks a series of nodes for 
information from their finger tables, each time moving closer on the Chord ring to the desired successor. 
In the recursive style, each intermediate node forwards a request to the next node until it reaches the successor. 
This projects take iterative approach.

The main relavent actor roles in the our Akka Cluster (https://doc.akka.io/docs/akka/current/cluster-usage.html) system are:
- Front End Nodes (`front-end-$n`): simulates submission of workloads to the chord ring and consumes results
- Worker Nodes (`worker-$n`): represents nodes in a chord ring which are responsible for serving the incoming requests.

Internally, we also make use of a `master` singleton which is responsible for keeping track of work requests in the chord ring. 
This is only so that we can log results and get an overview of the simulation. 
It makes no lookup decisions on its own and all lookup is done by the worker nodes.

For the cloud simulation we perform a computation at the data/computation node that will be initialized by the
client node and the final message will be correctly redirect to it. This can be considered an analogy to the data fetch 
and put requests. The request to perform a calculation is forwarded to an ideal node in the ring which then performs 
consistent hashing on the file name and direct it to one of the live computation nodes(storage units) which are present 
in the system (based on the entries in its finger table). If the hash value for the file is consistent with the current 
node then the computation is performed on the same node and front end is updated. Else a lookup is performed in the 
fingertable of the node to find the right successor for the work assignment and it is passsed along in the ring. 
This process continues untill the file is stored. A simillar process is followed when the result needs to be redirected 
to the client request. Once the file requested is located it is returned to the master node which in turns returns it
to the user. The system is initialized using the number of users, storage nodes and read/write ratio present in the 
configuration file of the application. Once the system is initialized, a storage node can leave and join the system.

We have used Akka Management module to implement REST functionality.
Nodes can also be dynamically removed via DELETE requests to `ClusterSystem@127.0.0.1:8558/cluster/members/{address}` 
    ```
    {
        "message": "Leaving akka.tcp://ClusterSystem@127.0.0.1:2551"
    }
    ```
Further one can make GET requests to `ClusterSystem@127.0.0.1:8558/cluster/members` to get the overview of the ring.
    ```
    {
        "leader": "akka.tcp://ClusterSystem@127.0.0.1:2551",
        "members": [
            {
                "node": "akka.tcp://ClusterSystem@127.0.0.1:2551",
                "nodeUid": "-972370068",
                "roles": [
                    "back-end",
                    "dc-default"
                ],
                "status": "Up"
            },
            {
                "node": "akka.tcp://ClusterSystem@127.0.0.1:3000",
                "nodeUid": "-1884241496",
                "roles": [
                    "front-end",
                    "dc-default"
                ],
                "status": "Up"
            },
            {
                "node": "akka.tcp://ClusterSystem@127.0.0.1:3001",
                "nodeUid": "619205643",
                "roles": [
                    "front-end",
                    "dc-default"
                ],
                "status": "Up"
            },
            {
                "node": "akka.tcp://ClusterSystem@127.0.0.1:5001",
                "nodeUid": "981246470",
                "roles": [
                    "worker",
                    "dc-default"
                ],
                "status": "Up"
            }
            .
            .
            .
            .
        ],
        "oldest": "akka.tcp://ClusterSystem@127.0.0.1:2551",
        "oldestPerRole": {
            "back-end": "akka.tcp://ClusterSystem@127.0.0.1:2551",
            "dc-default": "akka.tcp://ClusterSystem@127.0.0.1:2551",
            "front-end": "akka.tcp://ClusterSystem@127.0.0.1:3000",
            "worker": "akka.tcp://ClusterSystem@127.0.0.1:5001"
        },
        "selfNode": "akka.tcp://ClusterSystem@127.0.0.1:2551",
        "unreachable": []
    }
    ```


------------------------------
4. Setup Instructions (Docker)
------------------------------
We followed up the following procedure to deploy the application on Docker

+ Setting Up Docker:
    
    1. You can link your bitbucket repository with the dockerhhub account to push docker image evert time a
    build is generated using Bitbucket's pipeling.
    
    2. Create and Push Repository on Docker:
        -Add a Dockerfile to the root directory of the repository. Please note that Dockerfile does not have any extension
        -In the Dockerfile, setup details corresponding to the container environment and dependencies. 
        Please note that no line in docker file should preceed the From statement. 
        An excerpt of the dockerfile would like like:

        ```
        `FROM openjdk:8
        #Install OpenJDK-8
        RUN apt-get update && \
            apt-get install -y openjdk-8-jdk && \
            apt-get install -y ant && \
            apt-get clean;
        #Fix certificate issues
        RUN apt-get update && \
            apt-get install ca-certificates-java && \
            apt-get clean && \
            update-ca-certificates -f;
        ```

+ After adding the docker file, you need to add bitbucket-pipeline in which you mention build scripts 
which are to be executed everytime a repository is pushed on bitbucket

+ In the bitbucket-pipeline file, mention the docker credentials and details of the the repository on dockerhub

+ On completion of this, the container image is generated and pushed to the docker hub everytime you make 
commit and push to the repository on bitbucket.

+ You need to setup Docker on the local machine to get container image and run the application. 
Follow the instructions at [Installation Guide](https://docs.docker.com/install/linux/docker-ce/ubuntu/).

+ After installing and setting up docker, you need to create a docker group and add current user to the group. 
To do so execute the following command;
    
    ```
    $ sudo groupadd docker
    $ sudo usermod -aG docker $USER
    ```

+ Now login to the docker hub using the below command:
    
    ```
    $ sudo docker login
    ```

+ It prompts you to enter the username and password. Enter the details as prompted.

+ On successfully login, we will first have to pull the docker image from docker hub. To do so execute the below command,
    
    ```
    $ sudo docker pull <repo-name>
    ```

    Here name of the repo is ajithnair20/chord-algorithm.

+ This command fetches the conatiner image from the docker. On completion of this, run the container
image using the below command:
    
    ```
    $ sudo docker run <repo-name>
    ```

    On executing the command, the dependencies of the application are fetch and the application commences execution.

----------------
5. Usage Details
----------------

The project in its default configuration, creates 2 `front-end` nodes and 8 `worker` node chord ring. 
The front end nodes submits multiple work requests (to find the square of a number) to the `worker` nodes 
and consume results. Each work request has a unique UUID eg. `fe8eb4bd-100d-479a-98a9-4693a250d06b` or movie name from
`src/main/resources/movies.txt` which is hashed to a value ranging from 0-8 by the 1st worker node that receives it. 
It is then passed along in the ring using the implemntation details of Chord, until it reaches the target worker node.

5.1 Configuration Details
-------------------------

The available configuration options are:
    - `numberOfPositions`         number of positions in the chord ring
    - `numberOfUsers`             number of users making work requests (`front-end`)
    - `requestTimeout`            the time in seconds under which the user expects results
    - `retryRequest`              the duration after which the request is retried
    - The rate at which nodes the ring process the work load
        `minProcessingTime` and `maxProcessingTime`
    - The rate at whcih the users submit work reqests
        `minRequest` and `maxRequest`  

To execute the program:

1. Clone the repo on your system

2. Compile the project using following commands
    
    ```
    sbt clean compile
    ```

3. Now to execute the program,
    
    ```
    sbt run
    ```

4. To run the test cases please input the following command on terminal after cloning the repo
    
    ```
    sbt clean compile test
    ```
    
    The test cases runs as follows:
    
    ```
    [info] HelperFunctionTests:
    [info] - Finger Table Size Check
    [info] - Finger Table generation
    [info] - Finding the correct successor
    [info] - Consitent Hashing using MD5
    [info] ConfigurationTest:
    [info] - Configuration File is loaded correctly
    [info] - Movies DataBase Check
    [info] Run completed in 2 seconds, 72 milliseconds.
    [info] Total number of tests run: 6
    [info] Suites: completed 2, aborted 0
    [info] Tests: succeeded 6, failed 0, canceled 0, ignored 0, pending 0
    [info] All tests passed.
    [success] Total time: 4 s, completed Dec 10, 2019 8:22:31 PM
    ```

5.2 Sample Exection Results
---------------------------

+ Once the porgram starts execution, following logs indicates the successful initialization of the Akka cluster system
    ```
    20:26:51.999 | .default-dispatcher-4 | INFO  | a.event.slf4j.Slf4jLogger | Slf4jLogger started
    20:26:52.001 | .default-dispatcher-3 | INFO  |      akka.remote.Remoting | Starting remoting
    20:26:52.018 | .default-dispatcher-4 | INFO  |      akka.remote.Remoting | Remoting started; listening on addresses :[akka.tcp://ClusterSystem@127.0.0.1:3001]
    20:26:52.018 | .default-dispatcher-4 | INFO  |      akka.remote.Remoting | Remoting now listens on addresses: [akka.tcp://ClusterSystem@127.0.0.1:3001]
    20:26:52.019 | .default-dispatcher-4 | INFO  | ter(akka://ClusterSystem) | Cluster Node [akka.tcp://ClusterSystem@127.0.0.1:3001] - Starting up, Akka version [2.5.26] ...
    20:26:52.024 | .default-dispatcher-2 | INFO  | ter(akka://ClusterSystem) | Cluster Node [akka.tcp://ClusterSystem@127.0.0.1:3001] - Registered cluster JMX MBean [akka:type=Cluster,port=3001]
    20:26:52.024 | .default-dispatcher-2 | INFO  | ter(akka://ClusterSystem) | Cluster Node [akka.tcp://ClusterSystem@127.0.0.1:3001] - Started up successfully
    ```

+ Based on the input configuration, a number of `front-end` and `worker` nodes are initallized. Additionally, the finger tables for each worker node is calculated and stored within the actors.
    ```
    [worker-1], akka://ClusterSystem/user/worker-1, finger: TreeMap(1 -> (Range 2 until 3,2), 2 -> (Range 3 until 5,3), 3 -> (Range 1 until 5,5))
    [worker-4], akka://ClusterSystem/user/worker-4, finger: TreeMap(1 -> (Range 5 until 6,5), 2 -> (Range 0 until 6,6), 3 -> (Range 0 until 4,0))
    [worker-5], akka://ClusterSystem/user/worker-5, finger: TreeMap(1 -> (Range 6 until 7,6), 2 -> (Range 1 until 7,7), 3 -> (Range 1 until 5,1))
    [worker-6], akka://ClusterSystem/user/worker-6, finger: TreeMap(1 -> (Range 0 until 7,7), 2 -> (Range 0 until 2,0), 3 -> (Range 2 until 6,2))
    [worker-7], akka://ClusterSystem/user/worker-7, finger: TreeMap(1 -> (Range 0 until 1,0), 2 -> (Range 1 until 3,1), 3 -> (Range 1 until 3,3))
    [worker-2], akka://ClusterSystem/user/worker-2, finger: TreeMap(1 -> (Range 3 until 4,3), 2 -> (Range 4 until 6,4), 3 -> (Range 2 until 6,6))
    [worker-3], akka://ClusterSystem/user/worker-3, finger: TreeMap(1 -> (Range 4 until 5,4), 2 -> (Range 5 until 7,5), 3 -> (Range 3 until 7,7))
    [worker-0], akka://ClusterSystem/user/worker-0, finger: TreeMap(1 -> (Range 1 until 2,1), 2 -> (Range 2 until 4,2), 3 -> (Range 0 until 4,4))

    ```

+ In our design the requests are made by the clients (workers.FrontEnd) that creates a workID. Sample of such request:
    ```
    [front-end-1] Produced workId: b747af99-8040-437d-8a21-86bcb6cae0b2 with workCounter: 1
    ```
    Alternatively if `altWork` commandline option is passes, the work id is the movie name
    ```
    [front-end-1] Produced workId: Pulp Fiction with workCounter: 1
    ``` 

+ The requested client then gets the acknowledgement for the workID that it was accepted
    ```
    [front-end-1] Got ack for workId: b747af99-8040-437d-8a21-86bcb6cae0b2
    ```

+ When the request reaches the node whose hashID is not consistent with the job, a lookup is done using its finger table
and the request then is forwarded to the entry in the finger table. In this case from worker node 4 to worker node 5.
    ```
    [worker-4] Forward job: 58 with workId: 6c4bb13a-a566-4a70-81b8-942520e39a6d, workIdHash: 5 to successor: 5
    ```

+ This messages indicate the arrival of the request at the correct node.
    ```
    [worker-5] Got job: 58 with workId: 6c4bb13a-a566-4a70-81b8-942520e39a6d, workIdHash: 5
    ```

+ Once the request is recieved it is processed by the node and the result is calculated.
    ```
    [worker-5] Completed work: (6c4bb13a-a566-4a70-81b8-942520e39a6d,58,Actor[akka.tcp://ClusterSystem@127.0.0.1:3000/user/front-end-1#-958904850]) with result: 58 * 58 = 3364
    ```

+ Finally the result is directed back to the correct client node.
    ```
    [front-end-1] Consumed result: 58 * 58 = 3364 for job: 58, workId: 6c4bb13a-a566-4a70-81b8-942520e39a6d, workIdHash: 5 from [worker-worker-5]
    ```
    
------------------
6. Additional Logs
------------------

Providing longer chunk of logs for seeing extended results for multiple work assignments.

23:04:22.218 | default-dispatcher-20 | INFO  | .project.workers.FrontEnd | [front-end-1] Got ack for workId: aa7149ce-5b05-4dfd-941c-4b13299d636b
23:04:22.243 | .default-dispatcher-4 | INFO  | .project.workers.FrontEnd | [front-end-2] Got ack for workId: ac1d2d34-53f7-4a04-bb70-6c69caf28be5
23:04:22.338 | .default-dispatcher-2 | INFO  | 41.project.workers.Worker | [worker-1] Forward job: 1 with workId: aa7149ce-5b05-4dfd-941c-4b13299d636b, workIdHash: 5 to successor: 5
23:04:22.367 | default-dispatcher-18 | INFO  | 41.project.workers.Worker | [worker-5] Got job: 1 with workId: aa7149ce-5b05-4dfd-941c-4b13299d636b, workIdHash: 5
23:04:22.384 | default-dispatcher-19 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 5 is ListBuffer(1, 5)
23:04:23.394 | default-dispatcher-18 | INFO  | 41.project.workers.Worker | [worker-5] Completed work: (aa7149ce-5b05-4dfd-941c-4b13299d636b,1,Actor[akka.tcp://ClusterSystem@127.0.0.1:3000/user/front-end-1#1380129533]) with result: 1 * 1 = 1
23:04:23.432 | default-dispatcher-16 | INFO  | .project.workers.FrontEnd | [front-end-1] Consumed result: 1 * 1 = 1 for job: 1, workId: aa7149ce-5b05-4dfd-941c-4b13299d636b, workIdHash: 5 from [worker-worker-5]

23:04:24.003 | default-dispatcher-19 | INFO  | ter(akka://ClusterSystem) | Cluster Node [akka.tcp://ClusterSystem@127.0.0.1:2551] - Leader is moving node [akka.tcp://ClusterSystem@127.0.0.1:3001] to [Up]
23:04:38.256 | .default-dispatcher-4 | INFO  | .project.workers.FrontEnd | [front-end-2] Produced workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf with workCounter: 2
23:04:38.265 | .default-dispatcher-4 | INFO  | .project.workers.FrontEnd | [front-end-2] Got ack for workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf
23:04:38.277 | default-dispatcher-21 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 2 with workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf, workIdHash: 1 to successor: 0
23:04:38.282 | .default-dispatcher-2 | INFO  | 41.project.workers.Worker | [worker-0] Forward job: 2 with workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf, workIdHash: 1 to successor: 1
23:04:38.287 | default-dispatcher-21 | INFO  | 41.project.workers.Worker | [worker-1] Got job: 2 with workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf, workIdHash: 1
23:04:38.291 | default-dispatcher-20 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 1 is ListBuffer(4, 0, 1)
23:04:39.303 | default-dispatcher-17 | INFO  | 41.project.workers.Worker | [worker-1] Completed work: (b49641c1-1e2d-4585-99f3-ffbd041e4caf,2,Actor[akka.tcp://ClusterSystem@127.0.0.1:3001/user/front-end-2#1554763353]) with result: 2 * 2 = 4
23:04:39.313 | .default-dispatcher-5 | INFO  | .project.workers.FrontEnd | [front-end-2] Consumed result: 2 * 2 = 4 for job: 2, workId: b49641c1-1e2d-4585-99f3-ffbd041e4caf, workIdHash: 1 from [worker-worker-1]

23:04:41.238 | default-dispatcher-17 | INFO  | .project.workers.FrontEnd | [front-end-1] Produced workId: 6cec9db3-ab4e-4757-b096-790c462f4c8a with workCounter: 2
23:04:41.252 | default-dispatcher-19 | INFO  | .project.workers.FrontEnd | [front-end-1] Got ack for workId: 6cec9db3-ab4e-4757-b096-790c462f4c8a
23:04:41.263 | .default-dispatcher-2 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 2 with workId: 6cec9db3-ab4e-4757-b096-790c462f4c8a, workIdHash: 0 to successor: 0
23:04:41.271 | default-dispatcher-20 | INFO  | 41.project.workers.Worker | [worker-0] Got job: 2 with workId: 6cec9db3-ab4e-4757-b096-790c462f4c8a, workIdHash: 0
23:04:41.274 | .default-dispatcher-3 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 0 is ListBuffer(4, 0)
23:04:42.294 | default-dispatcher-20 | INFO  | 41.project.workers.Worker | [worker-0] Completed work: (6cec9db3-ab4e-4757-b096-790c462f4c8a,2,Actor[akka.tcp://ClusterSystem@127.0.0.1:3000/user/front-end-1#1380129533]) with result: 2 * 2 = 4
23:04:42.304 | .default-dispatcher-2 | INFO  | .project.workers.FrontEnd | [front-end-1] Consumed result: 2 * 2 = 4 for job: 2, workId: 6cec9db3-ab4e-4757-b096-790c462f4c8a, workIdHash: 0 from [worker-worker-0]

23:04:56.286 | .default-dispatcher-4 | INFO  | .project.workers.FrontEnd | [front-end-2] Produced workId: bd41ea96-0e89-4984-bffc-0530dfa51909 with workCounter: 3
23:04:56.293 | default-dispatcher-19 | INFO  | .project.workers.FrontEnd | [front-end-2] Got ack for workId: bd41ea96-0e89-4984-bffc-0530dfa51909
23:04:56.301 | .default-dispatcher-2 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 3 with workId: bd41ea96-0e89-4984-bffc-0530dfa51909, workIdHash: 7 to successor: 6
23:04:56.306 | default-dispatcher-20 | INFO  | 41.project.workers.Worker | [worker-6] Forward job: 3 with workId: bd41ea96-0e89-4984-bffc-0530dfa51909, workIdHash: 7 to successor: 7
23:04:56.311 | .default-dispatcher-2 | INFO  | 41.project.workers.Worker | [worker-7] Got job: 3 with workId: bd41ea96-0e89-4984-bffc-0530dfa51909, workIdHash: 7
23:04:56.313 | default-dispatcher-15 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 7 is ListBuffer(4, 6, 7)
23:04:58.323 | .default-dispatcher-7 | INFO  | 41.project.workers.Worker | [worker-7] Completed work: (bd41ea96-0e89-4984-bffc-0530dfa51909,3,Actor[akka.tcp://ClusterSystem@127.0.0.1:3001/user/front-end-2#1554763353]) with result: 3 * 3 = 9
23:04:58.332 | .default-dispatcher-3 | INFO  | .project.workers.FrontEnd | [front-end-2] Consumed result: 3 * 3 = 9 for job: 3, workId: bd41ea96-0e89-4984-bffc-0530dfa51909, workIdHash: 7 from [worker-worker-7]

23:04:59.266 | .default-dispatcher-5 | INFO  | .project.workers.FrontEnd | [front-end-1] Produced workId: 444b5af1-8c73-4ff0-9ca6-2e9e74da51fe with workCounter: 3
23:04:59.275 | .default-dispatcher-2 | INFO  | .project.workers.FrontEnd | [front-end-1] Got ack for workId: 444b5af1-8c73-4ff0-9ca6-2e9e74da51fe
23:04:59.281 | .default-dispatcher-7 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 3 with workId: 444b5af1-8c73-4ff0-9ca6-2e9e74da51fe, workIdHash: 5 to successor: 5
23:04:59.286 | .default-dispatcher-7 | INFO  | 41.project.workers.Worker | [worker-5] Got job: 3 with workId: 444b5af1-8c73-4ff0-9ca6-2e9e74da51fe, workIdHash: 5
23:04:59.289 | default-dispatcher-15 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 5 is ListBuffer(4, 5)
23:05:00.302 | .default-dispatcher-7 | INFO  | 41.project.workers.Worker | [worker-5] Completed work: (444b5af1-8c73-4ff0-9ca6-2e9e74da51fe,3,Actor[akka.tcp://ClusterSystem@127.0.0.1:3000/user/front-end-1#1380129533]) with result: 3 * 3 = 9
23:05:00.311 | .default-dispatcher-5 | INFO  | .project.workers.FrontEnd | [front-end-1] Consumed result: 3 * 3 = 9 for job: 3, workId: 444b5af1-8c73-4ff0-9ca6-2e9e74da51fe, workIdHash: 5 from [worker-worker-5]

23:05:13.306 | default-dispatcher-17 | INFO  | .project.workers.FrontEnd | [front-end-2] Produced workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7 with workCounter: 4
23:05:13.313 | default-dispatcher-17 | INFO  | .project.workers.FrontEnd | [front-end-2] Got ack for workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7
23:05:13.322 | default-dispatcher-17 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 4 with workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7, workIdHash: 3 to successor: 5
23:05:13.326 | default-dispatcher-21 | INFO  | 41.project.workers.Worker | [worker-5] Forward job: 4 with workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7, workIdHash: 3 to successor: 1
23:05:13.330 | default-dispatcher-21 | INFO  | 41.project.workers.Worker | [worker-1] Forward job: 4 with workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7, workIdHash: 3 to successor: 3
23:05:13.334 | default-dispatcher-17 | INFO  | 41.project.workers.Worker | [worker-3] Got job: 4 with workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7, workIdHash: 3
23:05:13.336 | default-dispatcher-20 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 3 is ListBuffer(4, 5, 1, 3)
23:05:14.353 | .default-dispatcher-6 | INFO  | 41.project.workers.Worker | [worker-3] Completed work: (0914c3f8-9f3c-4c62-9fde-da57f69c66e7,4,Actor[akka.tcp://ClusterSystem@127.0.0.1:3001/user/front-end-2#1554763353]) with result: 4 * 4 = 16
23:05:14.361 | .default-dispatcher-3 | INFO  | .project.workers.FrontEnd | [front-end-2] Consumed result: 4 * 4 = 16 for job: 4, workId: 0914c3f8-9f3c-4c62-9fde-da57f69c66e7, workIdHash: 3 from [worker-worker-3]

23:05:15.286 | .default-dispatcher-2 | INFO  | .project.workers.FrontEnd | [front-end-1] Produced workId: dafb1db0-0369-4dc7-94ef-0217259e0f19 with workCounter: 4
23:05:15.294 | .default-dispatcher-2 | INFO  | .project.workers.FrontEnd | [front-end-1] Got ack for workId: dafb1db0-0369-4dc7-94ef-0217259e0f19
23:05:15.299 | default-dispatcher-18 | INFO  | 41.project.workers.Worker | [worker-4] Forward job: 4 with workId: dafb1db0-0369-4dc7-94ef-0217259e0f19, workIdHash: 3 to successor: 5
23:05:15.303 | default-dispatcher-18 | INFO  | 41.project.workers.Worker | [worker-5] Forward job: 4 with workId: dafb1db0-0369-4dc7-94ef-0217259e0f19, workIdHash: 3 to successor: 1
23:05:15.307 | default-dispatcher-20 | INFO  | 41.project.workers.Worker | [worker-1] Forward job: 4 with workId: dafb1db0-0369-4dc7-94ef-0217259e0f19, workIdHash: 3 to successor: 3
23:05:15.310 | default-dispatcher-20 | INFO  | 41.project.workers.Worker | [worker-3] Got job: 4 with workId: dafb1db0-0369-4dc7-94ef-0217259e0f19, workIdHash: 3
23:05:15.312 | default-dispatcher-17 | INFO  | 41.project.workers.Master | Forward chain for workIdHash: 3 is ListBuffer(4, 5, 1, 3)
23:05:16.324 | default-dispatcher-18 | INFO  | 41.project.workers.Worker | [worker-3] Completed work: (dafb1db0-0369-4dc7-94ef-0217259e0f19,4,Actor[akka.tcp://ClusterSystem@127.0.0.1:3000/user/front-end-1#1380129533]) with result: 4 * 4 = 16
23:05:16.330 | .default-dispatcher-4 | INFO  | .project.workers.FrontEnd | [front-end-1] Consumed result: 4 * 4 = 16 for job: 4, workId: dafb1db0-0369-4dc7-94ef-0217259e0f19, workIdHash: 3 from [worker-worker-3]


-------------------------
7. Visualization and Demo
-------------------------

https://asing80.people.uic.edu/cs441/project/

```