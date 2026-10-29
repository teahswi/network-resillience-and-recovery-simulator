# NetRescue: Transactional Routing & Failure Recovery Simulator

NetRescue simulates a computer network whose routers and links fail. It finds alternative routes, identifies the single points of failure, and moves **transfers** across the network hop by hop. When a hop fails part-way through, the transfer **rolls back** with a stack and retries on another route.

It is written in **pure Java with zero libraries**. Every data structure (lists, stack, queue, heaps, hash maps, trie, union-find, LRU cache) and every algorithm is implemented from scratch. The UI is a web page served by Java's built-in HTTP server and opened in your browser.

---

## 1. Run it

You need a **JDK 17 or newer** (it was built and tested on JDK 21). Nothing else is required: no Maven, no internet.

### Linux / macOS

```sh
./build.sh
java -jar netrescue.jar
```

### Windows

```bat
build.bat
java -jar netrescue.jar
```

Your browser then opens at `http://localhost:8080/`. If the browser doesn't open, paste that address in yourself. Press `Ctrl+C` in the terminal to stop.

### What the commands do

**`./build.sh`** is one script that runs four steps:

| Step | Command inside the script | What it does |
|---|---|---|
| 1 | `javac -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')` | Compiles every source file into `.class` bytecode |
| 2 | `cp -r src/main/resources/. build/classes/` | Copies the web page and sample networks next to the classes, so they end up inside the jar |
| 3 | `javac ... src/test/java/...` then `java -cp build/classes:build/test-classes netrescue.Tests` | Compiles and runs the test suite. The build stops if any check fails |
| 4 | `jar --create --file netrescue.jar --main-class netrescue.server.App -C build/classes .` | Packs everything into one runnable jar |

The parts of these commands:

- **`javac`** is the Java compiler.
  - `-encoding UTF-8` reads the sources as UTF-8.
  - `-d build/classes` writes the output into that folder, creating one sub-folder per package.
  - `$( … )` is *command substitution*: the shell runs the inner command and pastes its output (the list of files) here.
  - `find src/main/java -name '*.java'` lists every file under `src/main/java` whose name ends in `.java`. The quotes stop the shell from expanding `*` itself.
- **`cp -r src/main/resources/. build/classes/`**: `cp` copies, and `-r` means recursively, into sub-folders. The trailing `/.` means "the *contents* of resources", not the folder itself.
- **`java -cp A:B netrescue.Tests`**: `-cp` (class path) lists where to look for classes. Folders are separated by `:` on Linux/macOS and `;` on Windows. `netrescue.Tests` is the class whose `main` method runs.
- **`jar --create --file netrescue.jar --main-class netrescue.server.App -C build/classes .`**:
  - `--create` makes a new archive.
  - `--file` names it.
  - `--main-class` writes the entry point into the jar's manifest, which is what makes `java -jar` work.
  - `-C build/classes .` means "change into `build/classes` and add everything (`.`) from there".

**`java -jar netrescue.jar [--port N] [--no-browser]`** starts the program.

- `-jar` runs the main class named in the jar's manifest.
- `--port 9000` picks another port. If 8080 is busy, the program tries 8081, 8082, … by itself.
- `--no-browser` skips opening the browser.

### Opening it in an IDE

In IntelliJ IDEA, open the folder and mark `src/main/java` as *Sources Root*, `src/main/resources` as *Resources Root*, and `src/test/java` as *Test Sources Root*. Then run `netrescue.server.App` (the app) or `netrescue.Tests` (the tests).

---

## 2. What you can do (the six tabs)

