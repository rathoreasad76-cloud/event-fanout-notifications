# event-fanout-notifications

A small demo of a pattern I use in production, isolated so it's easy to look at
on its own: one SNS topic fanning out to multiple SQS subscribers, each getting
only the events it cares about, with a dead-letter queue for messages a consumer
can't process and idempotent handling so retried deliveries don't double up.

It's a companion piece to
[`order-entitlement-service`](https://github.com/rathoreasad76-cloud/order-entitlement-service),
which covers the *producer* side of event-driven design (the transactional
outbox pattern). This one is about the *consumer* side — what happens once a
message actually lands on a queue, and everything that can go wrong there.

## What it does

A `PaymentEvent` (simulated — this isn't a real payments system) gets published
to one SNS topic. Two SQS queues are subscribed to it:

- **`audit-log-queue`** — no filter policy, gets every event. This is the
  unfiltered trail: everything that happened, for compliance/debugging.
- **`notification-queue`** — filtered at the SNS level to only `PaymentFailed`
  and `RefundIssued` events, via a subscription filter policy. A routine
  `PaymentProcessed` event never even reaches this consumer; SNS filters it out
  before delivery, so the consumer isn't burning cycles ignoring messages it
  doesn't care about.

If the notification consumer can't process a message (simulated via a
`simulateFailure` flag on the request — see below), it's left on the queue
instead of deleted. SQS redelivers it; after `max-receive-count` failed
attempts, SQS itself moves it to `notification-dlq`. A separate drainer polls
the DLQ and writes each one into a queryable table, so a dead letter is
something you can actually find later, not something invisible until someone
thinks to check a queue depth metric.

Endpoints:

- `POST /api/payment-events` — publish an event.
- `GET /api/audit-log` — everything the audit consumer has recorded.
- `GET /api/notifications` — everything the notification consumer successfully processed.
- `GET /api/notifications/dead-letter` — messages that exhausted their retries.

## Why it's built this way

**Filtering happens at SNS, not in the consumer.** I could have had the
notification consumer subscribe to everything and just `if (isNotifiable)` its
way past events it doesn't care about. The reason not to: that means every
irrelevant message still costs a network round-trip, still occupies a consumer
thread, and still needs deleting. A filter policy on the subscription means SNS
does that work once, centrally, instead of every consumer re-implementing the
same "is this for me?" check. It also means the filtering rule is visible in the
topology (`TopologyBootstrapper`) rather than buried in application code.

**The DLQ redrive count lives on the queue, not in consumer code.** The
consumer's only job on failure is to *not delete the message*. It doesn't count
attempts, it doesn't decide when to give up — SQS does that, based on the
`RedrivePolicy` attribute set when the queue was created. This matters because
it means retry behaviour survives a consumer restart or a rolling deploy; it's
not state the consumer has to remember.

**Idempotency is a unique constraint, not an in-memory set.** Both
`audit_log_entries` and `notification_log_entries` have a unique constraint on
`message_id`. A redelivered message either inserts cleanly (first time) or hits
the constraint and is treated as "already handled" — no separate dedup table,
no cache that could be stale after a restart. The database is the single source
of truth for "have I seen this before," which is the only place that's reliably
true across restarts and multiple consumer instances.

**Topology is created by application code, not a shell script.** In
`order-entitlement-service`, LocalStack setup is a small init shell script,
because the topology there is trivial (one topic). Here, the topology — the
filter policy, the DLQ, the redrive policy, the queue permissions letting SNS
write to SQS — is the actual subject of this project, so it's written as
ordinary, readable Java (`TopologyBootstrapper`) instead of hidden in
infrastructure config. It's also idempotent (safe to run on every startup,
including every test run) since SNS/SQS create-by-name calls return the existing
resource if it's already there.

**Queue URLs and the topic ARN are resolved lazily, not injected as config.**
Early on I tried wiring the topic ARN in as a Spring `@Value` property. That
doesn't work cleanly here: the topic doesn't exist until `TopologyBootstrapper`
creates it at startup, and Spring builds all beans — including ones that would
need that ARN — before running `ApplicationRunner`s. `TopologyResolver` looks
resources up by name on first use instead (and caches the result), so there's no
ordering dependency between "the topic exists" and "the publisher bean is
constructed."

## Running it locally

```bash
docker compose up --build
```

Publish an event:

```bash
curl -X POST http://localhost:8081/api/payment-events \
  -H "Content-Type: application/json" \
  -d '{"eventType": "PaymentFailed", "customerId": "customer-1", "amount": 120.00, "currency": "USD", "simulateFailure": false}'
```

Check where it landed:

```bash
curl http://localhost:8081/api/audit-log
curl http://localhost:8081/api/notifications
```

Try a `PaymentProcessed` event instead and you'll see it in `/api/audit-log` but
never in `/api/notifications` — that's the filter policy working.

To watch a message actually reach the dead-letter queue, publish with
`"simulateFailure": true` and poll `/api/notifications/dead-letter` — it takes a
minute or two, since it has to exhaust the queue's visibility timeout across
`max-receive-count` redeliveries (default 3) before SQS moves it.

Ports are offset from `order-entitlement-service` (`8081`, Postgres on `5433`,
LocalStack on `4567`) specifically so both projects can run side by side without
colliding.

## Running the tests

```bash
mvn clean verify
```

- `AuditLogConsumerTest`, `NotificationConsumerTest` — unit tests with mocked
  SQS, checking the failure-doesn't-delete and duplicate-doesn't-reinsert
  behaviours directly.
- `FanoutFilteringIntegrationTest` — real Postgres + LocalStack via
  Testcontainers; publishes both a filtered-out and a notifiable event and
  asserts each ends up in the right place(s).
- `DeadLetterQueueIntegrationTest` — publishes a permanently-failing event with
  `max-receive-count` overridden to 2 (to keep the test fast) and asserts it
  eventually appears in the drained dead-letter table.

Needs a working Docker daemon.

## A note on how this was built

Same disclosure as the companion repo: I used Claude to scaffold this quickly —
boilerplate, the Liquibase changesets, this README — while I made the design
decisions (the filter-policy-over-in-code-filtering choice, the DLQ approach,
the lazy topology resolution) and reviewed the result. I'd rather say that
plainly than have it read as something else.

## What's not here (yet)

- No auth/authz.
- The DLQ drainer doesn't alert anyone — in a real system, something moving to
  `failed_notification_entries` should probably page someone or raise a metric.
- FIFO queues / ordering guarantees aren't covered — this is standard (best
  effort ordering) SQS throughout.
