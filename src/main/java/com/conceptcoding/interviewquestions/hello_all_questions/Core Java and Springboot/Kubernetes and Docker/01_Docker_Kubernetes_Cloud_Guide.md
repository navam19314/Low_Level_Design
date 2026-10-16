# Docker · Kubernetes · Cloud — Easy Interview Notes

**How to read:** each topic has 👉 **simple meaning**, 🌍 **real-world example**, 🗣️ **what to say in the interview**.
We use one running example app all through: **"QuickBite" — a food delivery app** (like Swiggy/Zomato).

> **Your 20-second intro:**
> "At Jio our services run on Kubernetes — a Node.js recommendation API on about 80 pods and a Java backend. We build Docker images in CI, deploy with Kubernetes Deployments, Services and ConfigMaps, and I worked on moving the API to Google Cloud (GKE). I've debugged real issues like pods getting OOMKilled and CPU limits."

⚠️ Your real work is **Kubernetes + Google Cloud (GCP)**, not AWS. Say: *"I used GCP at work; in AWS the same thing is called …"* (table in Part 3).

---

# PART 1 — DOCKER

## 1.1 Why Docker exists
👉 **Problem:** "It works on my laptop but not on the server." Different Java/Node versions, missing libraries, different OS settings.
👉 **Docker's fix:** pack the app **together with everything it needs** into one box. The box runs the same everywhere.

🌍 **Example:** QuickBite's order service needs Java 21 and some libraries. The developer's laptop has Java 21, the server has Java 17 → crash. With Docker, Java 21 is **inside the box**, so the server's Java version doesn't matter.

## 1.2 Image, container, Dockerfile, registry
| Term | Simple meaning | Real-world example |
|---|---|---|
| **Dockerfile** | the recipe | a written recipe for biryani |
| **Image** | the packed meal kit made from the recipe (can't be changed) | a sealed meal-kit box |
| **Container** | a running copy of the image | the biryani actually cooking on a stove |
| **Registry** | where images are stored | a warehouse of meal kits (Docker Hub, AWS ECR, Google Artifact Registry) |

🌍 One QuickBite image → you can start **10 containers** from it on 10 servers, all identical.

## 1.3 Container vs Virtual Machine
👉 **VM** = a whole separate house: own walls, own plumbing (own operating system). Heavy, takes minutes to build.
👉 **Container** = a room in an apartment building: own door and lock, but **shares the building's plumbing** (the host's Linux kernel). Light, ready in seconds.

| | VM | Container |
|---|---|---|
| Starts in | minutes | seconds |
| Size | GBs | MBs |
| Has its own OS? | yes | no — shares the host's kernel |
| Isolation | stronger | good, but a kernel bug can affect all containers 🛡️ |

🗣️ "A container is just a normal Linux process that is **isolated** using *namespaces* (what it can see) and **limited** using *cgroups* (how much CPU/memory it can use)."

🌍 **Namespaces vs cgroups example:** in an apartment, *namespaces* = you can only see inside your own flat; *cgroups* = your flat has a fixed electricity and water limit.

## 1.4 Layers and caching
👉 Each line in a Dockerfile creates a **layer**. Docker **reuses layers** that didn't change, so rebuilds are fast.

🌍 **Example:** like packing a suitcase in the same order every trip — if only your shirt changes, you don't repack the shoes at the bottom.
```dockerfile
FROM node:18-slim
COPY package.json .        # changes rarely
RUN npm install            # slow step → cached if package.json didn't change
COPY . .                   # your code changes often → only this layer rebuilds
CMD ["node", "server.js"]
```
🗣️ "Put rarely-changing steps first and code last, so dependency installs are cached."

## 1.5 Multi-stage build
👉 Build the app in a **big image** (with compilers), then copy only the finished app into a **small image**.
🌍 **Example:** a bakery bakes in a big kitchen full of ovens, but delivers only the **cake** in a small box — not the ovens.
🗣️ "Smaller image = faster deploys and fewer security holes."

## 1.6 Quick Docker Q&A
| Question | Easy answer | Real-world example |
|---|---|---|
| `CMD` vs `ENTRYPOINT`? | ENTRYPOINT = the fixed command; CMD = default arguments you can change | ENTRYPOINT = "make coffee"; CMD = "medium size" (customer can change the size) |
| `COPY` vs `ADD`? | Use COPY. ADD also unzips/downloads things — surprising | |
| `.dockerignore`? | list of files to **leave out** of the image | don't pack your house keys and passwords in the suitcase you give to the courier |
| Volume? | storage that **survives** when the container is deleted | QuickBite's uploaded restaurant photos must not vanish when the container restarts |
| Port mapping `-p 8080:80`? | outside port 8080 → container port 80 | building reception (8080) forwards visitors to flat 80 |
| Why not use `:latest` tag? | you can't tell which version is running; rollback is hard | ordering "today's special" — you don't know what you'll get |
| Exit code **137**? | container was killed — usually **out of memory** | the electricity got cut because the flat used too much |

