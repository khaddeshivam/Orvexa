import { useState } from 'react';

function BrandMark() {
  return (
    <div className="brand-mark" aria-hidden="true">
      <span />
      <span />
      <span />
    </div>
  );
}

function IntelligenceCore() {
  return (
    <div className="intelligence-core">
      <div className="core-ring core-ring-1" />
      <div className="core-ring core-ring-2" />
      <div className="core-center">
        <span />
      </div>

      <div className="core-orbit orbit-1" />
      <div className="core-orbit orbit-2" />
      <div className="core-orbit orbit-3" />
    </div>
  );
}

function WelcomeScreen({ onStart }: { onStart: () => void }) {
  return (
    <main className="welcome-screen">
      <header className="onboarding-top">
        <div className="brand">
          <BrandMark />
          <span className="brand-wordmark">DOTBOT</span>
        </div>

        <div className="onboarding-progress" aria-label="Step 1 of 3">
          <span className="progress-step active" />
          <span className="progress-step" />
          <span className="progress-step" />
        </div>
      </header>

      <section className="hero">
        <div className="hero-stage">
          <div className="hero-glow" />

          <div className="floating-card floating-plan">
            <span className="floating-icon">✦</span>
            <span>Plan</span>
          </div>

          <div className="floating-card floating-focus">
            <span className="floating-icon">◉</span>
            <span>Focus</span>
          </div>

          <div className="floating-card floating-done">
            <span className="floating-icon">✓</span>
            <span>Done</span>
          </div>

          <IntelligenceCore />

          <div className="momentum-card">
            <div className="momentum-number">01</div>

            <div>
              <strong>Small win complete</strong>
              <p>Your momentum is building</p>
            </div>
          </div>
        </div>

        <div className="hero-copy">
          <span className="eyebrow">Thoughtful productivity</span>

          <h1 id="welcome-title">
            Turn small tasks into
            <br />
            real progress.
          </h1>

          <p className="hero-description">
            Your intelligent companion for getting things done.
          </p>

          <div className="hero-actions">
            <button className="primary-button" onClick={onStart}>
              Get Started
              <span>→</span>
            </button>

            <button className="secondary-button">
              I already have an account
            </button>
          </div>

          <p className="privacy-note">
            <span>✦</span>
            Private by design. Always in your control.
          </p>
        </div>
      </section>
    </main>
  );
}

function FocusScreen({
                       onContinue,
                     }: {
  onContinue: () => void;
}) {
  const [selected, setSelected] = useState<string | null>(null);

  const options = [
    'Getting organized',
    'Staying focused',
    'Making progress',
    'Reducing stress',
  ];

  return (
    <main className="simple-screen">
      <header className="onboarding-top">
        <div className="brand">
          <BrandMark />
          <span className="brand-wordmark">DOTBOT</span>
        </div>

        <div className="onboarding-progress">
          <span className="progress-step active" />
          <span className="progress-step active" />
          <span className="progress-step" />
        </div>
      </header>

      <section className="onboarding-content">
        <span className="eyebrow">A little context</span>

        <h1>What would make today feel lighter?</h1>

        <p>
          Pick what matters most right now. DotBot will use this to shape
          your experience.
        </p>

        <div className="focus-options">
          {options.map((option) => (
            <button
              key={option}
              className={`focus-option ${
                selected === option ? 'selected' : ''
              }`}
              onClick={() => setSelected(option)}
            >
              {option}
            </button>
          ))}
        </div>

        <button className="primary-button onboarding-button" onClick={onContinue}>
          Continue
          <span>→</span>
        </button>

        <button className="skip-button" onClick={onContinue}>
          Skip for now
        </button>
      </section>
    </main>
  );
}

function App() {
  const [screen, setScreen] = useState<'welcome' | 'focus'>('welcome');

  if (screen === 'focus') {
    return <FocusScreen onContinue={() => setScreen('welcome')} />;
  }

  return <WelcomeScreen onStart={() => setScreen('focus')} />;
}

export default App;
