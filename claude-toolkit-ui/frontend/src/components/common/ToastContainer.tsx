import { useToastStore } from '../../stores/toastStore'
import { FaTimes, FaCheckCircle, FaExclamationCircle, FaExclamationTriangle, FaInfoCircle } from 'react-icons/fa'

const icons = {
  success: <FaCheckCircle style={{ color: 'var(--green)', flexShrink: 0 }} />,
  error: <FaExclamationCircle style={{ color: 'var(--red)', flexShrink: 0 }} />,
  warning: <FaExclamationTriangle style={{ color: 'var(--yellow)', flexShrink: 0 }} />,
  info: <FaInfoCircle style={{ color: 'var(--blue)', flexShrink: 0 }} />,
}

export default function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts)
  const remove = useToastStore((s) => s.remove)

  if (toasts.length === 0) return null

  return (
    <div className="toast-container">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`toast-item ${t.type}${t.removing ? ' removing' : ''}`}
        >
          {icons[t.type]}
          <span>{t.message}</span>
          <button className="toast-close" onClick={() => remove(t.id)}>
            <FaTimes />
          </button>
        </div>
      ))}
    </div>
  )
}
