# CrowdStrike — Recruiter Screen Prep (30 min)

## What this round is (and isn't)
- **Who:** a recruiter/talent partner — usually **not** an engineer.
- **What they check:** Can you explain your work simply? Do your skills match the role? Why CrowdStrike? Why leaving? Salary, notice period, location. Are you pleasant and clear?
- **Not:** coding or deep system design. Keep answers **short, clear, confident**. Avoid heavy jargon; if they want depth, they'll ask.

### Typical 30-minute flow
| Time | Part |
|---|---|
| 0–3 min | hello, small talk, role overview from recruiter |
| 3–8 min | **"Tell me about yourself"** |
| 8–18 min | your experience, a project, why CrowdStrike, why leaving |
| 18–25 min | logistics: notice period, salary, location, other interviews |
| 25–30 min | **your questions** + next steps |

---

## 1. Your intro — improved

### What was wrong with the original
| Issue | Why it matters | Fix |
|---|---|---|
| ❌ **"Temple caught my attention"** | wrong company name — an instant red flag | say **CrowdStrike** and give a real reason |
| "I **own** a personalization service" | a recruiter may pass this to engineers, who will ask "what did *you* own?" — and the service has many contributors | "I'm a core engineer on…" |
| "two-level **Redis**/local cache" | Redis is being introduced, not running today | "two-level caching — in-memory, and we're introducing Redis" |
| No link to **security** | CrowdStrike is a security company — any security work is a big plus | mention SSO / security fixes |
| Ending is generic ("closer to product, earlier stage") | sounds like a startup answer; CrowdStrike is a large company | tie to **scale + security + impact** |

### ✅ Improved intro (~60–75 seconds — say it out loud, time it)
> "I'm a backend software engineer with a little over three years of experience. I'm currently an SDE II at Jio Platforms, where I work on high-throughput backend microservices using Java, Spring Boot, Node.js, Kafka and Kubernetes.
>
> Most of my work is on our personalization platform — the system that decides what content **millions of Jio TV and app users** see on their screens, with traffic going up to **tens of thousands of requests per second during live cricket matches**.
>
> One project I'm proud of is improving our **caching layer**. Under that traffic, servers were running out of memory and putting heavy load on an expensive ML service. We fixed our in-memory caches, made sure only one request fetches missing data instead of hundreds, and we're now adding **Redis as a shared cache** across all servers — which made the service faster, more stable and cheaper to run.
>
> I've also worked on **modernizing legacy Java services** onto Spring Boot 3 and Java 21, and on **security** — single sign-on and fixing security issues in our code.

**If asked "what was the impact?":** "Memory became stable instead of crashing, response times dropped noticeably, and we made far fewer calls to the ML service." (If you use resume figures, say "roughly" and be ready to explain how they were measured.)
>
> Now I'm looking for a role with **even larger scale, where reliability and security really matter** — which is exactly why CrowdStrike interests me."

**If asked "how did you modernize it?":** "We moved one piece at a time while the old system kept running, and checked that the new code gave exactly the same responses as the old one before switching over."

**Numbers:** use 1–2 rounded ones ("millions of users", "tens of thousands of requests per second"). They must match your resume and be explainable if an engineer asks later. Don't list five metrics in one breath.

**Tips:**
- Speak slowly. Smile. Stop at the end — let them ask.
- If they ask for numbers: "It serves millions of users and peaks at tens of thousands of requests per second during live matches." *(Only use exact figures you can stand behind.)*

---

## 2. "Why CrowdStrike?" (they WILL ask)

### Know the basics (say these in your own words)
- CrowdStrike is a **cybersecurity company**. Its main product is the **Falcon platform**: it protects companies' laptops, servers, cloud and identities from attacks.
- **Cloud-native:** a small agent ("sensor") runs on each machine and sends security events to CrowdStrike's cloud, where they're analysed — **huge data volumes, in real time**.
- That means **massive-scale, low-latency, highly reliable backend systems** — exactly your area.

### Answer (30–45 seconds)
> "Three reasons. First, the **scale and the problem**: Falcon processes huge streams of security events in real time — that's the kind of high-throughput, low-latency backend work I enjoy, just at a much bigger scale than I work on today. Second, **the work matters** — stopping breaches has real impact for customers. Third, I've enjoyed the security side of my current job — SSO, fixing security issues in our code — and I'd like to go deeper into that domain."

✍️ Before the call: read the **job description** again and add one line that matches it (e.g. "I saw the role involves cloud services in Go/Java on Kubernetes…").

### If they mention the July 2024 outage
(A faulty Falcon sensor update crashed many Windows machines worldwide in July 2024.)
> "I read about it. What stood out to me was how much it showed the importance of safe rollouts — staged deployments, testing and fast rollback. That's something I care about in my own work, like using parity tests and step-by-step migrations so changes can't break production."
→ Calm, no criticism, turns it into a **reliability** point.

---

## 3. "Why are you leaving Jio?"
Keep it **positive** — never complain about your manager, team or company.
> "I've learned a lot at Jio — scaling systems and handling production traffic. After three years, I'm looking for a bigger challenge: larger-scale systems, a strong engineering culture, and a domain like security where reliability really matters. CrowdStrike fits all of that."

