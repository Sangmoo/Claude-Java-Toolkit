import { useEffect, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'
import { useThemeStore } from './stores/themeStore'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './pages/LoginPage'
import './styles/theme.css'

// Lazy load all pages
const HomePage = lazy(() => import('./pages/HomePage'))
const ChatPage = lazy(() => import('./pages/ChatPage'))
const PipelinePage = lazy(() => import('./pages/PipelinePage'))
const PipelineExecutionPage = lazy(() => import('./pages/PipelineExecutionPage'))
const HistoryPage = lazy(() => import('./pages/HistoryPage'))
const FavoritesPage = lazy(() => import('./pages/FavoritesPage'))
const SettingsPage = lazy(() => import('./pages/SettingsPage'))
const SearchPage = lazy(() => import('./pages/SearchPage'))
const WorkspacePage = lazy(() => import('./pages/WorkspacePage'))
const UsagePage = lazy(() => import('./pages/UsagePage'))
const SchedulePage = lazy(() => import('./pages/SchedulePage'))
const ReviewRequestsPage = lazy(() => import('./pages/ReviewRequestsPage'))
const RoiReportPage = lazy(() => import('./pages/RoiReportPage'))
const PromptsPage = lazy(() => import('./pages/PromptsPage'))

// Analysis pages
const SqlReviewPage = lazy(() => import('./pages/analysis/SqlReviewPage'))
const SqlTranslatePage = lazy(() => import('./pages/analysis/SqlTranslatePage'))
const SqlBatchPage = lazy(() => import('./pages/analysis/SqlBatchPage'))
const ErdPage = lazy(() => import('./pages/analysis/ErdPage'))
const ComplexityPage = lazy(() => import('./pages/analysis/ComplexityPage'))
const ExplainPlanPage = lazy(() => import('./pages/analysis/ExplainPlanPage'))
const ExplainComparePage = lazy(() => import('./pages/analysis/ExplainComparePage'))
const DocGenPage = lazy(() => import('./pages/analysis/DocGenPage'))
const ApiSpecPage = lazy(() => import('./pages/analysis/ApiSpecPage'))
const ConverterPage = lazy(() => import('./pages/analysis/ConverterPage'))
const CodeReviewPage = lazy(() => import('./pages/analysis/CodeReviewPage'))
const HarnessBatchPage = lazy(() => import('./pages/analysis/HarnessBatchPage'))
const HarnessDependencyPage = lazy(() => import('./pages/analysis/HarnessDependencyPage'))
const MockDataPage = lazy(() => import('./pages/analysis/MockDataPage'))
const BatchPage = lazy(() => import('./pages/analysis/BatchPage'))
const LogAnalyzerPage = lazy(() => import('./pages/analysis/LogAnalyzerPage'))
const RegexPage = lazy(() => import('./pages/analysis/RegexPage'))
const CommitMsgPage = lazy(() => import('./pages/analysis/CommitMsgPage'))
const MaskGenPage = lazy(() => import('./pages/analysis/MaskGenPage'))
const InputMaskingPage = lazy(() => import('./pages/analysis/InputMaskingPage'))
const DepCheckPage = lazy(() => import('./pages/analysis/DepCheckPage'))
const MigratePage = lazy(() => import('./pages/analysis/MigratePage'))
const DbMigrationPage = lazy(() => import('./pages/analysis/DbMigrationPage'))
const GithubPrPage = lazy(() => import('./pages/analysis/GithubPrPage'))
const GitDiffPage = lazy(() => import('./pages/analysis/GitDiffPage'))

// Admin pages
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'))
const AdminHealthPage = lazy(() => import('./pages/admin/AdminHealthPage'))
const AuditLogPage = lazy(() => import('./pages/admin/AuditLogPage'))

function Loading() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '60px', color: 'var(--text-muted)', fontSize: '14px' }}>
      로딩 중...
    </div>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuthStore()
  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', color: 'var(--text-muted)', fontSize: '14px' }}>
        로딩 중...
      </div>
    )
  }
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  const checkAuth = useAuthStore((s) => s.checkAuth)
  const theme = useThemeStore((s) => s.theme)

  useEffect(() => { document.documentElement.setAttribute('data-theme', theme) }, [theme])
  useEffect(() => { checkAuth() }, [checkAuth])

  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          {/* Dashboard */}
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />

          {/* Chat & Pipeline */}
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/pipelines" element={<PipelinePage />} />
          <Route path="/pipelines/execution/:id" element={<PipelineExecutionPage />} />

          {/* SQL Analysis */}
          <Route path="/advisor" element={<SqlReviewPage />} />
          <Route path="/sql-translate" element={<SqlTranslatePage />} />
          <Route path="/sql-batch" element={<SqlBatchPage />} />
          <Route path="/erd" element={<ErdPage />} />
          <Route path="/complexity" element={<ComplexityPage />} />
          <Route path="/explain" element={<ExplainPlanPage />} />
          <Route path="/explain/compare" element={<ExplainComparePage />} />

          {/* Code Analysis & Generation */}
          <Route path="/workspace" element={<WorkspacePage />} />
          <Route path="/harness" element={<CodeReviewPage />} />
          <Route path="/harness/batch" element={<HarnessBatchPage />} />
          <Route path="/harness/dependency" element={<HarnessDependencyPage />} />
          <Route path="/docgen" element={<DocGenPage />} />
          <Route path="/apispec" element={<ApiSpecPage />} />
          <Route path="/converter" element={<ConverterPage />} />
          <Route path="/mockdata" element={<MockDataPage />} />
          <Route path="/migration" element={<DbMigrationPage />} />
          <Route path="/batch" element={<BatchPage />} />
          <Route path="/depcheck" element={<DepCheckPage />} />
          <Route path="/migrate" element={<MigratePage />} />

          {/* Tools */}
          <Route path="/loganalyzer" element={<LogAnalyzerPage />} />
          <Route path="/regex" element={<RegexPage />} />
          <Route path="/commitmsg" element={<CommitMsgPage />} />
          <Route path="/maskgen" element={<MaskGenPage />} />
          <Route path="/input-masking" element={<InputMaskingPage />} />
          <Route path="/github-pr" element={<GithubPrPage />} />
          <Route path="/git-diff" element={<GitDiffPage />} />

          {/* Records */}
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/favorites" element={<FavoritesPage />} />
          <Route path="/usage" element={<UsagePage />} />
          <Route path="/roi-report" element={<RoiReportPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/review-requests" element={<ReviewRequestsPage />} />

          {/* Settings & Prompts */}
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/prompts" element={<PromptsPage />} />

          {/* Admin */}
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/health" element={<AdminHealthPage />} />
          <Route path="/admin/audit-dashboard" element={<AuditLogPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
