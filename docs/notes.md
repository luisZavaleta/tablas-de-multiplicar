# Development notes

## Goal and requirements

Built for a 7-year-old to practice multiplication tables ("tablas de
multiplicar"), starting with only the table of 7. Confirmed requirements:

- Web app first; a later phase will wrap it as an Android app (not done
  yet — see [Android conversion](#android-conversion-not-yet-done) below).
- Game-style UI (points, streak, levels), not plain flashcards.
- Backend must be Java — the project owner is a Java developer and wants to
  maintain/extend that side himself.
- Frontend technology was left to Claude's discretion; React + Vite was
  chosen specifically because it's the stack most compatible with wrapping
  in Capacitor later (see below).
- Started with only the table of 7 active; a sidebar was later added to
  select any combination of tables 1-10 (see
  [Table selection](#table-selection)) — the data model and API were
  designed from the start so this was a frontend-only change.

## Architecture

```
tablas/
├── backend/   Spring Boot 3.3.4, Java 21, Maven
└── frontend/  React 19 + Vite 8 (JavaScript, no TypeScript)
```

Two independent processes in development: backend on `:8080`, frontend on
`:5173`. Vite proxies `/api/*` to the backend (configured in
`frontend/vite.config.js`) so the browser never needs CORS for local dev;
a `WebConfig` CORS mapping (`backend/src/main/java/com/tablas/backend/config/WebConfig.java`)
also allows `http://localhost:5173` directly, in case the proxy is bypassed.

**Why game logic lives on the backend**: question generation, multiple-choice
distractor generation, and scoring are all done server-side
(`QuizService.java`). The client never receives or computes the correct
answer client-side before submitting — it's validated against an in-memory
`PendingQuestion` keyed by a generated `questionId`. This was a deliberate
choice to keep the Java side meaningful (per the project owner's Java
background) and to make the client trivially portable to other frontends
later without duplicating game rules.

### Backend

Package `com.tablas.backend`:

- `model/Player.java` — JPA entity: name, total score, correct/total answer
  counts. `getLevel()` derives level from `totalScore` (`score / 100 + 1`)
  rather than storing it, so the formula can change without a migration.
  Used to also hold per-mode streak fields directly; those moved out to
  `StreakStat` (below) once streaks needed to be scoped per table too, not
  just per mode — hardcoding fields for every `table × mode` combination on
  `Player` itself wasn't going to scale past 2 modes.
- `model/StreakStat.java` — JPA entity: one row per
  `(playerId, tableNumber, mode)`, holding that specific combination's
  `currentStreak`/`bestStreak`. This is what makes racha independent per
  table *and* per mode — table 7 in Normal, table 3 in Normal, and table 7
  in Difícil are three separate rows, none of which affect each other. Rows
  are created lazily on first answer for a given combination (an
  unpracticed combo just isn't in the table yet, and reads as 0/0 — see
  `QuizService.getStreak`).
- `model/PendingQuestion.java` — plain record, **not** a JPA entity. Held
  in an in-memory `ConcurrentHashMap<UUID, PendingQuestion>` inside
  `QuizService` while a question is unanswered. This is intentionally
  non-persistent: if the backend restarts mid-question, that one question
  is lost, which is an acceptable tradeoff for a single-player kids' game.
  Also records which `QuizMode` the question was asked in (its `factorA`
  already *is* the table) so `submitAnswer` updates the matching
  `StreakStat` row — the client never tells the backend which mode/table
  it's answering in, avoiding a client that could otherwise claim credit
  for the wrong streak.
- `model/FactStat.java` — JPA entity tracking, per player, how many times a
  specific fact (`tableNumber × multiplier`) has been asked (`totalCount`)
  and answered correctly (`correctCount`). Drives the adaptive question
  selection described below. Not related to `PendingQuestion` — this one
  *is* persisted, since it needs to survive restarts.
- `model/QuizMode.java` — enum (`MULTIPLE_CHOICE`, `TYPE_ANSWER`) selecting
  how a question is answered; see [Answer modes](#answer-modes).
- `repository/PlayerRepository.java`, `repository/FactStatRepository.java`,
  `repository/StreakStatRepository.java` — plain Spring Data JPA
  repositories.
- `service/QuizService.java` — question selection/distractor generation,
  scoring rules (see [Game rules](#game-rules)).
- `controller/QuizController.java` — REST endpoints (see
  [API reference](#api-reference)).
- `controller/GlobalExceptionHandler.java` — maps `IllegalArgumentException`
  (unknown player, already-answered/expired question) to HTTP 400.
- `dto/*` — Java records for request/response shapes, kept separate from
  the `Player` entity so the API contract doesn't leak JPA internals.

Persistence: H2 in file mode (`backend/data/tablas.mv.db`,
`AUTO_SERVER=TRUE` so the H2 console can connect concurrently with the
running app). `spring.jpa.hibernate.ddl-auto=update` — fine for this
project's scale, would need real migrations (Flyway/Liquibase) if the
schema outgrows this.

### Frontend

`frontend/src/`:

- `api.js` — thin `fetch` wrapper for the backend endpoints.
- `App.jsx` — owns all game state (player id/name, mode, selected table,
  score, level, a single `streak` number for whichever table+mode
  combination is currently active, current question, feedback, error).
  `streak` is refetched from `GET /quiz/streak` (via `loadStreak`) on
  mount and on every table/mode change — it's not derived client-side,
  since the true value lives on the backend per combination. Player id is
  generated by the backend on first name entry and cached in `localStorage`
  (`tablas.playerId`, `tablas.playerName`) so returning to the page skips
  the name screen and reloads saved progress via `GET /quiz/stats`. Also
  handles falling back to the name screen if the stored `playerId` turns
  out to be invalid (see [Gotchas](#gotchas-worth-knowing-about)).
- `components/QuestionCard.jsx` — renders the question, then either the 4
  multiple-choice buttons or a type-in-the-answer form depending on
  `mode` (see [Answer modes](#answer-modes)). Receives `key={question.questionId}`
  from `App.jsx` so it remounts (and clears any typed input) on every new
  question, instead of manually resetting internal state.
- `components/ScoreBar.jsx` — level/points/streak badges + a progress bar
  toward the next level.
- `components/Feedback.jsx` — correct/wrong message shown after answering.
  No confetti library — deliberately kept dependency-free; CSS transitions
  only.
- `components/TableSidebar.jsx` — the tables 1-10 picker; see
  [Table selection](#table-selection).
- `sound.js` — correct/wrong sound effects; see [Sound effects](#sound-effects).

Answer flow: answer (click an option, or type + Enter/submit) →
`POST /quiz/answer` → update score/level/streak from the response → play
a correct/wrong sound → show feedback → after `NEXT_QUESTION_DELAY_MS`
(1300ms), fetch the next question automatically. No manual "next" button,
to keep the loop fast for a kid.

## Sound effects

`sound.js` plays a short chime via the Web Audio API directly — no audio
files, no library, in keeping with the project's "stay dependency-free
where reasonable" pattern (see `Feedback.jsx` above). A correct answer
plays an ascending three-note major chime (C5-E5-G5, triangle wave); a
wrong answer plays a soft two-note descending dip (sine wave) — deliberately
gentle, not a harsh buzzer, since this is for a 7-year-old.

Browsers only allow starting/resuming an `AudioContext` from inside a
user-gesture call stack (a click handler, not an arbitrary async
callback). `unlockAudio()` creates (or resumes, if suspended) a single
shared `AudioContext` and must be called synchronously at the top of a
click handler, before any `await` — `App.jsx` calls it at the start of
both `handleNameSubmit` (first-time players) and `handleAnswer` (returning
players, who skip the name screen entirely). Once unlocked, the actual
`playCorrectSound()`/`playWrongSound()` calls later in an async function
work fine even though they happen after an `await`, because the context
itself was already running.

## Table selection

A sidebar (`TableSidebar.jsx`, to the left of the game card on wide
screens, stacked above it below the `640px` breakpoint) lists tables 1-10
as chips; exactly one is active at a time (single-select, like a radio
group — picking a new one deselects whichever was active). Backend already
accepted an arbitrary comma-separated `tables` list before this UI existed
(`GET /quiz/question?tables=7`), and `generateOptions`'s distractor math
was already generic per-table, so this was a **frontend-only** change — no
backend code changed, only verified (see below). The backend endpoint
still technically accepts multiple comma-separated tables if called
directly; the frontend just never sends more than one.

- `App.jsx` holds `selectedTable` (a single int), persisted to
  `localStorage` (`tablas.selectedTable`) so the selection survives a
  reload, same pattern as `playerId`.
- `handleSelectTable` no-ops if you click the already-active table;
  otherwise it swaps the selection and immediately loads a new question
  for it, the same way switching [answer mode](#answer-modes) does.

Was multi-select (checkboxes, several tables mixed together) in an earlier
version of this feature; changed to single-select by request. If mixed
practice across tables is wanted again later, the backend needs no changes
— `loadNextQuestion` would just need to send more than one table. Streak
attribution (see [Streaks](#streaks)) already works correctly for that
case too, since each individual question still carries its own specific
table (`factorA`), regardless of how many tables were eligible to be
drawn from.

## Answer modes

Two modes, switchable anytime via the toggle at the top of the game screen
(does not require restarting the game or losing progress):

- **Normal** (`MULTIPLE_CHOICE`, default) — 4 answer buttons, as originally
  built.
- **Difícil** (`TYPE_ANSWER`) — no options at all; the child types the
  number and submits (button or Enter key). The backend doesn't even
  generate distractor options for this mode (`QuestionResponse.options` is
  `[]`) — kept server-side rather than just hiding options in the UI, in
  keeping with the "game logic stays on the backend" rule above.

Switching modes immediately requests a new question in the new mode; the
in-flight question from the old mode is simply abandoned (its
`PendingQuestion` entry sits unanswered in memory until the process
restarts — harmless, see the note on `PendingQuestion` above).

**Streaks are independent per (table, mode) combination**, by explicit
request — see [Streaks](#streaks) below. `totalScore`, `level`,
`correctAnswers`/`totalAnswers`, and the adaptive fact weighting are all
still shared across every table and mode — only the streak is split this
finely.

Scoring is otherwise identical in both modes today (no difficulty bonus for
Difícil beyond it being harder) — that's a deliberate simplification, not
an oversight; revisit if it turns out kids want Difícil to be worth more.

## Streaks

Racha is scoped to one specific `(table, mode)` combination, not shared
globally and not shared across modes either — this went through two
iterations by request: first split per mode only, then split per table
*and* mode once the [table sidebar](#table-selection) existed. Getting a
streak of 5 on table 7 in Normal doesn't carry over to (or get reset by)
table 3 in Normal, or table 7 in Difícil — each of the up-to-20
combinations (10 tables × 2 modes) tracks its own `currentStreak` and
`bestStreak`, via `StreakStat` (see above).

- `QuizService.submitAnswer` looks up (or lazily creates) the `StreakStat`
  row for `(playerId, question.factorA(), question.mode())` — recall
  `PendingQuestion.factorA()` **is** the table — increments or resets
  `currentStreak` on it, and the streak-bonus check
  (`currentStreak % 5 == 0`) reads from that same row. `POST /quiz/answer`
  returns that row's `currentStreak` as `streak` in the response.
- `GET /quiz/streak?playerId=&table=&mode=` (`QuizService.getStreak`) is a
  read-only lookup for one specific combination, returning `0`/`0` if that
  combination has never been played (no row created just by reading).
  `App.jsx` calls this via `loadStreak` on mount and whenever the selected
  table or mode changes, so the displayed racha always reflects that
  combination's real persisted value instead of resetting to 0 or leaking
  a different combination's count.
- `StatsResponse` no longer carries any streak fields — with up to 20
  combinations per player, a growing list didn't belong in a flat "overall
  stats" DTO, and the frontend only ever needs *one* combination's streak
  at a time (whichever is currently selected), which is what the dedicated
  endpoint is for.

## Adaptive question selection

Question selection is weighted per player, not uniform-random, so facts a
player keeps getting right show up less often and facts they haven't
mastered show up more:

- `FactStat` tracks `correctCount` per `(playerId, table, multiplier)`.
- `QuizService.pickWeightedFact` assigns each candidate fact a weight of
  `1 / (correctCount + 1)` and does a weighted random draw. A fact never
  answered correctly has weight `1` (highest); a fact answered correctly 5
  times has weight `1/6` — lower, but never zero, so mastered facts still
  resurface occasionally instead of disappearing entirely.
- `correctCount` (and `totalCount`) update on every `POST /quiz/answer`,
  regardless of which mode was used to answer.
- This is why `GET /quiz/question` requires `playerId` now — the weighting
  is meaningless without knowing whose history to weight against.

## Game rules

Defined in `QuizService.java`, easy to tune:

| Rule | Value |
|---|---|
| Points per correct answer | 10 |
| Streak bonus | +5, every 5th consecutive correct answer |
| Wrong answer penalty | none — streak resets to 0, no point loss |
| Level formula | `totalScore / 100 + 1` |
| Answer options (Normal mode only) | 4 total: 1 correct + up to 3 distractors, generated as `correctAnswer ± (1..3) × table`, deduplicated and shuffled |
| Question selection | weighted toward facts with a lower `correctCount` for that player — see [Adaptive question selection](#adaptive-question-selection) |

## API reference

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/player` | `{name}` | `{playerId, name}` |
| GET | `/api/quiz/question?playerId=&tables=7&mode=MULTIPLE_CHOICE` | — | `{questionId, factorA, factorB, options[]}` |
| POST | `/api/quiz/answer` | `{playerId, questionId, answer}` | `{correct, correctAnswer, pointsEarned, totalScore, streak, level}` |
| GET | `/api/quiz/stats/{playerId}` | — | `{name, totalScore, level, correctAnswers, totalAnswers, accuracy}` |
| GET | `/api/quiz/streak?playerId=&table=&mode=` | — | `{table, mode, currentStreak, bestStreak}` |

- `playerId` is required on `GET /quiz/question` (needed for weighting, see
  above) — a missing or unknown one returns 400.
- `tables` accepts a comma-separated list (e.g. `tables=7,3,5`); the
  frontend sends whatever's picked in the [table sidebar](#table-selection)
  (`selectedTable` in `App.jsx`, defaults to `7` for a new player) as a
  single-element list.
- `mode` is `MULTIPLE_CHOICE` (default) or `TYPE_ANSWER`; an unrecognized
  value returns 400. `options` is `[]` when `mode=TYPE_ANSWER`.
- `POST /quiz/answer`'s `streak` in the response is for whichever
  `(table, mode)` combination the answered question was originally asked
  in — see [Streaks](#streaks). There's no separate `table`/`mode` field on
  this request; the backend already knows from the stored
  `PendingQuestion`.
- `GET /quiz/streak` is a read-only lookup, doesn't require a valid
  `questionId` or affect any state — safe to call anytime to check a
  combination's current/best streak.

## Verification performed

- `mvn compile` — backend compiles cleanly.
- `npm run build` — frontend production build succeeds.
- Manual end-to-end pass via `curl`: create player → fetch question →
  submit correct answer → confirm score/streak/level response → fetch
  stats → confirm a re-used `questionId` is rejected with 400 (prevents
  answering the same question twice).
- Manual browser pass (Chrome, via browser automation): name entry screen,
  correct-answer feedback and score/streak update, wrong-answer feedback
  and streak reset, auto-advance to the next question, and persistence of
  score/level across a full page reload (stats reloaded from the backend
  on mount using the cached `playerId`).
- Adaptive selection: scripted probe that repeatedly answers one specific
  fact correctly while answering all others wrong, confirming that fact's
  draw frequency trends down as its `correctCount` climbs.
- Answer modes: verified `GET /quiz/question` returns populated `options`
  for `MULTIPLE_CHOICE` and `options: []` for `TYPE_ANSWER` via curl, then
  a full browser pass of Difícil mode — typing a correct answer (Enter key
  and submit button both work), typing a wrong answer, and switching modes
  mid-session without losing score/streak/level.
- Table selection: scripted curl pass generating 5 questions per table
  (1-10) validating `factorA` matches, the correct answer is among exactly
  4 unique positive options, confirming `generateOptions` holds up at both
  edges (table 1 and table 10, where distractor math has the least room).
  Then a browser pass confirming picking a new table swaps out (not adds
  to) the active one, and the selection persists across a reload.
- Per-(table, mode) streaks: scripted curl probe — 3 correct on
  `(table=7, MULTIPLE_CHOICE)`, then 1 correct on `(table=3, MULTIPLE_CHOICE)`
  and 1 correct on `(table=7, TYPE_ANSWER)` — confirmed each new
  combination starts its own streak at 1 rather than continuing the first
  one, `GET /quiz/streak` reports each combination independently, and an
  unplayed combination (`table=5, MULTIPLE_CHOICE`) reads `0`/`0`. Then a
  full browser pass on the real player: built a streak on table 4,
  switched to table 1 and confirmed racha showed 0 (not table 4's value),
  switched back to table 4 and confirmed its streak was still there
  exactly as left.
- Sound effects: `npm run build` succeeds with the new module; exercised
  `unlockAudio`/`playCorrectSound`/`playWrongSound` directly in-browser
  with no thrown errors, then confirmed the real click flow (answering a
  question live, both correct and via a returning-player session that
  skips the name screen) hits `unlockAudio()` before the first `await` in
  both entry points with no console errors.

## Gotchas worth knowing about

**Moving streaks from per-mode to per-(table, mode) reset existing streak
counts, on purpose — not a bug.** The old `Player.currentStreakMultipleChoice`
etc. columns were dropped rather than migrated into `StreakStat` rows.
Unlike the earlier per-mode migration (which backfilled cleanly because
"all past play was in one mode"), there was no honest way to backfill this
one: the old per-mode streak was an aggregate across *whatever tables had
been played in that mode*, with no record of which table each point of the
streak came from — attributing all of it to any single table (say, table
7) would have fabricated a more specific number than the data actually
supported. Score/level/accuracy were untouched; only the streak counters
reset to 0 for every combination, which self-heals within a few correct
answers anyway.

**React StrictMode double-fires the mount effect in dev.** `StrictMode`
(enabled in `frontend/src/main.jsx`, the Vite React template default)
intentionally double-invokes effects on mount in development only. This
means `App.jsx`'s mount effect fires `fetchStats` and `fetchQuestion` twice
on first load in `npm run dev` — harmless (both are idempotent GETs; the
second question just replaces the first one in state) but visible if you
inspect network requests. This does not happen in a production build
(`npm run build`), and it does not affect scoring since `POST /quiz/answer`
is only ever triggered by a real click.

**Stop the backend gracefully, or you can lose data.** H2's file-mode
MVStore needs a clean JVM shutdown to flush pending writes — `kill <pid>`
(SIGTERM) on the actual `java` process and waiting for it to exit is safe;
`mvn spring-boot:run`'s data was confirmed to survive that. During
development, killing it via `pkill -f <pattern>` against the Maven wrapper
process instead of the JVM directly caused a restart with an emptied
`PLAYER` table (schema intact, rows gone) — almost certainly because the
match killed things out of order and the JVM never got to check-point. If
the backend needs restarting, find the actual Java process
(`pgrep -f com.tablas.backend.TablasApplication`) and `kill` that PID
specifically, then wait for it to exit before starting a new one.

**`ddl-auto=update` can silently fail to add `NOT NULL` columns to a
non-empty table, without failing startup.** When the per-mode streak
columns were added to `Player`, Hibernate generated
`ALTER TABLE player ADD COLUMN best_streak_multiple_choice INTEGER NOT NULL`
(no `DEFAULT`) against a `PLAYER` table that already had rows. H2 rejected
it (existing rows can't get a non-null value out of nowhere), but Hibernate
only logs a `WARN` for a failed schema-update statement instead of aborting
startup — so the app came up looking healthy while `POST /api/player` then
threw 500s on every insert, because the entity expected columns the table
didn't actually have. Fixed by hand: connect with the H2 CLI Shell
(`java -cp <h2.jar> org.h2.tools.Shell -url "jdbc:h2:file:<path>;AUTO_SERVER=TRUE" -user sa -password ""`,
works fine alongside a running app in `AUTO_SERVER` mode), `ALTER TABLE
... ADD COLUMN ... DEFAULT 0` explicitly, backfill from the old columns,
then drop the old ones. **Lesson**: any schema change that adds a `NOT
NULL` column needs a `DEFAULT`, or needs applying by hand like this, once
there's real data in the table — `ddl-auto=update` alone isn't enough past
that point. This project doesn't use Flyway/Liquibase (see the Persistence
note above); if schema changes keep happening, that's the point to add one.

## Android conversion (not yet done)

Deferred by design — the plan is: build `frontend/dist` and wrap it with
Capacitor once the web version is validated. The Java backend would keep
running as a separate server reachable over the network (or on `localhost`
if run on-device, if that path is chosen later); that decision hasn't been
made yet and isn't blocking the current web app.
