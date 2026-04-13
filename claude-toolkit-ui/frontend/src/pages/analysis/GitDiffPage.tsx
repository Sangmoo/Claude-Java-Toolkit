import { FaCodeBranch } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function GitDiffPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'Git Diff 분석',
        icon: FaCodeBranch,
        iconColor: '#f97316',
        description: 'Git diff를 분석하여 변경사항을 리뷰합니다.',
        feature: 'code_review',
        inputLabel: 'Diff 입력',
        inputPlaceholder: 'git diff 결과를 입력하세요...',
      }}
    />
  )
}
