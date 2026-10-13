# M5 Sprint 4 integration evidence (#75)

Author: CUI ZIJIAN (KabiKabiKabigon)  
Local validation: 2026-10-13 · baseline main: `fdea649`

## Reproduce

Use JDK 17, Maven 3.9 and a running Docker engine, then run `mvn clean verify`.
Failsafe starts MySQL 8.0.36 with the production `init.sql` schema through Testcontainers.
The tests use real Spring security, controllers, services and MyBatis mappings. External
file storage is stubbed by the existing test foundation; these runs are not staging QA.

The JaCoCo report now runs in `verify`, after Failsafe integration tests, so the uploaded
HTML/XML includes the same execution data as the coverage check. The line gate remains
40%. Previously the report ran during `test` and omitted the later integration coverage.
For a comparable baseline, main was run with `mvn verify` followed by `mvn jacoco:report`.

## Results

| Metric | Main baseline | This change |
|---|---:|---:|
| Unit tests | 130 passed | 140 passed |
| Integration tests | 37 passed | 45 passed |
| Instruction coverage | 37.71% | 42.24% |
| Branch coverage | 16.13% | 18.29% |
| Line coverage | 65.65% | 72.23% |
| Failed / skipped tests | 0 / 0 | 0 / 0 |

Source: `target/surefire-reports/TEST-*.xml`, `target/failsafe-reports/TEST-*.xml` and the
root counters in `target/site/jacoco/jacoco.xml`. The agenda's 44.5% line figure was an
older baseline; the table measures current main with the integration tests included.
Generated reports are CI artifacts, not committed source files.

## Acceptance coverage

| Path | Evidence |
|---|---|
| Pay → ship → confirm receipt | `TransactionLifecycleIT.buyerPaysSellerShipsBuyerConfirmsAndReadsBothOrderViews`: HTTP calls, persisted state, wallet deduction, stock reservation, price snapshot, wrong-party rejection, buyer detail and seller list |
| Cancel | `cancelledOrderReleasesStockExactlyOnceAndCannotBePaid`: wrong caller, single release, repeated cancel and payment refusal |
| Expire | `expirySweepReleasesOnlyOverdueReservationsAndIsIdempotent`: same `OrderService` sweep invoked by the scheduler, real expired/future timestamps, reservation release and repeated sweep |
| Refund | `refundThroughHttpRestoresBalanceStockAndKeepsBothLedgerRows`, plus existing `RefundIT`: balance/stock restoration and preserved payment/refund ledger |
| Bargaining acceptance | `acceptedBargainCreatesPayableOrderAtNegotiatedPrice`: real HTTP offer/accept/pay, maker and repeat rejection, negotiated order-line snapshot and wallet debit |
| Credit and recommendation reads | `CreditRecommendationIT`: automatic review-derived score, account/product/list APIs, excluded order states and strategy endpoints |

Expiry itself has no public controller endpoint; its test deliberately calls the production
sweep after creating an order through HTTP. The Redis scheduler lock retains its existing
unit tests. This does not claim an external Redis or staging deployment test.

## Later deliverables

#77 remains a separate whole-site/staging acceptance pass and defect log during Oct 18–21.
#94 should use refreshed CI evidence for the Oct 24 quality presentation. Neither is marked
complete by this local integration run. The design UML pack remains Sprint 5 issue #100.
