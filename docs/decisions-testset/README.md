# Decisions API test set

About 200 labelled examples used to pick and check confidence thresholds in Phases 2-6.
One JSONL file per area; one example per line. Aim for the counts below.

| File | Target | Fields |
|---|---|---|
| `routing.jsonl` | 80 | `utterance`, `main_connector`, `needed_connectors[]` |
| `safety.jsonl` | 50 | `app`, `visible_text`, `action`, `user_request`, `risky` (bool), `risk_kinds[]`, `matches_request` (bool) |
| `screens.jsonl` | 40 | `image` (path under `screens/`), `step`, `succeeded` (bool), `blocker` |
| `memory_pairs.jsonl` | 30 | `new_note`, `stored_note`, `duplicate` (bool), `contradicts` (bool) |

Rules:
- Use real phrasing (Hinglish included), not invented tidy sentences.
- Redact names, numbers and OTPs. Screenshots must not show passwords, OTPs or banking balances.
- Include hard negatives: safe actions that look risky (a "Delete" on a draft) and risky ones that look safe.
- Keep `screens/` out of git if it holds personal screenshots (add to `.gitignore`).

Starter rows are in each file; extend them from real sessions and the conversation history.
