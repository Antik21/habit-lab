# Routing and documentation governance

<!-- fact-owner: documentation-governance -->
<!-- canonical-signature: documentation-governance-v1 -->

`AGENTS.md` is the request router. Select one row before reading policy; a route must link to one through three Markdown documents. Owner documents define policy, while `09-common-cases.md` gives short procedures that link back to owners instead of restating them.

## Required set

Current policy is split into numbered files `00`–`09` under this directory. ADR governance lives in `docs/adr/README.md`; historical `docs/navigation`, `docs/data`, and `docs/spikes` records remain evidence for completed tasks and are not current policy owners.

`./gradlew checkDocumentation` validates:

- local links and Markdown heading anchors in `AGENTS.md`, `README.md`, `.agents/**/*.md`, and `docs/**/*.md` while ignoring fenced code and web/mail links;
- repository-root containment for local targets;
- the required routed files and required route keys;
- one to three local documents per route;
- per-file word limits from [`docs/document-budgets.tsv`](../../docs/document-budgets.tsv);
- exact marker and canonical-signature ownership from the three-column [`docs/fact-owners.tsv`](../../docs/fact-owners.tsv): each signature must occur once in its declared owner and nowhere else in governed Markdown.

Diagnostics use stable `path:line: message` output. The scanner supports inline and reference-style Markdown links without becoming a full Markdown renderer. Exact signatures catch structural duplication; review remains responsible for semantic paraphrases and conflicts expressed with different wording.

## Split policy

Every checked Markdown file has an explicit budget. Word counting covers the complete Markdown source, including fenced-code content; punctuation-only fence markers naturally add no words. Defaults for active documentation are: router 350 words, README 450, policy owner 700, recipes 650, Compose rule 900, and ADR policy/template 700. Historical evidence gets a larger explicit per-file allowance. When a file approaches its budget, split by responsibility, assign the new fact owner in the registry, add a precise router link only when a request needs it, and avoid duplicating the old owner's statements.

When behavior changes, update production code, its owner document, relevant verification, and the router only if request selection changed. A compatible route or implementation within current policy does not need an ADR; every change listed by the [ADR policy](../../docs/adr/README.md) requires one.
