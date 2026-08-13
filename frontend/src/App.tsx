const principles = [
  ["Transparent", "Inspect every game used in a calculation."],
  ["Contextual", "Compare matchup results with the player's baseline."],
  ["Responsible", "See sample size and data freshness before drawing conclusions."],
];

export default function App() {
  return (
    <main>
      <nav aria-label="Primary navigation">
        <a className="brand" href="/">SplitEdge</a>
        <span className="status">Foundation ready</span>
      </nav>

      <section className="hero">
        <p className="eyebrow">NBA MATCHUP ANALYTICS</p>
        <h1>Understand the matchup behind the prop.</h1>
        <p className="intro">
          SplitEdge turns historical game data into opponent-specific player-prop
          reports with visible evidence and honest sample-size warnings.
        </p>
        <button type="button" disabled>Prop Research begins in Milestone 3</button>
      </section>

      <section className="principles" aria-label="Product principles">
        {principles.map(([title, description]) => (
          <article key={title}>
            <h2>{title}</h2>
            <p>{description}</p>
          </article>
        ))}
      </section>

      <footer>
        Historical sports analytics for informational and educational purposes.
        Historical trends do not guarantee future results.
      </footer>
    </main>
  );
}
