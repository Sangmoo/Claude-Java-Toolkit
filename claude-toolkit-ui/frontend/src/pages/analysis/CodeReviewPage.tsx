import { FaCodeBranch } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function CodeReviewPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '코드 리뷰 하네스',
        icon: FaCodeBranch,
        iconColor: '#8b5cf6',
        description: '코드를 3단계(Analyst → Builder → Reviewer)로 심층 리뷰합니다.',
        feature: 'harness_review',
        inputLabel: '코드 입력',
        inputPlaceholder: 'Java/Spring 코드를 입력하세요...',
        inputLanguage: 'java',
        options: [
          {
            name: 'reviewType',
            label: '리뷰 유형',
            type: 'select',
            defaultValue: 'code_review',
            options: [
              { value: 'code_review', label: '코드 리뷰' },
              { value: 'code_review_security', label: '보안 리뷰' },
              { value: 'test_gen', label: '테스트 생성' },
              { value: 'refactor_gen', label: '리팩터링' },
              { value: 'javadoc_gen', label: 'Javadoc 생성' },
            ],
          },
        ],
      }}
    />
  )
}