## 1.7 🛡️ Docker security (CrowdStrike cares a lot)
| Rule | Why — real example |
|---|---|
| **Don't run as root** (`USER 1000`) | if a hacker breaks into the app, they get a normal user, not the master key to the building |
| **Small base image** (slim/alpine/distroless) | fewer things installed = fewer doors for attackers |
| **Scan images** in CI (Trivy, etc.) | like airport security scanning bags before the flight |
| **No passwords/keys inside images** | anyone who downloads the image can read them |
| **Pin versions** | same ingredients every time, no surprise changes |
| **Sign images** | like a seal on a medicine bottle — proves nobody tampered with it |

🗣️ **Your real example (great for "what would you improve?"):**
> "Our customer API Dockerfile copied the whole project folder and ran as root, and the ignore file didn't exclude a credentials folder — so a cloud key could end up inside the image. I'd add it to `.dockerignore`, run as a non-root user, use a multi-stage build, and inject credentials at runtime from a secret store. Our Java backend image also gave everyone full permissions on its files (`chmod 777`) — I'd replace that with least-privilege permissions."

---

# PART 2 — KUBERNETES (K8s)

## 2.1 Why Kubernetes exists
👉 Docker runs containers on **one machine**. But QuickBite needs **100 containers on 20 machines**, restarted if they crash, and more of them at dinner time.
👉 **Kubernetes = the manager** that runs, heals and scales containers across many machines.

🌍 **Example:** Docker is one cook. Kubernetes is the **restaurant manager** who decides which kitchen each cook works in, replaces a cook who goes home sick, and calls in extra cooks during the Friday dinner rush.

## 2.2 The big picture
```
          CONTROL PLANE (the manager's office)
   API server  → the front desk; every request goes through it
   etcd        → the notebook where the plan is written
   scheduler   → decides which machine a new pod goes to
   controllers → keep checking "does reality match the plan?" and fix it

          WORKER NODES (the kitchens)
   kubelet     → the kitchen supervisor; starts pods and reports health
   kube-proxy  → handles network routing to pods
   pods        → the cooks doing the work
```
👉 **Key idea — "desired state":** you write *"I want 5 copies of QuickBite orders running"*. If one dies, Kubernetes notices and starts a new one — automatically, forever.
🌍 Like a thermostat: you set 24°C; it keeps adjusting until the room matches.

