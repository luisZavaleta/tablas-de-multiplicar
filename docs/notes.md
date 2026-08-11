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
  counts, and current/best streak **per quiz mode** (`currentStreakMultipleChoice`,
  `bestStreakMultipleChoice`, `currentStreakTypeAnswer`, `bestStreakTypeAnswer`)
  — switching modes doesn't reset or share streak progress with the other
  mode. `getCurrentStreak(mode)` / `getBestStreak(mode)` read the right pair
  for a given `QuizMode`. `getLevel()` derives level from `totalScore`
  (`score / 100 + 1`, shared across both modes) rather than storing it, so
  the formula can change without a migration.
- `model/PendingQuestion.java` — plain record, **not** a JPA entity. Held
  in an in-memory `ConcurrentHashMap<UUID, PendingQuestion>` inside
  `QuizService` while a question is unanswered. This is intentionally
  non-persistent: if the backend restarts mid-question, that one question
  is lost, which is an acceptable tradeoff for a single-player kids' game.
  Also records which `QuizMode` the question was asked in, so
  `submitAnswer` updates the matching mode's streak — the client never
  tells the backend which mode it's answering in, avoiding a client that
  could otherwise claim the "easy" streak while typing free-form answers.
- `model/FactStat.java` — JPA entity tracking, per player, how many times a
  specific fact (`tableNumber × multiplier`) has been asked (`totalCount`)
  and answered correctly (`correctCount`). Drives the adaptive question
  selection described below. Not related to `PendingQuestion` — this one
  *is* persisted, since it needs to survive restarts.
- `model/QuizMode.java` — enum (`MULTIPLE_CHOICE`, `TYPE_ANSWER`) selecting
  how a question is answered; see [Answer modes](#answer-modes).
- `repository/PlayerRepository.java`, `repository/FactStatRepository.java`
  — plain Spring Data JPA repositories.
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
- `App.jsx` — owns all game state (player id/name, mode, score, level,
  a `streaks` object keyed by mode — `{ MULTIPLE_CHOICE, TYPE_ANSWER }` —
  so the displayed racha (`streaks[mode]`) always matches whichever mode is
  active, current question, feedback, error). Player id is generated by
  the backend on first name entry and cached in `localStorage`
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
  No sound or confetti libraries yet — deliberately kept dependency-free
  for now; CSS transitions only.
- `components/TableSidebar.jsx` — the tables 1-10 picker; see
  [Table selection](#table-selection).

Answer flow: answer (click an option, or type + Enter/submit) →
`POST /quiz/answer` → update score/level/streak from the response → show
feedback → after `NEXT_QUESTION_DELAY_MS` (1300ms), fetch the next question
automatically. No manual "next" button, to keep the loop fast for a kid.

## Table selection

A sidebar (`TableSidebar.jsx`, to the left of the game card on wide
screens, stacked above it below the `640px` breakpoint) lists tables 1-10
as toggleable chips. Multiple tables can be active at once — questions are
then drawn from the combined pool, still weighted by the adaptive selection
below. Backend already accepted an arbitrary comma-separated `tables` list
before this UI existed (`GET /quiz/question?tables=1,7,10`), and
`generateOptions`'s distractor math was already generic per-table, so this
was a **frontend-only** change — no backend code changed, only verified
(see below).

- `App.jsx` holds `selectedTables` (array of ints), persisted to
  `localStorage` (`tablas.selectedTables`) so the selection survives a
  reload, same pattern as `playerId`.
- At least one table must stay selected — `handleToggleTable` no-ops if
  you try to deselect the last remaining one, rather than letting the
  active set go empty (the backend would silently fall back to `[7]` if it
  ever received an empty list, but the UI prevents that case entirely).
- Toggling immediately loads a new question for the new table set, the
  same way switching [answer mode](#answer-modes) does.

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

**Streaks are independent per mode**, by explicit request — getting a
5-streak in Normal doesn't carry over to (or get reset by) Difícil. Both
current and best streak are tracked separately per mode on `Player`, and
the streak-bonus check (`currentStreak % 5 == 0`) uses whichever mode the
question being answered was actually asked in (see the `PendingQuestion`
note above). `totalScore`, `level`, `correctAnswers`/`totalAnswers`, and
the adaptive fact weighting are all still shared across modes — only the
streak is split.

Scoring is otherwise identical in both modes today (no difficulty bonus for
Difícil beyond it being harder) — that's a deliberate simplification, not
an oversight; revisit if it turns out kids want Difícil to be worth more.

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
| GET | `/api/quiz/stats/{playerId}` | — | `{name, totalScore, level, correctAnswers, totalAnswers, accuracy, currentStreakMultipleChoice, bestStreakMultipleChoice, currentStreakTypeAnswer, bestStreakTypeAnswer}` |

- `playerId` is required on `GET /quiz/question` (needed for weighting, see
  above) — a missing or unknown one returns 400.
- `tables` accepts a comma-separated list (e.g. `tables=7,3,5`); the
  frontend sends whatever's checked in the [table sidebar](#table-selection)
  (`selectedTables` in `App.jsx`, defaults to `[7]` for a new player).
- `mode` is `MULTIPLE_CHOICE` (default) or `TYPE_ANSWER`; an unrecognized
  value returns 400. `options` is `[]` when `mode=TYPE_ANSWER`.
- `POST /quiz/answer`'s `streak` in the response is for whichever mode the
  answered question was originally asked in — there's no separate `mode`
  field on this request; the backend already knows from the stored
  `PendingQuestion`.

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
  edges (table 1 and table 10, where distractor math has the least room);
  a mixed-tables draw (`tables=1,7,10`) confirmed only those three appear.
  Then a full browser pass: toggling chips on/off, multi-select drawing
  from the combined pool, and the "can't deselect the last one" guard.
- Per-mode streaks: scripted probe answering 3 in a row correctly in
  `MULTIPLE_CHOICE` then 1 in `TYPE_ANSWER`, confirming the second mode's
  streak starts at 1 (not 4) and `stats` reports both independently; also
  confirmed in-browser that switching modes shows that mode's own streak.

## Gotchas worth knowing about

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
