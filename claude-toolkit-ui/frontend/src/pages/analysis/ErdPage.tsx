import { FaProjectDiagram } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ErdPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'ERD 분석',
        icon: FaProjectDiagram,
        iconColor: '#3b82f6',
        description: 'DDL 또는 테이블 구조를 분석하여 ERD 다이어그램과 관계를 파악합니다.',
        feature: 'erd_analysis',
        inputLabel: 'DDL / 테이블 구조',
        sourceMode: 'sql',
        inputPlaceholder: 'CREATE TABLE 문 또는 테이블 구조를 입력하세요...',
      }}
    />
  )
}
