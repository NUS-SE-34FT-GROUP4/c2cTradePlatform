# SWE5006 Project Self Reflection

CUI ZIJIAN · Team 4

C2C SecTrade

29 October 2026

[Download the Word document](SWE5006-Self-Reflection-CUI-ZIJIAN.docx)

---

My work in C2C SecTrade covered reviews, credit and recommendations, as well as quality assurance, performance testing and report preparation. I became more confident in checking a complete user flow and explaining a test result. I also realised that I still need to improve my planning, my understanding of architecture, and the way I connect code, diagrams and evidence.

## Process

Working in this team showed me that testing depends on more than having a script ready. Accounts, balances, products and the deployed version all need to be prepared. I also needed to coordinate testing windows so that another person's deployment or test would not change the results. I became more careful about recording the version and conditions before starting a run.

I could have planned these dependencies earlier. Some time was spent checking missing preconditions or repeating work after the environment changed. In another project, I would prepare the test cases and data requests during sprint planning. I would also keep a small checklist for each run and update the defect record immediately, rather than trying to organise everything near the report deadline.

## Architecture and Design

The review use case helped me understand why responsibilities need to be clear. The controller receives the request, the service checks the business rules, and the mappers handle persistence. A review is not just a comment saved to a table. The system must check the buyer, the completed order and duplicate submissions. The database constraint matters because two requests can arrive at nearly the same time.

The recommendation feature also made the design patterns more concrete for me. Different algorithms can use the same Strategy interface, while the registry-based Factory selects an implementation by name. I can now explain this better than simply naming a pattern. However, drawing the analysis and design models showed that I still need more practice with UML. Next time, I would develop the diagrams alongside the code and review them when the implementation changes.

## Development and Implementation

QA helped me look beyond a successful screen response. In the payment and settlement flow, I needed to check when money was held and when the seller actually received it. A button opening the correct page was also different from proving that chat messages were delivered or that an edit was saved. I learned to describe exactly what a test had checked, including its limits.

Performance testing was another important part of my learning. The number of virtual users does not directly tell us the requests per second. Think time, the request mix and response time all affect the load. I collected client results and worked with teammates who investigated server metrics and implemented fixes. This made it easier to discuss a specific problem instead of only saying that the system was slow.

## Lessons Learnt

The 200-user objective was not met in the recorded tests. This taught me that a target written in a proposal still needs a workload definition and a capacity plan. Passing a smaller test does not close the original gap. I also learned not to explain every failure as a CPU problem without enough evidence. Client errors, connection behaviour and server measurements need to be considered together.

I sometimes focused too much on whether a run passed. A more useful question is what the result tells us and what it cannot tell us. I should have defined the acceptance criteria, duration, data size and recovery checks earlier. If I repeated the project, I would establish a baseline sooner, change fewer conditions between comparisons, and keep the raw results from the beginning. I would also practise explaining the design in my own words instead of relying mainly on technical terms.

## Innovation

I do not think the main contribution was inventing a new technology. For me, it was learning to use familiar methods in a more organised way. The recommendation registry allows another strategy to be added without changing the caller's selection logic. Keeping scripts, versions and raw results together also makes the testing easier for someone else to check.

The later tests helped me separate different questions. The gradual load test checked behaviour after increasing and reducing load. The local volume test changed the catalogue from 100 to 1,000 and 10,000 products while keeping concurrency low. These tests provided useful evidence, but I would not describe the local results as production capacity. I still need to improve how I design experiments and compare results fairly.

## Value

The project gave us a working example of how reviews, transaction rules and recommendations can support a second-hand trading platform. My QA work helped make problems and remaining limits visible to the team. I think the evidence is useful even when a target is not met, because it gives the next person a clearer starting point for improvement.

We could reduce effort and cost by preparing realistic data and smaller diagnostic tests earlier, before repeating large load tests. Reusing common components and automating repeatable checks would also leave more time for investigation. I would choose extra infrastructure based on measurements rather than assume that a larger server is always the answer. Overall, I have made progress in testing and explaining system behaviour. I am also clearer about my weaknesses, especially architecture knowledge, UML and planning, and I know what I need to practise next.