## 2.3 Main objects — with examples
| Object | Simple meaning | QuickBite example |
|---|---|---|
| **Pod** | smallest unit: 1+ containers that live together | one running copy of the order service |
| **Deployment** | "keep N copies of this pod running, and update them safely" | keep 5 copies of the order service |
| **ReplicaSet** | the part of a Deployment that counts the copies | (you rarely touch it directly) |
| **Service** | a fixed address in front of changing pods | "orders-service" — other apps call this name, even as pods come and go |
| **Ingress** | the main entrance; routes web traffic by URL | `quickbite.com/orders` → orders service, `/payments` → payment service |
| **ConfigMap** | settings outside the code | the delivery fee, the restaurant API URL |
| **Secret** | sensitive settings | the database password, payment gateway key |
| **StatefulSet** | pods that need a fixed name and their own disk | a database or Kafka cluster |
| **DaemonSet** | one pod on **every** machine | a log collector or a **security agent** on every server (CrowdStrike's Falcon sensor runs this way) 🛡️ |
| **Job / CronJob** | run once / run on a schedule | send the daily sales report every night at 2 AM |
| **Namespace** | a separate folder in the cluster | `dev`, `test`, `prod`, or one per team |
| **PersistentVolume (PVC)** | storage that outlives pods | restaurant photos, database files |
| **HPA** | automatically add/remove pods based on load | more order pods at 8 PM, fewer at 4 AM |
| **PDB** | "always keep at least N pods up" | during server maintenance, never drop below 3 order pods |

## 2.4 Service types
| Type | Who can reach it | Real-world example |
|---|---|---|
| **ClusterIP** (default) | only apps inside the cluster | the order service calling the payment service — internal phone line |
| **NodePort** | anyone who can reach a server's IP on a port (30000–32767) | a side door with a number on it. *Our on-prem customer API used port 32000.* |
| **LoadBalancer** | gets a cloud load balancer with its own IP | the main reception desk. *Our Google Cloud setup used an internal load balancer.* |

## 2.5 Requests and limits ⭐ (you have real stories here)
👉 **Request** = "please reserve this much for me" (used to choose a machine).
👉 **Limit** = "never let me use more than this".

🌍 **Example:** booking a hotel room — *request* = the room you're guaranteed; *limit* = the maximum extra beds allowed.

| What happens if you go over the limit? | |
|---|---|
| **CPU** | you're **slowed down** (throttled), not killed — like a speed limiter on a car |
| **Memory** | you're **killed** (OOMKilled, exit code 137) — like a fuse blowing |

**QoS (who gets evicted first when a machine is full):** pods with **no** requests/limits go first, pods with request = limit go last.

🗣️ **Your real story 1 — OOMKilled:**
> "Our pods kept getting killed for using too much memory — up to 8 GB. Part of it was in-memory caches that had no size limit and kept growing; we replaced them with size-limited caches. Part was the Linux page cache: the pod reads a 171 MB IP-location file, and that cached file data counted towards the pod's memory. Setting a lower hard limit (8 GB → 6 GB) made the kernel free that cache before killing the pod. Memory then stayed around 2–3 GB."

🗣️ **Your real story 2 — CPU vs worker count:**
> "Our Node.js app starts one worker per CPU core using `os.cpus()`. Inside a container, that returns the **whole machine's** cores — say 16 — even though the pod is only allowed 2.5 CPUs. So 16 workers fight over 2.5 CPUs, use up the CPU allowance quickly and get throttled, and each worker keeps its own copy of the cache. The fix is to set the worker count from the pod's CPU limit."

🌍 **Simple picture:** a kitchen allowed 2.5 burners' worth of gas, but 16 cooks are hired. They all stand around waiting for gas, and each keeps their own recipe notebook.

## 2.6 Health checks (probes)
| Probe | Question it asks | If it fails | Real-world example |
|---|---|---|---|
| **Liveness** | "Are you still alive?" | restart the container | a cashier who has frozen → replace them |
| **Readiness** | "Are you ready for customers?" | stop sending traffic (no restart) | a cashier still counting the till → don't send customers yet |
| **Startup** | "Have you finished starting?" | wait before checking the others | a new employee's first-day training |

🗣️ "Our Google Cloud setup checks that port 3200 is open; the on-prem setup had no probes. I'd add a readiness check that also verifies the app can reach its database."

## 2.7 Updating safely
| Strategy | How | Real-world example |
|---|---|---|
| **Rolling update** (default) | replace pods a few at a time | renovating a hotel floor by floor — guests keep staying |
| **Blue/green** | run old and new side by side, switch all traffic at once | open the new restaurant branch, then move all customers over in one go |
| **Canary** | send 5% of users to the new version first | let a few regular customers taste the new dish before adding it to the menu |
| **Rollback** | `kubectl rollout undo` | go back to the old recipe if customers complain |

👉 `maxUnavailable: 0` = never have fewer pods than needed during an update → **zero downtime**.

## 2.8 Autoscaling
| Tool | Scales what | Real-world example |
|---|---|---|
| **HPA** | number of pods | more cooks during dinner rush |
| **VPA** | size of each pod (CPU/memory) | give a cook a bigger stove |
| **Cluster Autoscaler** | number of machines | open an extra kitchen when all kitchens are full |

🗣️ "In our test environment we used HPA (2–6 pods at 60% CPU). In production we ran a fixed ~80 pods sized for cricket-match peaks, because traffic jumps in seconds when a match starts — autoscaling reacts *after* the spike begins. For known events I'd **pre-scale** before the event and keep HPA for unexpected load."

## 2.9 🔧 Troubleshooting — very common questions
| Pod status | What it means | What you check | Real-world example |
|---|---|---|---|
| **Pending** | no machine can take it | `kubectl describe pod` → not enough CPU/memory? | a guest waiting because every hotel room is full |
| **ImagePullBackOff** | can't download the image | wrong image name/tag, no registry permission | the meal kit isn't in the warehouse, or you don't have the key |
| **CrashLoopBackOff** | starts, crashes, restarts again and again | `kubectl logs <pod> --previous` → missing config, wrong password | a car that starts then stalls every time |
| **OOMKilled** | used more memory than allowed | memory limit vs actual use; memory leak? | the fuse blew |
| **Running but no traffic** | readiness failing, or Service points to wrong pods | `kubectl get endpoints <service>` | shop is open but the signboard points to the wrong street |
| **Slow but "healthy"** | CPU throttling | CPU use vs CPU limit | car with a speed limiter on the highway |

**Commands to mention:**
```
kubectl get pods                      # list pods and status
kubectl describe pod <name>           # events: why is it pending/crashing?
kubectl logs <name> --previous        # logs from the crashed run
kubectl exec -it <name> -- sh         # open a shell inside
kubectl top pod                       # CPU / memory use
kubectl rollout undo deploy/<name>    # roll back
```

## 2.10 Networking (keep it simple)
- Every pod gets **its own IP address**, and pods can talk to each other directly.
- A **Service** gives a **fixed name** (DNS) like `orders-service`, so callers don't care which pod answers.
- 🛡️ **NetworkPolicy** = firewall rules between pods. By default, **every pod can talk to every pod**.
  🌍 Example: only the order service should be allowed to talk to the payment database — a NetworkPolicy blocks everyone else.

## 2.11 🛡️ Kubernetes security (CrowdStrike focus)
| Area | What to do | Real-world example |
|---|---|---|
| **RBAC** | give each person/app only the permissions they need | a waiter can take orders but can't open the safe |
| **Pod security** | non-root, read-only filesystem, no "privileged" pods | staff don't get master keys |
| **Secrets** | K8s Secrets are only base64 (not encrypted!) → turn on encryption or use Vault / cloud Secret Manager; **never write passwords in YAML files in git** | don't write the safe code on a sticky note on the door |
| **Network** | NetworkPolicies, deny by default | only the kitchen door connects to the storeroom |
| **Supply chain** | scan + sign images; block unsigned images (admission controllers like Kyverno/OPA) | only accept sealed, inspected deliveries |
| **Runtime protection** | detect strange behaviour in running containers (e.g. someone opens a shell, a crypto-miner starts) | CCTV + a guard noticing someone in the kitchen at 3 AM — this is what CrowdStrike Falcon does |

🗣️ **Your honest hardening story:**
> "Reviewing our setup, I saw database credentials written as plain values in deployment files, a cloud key committed to the repo, and containers running as root. I'd move credentials into a secret manager, rotate the exposed key, run containers as non-root, add NetworkPolicies, and add image scanning to the CI pipeline."
*(Say "I raised it" only if you actually raise it with your team — worth doing!)*

---

# PART 3 — CLOUD

## 3.1 Basics
| Term | Simple meaning | Real-world example |
|---|---|---|
| **IaaS** | rent raw machines | renting an empty kitchen |
| **PaaS** | rent a ready platform | renting a kitchen with staff and equipment (managed Kubernetes, managed database) |
| **SaaS** | just use the software | ordering food from an app — Gmail, Slack |
| **Region** | a geographic area | Mumbai region |
| **Availability Zone (AZ)** | a separate data centre inside a region | 3 different buildings in Mumbai with separate power |
| **High availability** | keep running if one part fails | QuickBite runs in 3 AZs; one building loses power, the app still works |
| **Disaster recovery** | recover from a big failure | the whole Mumbai region goes down → switch to Hyderabad |
| **RPO** | how much data you can afford to lose | "we can lose at most 5 minutes of orders" |
| **RTO** | how long you can be down | "we must be back within 30 minutes" |

## 3.2 🛡️ Shared responsibility model
👉 **The cloud provider** secures the buildings, hardware and network.
👉 **You** secure your data, passwords, user permissions, settings, and your app.
🌍 **Example:** renting a flat — the landlord fixes the building's main door and walls; **you** lock your own door and don't leave your keys under the mat.
🗣️ "Most cloud breaches are customer misconfigurations — public storage buckets, leaked keys, too-broad permissions."

## 3.3 AWS ⇄ Google Cloud (say "I used GCP; in AWS it's…")
| Need | AWS | GCP (what you used) | QuickBite example |
|---|---|---|---|
| Virtual machines | EC2 | Compute Engine | a raw server |
| Managed Kubernetes | **EKS** | **GKE** | runs all QuickBite pods |
| Image registry | ECR | Artifact Registry | stores QuickBite images |
| File storage | **S3** | Cloud Storage | restaurant photos, menus |
| Serverless | Lambda | Cloud Functions / Cloud Run | resize a photo when uploaded |
| Load balancer | ALB / NLB | Cloud Load Balancing | spreads users across pods |
| SQL database | RDS / Aurora | Cloud SQL | orders and users |
| NoSQL | DynamoDB | Firestore / Bigtable | live order status |
| Cache | ElastiCache (Redis) | Memorystore | restaurant menus cached |
| Messaging | SQS / SNS / Kinesis | **Pub/Sub** (you used this) | "order placed" events |
| Secrets | Secrets Manager | Secret Manager | DB passwords |
| Monitoring | CloudWatch | Cloud Monitoring/Logging | dashboards, alerts |
| Network | VPC, Security Groups | VPC, firewall rules | private network |

## 3.4 Network (VPC) in one picture
```
QuickBite VPC (private network in the cloud)
 ├─ Public subnet  → load balancer (faces the internet)
 └─ Private subnet → app pods + database (NOT reachable from the internet)
        └─ go out to the internet via a NAT gateway (e.g. to call a maps API)
Security Group = a guard at each server's door (who can come in, on which port)
```
🌍 **Example:** a restaurant — the **dining area** is public, the **kitchen and storeroom** are private; only waiters (the load balancer) go between them.

## 3.5 🛡️ IAM (Identity & Access Management) — must know
| Principle | Simple meaning | Real-world example |
|---|---|---|
| **Least privilege** | give only the permissions needed | the delivery app can *read* restaurant menus, not *delete* them |
| **Roles, not long-lived keys** | apps get temporary permissions automatically | a hotel key card that expires at checkout, not a permanent metal key |
| **MFA** | password + a second check | bank login with OTP |
| **Audit logs** | record who did what | CCTV for your cloud account |

🗣️ **Strong answer linking your work:**
> "When we moved to Google Cloud, a service-account key file was committed in the repo. The better way is **Workload Identity**: the pod gets cloud permissions through its Kubernetes identity, so there's **no key file at all** to leak. On AWS the same idea is **IRSA** for EKS."

## 3.6 Common cloud design questions
| Question | Easy answer | QuickBite example |
|---|---|---|
| How do you make it highly available? | 2+ copies across zones, load balancer with health checks, managed multi-zone database | order service in 3 zones |
| How do you handle a traffic spike? | autoscaling, caching, a queue to absorb bursts, pre-scale for known events | New Year's Eve dinner rush |
| How do you reduce cost? | right-size pods, scale down at night, cheaper spot machines for non-critical work, caching | fewer pods at 4 AM |
| Where do you store files? | object storage (S3/GCS) + CDN | menu photos |
| How do you secure a public API? | HTTPS, login tokens, rate limiting, firewall (WAF), private backend | stop bots from spamming orders |
| How do you deploy safely? | CI builds and scans the image → rolling/canary deploy → watch metrics → auto-rollback | new payment feature to 5% first |

---

# PART 4 — RAPID FIRE (one-liners)
| Question | Answer |
|---|---|
| What is a container, really? | a Linux process, isolated (namespaces) and limited (cgroups) |
| Exit code 137? | killed — usually out of memory |
| Exit code 143? | asked to stop politely (SIGTERM) |
| How does a pod shut down? | gets SIGTERM → has 30 s to finish current work → then force-killed. Like "closing in 30 minutes — finish your meal." |
| Sidecar? | a helper container in the same pod — e.g. a log shipper next to the app |
| Init container? | runs before the app starts — e.g. wait until the database is ready |
| Taint / toleration? | taint = "this machine is reserved"; toleration = "this pod is allowed there" (like a VIP lounge) |
| ConfigMap changed — do pods update? | env variables: no (restart needed); mounted files: yes, after a while |
| Helm? | package manager for Kubernetes — like an app store installer for YAML |
| If etcd goes down? | can't make changes, but running pods keep running |
| Service mesh? | Istio/Linkerd — adds encryption, retries, traffic splitting between services |
| eBPF? 🛡️ | small safe programs inside the Linux kernel — used to watch network and security events with low overhead |

---

# PART 5 — 3 STORIES TO HAVE READY
1. **Pods getting OOMKilled** → caches with no limit + file cache counted as memory → size-limited caches + lower memory limit → stable at 2–3 GB. *(2.5)*
2. **Too many workers for the CPU limit** → `os.cpus()` sees the whole machine → throttling → match workers to the pod's CPU limit. *(2.5)*
3. **Security hardening** → root containers, passwords in YAML, key in repo → non-root, secret manager, Workload Identity, image scanning. *(1.7, 2.11, 3.5)* — perfect for CrowdStrike.
