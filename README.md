# Alexandria

A personal book catalogue built on the FRBR/IFLA-LRM model, where the difference between a
*work*, a *translation*, an *edition* and *the copy on your shelf* is the whole point.

```
Work           Don Quixote                          Cervantes, 1605–1615
 └ Expression  English, translated by Grossman       the translation
    └ Manifestation  Ecco, 2003, hardcover, 940 pp.  the edition
       └ Item         living room shelf 3, read 2020-01-06, ★★★★★   your copy
```

Cervantes, Grossman and Ecco are not text on those records — they are references into an
agent registry that holds each of them once, with the aliases they answer to.

The catalogue is a directory of JSON files under `data/`, one file per aggregate. Those files
are the source of truth and they live in git, so the library's history is the repository's
history. H2 is rebuilt from them on every start and never committed.

## Using it

You need a JDK 25 or newer on `JAVA_HOME`. Nothing else — Maven comes with the wrapper.

```sh
./alexandria
```

That is `./mvnw … compile exec:java`, so Maven decides the JVM, works out what needs
recompiling and assembles the runtime classpath from the POM. An enforcer rule fails the
build immediately if Maven is running on anything older than the compiler release, because
that same JVM is the one the app runs on.

It starts a local editor on <http://127.0.0.1:4242> and opens a browser. Edit, and each save
rewrites one JSON file under `data/`. Then:

```sh
git add data && git commit -m "Read Der Prozess at last" && git push
```

GitHub Actions renders a browsable, searchable static site and publishes it to Pages.

Other options:

| Command | Effect |
| --- | --- |
| `./alexandria --port 8080` | bind another port |
| `./alexandria --no-browser` | don't open a browser |
| `./alexandria --site out/` | render the static site locally and exit |
| `./alexandria --data examples/library` | open the sample library instead of your own |
| `./alexandria --offline` | never contact an ISBN lookup service |
| `./mvnw verify` | run the tests |
| `./mvnw install && ./mvnw site` | build the full Maven site, catalogue included |

The site is two commands rather than one because the report comes from
`alexandria-maven-plugin`, built in this same reactor: it has to reach the local repository
before the site lifecycle can resolve it.

## Adding a book from its ISBN

**Add from ISBN** takes an ISBN-10 or ISBN-13 and fills in a form. It does not save anything:
third-party metadata is routinely wrong about precisely what this model cares about — which
name is on the title page, whether an edition is a translation, what the series number is —
so you check it and press save. One submission then creates the Work with its first
Expression, the Manifestation, and, if the checkbox is ticked, the Item that is your copy.

Three free services are tried in order, first hit wins:

| | Key | Licence | Notes |
| --- | --- | --- | --- |
| **Open Library** | none | CC0 | Models works and editions separately, the same split this catalogue does, so it can supply the original title *and* the edition title. Translators come from its `contributions` list |
| **BnF** | none | Licence Ouverte | Authoritative for French. Indexes the pre-2007 **ISBN-10**, so each lookup retries with the converted form — `9782246787051` misses, `2246787050` hits |
| **Google Books** | none | — | Last resort. Its quota is shared per-IP and unkeyed requests do get `429`, treated as an ordinary miss. It has no work-versus-edition distinction, so it cannot supply an original title or first publication year |

No provider's response is retained. It is rendered into the review form, you correct it, and
what reaches the catalogue is what you submitted — which is why a lookup writes no files at
all. The form names the provider it came from, so a suggestion is always attributable.

Lookups send only the ISBN you type, only when you press the button. `--offline` disables
them entirely and the form still works, just empty. To change the chain or drop a provider,
edit `ChainedLookup.standard()`.

### Rate limits

Open Library is the only one of the three that documents a timing rule: **one request per
second**, or three per second for callers whose `User-Agent` names the application and a
contact address. One ISBN costs three calls there — the edition, the resolved names, the work
— so a lookup is paced rather than fired all at once. Measured against the live service, an
unidentified lookup takes about 2.4 seconds, most of it waiting on purpose.

Identify yourself to get the faster tier:

```sh
./alexandria --contact you@example.org        # or set ALEXANDRIA_CONTACT
```

That brings the interval down from 1000 ms to 334 ms. Without it the User-Agent says so
plainly and the slower interval is used — claiming the faster tier without a real contact
would just be breaking the limit quietly.

The BnF publishes no limit for its SRU endpoint; two requests a second is a self-imposed
courtesy, which also paces the ISBN-13-then-ISBN-10 retry. Google documents a daily quota
rather than an interval and asks for exponential backoff on a `429`, which is what happens: a
`429` or `503` backs that host off, honouring `Retry-After` when sent, doubling otherwise,
capped at four seconds and two retries so the editor cannot stall.

## The model

Four aggregate roots. `Expression` is an entity inside `Work` — a translation of nothing is
meaningless, so it never exists on its own. `Manifestation` is its own root because one
volume can embody expressions of several works: an omnibus belongs to no single work and
cannot be nested under one.

