import { FaGithub } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function GithubPrPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'GitHub PR 리뷰',
        icon: FaGithub,
        iconColor: '#8b5cf6',
        description: 'GitHub Pull Request 코드를 리뷰합니다.',
        feature: 'code_review',
        inputLabel: 'PR Diff',
        inputPlaceholder: 'PR diff 내용을 입력하세요...',
      }}
    />
  )
}
