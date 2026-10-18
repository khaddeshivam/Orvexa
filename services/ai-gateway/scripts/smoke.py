"""End-to-end smoke test for a running AI gateway.

    python scripts/smoke.py                      # defaults to http://localhost:8000
    python scripts/smoke.py --base-url http://localhost:8000 --key local-internal-key

Ingests the sample docs, asks in-scope and out-of-scope questions, prints every topScore
(use them to tune GATEWAY_MIN_SCORE) and exits non-zero if behaviour is wrong.
"""
import argparse
import pathlib
import sys
import uuid

import httpx

EXAMPLES = pathlib.Path(__file__).resolve().parent.parent / "examples"

IN_SCOPE = [
    "How long do refunds take to process?",
    "Within how many days can I ask for a refund?",
    "How long does standard shipping take?",
    "Do you ship outside India?",
]
OUT_OF_SCOPE = [
    "What is the capital of France?",
    "Who won the last cricket world cup?",
    "How do I reset my router password?",
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://localhost:8000")
    ap.add_argument("--key", default="local-internal-key")
    args = ap.parse_args()

    tenant = str(uuid.uuid4())
    headers = {"X-Internal-API-Key": args.key, "X-Tenant-Id": tenant}
    client = httpx.Client(base_url=args.base_url, headers=headers, timeout=180)

    print(f"tenant {tenant}\n")
    client.get("/health").raise_for_status()

    for path in sorted(EXAMPLES.glob("*.md")):
        r = client.post(
            "/v1/ingest",
            json={"title": path.stem, "text": path.read_text(encoding="utf-8"), "documentId": path.stem},
        )
        r.raise_for_status()
        print(f"ingested {path.name}: {r.json()['chunkCount']} chunk(s)")

    failures = 0
    print(f"\n{'expect':<9}{'result':<10}{'score':<8}{'grounded':<10}question")
    for expect_answer, questions in ((True, IN_SCOPE), (False, OUT_OF_SCOPE)):
        for q in questions:
            r = client.post("/v1/query", json={"question": q})
            if r.status_code != 200:
                print(f"{'answer' if expect_answer else 'abstain':<9}HTTP {r.status_code}  {q}\n    {r.text}")
                failures += 1
                continue
            body = r.json()
            ok = (not body["abstained"] and body["grounded"]) if expect_answer else body["abstained"]
            failures += not ok
            score = body["topScore"]
            print(
                f"{'answer' if expect_answer else 'abstain':<9}"
                f"{'ok' if ok else 'WRONG':<10}"
                f"{(f'{score:.3f}' if score is not None else '-'):<8}"
                f"{str(body['grounded']):<10}{q}"
            )
            if not body["abstained"]:
                print(f"    -> {body['answer'][:160]}")

    client.delete("/v1/documents/refund-policy")
    client.delete("/v1/documents/shipping-policy")
    print(f"\n{'PASS' if not failures else f'FAIL ({failures} wrong)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
