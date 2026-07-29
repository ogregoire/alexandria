# Working on Alexandria

## `data/` is the user's library. Never write to it.

`data/` is a real personal book catalogue and the git-committed source of truth. It is not
scratch space, not a fixture, not a place to put demo records. Deleting or seeding it
destroys someone's work.

**Always pass `--data` when running the app**, pointing somewhere disposable:

```sh
./alexandria --data examples/library --no-browser      # the sample catalogue
./alexandria --data /tmp/scratch-lib --no-browser      # a throwaway
./alexandria --data examples/library --site /tmp/out   # render a site
```

Bare `./alexandria` defaults to `data/` and is for the user to run, not you.

Rules that follow from this:

- Sample and demo records belong in `examples/library/`. Add new ones there.
- Never `rm -rf data` — not to reset it, not to regenerate it. If it genuinely has to move,
  `mv` it aside and say so.
- Automated tests use `@TempDir`. They must never touch `data/` or `examples/`.
- Before writing to any path that holds user content, check whose it is. When unsure, ask.

Same reasoning applies to `target/catalog.mv.db`: derived, disposable, gitignored. The JSON
files are the catalogue; H2 is a consequence of them.

## Build

Java 25 or newer, enforced against the JVM Maven itself runs on — the app is launched with
`exec:java`, so that JVM is the one it runs on.

```sh
./mvnw verify                    # 51 tests
./mvnw install && ./mvnw site    # two commands, never `install site` in one
```

The site is two invocations because the catalogue report comes from
`alexandria-maven-plugin`, built in this same reactor: it has to reach the local repository
before the site lifecycle can resolve it. `install site` would reach the parent's site phase
first and fail.

`maven-plugin-plugin` has a pinned ASM override (`asm.version` in the parent POM); its
bundled ASM cannot read Java 25 bytecode. Do not remove it.

## ISBN lookups

`infrastructure/lookup/` reaches the network. Two rules:

- **Tests never make a real request.** `src/test/resources/lookup/` holds payloads captured
  from the live services; `StubHttp` serves them. If a provider's shape changes, re-capture
  the fixture rather than mocking around it.
- **A lookup only prefills.** Nothing a provider returns may be written to the catalogue
  without passing through the review form, where the user corrects it — a lookup writes no
  files. This is what keeps the app a presenter of suggestions rather than a mirror of
  someone else's database, so do not add a code path that persists a `BookDraft` directly.
  A miss, a timeout and a `429` are all the same thing: an empty `Optional`, never an
  exception reaching the editor.
- **Name the provider on the form.** `BookDraft.source()` is shown to the user so a
  suggestion stays attributable to where it came from.

Use `--offline` when running the app for anything other than testing the lookup itself.

**Respect the rate limits.** Open Library documents 1 request/second, or 3/second for callers
sending a `User-Agent` with a contact address. `Throttle` reserves per-host slots and `Http`
waits for them, so every outbound call must go through `Http` — do not add a second HTTP
client. The faster interval is used only when a contact is actually configured
(`--contact` / `ALEXANDRIA_CONTACT`); never hardcode the identified tier.

A `429` or `503` backs the host off, honouring `Retry-After`, capped at 4s and 2 retries.
Keep it bounded: a lookup happens while someone is waiting at a form.

`BnfLookup` parses third-party XML, so its `DocumentBuilderFactory` has DTDs and external
entities disabled. Do not relax that.

## Design constraints

- **DDD with algebraic data types.** Anything with alternative shapes is a sealed interface
  of nested records, not an enum plus nullable fields. Enums only for closed, payload-free
  scales (`Condition`, `Carrier.EbookFormat`).
- **The domain has no framework imports.** Jackson lives in
  `infrastructure/json/Mixins.java` and nowhere else. Keep it that way.
- **Tests use AssertJ**, not plain JUnit assertions.
- **Do not use the superpowers skills** on this project.

One naming rule spans every layer: a sum-type variant is its record's simple name in
kebab-case. `MassMarket` is `mass-market` in the JSON file, in the H2 column and in the
editor's form. `VariantNames` is the single source of it.

## Model shape

Four aggregate roots. `Expression` is an entity inside `Work` — a translation of nothing is
meaningless. `Manifestation` is its own root because an omnibus embodies expressions of
several works and cannot nest under any one of them.

Credits carry both `agent` (who it was) and `publishedAs` (what the title page said), which
is how one person publishes under several names without either fact being lost. The
published name is stored rather than derived on purpose: renaming an agent must not rewrite
the byline of a book never issued under the new name.

## Committed JSON

Fixed key order, two-space indent, LF endings, sorted subject sets, absent optionals
omitted. Changing one field must produce a one-line diff, and saving twice must produce
none. If a change to serialisation breaks that, it is a bug.
