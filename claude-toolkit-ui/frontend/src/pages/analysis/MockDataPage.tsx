import { FaTable } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function MockDataPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'Mock 데이터 생성',
        icon: FaTable,
        iconColor: '#10b981',
        description: 'DDL 기반으로 테스트용 Mock INSERT 문을 자동 생성합니다.',
        feature: 'mock_data',
        inputLabel: 'DDL 입력',
        sourceMode: 'sql',
        inputPlaceholder: 'CREATE TABLE 문을 입력하세요...',
      }}
    />
  )
}
