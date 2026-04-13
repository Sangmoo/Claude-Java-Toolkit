import { FaEyeSlash } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function InputMaskingPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '민감정보 마스킹',
        icon: FaEyeSlash,
        iconColor: '#06b6d4',
        description: '텍스트에서 민감정보를 자동 감지하고 마스킹합니다.',
        feature: 'input_masking',
        inputLabel: '텍스트 입력',
        inputPlaceholder: '민감정보가 포함된 텍스트를 입력하세요...',
      }}
    />
  )
}