| Tab | What it shows |
|---|---|
| **Network** | Build or edit a network (add, move, link, delete), fail or repair routers and links by clicking, and **undo/redo** every edit with two stacks. Search routers by name with **trie** autocomplete. Find routes by five methods; with *auto-reroute* on, failing a link on the shown route immediately computes a new one and draws the old route dashed. |
| **Algorithms** | Runs BFS, DFS, Dijkstra, Bellman-Ford, Tarjan, Kruskal (merge sort or quicksort) or Prim, and records every step. Play, pause or step through it while a side panel shows the algorithm's **queue, stack, heap or union-find sets** at that moment. |
| **Analysis** | Critical routers (articulation points) and critical links (bridges), each ranked by how many routers its failure cuts off. Also resilience scores, the minimum spanning tree (Kruskal = Prim check), the network diameter and center (Floyd-Warshall), and the distance matrix. |
| **Simulation** | Event-driven simulation of transfers under random or hand-placed failures: hop-by-hop reservation, **stack rollback**, rerouting, exponential back-off, FIFO or priority admission, and an LRU route cache. Link load is drawn live. Other panels show the event heap, the queue and the cache contents, plus a **timeline replay** of the whole run and a *Compare strategies* experiment. |
| **Benchmarks** | Measured timings that check theory against practice: ring-buffer queue vs shifting array, merge sort vs quicksort, chaining vs linear probing, BFS vs Dijkstra vs Bellman-Ford, heap vs array Dijkstra, and Floyd-Warshall vs V × Dijkstra. |
| **DS Lab** | Operate each data structure directly (push, pop, enqueue, put, union, …) and watch its insides: ring buffer slots, heap tree, hash buckets and chains, trie nodes, union-find forest, LRU order. |

The bundled networks are:

- **Classroom demo**: 10 routers, with obvious critical points.
- **India backbone**: 20 real cities with illustrative links.
- **Abilene/Internet2**: in GML format.
- **Campus mesh**: highly redundant, with no critical points.

You can also generate random networks (Waxman model) or open your own `.txt` or Internet Topology Zoo `.gml` files (download them from topology-zoo.org).

---

## 3. Data structures: what each one does here

All of these are in `netrescue.ds`. The core code never uses `java.util` collections; the tests use them only as reference answers.