| Aggregate | Holds | Refers to | File |
| --- | --- | --- | --- |
| `Agent` | preferred name, filing name, aliases | — | `data/agents/<id>.json` |
| `Work` | its `Expression`s | `AgentId`s, by role | `data/works/<id>.json` |
| `Manifestation` | imprint, carrier, extent | `ExpressionId`s, publisher `AgentId` | `data/manifestations/<id>.json` |
| `Item` | provenance, shelf, reading, condition | one `ManifestationId` | `data/items/<id>.json` |

## Agents

Everyone the catalogue names — authors, translators, narrators, illustrators and publishing
houses — is one `Agent` record, referenced by id. Roles live on the reference rather than on
the agent, so Willa Muir is one record whether she is writing or translating.

Each agent carries the aliases it answers to, which is what keeps "Penguin", "Penguin Books"
and "Penguin Classics" from becoming three publishers:

```json
{
  "id" : "penguin-books",
  "kind" : { "type" : "organisation" },
  "name" : "Penguin Books",
  "sortName" : "Penguin Books",
  "aliases" : [ "Penguin", "Penguin Classics" ]
}
```

When you add a book, the author and publisher fields complete against everything already
registered — preferred names and aliases alike — but still accept anything you type. A name
already on file resolves to that record; a name nobody is on file under creates one.
Matching folds case, accents and punctuation, so `J.R.R. Tolkien` finds `J. R. R. Tolkien`
rather than making a second Tolkien.

Two safeguards, because silently inventing people is how a registry rots:

- Agents invented while reading a form are **buffered, not saved**, until the whole
  aggregate parses. A form that fails validation halfway leaves nothing behind.
- Saving an agent is refused if another already answers to one of its names, naming the
  clash. The agents page shows a reference count per agent, so a zero is a typo you can see.

Deleting an agent that is still credited anywhere is refused, and the error lists what
refers to it.

### Pseudonyms

Identity and appearance are separate. A credit records *who* it was and *what the title page
said*, so one person can publish under several names without either fact being lost:

```json
"creators" : [ {
  "agent" : "robin-hobb",
  "role" : { "type" : "author" },
  "publishedAs" : "Megan Lindholm"
} ]
```

The book keeps the byline it was issued under. The agent reference gathers the whole output
regardless of how each one was signed. So an agent's page reads:

```
Robin Hobb                    person
also known as Megan Lindholm

as Robin Hobb
  Assassin's Apprentice       author · 1995
as Megan Lindholm — other name
  Wizard of the Pigeons       author · 1986
```

and searching the published site for either name returns both books, each under its own
byline. Type whichever name is on the book you are cataloguing: it resolves to the same
agent, and the name you typed is what the record keeps.

The published name is stored rather than derived, so renaming an agent cannot rewrite the
byline of a book that was never issued under the new name. The cost is one redundant string
per credit, guarded by an integrity check: a credit under a name the agent is no longer on
file for is reported, with the fix being to add it back as an alias.

Everything that can be in one of several states is a sealed interface rather than a nullable
field or a string, so the compiler enforces the cases:

```java
public sealed interface ExpressionKind {
    record Original() implements ExpressionKind {}
    record Translation(Language from) implements ExpressionKind {}
    record Revision(String label) implements ExpressionKind {}
    record Abridgement() implements ExpressionKind {}
    record Adaptation(String into) implements ExpressionKind {}
    record Narration() implements ExpressionKind {}
}
```

`BibliographicDate` is `Year | Exact | Circa | Between | Unknown`, because title pages are
genuinely that vague. `Acquisition` distinguishes `Borrowed` from the rest and answers
`owned()` from the variant, so "can I lend this out?" is a type question, not a flag.
`Location`, `ReadingProgress`, `Carrier`, `Identifier` and `Extent` work the same way.

One naming rule covers all of them: the record's simple name in kebab-case. `MassMarket` is
`mass-market` in the JSON file, in the H2 read model and in the editor's form — the same word
wherever you meet it.

## Layout

```
data/                     YOUR catalogue: agents, works, manifestations, items — starts empty
examples/library/         a sample catalogue to look at; never written to by default
app/                      be.imgn.alexandria:alexandria
  domain/                 WEMI model, sealed types, invariants — no framework imports
  application/            CatalogService: write JSON first, then rebuild the projection
  infrastructure/json/    the store; Jackson is confined here, via mix-ins
  infrastructure/h2/      the read model the reports query
  infrastructure/web/     the editor: JDK HttpServer, server-rendered forms, no framework
  site/                   the static site generator
alexandria-maven-plugin/  the `catalog` report, run by mvn site
```

Jackson never appears in `domain/`. Type discriminators and ignored derived accessors are
declared as mix-ins in `infrastructure/json/Mixins.java`, so the model stays free of
serialisation concerns.

## Why the files and not the database

The workflow is edit, commit, push. A binary H2 file would make every commit an opaque blob
with no diff and no merge. Text files give you `git log -p data/items/…` and a readable
history of what you read and when. The JSON is written with a fixed key order, two-space
indent, LF endings, sorted subject sets and absent optionals omitted, so changing one field
produces a one-line diff and saving twice produces no diff at all.

The editor binds to loopback only and has no authentication. It edits files in a git working
copy on your own machine; do not expose it.
