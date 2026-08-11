const POINTS_PER_LEVEL = 100

export default function ScoreBar({ score, streak, level }) {
  const progressInLevel = score % POINTS_PER_LEVEL
  const progressPercent = (progressInLevel / POINTS_PER_LEVEL) * 100

  return (
    <div className="score-bar">
      <div className="score-bar-row">
        <span className="score-badge">🏅 Nivel {level}</span>
        <span className="score-badge">⭐ {score} pts</span>
        <span className="score-badge">🔥 Racha {streak}</span>
      </div>
      <div className="level-progress-track">
        <div className="level-progress-fill" style={{ width: `${progressPercent}%` }} />
      </div>
    </div>
  )
}
