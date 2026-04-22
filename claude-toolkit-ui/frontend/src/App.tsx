import { useEffect, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'
import { useThemeStore } from './stores/themeStore'
import AppLayout from './components/layout/AppLayout'
import ErrorBoundary from './components/common/ErrorBoundary'
import LoginPage from './pages/LoginPage'
import './styles/theme.css'

// Lazy load all pages
const HomePage = lazy(() => import('./pages/HomePage'))
const ChatPage = lazy(() => import('./pages/ChatPage'))
const PipelinePage = lazy(() => import('./pages/PipelinePage'))
const PipelineEditorPage = lazy(() => import('./pages/PipelineEditorPage'))
const PipelineExecutionPage = lazy(() => import('./pages/PipelineExecutionPage'))
const PipelineHistoryPage = lazy(() => import('./pages/PipelineHistoryPage'))
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
const IndexAdvisorPage = lazy(() => import('./pages/analysis/IndexAdvisorPage'))
const SqlTranslatePage = lazy(() => import('./pages/analysis/SqlTranslatePage'))
const SqlBatchPage = lazy(() => import('./pages/analysis/SqlBatchPage'))
const ErdPage = lazy(() => import('./pages/analysis/ErdPage'))
const ComplexityPage = lazy(() => import('./pages/analysis/ComplexityPage'))
const ExplainPlanPage = lazy(() => import('./pages/analysis/ExplainPlanPage'))
const ExplainDashboardPage = lazy(() => import('./pages/analysis/ExplainDashboardPage'))
const HarnessDashboardPage = lazy(() => import('./pages/analysis/HarnessDashboardPage'))
const ExplainComparePage = lazy(() => import('./pages/analysis/ExplainComparePage'))
const DocGenPage = lazy(() => import('./pages/analysis/DocGenPage'))
const ApiSpecPage = lazy(() => import('./pages/analysis/ApiSpecPage'))
const ConverterPage = lazy(() => import('./pages/analysis/ConverterPage'))
const CodeReviewPage = lazy(() => import('./pages/analysis/CodeReviewPage'))
const HarnessBatchPage = lazy(() => import('./pages/analysis/HarnessBatchPage'))
const HarnessDependencyPage = lazy(() => import('./pages/analysis/HarnessDependencyPage'))
const FlowAnalysisPage = lazy(() => import('./pages/analysis/FlowAnalysisPage'))
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
const AdminPermissionsPage = lazy(() => import('./pages/admin/AdminPermissionsPage'))
const AdminHealthPage = lazy(() => import('./pages/admin/AdminHealthPage'))
const AdminBackupPage = lazy(() => import('./pages/admin/AdminBackupPage'))
const AuditLogPage = lazy(() => import('./pages/admin/AuditLogPage'))
const TeamDashboardPage = lazy(() => import('./pages/admin/TeamDashboardPage'))
const ReviewDashboardPage = lazy(() => import('./pages/admin/ReviewDashboardPage'))
const DbMigrationGuidePage = lazy(() => import('./pages/admin/DbMigrationGuidePage'))
const AdminEndpointStatsPage = lazy(() => import('./pages/admin/AdminEndpointStatsPage'))
const CostOptimizerPage = lazy(() => import('./pages/admin/CostOptimizerPage'))
const AdminErrorLogPage = lazy(() => import('./pages/admin/AdminErrorLogPage'))

// Special pages
const SetupPage = lazy(() => import('./pages/SetupPage'))
const TwoFactorPage = lazy(() => import('./pages/TwoFactorPage'))
const PasswordChangePage = lazy(() => import('./pages/PasswordChangePage'))
const ShareViewPage = lazy(() => import('./pages/ShareViewPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))
const ApiDocsPage = lazy(() => import('./pages/ApiDocsPage'))
const DbProfilesPage = lazy(() => import('./pages/DbProfilesPage'))
const SecurityPage = lazy(() => import('./pages/SecurityPage'))
const AccountPage = lazy(() => import('./pages/AccountPage'))
const SettingsPromptsPage = lazy(() => import('./pages/SettingsPromptsPage'))
const SharedConfigPage = lazy(() => import('./pages/SharedConfigPage'))

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
    <ErrorBoundary>
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
          <Route path="/pipelines/new" element={<PipelineEditorPage />} />
          <Route path="/pipelines/:id" element={<PipelineEditorPage />} />
          <Route path="/pipelines/execution/:id" element={<PipelineExecutionPage />} />
          <Route path="/pipelines/history" element={<PipelineHistoryPage />} />

          {/* SQL Analysis */}
          <Route path="/advisor" element={<SqlReviewPage />} />
          <Route path="/sql/index-advisor" element={<IndexAdvisorPage />} />
          <Route path="/sql-translate" element={<SqlTranslatePage />} />
          <Route path="/sql-batch" element={<SqlBatchPage />} />
          <Route path="/erd" element={<ErdPage />} />
          <Route path="/complexity" element={<ComplexityPage />} />
          <Route path="/explain" element={<ExplainPlanPage />} />
          <Route path="/explain/compare" element={<ExplainComparePage />} />
          <Route path="/explain/dashboard" element={<ExplainDashboardPage />} />

          {/* Code Analysis & Generation */}
          <Route path="/workspace" element={<WorkspacePage />} />
          <Route path="/harness" element={<CodeReviewPage />} />
          <Route path="/harness/batch" element={<HarnessBatchPage />} />
          <Route path="/harness/dependency" element={<HarnessDependencyPage />} />
          <Route path="/harness/dashboard" element={<HarnessDashboardPage />} />
          <Route path="/flow-analysis" element={<FlowAnalysisPage />} />
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
          <Route path="/admin/permissions" element={<AdminPermissionsPage />} />
          <Route path="/admin/team-dashboard" element={<TeamDashboardPage />} />
          <Route path="/admin/review-dashboard" element={<ReviewDashboardPage />} />
          <Route path="/admin/db-migration" element={<DbMigrationGuidePage />} />
          <Route path="/admin/backup" element={<AdminBackupPage />} />
          <Route path="/admin/endpoint-stats" element={<AdminEndpointStatsPage />} />
          <Route path="/admin/cost-optimizer" element={<CostOptimizerPage />} />
          <Route path="/admin/error-log" element={<AdminErrorLogPage />} />

          {/* Special */}
          <Route path="/api-docs" element={<ApiDocsPage />} />
          <Route path="/db-profiles" element={<DbProfilesPage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/settings/prompts" element={<SettingsPromptsPage />} />
          <Route path="/settings/shared" element={<SharedConfigPage />} />
        </Route>

        {/* Public routes (no auth) */}
        <Route path="/setup" element={<SetupPage />} />
        <Route path="/login/2fa" element={<TwoFactorPage />} />
        <Route path="/account/password" element={<PasswordChangePage />} />
        <Route path="/share/:token" element={<ShareViewPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
    </ErrorBoundary>
  )
}
