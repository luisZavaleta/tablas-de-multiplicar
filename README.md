# Tablas de Multiplicar

[![GitHub repo](https://img.shields.io/badge/GitHub-tablas--de--multiplicar-181717?logo=github)](https://github.com/luisZavaleta/tablas-de-multiplicar)

A multiplication-tables practice game built for a 7-year-old: a sidebar to
pick which table (1-10) to practice, points, streaks, levels, two answer
modes (multiple-choice or type-the-answer), and questions that adapt to
which facts the player hasn't mastered yet. The app's UI text is in Spanish
(the target user is a Spanish speaker); code and documentation are in
English.

- `backend/` — Java 21 + Spring Boot (REST API + H2-backed persistence).
- `frontend/` — React + Vite (game UI).

See [`docs/notes.md`](docs/notes.md) for architecture decisions, API
reference, and dev notes.

Intended to be packaged as an Android app later via
[Capacitor](https://capacitorjs.com/), wrapping the `frontend/` build.

## Running locally

You need two terminals — backend and frontend run as separate processes.

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

Starts on `http://localhost:8080`. Progress data is persisted to
`backend/data/tablas.mv.db` (H2, created automatically on first run).

### 2. Frontend

```bash
cd frontend
npm install   # first time only
npm run dev
```

Starts on `http://localhost:5173` and calls the backend through a dev
proxy (`/api/*` → `http://localhost:8080`), configured in `vite.config.js`.

Open `http://localhost:5173` in a browser, enter a name, and play.

## How the game works

- The sidebar lists tables 1-10 as chips; picking one selects it and
  deselects the previous one — only one table is practiced at a time.
  Within that table, questions are weighted toward facts the player hasn't
  answered correctly as many times yet.
- Two answer modes, switchable anytime from the toggle at the top of the
  game screen: **Normal** (4 multiple-choice options) and **Difícil**
  (type the answer, no options shown).
- Racha (streak) is independent per table *and* per mode — table 7 in
  Normal, table 3 in Normal, and table 7 in Difícil each keep their own
  streak, none of which affect the others.
- Correct answer: +10 points, streak (for the active table+mode) increments;
  every 5th answer in a row adds a +5 bonus.
- Wrong answer: streak (for the active table+mode) resets to 0, no point
  penalty.
- Level = total points / 100 (+1).
- Progress (points, level, per-fact accuracy, and every table+mode streak)
  is saved per player and persists across sessions.

## API (backend)

- `POST /api/player` `{name}` → creates a player, returns `playerId`.
- `GET /api/quiz/question?playerId=&tables=7&mode=MULTIPLE_CHOICE` → next
  question; `options` is populated for `MULTIPLE_CHOICE`, empty for
  `TYPE_ANSWER`.
- `POST /api/quiz/answer` `{playerId, questionId, answer}` → validates the
  answer, returns updated points/streak/level.
- `GET /api/quiz/stats/{playerId}` → the player's accumulated stats.
- `GET /api/quiz/streak?playerId=&table=&mode=` → current/best streak for
  one specific table+mode combination.

