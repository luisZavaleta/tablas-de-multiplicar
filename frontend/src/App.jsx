import { useEffect, useState } from 'react'
import { createPlayer, fetchQuestion, fetchStats, fetchStreak, submitAnswer } from './api'
import QuestionCard from './components/QuestionCard'
import ScoreBar from './components/ScoreBar'
import Feedback from './components/Feedback'
import TableSidebar from './components/TableSidebar'
import { playCorrectSound, playWrongSound, unlockAudio } from './sound'

const DEFAULT_TABLE = 7
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
  const [selectedTable, setSelectedTable] = useState(() => {
    const saved = Number(localStorage.getItem('tablas.selectedTable'))
    return saved >= 1 && saved <= 10 ? saved : DEFAULT_TABLE
  })
  const [score, setScore] = useState(0)
  const [level, setLevel] = useState(1)
  const [streak, setStreak] = useState(0)
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
      })
      .catch(() => forgetPlayer())
    loadStreak()
    loadNextQuestion()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playerId])

  function forgetPlayer() {
    localStorage.removeItem('tablas.playerId')
    localStorage.removeItem('tablas.playerName')
    setPlayerId(null)
    setQuestion(null)
  }

  function loadNextQuestion(modeOverride, tableOverride) {
    setFeedback(null)
    fetchQuestion(playerId, [tableOverride ?? selectedTable], modeOverride ?? mode)
      .then(setQuestion)
      .catch(() => forgetPlayer())
  }

  function loadStreak(modeOverride, tableOverride) {
    fetchStreak(playerId, tableOverride ?? selectedTable, modeOverride ?? mode)
      .then((result) => setStreak(result.currentStreak))
      .catch(() => {})
  }

  function handleModeChange(newMode) {
    if (newMode === mode || answering) return
    setMode(newMode)
    loadNextQuestion(newMode)
    loadStreak(newMode)
  }

  function handleSelectTable(table) {
    if (table === selectedTable || answering) return
    setSelectedTable(table)
    localStorage.setItem('tablas.selectedTable', String(table))
    loadNextQuestion(undefined, table)
    loadStreak(undefined, table)
  }

  async function handleNameSubmit(event) {
    event.preventDefault()
    unlockAudio()
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
    unlockAudio()
    setAnswering(true)
    try {
      const result = await submitAnswer(playerId, question.questionId, answer)
      setFeedback(result)
      setScore(result.totalScore)
      setLevel(result.level)
      setStreak(result.streak)
      if (result.correct) {
        playCorrectSound()
      } else {
        playWrongSound()
      }
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
      <TableSidebar selectedTable={selectedTable} onSelect={handleSelectTable} />
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
        <ScoreBar score={score} streak={streak} level={level} />
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
