import { FaUserShield } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function MaskGenPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '마스킹 스크립트 생성',
        icon: FaUserShield,
        iconColor: '#06b6d4',
        description: 'DDL 기반으로 개인정보 마스킹 UPDATE 스크립트를 생성합니다.',
        feature: 'mask_gen',
        inputLabel: 'DDL 입력',
        inputPlaceholder: 'CREATE TABLE 문을 입력하세요...',
      }}
    />
  )
}
