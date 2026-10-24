# ADR 0003: Abstain before generating

Status: Accepted

## Context

A grounded-answer system has two ways to fail: answering without evidence, and wasting a slow model call
on a question the knowledge base cannot answer. A language model asked a question it has no context
for will often produce a fluent, wrong answer.

## Decision

The gateway retrieves candidates first and compares the best cosine score with `MIN_SCORE`. If no
candidate clears it, the gateway returns `abstained: true` **without calling the model**. If the model
is called, it must cite numbered passages or reply `INSUFFICIENT_EVIDENCE`, which also becomes an
abstention. Answers that cite no valid passage are returned with `grounded: false`.

The best score is returned as `topScore` even on abstention.

## Consequences

- Removes the main hallucination path and saves latency and compute for out-of-scope questions.
- Callers get a clear signal (`abstained`) to route to a human, which Phase 3 uses for escalation.
- Quality depends on the threshold. A single global threshold is crude; it must be tuned on real
  data (Phase 6 evals) and may need per-tenant or per-model values.
- Cosine scores are model-specific, so changing the embedding model invalidates the threshold.