---

## 4. Common recruiter questions — short answers

| Question | Your answer (adapt) |
|---|---|
| **Walk me through your current role.** | "SDE II on the personalization platform at Jio. I work on the Java backend that the configuration portal uses, and the Node.js service that serves recommendations to TVs and apps. I do feature work, performance fixes, migrations and production support." |
| **What's your tech stack?** | "Mainly Java and Spring Boot, also Node.js. Kafka, Elasticsearch, Redis, Docker and Kubernetes, and Google Cloud." |
| **Tell me about a project you're proud of.** | The Spring Boot migration (step by step + parity tests + ~1,200 unit tests + security fixes) — or the **preview tool** you built for the portal. Keep to 1 minute. |
| **A challenge you faced?** | A production issue: e.g. ML API traffic spiking — you first suspected connection settings, then found the real bug (cache freshness check was passed the wrong value, so valid cache entries were treated as expired). Shows debugging and honesty. |
| **Team size / who do you work with?** | ✍️ your real team size; "I work with frontend, data/ML and DevOps teams." |
| **Experience with cloud?** | "Kubernetes on-prem and Google Cloud — I worked on moving our API to GKE. I know the AWS equivalents well." *(be honest: GCP, not AWS)* |
| **Go / C++ / Python?** | (CrowdStrike uses a lot of **Go**.) "My main languages are Java and Node.js. I've used C++ for a work-stealing thread pool project and Python for scripting. I pick up languages quickly — I'd be happy to work in Go." |
| **Security experience?** | "Worked on SAML SSO and session handling, and fixed security findings — risky regex patterns (ReDoS), file permissions, resource leaks. I'm interested in going deeper." |
| **Leadership / mentoring?** | ✍️ any real example: onboarding someone, code reviews, leading the migration effort. |
| **Are you interviewing elsewhere?** | "Yes, I'm in early conversations with a couple of companies, but CrowdStrike is a top choice for me." (honest, creates mild urgency) |
| **When can you join? / Notice period?** | ✍️ your real notice period (e.g. "60 days; I can check about an early release"). |
| **Location / relocation / hybrid?** | ✍️ check the JD's city; answer clearly ("Yes, open to relocating to ___" / "Happy with hybrid"). |

---

## 5. Salary question — how to handle

**Rule:** try to let them share the range first; don't give a low number too early.

1. **First try:**
   > "I'd like to understand the role and level better first. Could you share the budgeted range for this position?"
2. **If they insist on current CTC:**
   > "My current total compensation is around ✍️ ___ (fixed + variable). Based on the role and market, I'm looking for something in the range of ✍️ ___, but I'm flexible depending on the overall package."
3. **Know before the call:**
   - ✍️ your current **fixed**, **variable**, **total CTC**
   - ✍️ your **expected range** (research CrowdStrike SDE-2/Senior India on levels.fyi, Glassdoor, AmbitionBox)
   - Mention you'll consider **base + RSUs (stock) + bonus** together.

---

## 6. Questions YOU should ask (pick 3–4)
1. "Which team is this role for, and what does that team work on?"
2. "What does the tech stack look like — languages, cloud, data systems?"
3. "What does the interview process look like from here, and how should I prepare?"
4. "What does success look like in the first 6 months for this role?"
5. "Is the role hybrid or in-office, and which location?"
6. "What do people on the team enjoy most about working at CrowdStrike?"

❌ Don't ask about salary/leave first, or "what does CrowdStrike do?".

---

## 7. Tell-me-about-yourself in 30 seconds (if they ask for the short version)
> "I'm a backend engineer with three years at Jio Platforms, working on Java, Spring Boot, Node.js, Kafka and Kubernetes. I work on our personalization platform that serves millions of TV and app users, mostly on performance, reliability and backend modernization — including moving our legacy Java backend to Spring Boot 3. I'm looking to work on larger-scale, security-focused systems, which is why CrowdStrike is exciting for me."

---

## 8. Day-before checklist
- [ ] Read the **job description** twice; note 3 keywords and match each to your experience
- [ ] Practise the intro **out loud 5 times**, timed (60–75 s)
- [ ] Fill every ✍️ (notice period, CTC, expected range, location, team size)
- [ ] Have your **resume open** — and make sure it says nothing you can't explain
- [ ] Quiet room, good internet, headphones, water
- [ ] Write down the recruiter's name; join 2 minutes early
- [ ] After the call: send a short **thank-you email** within 24 h

---

## 9. Words to avoid → use instead
| ❌ Avoid | ✅ Use |
|---|---|
| "I own the whole service" | "I'm a core engineer on…" |
| "Redis L2 in production" | "we're introducing Redis" |
| "I'm leaving because of my manager/pay" | "looking for bigger scale and challenge" |
| long technical detail | one simple sentence + "happy to go deeper" |
| "AWS expert" | "Google Cloud at work, familiar with AWS" |
| any other company's name | **CrowdStrike** — say it a few times naturally |
