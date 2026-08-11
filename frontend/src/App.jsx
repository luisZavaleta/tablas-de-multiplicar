import { useEffect, useState } from 'react'
import { createPlayer, fetchQuestion, fetchStats, submitAnswer } from './api'
import QuestionCard from './components/QuestionCard'
import ScoreBar from './components/ScoreBar'
import Feedback from './components/Feedback'
import TableSidebar from './components/TableSidebar'

const DEFAULT_TABLES = [7]
const NEXT_QUESTION_DELAY_MS = 1300

const QUIZ_MODES = {
  MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
  TYPE_ANSWER: 'TYPE_ANSWER',
}

export default function App() {
  const [playerId, setPlayerId] = useState(() => localStorage.getItem('tablas.playerId'))
  const [playerName, setPlayerName] = useState(() => localStorage.getItem('tablas.playerName') || '')
  const [nameInput, setNameInput] = useState('')
  const [mode, setMode] = useState(QUIZ_MODES.MULTIPLE_CHOICE)
  const [selectedTables, setSelectedTables] = useState(() => {
    const saved = localStorage.getItem('tablas.selectedTables')
    if (!saved) return DEFAULT_TABLES
    try {
      const parsed = JSON.parse(saved)
      return Array.isArray(parsed) && parsed.length > 0 ? parsed : DEFAULT_TABLES
    } catch {
      return DEFAULT_TABLES
    }
  })
  const [score, setScore] = useState(0)
  const [level, setLevel] = useState(1)
  const [streaks, setStreaks] = useState({ MULTIPLE_CHOICE: 0, TYPE_ANSWER: 0 })
  const [question, setQuestion] = useState(null)
  const [feedback, setFeedback] = useState(null)
  const [answering, setAnswering] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!playerId) return
    fetchStats(playerId)
      .then((stats) => {
        setScore(stats.totalScore)
        setLevel(stats.level)
        setStreaks({
          MULTIPLE_CHOICE: stats.currentStreakMultipleChoice,
          TYPE_ANSWER: stats.currentStreakTypeAnswer,
        })
      })
      .catch(() => forgetPlayer())
    loadNextQuestion()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playerId])

  function forgetPlayer() {
    localStorage.removeItem('tablas.playerId')
    localStorage.removeItem('tablas.playerName')
    setPlayerId(null)
    setQuestion(null)
  }

  function loadNextQuestion(modeOverride, tablesOverride) {
    setFeedback(null)
    fetchQuestion(playerId, tablesOverride ?? selectedTables, modeOverride ?? mode)
      .then(setQuestion)
      .catch(() => forgetPlayer())
  }

  function handleModeChange(newMode) {
    if (newMode === mode || answering) return
    setMode(newMode)
    loadNextQuestion(newMode)
  }

  function handleToggleTable(table) {
    if (answering) return
    const isSelected = selectedTables.includes(table)
    if (isSelected && selectedTables.length === 1) return // always keep at least one table active
    const next = isSelected
      ? selectedTables.filter((t) => t !== table)
      : [...selectedTables, table].sort((a, b) => a - b)
    setSelectedTables(next)
    localStorage.setItem('tablas.selectedTables', JSON.stringify(next))
    loadNextQuestion(undefined, next)
  }

  async function handleNameSubmit(event) {
    event.preventDefault()
    const trimmed = nameInput.trim()
    if (!trimmed) return
    try {
      const player = await createPlayer(trimmed)
      localStorage.setItem('tablas.playerId', player.playerId)
      localStorage.setItem('tablas.playerName', player.name)
      setPlayerName(player.name)
      setPlayerId(player.playerId)
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleAnswer(answer) {
    if (!question || answering) return
    setAnswering(true)
    try {
      const result = await submitAnswer(playerId, question.questionId, answer)
      setFeedback(result)
      setScore(result.totalScore)
      setLevel(result.level)
      setStreaks((prev) => ({ ...prev, [mode]: result.streak }))
      setTimeout(() => {
        setAnswering(false)
        loadNextQuestion()
      }, NEXT_QUESTION_DELAY_MS)
    } catch (err) {
      setAnswering(false)
      setError(err.message)
    }
  }

  if (!playerId) {
    return (
      <div className="app-shell welcome-shell">
        <h1>Tablas de Multiplicar 🧮</h1>
        <p>¿Cómo te llamas?</p>
        <form onSubmit={handleNameSubmit} className="name-form">
          <input
            autoFocus
            value={nameInput}
            onChange={(event) => setNameInput(event.target.value)}
            placeholder="Tu nombre"
          />
          <button type="submit">¡Empezar a jugar!</button>
        </form>
        {error && <p className="error-text">{error}</p>}
      </div>
    )
  }

  return (
    <div className="layout">
      <TableSidebar selectedTables={selectedTables} onToggle={handleToggleTable} />
      <div className="app-shell">
        <h1>¡Hola, {playerName}! 👋</h1>
        <div className="mode-toggle">
          <button
            className={`mode-button ${mode === QUIZ_MODES.MULTIPLE_CHOICE ? 'mode-button-active' : ''}`}
            onClick={() => handleModeChange(QUIZ_MODES.MULTIPLE_CHOICE)}
          >
            🎯 Normal
          </button>
          <button
            className={`mode-button ${mode === QUIZ_MODES.TYPE_ANSWER ? 'mode-button-active' : ''}`}
            onClick={() => handleModeChange(QUIZ_MODES.TYPE_ANSWER)}
          >
            🔥 Difícil
          </button>
        </div>
        <ScoreBar score={score} streak={streaks[mode]} level={level} />
        {question ? (
          <QuestionCard
            key={question.questionId}
            factorA={question.factorA}
            factorB={question.factorB}
            options={question.options}
            mode={mode}
            onAnswer={handleAnswer}
            disabled={answering}
          />
        ) : (
          <p>Cargando pregunta...</p>
        )}
        <Feedback feedback={feedback} />
        {error && <p className="error-text">{error}</p>}
      </div>
    </div>
  )
}
