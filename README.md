<b>jvmtop</b> is a lightweight console application to monitor all accessible, running JVMs on a machine.<br>
In a top-like manner, it displays JVM internal metrics (e.g. memory information) of running Java processes.<br>
<br>
Jvmtop also includes a CPU console profiler.<br>
<br>
It's tested with different releases of Oracle JDK, IBM JDK and OpenJDK on Linux, Solaris, FreeBSD and Windows hosts.<br>
Jvmtop requires a JDK - a JRE will not suffice.<br>
<br>
Jvmtop is open-source. Checkout the <a href='https://github.com/MOschIT/jvmtop'>source code</a>. Patches are very welcome!<br>
<br>
Also have a look at the <a href='https://github.com/MOschIT/jvmtop/blob/master/doc/Documentation.md'>documentation</a> or at a <a href='https://github.com/MOschIT/jvmtop/blob/master/doc/ExampleOutput.md'>captured live-example</a>.<br>

<hr />

<h3>About this fork</h3>
This project is a fork of <a href='https://github.com/patric-r/jvmtop'>jvmtop</a> by Patric Rufflar, starting at version 0.8.0.<br>
The original code is licensed under the <a href='https://www.gnu.org/licenses/old-licenses/gpl-2.0.html'>GNU General Public License v2.0</a>.<br>
This fork is also licensed under the GPL v2.0.

<hr />

<h3>Overview</h3>

<pre>
  JvmTop 0.9.0   amd64,  8 cpus, Linux 5.15.0, load avg 0.42
  https://github.com/MOschIT/jvmtop

   PID MAIN-CLASS        HPCUR   HPMAX   NHCUR   NHMAX     CPU      GC VM     USERNAME   #T DL
 12345 app.MainApp        256m  4096m    128m   512m   2.34%   0.12% 21.0.2 user       45
 23456 tomcat.Bootstrap   512m  8192m    256m   768m   5.67%   1.23% 17.0.4 web        89
</pre>

<h4>Column descriptions</h4>

<table>
<tr><th>Field</th><th>Description</th></tr>
<tr><td><code>PID</code></td><td>Process ID of the JVM</td></tr>
<tr><td><code>MAIN-CLASS</code></td><td>Main class or JAR file the JVM was started with</td></tr>
<tr><td><code>HPCUR</code></td><td>Currently used heap memory</td></tr>
<tr><td><code>HPMAX</code></td><td>Maximum heap memory</td></tr>
<tr><td><code>NHCUR</code></td><td>Currently used non-heap memory</td></tr>
<tr><td><code>NHMAX</code></td><td>Maximum non-heap memory</td></tr>
<tr><td><code>CPU</code></td><td>CPU usage percentage (since last refresh)</td></tr>
<tr><td><code>GC</code></td><td>Percentage of time spent in garbage collection</td></tr>
<tr><td><code>VM</code></td><td>JVM vendor and version</td></tr>
<tr><td><code>USERNAME</code></td><td>Operating system user running the JVM</td></tr>
<tr><td><code>#T</code></td><td>Current number of live threads</td></tr>
<tr><td><code>DL</code></td><td>Deadlock indicator — <code>!D</code> if deadlocked threads are detected</td></tr>
</table>

<hr />

<h3>Installation</h3>
Click on the <a href="https://github.com/MOschIT/jvmtop/releases">releases tab</a>, download the
most recent tar.gz archive. Extract it, ensure that the <code>JAVA_HOME</code> environment variable points to a valid JDK and run <code>./jvmtop.sh</code>.<br><br>
Further information can be found in the <a href="https://github.com/MOschIT/jvmtop/blob/master/INSTALL">INSTALL file</a>

<hr />

<h3>Building from source</h3>
Requires JDK 21+ and Maven.

<pre><code>./build.sh</code></pre>

This produces <code>target/jvmtop-0.9.0-SNAPSHOT.jar</code> and copies dependencies into <code>target/lib/</code>.

<h4>Running on JDK 9+</h4>
Jvmtop uses internal JDK APIs (<code>sun.jvmstat.monitor</code>, <code>jdk.internal.agent</code>, etc.)
that are encapsulated starting with JDK 9. You must pass <code>--add-opens</code> flags at runtime:

<pre><code>java --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
     --add-opens=jdk.management.agent/jdk.internal.agent=ALL-UNNAMED \
     --add-opens=java.rmi/sun.rmi.server=ALL-UNNAMED \
     --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED \
     -jar target/jvmtop-0.9.0-SNAPSHOT.jar</code></pre>

<hr />

<h3>VM detail mode</h3>
In <a href='https://github.com/MOschIT/jvmtop/blob/master/doc/ExampleOutput.md'>VM detail mode</a> it shows you the top CPU-consuming threads, beside detailed metrics:<br>
<br>

<pre>
  JvmTop 0.9.0   amd64,  4 cpus, Linux 5.15.0

  PID 3539: org.apache.catalina.startup.Bootstrap
  ARGS: start
  VMARGS: -Djava.util.logging.config.file=/home/webserver/apache-tomcat[...]
  VM: Oracle OpenJDK 64-Bit Server VM 17.0.4
  UP: 120:15m #THR: 106  #THRPEAK: 143  #THRCREATED: 128020 USER: webserver
  CPU:  4.55% GC:  3.25% HEAP: 137m / 227m NONHEAP:  75m / 304m
   TID   NAME                                    STATE    CPU  TOTALCPU BLOCKEDBY
      25 http-8080-Processor13                RUNNABLE  4.55%     1.60%
  128022 RMI TCP Connection(18)-10.101.       RUNNABLE  1.82%     0.02%
   36578 http-8080-Processor164               RUNNABLE  0.91%     2.35%
</pre>

<hr />

<h3>Command-line options</h3>

<pre><code>jvmtop --help</code></pre>

Shows all available options including <code>--delay</code>, <code>--once</code>, <code>--pid</code>, <code>--profile</code>, and more.

<hr />

<a href='https://github.com/MOschIT/jvmtop/issues'>Pull requests / bug reports</a> are always welcome.<br>
