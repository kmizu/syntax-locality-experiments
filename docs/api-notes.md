# API notes

Documentation review date: **2026-10-05**. These notes describe the intended/implemented API contract; they do not establish access for this project or verify its remaining allowance.

## Model and request parameters

Initial requested model: `gpt-5.6-terra`, with Responses support and a requested reasoning effort of `low`. Planning fixes the resolved model. Unsupported model/parameter/authentication/project responses are recorded without automatic substitution. Preserve the returned model, response ID, request ID, timestamps, and raw body; an alias does not fully pin a future backend.

The client uses JDK `java.net.http.HttpClient`, with no OpenAI SDK. Generation targets `POST https://api.openai.com/v1/responses`; input counting targets `POST /v1/responses/input_tokens`. Serialize model, instructions, ordered text messages, reasoning effort, and `max_output_tokens`. Explicitly use `store=false` and `truncation="disabled"`; do not silently drop an unsupported parameter or retry a different request shape. Do not treat token counting as a local/free computation.

Decode every output item, including reasoning and refusal items, rather than assuming `output[0]` contains text. Preserve completed/incomplete/refusal states and usage: input/output/total, cached-input/cache-write when provided, and reasoning-output tokens when provided. Reasoning tokens are within the output budget; output minus reasoning is an estimate only when both fields are available. A usage-unknown failed attempt retains its reservation.

Optional authentication routing headers are `OpenAI-Project` and `OpenAI-Organization`; credentials never belong in prompts, hashes, or artifacts. Safe redaction removes only secrets and records that masking occurred. Rate-limit retry honors documented retriable failures and backoff/Retry-After; authentication, quota/billing rejection, and unsupported parameters are not blanket retry candidates.

## Capability probe record

Current status: **fresh integrated-source live preflight verified** at `2026-10-05T17:55:40.117919Z`. Saved evidence: `runs/preflight-terra-v2/capabilities.json`, `request.json`, the separate `count-attempt-001-request.json`, and both raw response bodies/metadata. Requested/returned model `gpt-5.6-terra`; effort low; reading output cap8,192; count and generation both succeeded. Two HTTP requests, 29 known tokens, zero unresolved reservations. Generation response ID `resp_07ed45944b54253f016ac3e495ed4087d0a17e22a8fca5ccdd`. Shared canonical request serialization is used for persistence and transport; fresh loopback byte checks passed in the required full sbt test. No syntax-effect inference follows from this probe.

The earlier `runs/preflight-terra` probe succeeded at `2026-10-05T15:41:48Z` with the same model/effort/cap, two HTTP requests, 29 known tokens, and zero unresolved reservations. It remains source-v1 evidence: original wire text was not captured, although the complete normalized logical request was saved. Do not relabel it as a v2 capture or pool its observations with main.

Preflight checks a small count request and one generation with exactly the requested model/settings. Save supported/unsupported parameters, requested/returned model, status/schema observations, request/response IDs, actual usage, and the date. Freeze requires successful matching count and generation capabilities. A failed probe blocks larger live runs; it does not demonstrate a syntax effect.

Shared-data eligibility and account limits are separate from local accounting. Do not assume the user's tier, sharing-project settings, other-application usage, daily remaining allowance, or zero billing. A persisted UTC-date token ledger is scoped to one run directory and is a local safety bound, not an organization-wide quota monitor. Preflight uses its own directory; all initialized preflight directories are refused on reuse so interruption cannot reset that probe's ledger or overwrite its attempts. Allocate allowances across directories explicitly.

## Primary documentation

- [GPT-5.6 Terra model](https://developers.openai.com/api/docs/models/gpt-5.6-terra)
- [Create a response](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)
- [Counting tokens](https://developers.openai.com/api/docs/guides/token-counting)
- [API overview: authentication and request IDs](https://developers.openai.com/api/reference/overview)
- [Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
- [Sharing feedback, evaluation/fine-tuning data, and API inputs/outputs](https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai)
- [JDK 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)
