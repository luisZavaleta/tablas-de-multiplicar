# Tablas de Multiplicar

[![GitHub repo](https://img.shields.io/badge/GitHub-tablas--de--multiplicar-181717?logo=github)](https://github.com/luisZavaleta/tablas-de-multiplicar)

A multiplication-tables practice game built for a 7-year-old: a sidebar to
pick any combination of tables 1-10, points, streaks, levels, two answer
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

- The sidebar lists tables 1-10 as toggleable chips — any combination can
  be active at once (at least one always stays selected), and questions
  are drawn from that combined pool, weighted toward facts the player
  hasn't answered correctly as many times yet.
- Two answer modes, switchable anytime from the toggle at the top of the
  game screen: **Normal** (4 multiple-choice options) and **Difícil**
  (type the answer, no options shown). Each mode has its own independent
  streak — a streak built in Normal doesn't carry over to (or get reset
  by) Difícil, and vice versa.
- Correct answer: +10 points, streak (for the active mode) increments;
  every 5th answer in a row adds a +5 bonus.
- Wrong answer: streak (for the active mode) resets to 0, no point penalty.
- Level = total points / 100 (+1).
- Progress (points, best streak, level, and per-fact accuracy) is saved
  per player and persists across sessions.

## API (backend)

- `POST /api/player` `{name}` → creates a player, returns `playerId`.
- `GET /api/quiz/question?playerId=&tables=7&mode=MULTIPLE_CHOICE` → next
  question; `options` is populated for `MULTIPLE_CHOICE`, empty for
  `TYPE_ANSWER`.
- `POST /api/quiz/answer` `{playerId, questionId, answer}` → validates the
  answer, returns updated points/streak/level.
- `GET /api/quiz/stats/{playerId}` → the player's accumulated stats.

