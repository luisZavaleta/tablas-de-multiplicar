import { useState } from 'react'

export default function QuestionCard({ factorA, factorB, options, mode, onAnswer, disabled }) {
  const [typedAnswer, setTypedAnswer] = useState('')

  function handleTypedSubmit(event) {
    event.preventDefault()
    const trimmed = typedAnswer.trim()
    if (trimmed === '') return
    const parsed = Number(trimmed)
    if (Number.isNaN(parsed)) return
    onAnswer(parsed)
  }

  return (
    <div className="question-card">
      <p className="question-text">
        {factorA} × {factorB} = ?
      </p>
      {mode === 'TYPE_ANSWER' ? (
        <form className="type-answer-form" onSubmit={handleTypedSubmit}>
          <input
            autoFocus
            type="number"
            inputMode="numeric"
            className="type-answer-input"
            value={typedAnswer}
            onChange={(event) => setTypedAnswer(event.target.value)}
            disabled={disabled}
            placeholder="?"
          />
          <button type="submit" className="type-answer-submit" disabled={disabled}>
            Responder
          </button>
        </form>
      ) : (
        <div className="options-grid">
          {options.map((option) => (
            <button
              key={option}
              className="option-button"
              disabled={disabled}
              onClick={() => onAnswer(option)}
            >
              {option}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
