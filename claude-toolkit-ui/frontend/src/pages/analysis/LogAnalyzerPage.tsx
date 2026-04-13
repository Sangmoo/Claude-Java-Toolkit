import { FaBug } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function LogAnalyzerPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '로그 분석기',
        icon: FaBug,
        iconColor: '#06b6d4',
        description: '로그 파일을 분석하여 오류 패턴과 보안 위협을 탐지합니다.',
        feature: 'log_analysis',
        inputLabel: '로그 입력',
        inputPlaceholder: '로그 텍스트를 입력하세요...',
        options: [
          {
            name: 'analysisType',
            label: '분석 유형',
            type: 'select',
            defaultValue: 'general',
            options: [
              { value: 'general', label: '일반 분석' },
              { value: 'security', label: '보안 위협 탐지' },
            ],
          },
        ],
      }}
    />
  )
}
