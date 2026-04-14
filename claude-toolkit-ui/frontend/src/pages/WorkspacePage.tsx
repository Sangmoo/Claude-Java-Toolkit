import { FaLayerGroup } from 'react-icons/fa'
import AnalysisPageTemplate from '../components/common/AnalysisPageTemplate'

/**
 * 통합 워크스페이스 — 4단계 하네스 파이프라인 실행
 * (Analyst → Builder → Reviewer → Verifier)
 */
export default function WorkspacePage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '통합 워크스페이스',
        icon: FaLayerGroup,
        iconColor: '#f97316',
        description: '4단계 심층 분석: Analyst(분석) → Builder(개선 코드) → Reviewer(리뷰) → Verifier(검증)',
        feature: 'harness_review', // 4단계 하네스 파이프라인 사용
        inputLabel: '코드 입력',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'Java/SQL 코드를 입력하거나 소스 선택/파일 드래그앤드롭...',
        inputLanguage: 'java',
        options: [
          {
            name: 'language',
            label: '언어',
            type: 'select',
            defaultValue: 'java',
            options: [
              { value: 'java', label: 'Java' },
              { value: 'sql', label: 'SQL / Oracle' },
            ],
          },
          {
            name: 'templateHint',
            label: '포커스',
            type: 'select',
            defaultValue: '',
            options: [
              { value: '', label: '균형' },
              { value: 'performance', label: '성능' },
              { value: 'security', label: '보안' },
              { value: 'refactoring', label: '리팩터링' },
              { value: 'readability', label: '가독성' },
            ],
          },
        ],
      }}
    />
  )
}
