const ALL_TABLES = Array.from({ length: 10 }, (_, i) => i + 1)

export default function TableSidebar({ selectedTable, onSelect }) {
  return (
    <aside className="table-sidebar">
      <h2 className="table-sidebar-title">Tablas</h2>
      <div className="table-list">
        {ALL_TABLES.map((table) => (
          <button
            key={table}
            className={`table-chip ${table === selectedTable ? 'table-chip-active' : ''}`}
            onClick={() => onSelect(table)}
            aria-pressed={table === selectedTable}
          >
            {table}
          </button>
        ))}
      </div>
    </aside>
  )
}
