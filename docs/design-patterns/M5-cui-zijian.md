# M5 — Selecting a recommendation strategy

Author: CUI ZIJIAN (KabiKabiKabigon)  
Sprint 4: #74, #83 · UML follow-up: #100

## Problem and the original implementation

An item page needs related listings. Before this change, `RecommendationService.similarTo`
selected the category query, filtered the results, and switched to recent listings when
that category was exhausted. Adding another algorithm meant editing this caller and its
conditional branches. Selection, fallback policy and algorithm details changed together.

## Candidates and decision

- **Strategy** encapsulates each algorithm behind a common interface. It removes the
  algorithm bodies from the caller but, alone, leaves the caller deciding which concrete
  class to instantiate or invoke.
- **Factory with a discovered registry**, combined with Strategy, centralises name lookup
  and defaults while keeping concrete implementations out of the caller. This is the
  selected solution because a third algorithm should require only a new component.
- **Template Method** could share filtering through inheritance, but the algorithms do
  not need a fixed inherited sequence. It also does not solve name-based selection.
- **Abstract Factory** would create families of related objects; here we select one
  recommendation algorithm, so that additional structure is unnecessary.

This is a registry-based factory, not the GoF subclass-overridden Factory Method. Spring
constructs the strategy instances; the factory supplies the appropriate instance to use.

## Implementation decisions

`RecommendationStrategy` declares `name()` and `recommend(Product, int)`.
`SameCategoryStrategy` and `MostRecentStrategy` are independently discovered `@Component`s;
each supplies its registration name. `RecommendationStrategyFactory` receives all strategy
beans, builds an immutable registry and resolves requests by name. It contains no concrete
strategy class list, `new` calls for strategies, or algorithm switch statement. Duplicate
names and a missing default fail at startup rather than select a random implementation.

`RecommendationService.similarTo` validates the product and limit, then delegates by name.
The HTTP endpoint accepts `strategy`, defaulting to `same-category`, preserving the existing
item-page behaviour. Unknown or unregistered names resolve to `most-recent`. An empty or
failed selected strategy also falls back to recent listings; if the fallback itself fails,
the endpoint returns an empty list and logs the failure instead of breaking the item page.
`RecommendationCandidates` shares the eligibility rule: exclude the current item, delisted
items and fully reserved stock. The existing browsing-history home feed stays separate.

To extend: add an implementation annotated `@Component`, give it a unique `name()`, and
add its algorithm tests. No change is required in the factory, controller or service.
`RecommendationStrategyFactoryTest` demonstrates discovery of a third strategy in a Spring
context, unknown-name resolution, unavailable/empty fallback and startup validation.
`RecommendationStrategyTest` tests the two algorithms independently. The existing related-
item acceptance tests remain, and `CreditRecommendationIT` verifies the public endpoints
against the real Spring context and MySQL mappings.

## Diagrams

The current Sprint 4 deliverable is this text. The before/after class and sequence diagrams,
plus the separate “post a review” use-case analysis/design diagrams, are explicitly deferred
to Sprint 5 issue #100. Native drawio files and PNG exports remain required there; this
one-pager does not claim that the UML pack is complete.
