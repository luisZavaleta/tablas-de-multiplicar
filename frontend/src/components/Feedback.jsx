export default function Feedback({ feedback }) {
  if (!feedback) {
    return <div className="feedback-placeholder" />
  }

  if (feedback.correct) {
    return (
      <div className="feedback feedback-correct">
        🎉 ¡Muy bien! +{feedback.pointsEarned} puntos
      </div>
    )
  }

  return (
    <div className="feedback feedback-wrong">
      😅 Casi... la respuesta era {feedback.correctAnswer}
    </div>
  )
}
