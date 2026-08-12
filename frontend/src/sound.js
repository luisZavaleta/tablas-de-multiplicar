let audioContext = null

function getAudioContext() {
  if (!audioContext) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    audioContext = new AudioContextClass()
  }
  if (audioContext.state === 'suspended') {
    audioContext.resume()
  }
  return audioContext
}

function playTone(frequency, startOffset, duration, type, peakVolume) {
  const ctx = getAudioContext()
  const startTime = ctx.currentTime + startOffset

  const oscillator = ctx.createOscillator()
  oscillator.type = type
  oscillator.frequency.value = frequency

  const gain = ctx.createGain()
  gain.gain.setValueAtTime(0, startTime)
  gain.gain.linearRampToValueAtTime(peakVolume, startTime + 0.02)
  gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration)

  oscillator.connect(gain)
  gain.connect(ctx.destination)
  oscillator.start(startTime)
  oscillator.stop(startTime + duration)
}

// Browsers only allow starting/resuming audio from inside a user-gesture
// call stack, so call this synchronously at the top of a click handler
// (before any `await`) to unlock sound for later, async playback.
export function unlockAudio() {
  getAudioContext()
}

export function playCorrectSound() {
  // Cheerful ascending major chime: C5 - E5 - G5
  playTone(523.25, 0, 0.15, 'triangle', 0.2)
  playTone(659.25, 0.1, 0.15, 'triangle', 0.2)
  playTone(783.99, 0.2, 0.25, 'triangle', 0.2)
}

export function playWrongSound() {
  // Soft two-note dip, gentle rather than harsh/buzzer-like
  playTone(392.0, 0, 0.18, 'sine', 0.15)
  playTone(311.13, 0.12, 0.25, 'sine', 0.15)
}

export function playSixSevenSound() {
  // Silly little seesaw: alternates back and forth like the "6 7" hand tilt
  const low = 392.0 // G4
  const high = 587.33 // D5
  const step = 0.11
  for (let i = 0; i < 6; i++) {
    playTone(i % 2 === 0 ? low : high, i * step, step, 'square', 0.12)
  }
}
