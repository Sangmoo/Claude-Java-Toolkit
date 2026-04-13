import { FaFileAlt } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function DocGenPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '기술 문서 생성',
        icon: FaFileAlt,
        iconColor: '#10b981',
        description: 'Java/Oracle 소스 코드로부터 기술 문서를 자동 생성합니다.',
        feature: 'doc_gen',
        inputLabel: '코드 입력',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'Java 클래스 또는 Oracle Package 소스를 입력하세요...',
        inputLanguage: 'java',
        options: [
          {
            name: 'sourceType',
            label: '소스 유형',
            type: 'select',
            defaultValue: 'java',
            options: [
              { value: 'java', label: 'Java Class' },
              { value: 'oracle_package', label: 'Oracle Package' },
              { value: 'spring_controller', label: 'Spring Controller' },
            ],
          },
        ],
      }}
    />
  )
}
