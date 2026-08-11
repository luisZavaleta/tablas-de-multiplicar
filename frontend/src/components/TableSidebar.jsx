const ALL_TABLES = Array.from({ length: 10 }, (_, i) => i + 1)

export default function TableSidebar({ selectedTables, onToggle }) {
  return (
    <aside className="table-sidebar">
      <h2 className="table-sidebar-title">Tablas</h2>
      <div className="table-list">
        {ALL_TABLES.map((table) => (
          <button
            key={table}
            className={`table-chip ${selectedTables.includes(table) ? 'table-chip-active' : ''}`}
            onClick={() => onToggle(table)}
            aria-pressed={selectedTables.includes(table)}
          >
            {table}
          </button>
        ))}
      </div>
    </aside>
  )
}