| Structure | Implementation | Used for | Key costs |
|---|---|---|---|
| `DynamicArray` | `Object[]` that doubles when full | base of stack, heap and lists of edges | get $O(1)$, append $O(1)$ amortised, insert/remove in middle $O(n)$ |
| `SinglyLinkedList` | head + tail pointers | adjacency lists (each router's links) | add at either end $O(1)$ |
| `DoublyLinkedList` | sentinel head/tail nodes | LRU recency order; simulator event log | add/remove at ends and unlink a node $O(1)$ |
| `Stack` | on `DynamicArray` | **transfer rollback**, undo/redo, iterative DFS, GML bracket matching | push/pop $O(1)$ |
| `Queue` | circular array (ring buffer) | BFS, transfer admission queue | enqueue/dequeue $O(1)$ |
| `Deque` | circular array | 0-1 BFS ("safest route"), sliding window of setup times | both ends $O(1)$ |
| `MinHeap` | array, children of $i$ at $2i+1$, $2i+2$ | simulator event clock, Prim, priority admission | insert/extract $O(\log n)$, heapify $O(n)$ |
| `IndexedMinHeap` | heap + position array | Dijkstra (`decreaseKey`) | all ops $O(\log n)$ |
| `HashMap` | separate chaining, resize at load 0.75 | name → router, transfer id → transfer, LRU index | $O(1)$ expected |
| `LinearProbingHashMap` | open addressing + tombstones | benchmark comparison | $O(1)$ expected at load ≤ 0.5 |
| `HashSet` | wraps `HashMap` | visited sets, bridge set | $O(1)$ expected |
| `UnionFind` | union by rank + path compression | "still connected?", pieces after failures, Kruskal | $O(\alpha(n))$ per operation |
| `Trie` | children in our `HashMap` | router-name autocomplete | $O(L)$ per word |
| `LRUCache` | `HashMap` + `DoublyLinkedList` | route cache in the simulator | get/put $O(1)$ |
| `Sorting` | merge sort, quicksort (median of three) | Kruskal, impact ranking | $O(n \log n)$ |

## 4. Algorithms

All of these are in `netrescue.algo`.

| Algorithm | File | Purpose | Time |
|---|---|---|---|
| BFS | `BFS.java` | fewest hops, reachability | $O(V+E)$ |
| DFS (iterative and recursive) | `DFS.java` | traversal, connected components | $O(V+E)$ |
| Dijkstra (heap and $O(V^2)$ array versions) | `Dijkstra.java` | cheapest route | $O((V+E)\log V)$ / $O(V^2)$ |
| Bellman-Ford (with early stop and negative-cycle check) | `BellmanFord.java` | cheapest route, comparison | $O(VE)$ |
| Floyd-Warshall | `FloydWarshall.java` | all pairs, diameter, center | $O(V^3)$ |
| Tarjan | `Tarjan.java` | articulation points and bridges | $O(V+E)$ |
| Kruskal / Prim | `MST.java` | minimum spanning tree/forest | $O(E\log E)$ |
| 0-1 BFS | `Routing.java` | route crossing the fewest critical links | $O(V+E)$ |
| Edge-penalty alternatives | `Routing.java` | $k$ different routes using only Dijkstra | $O(k(V+E)\log V)$ |
| Impact analysis | `Resilience.java` | routers cut off by each critical failure | $O(A \cdot E\,\alpha(V))$ |
| Waxman generator, Fruchterman-Reingold layout | `graph/Generators.java` | random networks, auto layout | $O(V^2)$ |

---

## 5. How a transfer survives a failure (the core idea)

```
arrive  ->  admission QUEUE (or priority HEAP)  ->  route (LRU cache, else Dijkstra/BFS)
        ->  walk the route hop by hop:
              link up and has room?  yes: reserve it, PUSH onto the undo stack
                                     no:  ROLLBACK: POP every reserved link (last in, first out)
                                          and release it, then reroute and try again
        ->  every hop reserved  =>  COMMIT (data flows for a while, then all links are released)
```

- **Before routing**, union-find checks whether the destination is reachable at all. If it isn't, the transfer waits with **exponential back-off** (1, 2, 4, 8 … time units) instead of wasting a search.
- **When a link fails**, every cached route through it is invalidated. Every transfer *holding* that link is rolled back and rerouted.
- **Invariants checked by the tests:**
  - At every event, $0 \le \text{used} \le \text{capacity}$ on every link.
  - At the end, every transfer is DONE or FAILED, every undo stack is empty, and all capacity is released.

The simulator is **discrete-event**. A min-heap holds future events ordered by time; the loop takes the earliest event, jumps the clock to it, and handles it. Handlers schedule more events.

---

## 6. Project layout

```
netrescue/
├── build.sh / build.bat        build + test + package (JDK only)
├── src/main/java/netrescue/
│   ├── ds/       the 15 data structures
│   ├── graph/    Graph, Node, Edge, EditHistory (undo/redo), Generators
│   ├── algo/     BFS, DFS, Dijkstra, Bellman-Ford, Floyd-Warshall, Tarjan, MST, Routing, Resilience, Trace
│   ├── sim/      Simulator, Event, Transfer, SimView (JSON), Experiments
│   ├── io/       Json writer, TextFormat, GmlParser, Samples
│   ├── bench/    Benchmarks
│   └── server/   App (main), WebServer (JDK HttpServer), Api, Lab
├── src/main/resources/
│   ├── web/      index.html, style.css, app.js + one script per tab (drawing only)
│   └── samples/  classroom.txt, india.txt, mesh.txt, abilene.gml
└── src/test/java/netrescue/Tests.java   ~280,000 checks, no JUnit needed
```

The browser does **no** algorithmic work. It sends a request such as `/api/algo?name=dijkstra&src=0&dst=9`. Java runs the algorithm, records each step (`algo/Trace.java`), and returns JSON. The page then only draws it.

### Text network format

```
title My network
node Delhi 320 170
node "New York" 820 140        # quotes for names with spaces
link Delhi "New York" 12.5 40  # cost capacity
```

---

## 7. Testing

`build.sh` runs `netrescue.Tests` automatically:

- Every data structure is driven with thousands of random operations side by side with the matching `java.util` class (`ArrayList`, `ArrayDeque`, `PriorityQueue`, `HashMap`, `TreeSet`, `LinkedHashMap`, …).
- Dijkstra (both versions), Bellman-Ford and Floyd-Warshall must agree on every pair of routers in 60 random graphs, some with failed links.
- BFS hop counts are checked against brute force, and every returned path is checked to be a real walk with the reported cost.
- Tarjan is checked against brute force: remove each router or link and count the pieces.
- Kruskal = Prim = an exhaustive search over all edge subsets on small graphs.
- File round-trips, GML syntax errors, and undo/redo sequences are tested too.
- Simulator: a forced mid-route failure must cause a rollback and a successful reroute. An unreachable destination must fail. A repaired one must succeed after back-off. Random load on all samples must keep the capacity invariants.

---

## 8. Viva notes (likely questions)

- **Why an indexed heap for Dijkstra?** A plain heap can't find a router's entry to lower its distance. The position array `pos[key]` makes `decreaseKey` $O(\log V)$.
- **Why does Dijkstra need non-negative costs?** Once a router is extracted, its distance is final. A negative link found later could still lower it. Bellman-Ford makes no greedy choice, so it handles negative costs, at $O(VE)$.
- **Why a stack for rollback?** Reservations must be undone in reverse order (LIFO). That is exactly what a stack gives. It is the same idea as an undo log or the "compensating actions" of the Saga pattern.
- **Why union-find before routing?** Building it costs almost $O(E)$, and it answers "can I reach the destination at all?" without running a search that would fail.
- **Why does Floyd-Warshall work?** Dynamic programming:

$$d_k[i][j] = \min\big(d_{k-1}[i][j],\; d_{k-1}[i][k] + d_{k-1}[k][j]\big)$$

  where $d_k[i][j]$ is the cheapest $i \to j$ route that only passes through routers $0..k$.
- **Tarjan's condition:** for a DFS child $v$ of $u$, $\text{low}[v] \ge \text{disc}[u]$ means $u$ is an articulation point (the root needs 2 or more children instead). $\text{low}[v] > \text{disc}[u]$ means $u$–$v$ is a bridge.
- **Why does the hash map resize at 0.75?** Expected chain length equals the load factor $\alpha = n/m$. Keeping $\alpha$ bounded keeps operations $O(1)$. Doubling costs $O(n)$, but it happens only after about $n$ inserts, so it is $O(1)$ amortised.
- **Reading the benchmarks:** when $n$ doubles, time grows ×2 for $O(n)$, ×4 for $O(n^2)$, ×8 for $O(n^3)$. The *growth* column shows this ratio directly.

---

## 9. References

Primary papers:

- E. W. Dijkstra (1959). *A note on two problems in connexion with graphs.* Numerische Mathematik 1.
- R. Bellman (1958). *On a routing problem.* Quarterly of Applied Mathematics 16.
- E. F. Moore (1959). *The shortest path through a maze.* (BFS)
- R. W. Floyd (1962). *Algorithm 97: Shortest path.* CACM 5(6).
- R. E. Tarjan (1972). *Depth-first search and linear graph algorithms.* SIAM J. Computing 1(2).
- J. Hopcroft and R. E. Tarjan (1973). *Algorithm 447: Efficient algorithms for graph manipulation.* CACM 16(6).
- R. E. Tarjan (1975). *Efficiency of a good but not linear set union algorithm.* JACM 22(2).
- J. B. Kruskal (1956). *On the shortest spanning subtree of a graph.* Proc. AMS 7.
- R. C. Prim (1957). *Shortest connection networks and some generalizations.* Bell System Tech. J. 36.
- J. W. J. Williams (1964). *Algorithm 232: Heapsort.* CACM 7(6). (binary heap)
- C. A. R. Hoare (1961). *Algorithm 64: Quicksort.* CACM 4(7).
- E. Fredkin (1960). *Trie memory.* CACM 3(9).
- B. M. Waxman (1988). *Routing of multipoint connections.* IEEE JSAC 6(9). (random topologies)
- T. Fruchterman and E. Reingold (1991). *Graph drawing by force-directed placement.* Software: Practice & Experience 21(11).
- H. Garcia-Molina and K. Salem (1987). *Sagas.* SIGMOD. (compensating rollback)
- S. Knight et al. (2011). *The Internet Topology Zoo.* IEEE JSAC 29(9).

Books:

- Sedgewick and Wayne, *Algorithms*, 4th ed. Written in Java; chapters 1.3, 2, 3.4, 4.1–4.4 map almost one to one onto this project. This is the best entry point.
- Cormen, Leiserson, Rivest and Stein, *Introduction to Algorithms* (CLRS). Chapters on heaps, hashing, elementary graph algorithms, MST, and single- and all-pairs shortest paths. The proofs are here.
- Kurose and Ross, *Computer Networking: A Top-Down Approach*, chapter 5. Link-state (Dijkstra) vs distance-vector (Bellman-Ford) routing.
